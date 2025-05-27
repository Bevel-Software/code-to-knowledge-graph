package software.bevel.code_to_knowledge_graph.vscode

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import software.bevel.file_system_domain.absolutizePath
import software.bevel.file_system_domain.LCPosition
import software.bevel.file_system_domain.LCRange
import software.bevel.graph_domain.graph.*
import software.bevel.graph_domain.graph.builder.MutableMapConnectionsBuilder
import software.bevel.graph_domain.parsing.ConnectionParser
import software.bevel.graph_domain.parsing.PotentialSymbol
import software.bevel.file_system_domain.relativizePath
import software.bevel.file_system_domain.services.FileHandler
import software.bevel.file_system_domain.web.LocalCommunicationInterface
import software.bevel.code_to_knowledge_graph.vscode.data.*
import software.bevel.code_to_knowledge_graph.vscode.languageSpecs.GeneralLanguageSpecification
import software.bevel.code_to_knowledge_graph.vscode.languageSpecs.VsCodeLanguageSpecification
import software.bevel.file_system_domain.services.CachedIoFileHandler
import java.io.File
import java.nio.file.Path
import kotlin.math.abs

/**
 * A [ConnectionParser] implementation that leverages a VS Code extension's language server capabilities
 * (like "go to definition" and "find all references") to discover connections (e.g., calls, inheritance)
 * between [Node]s in a [Graphlike] structure.
 *
 * This parser communicates with the VS Code extension via a [LocalCommunicationInterface] to request information
 * about symbols and their relationships.
 *
 * @property projectPath The absolute path to the root of the project being analyzed.
 *                       This is used to resolve relative file paths and provide context to the VS Code extension.
 * @property commsChannel The [LocalCommunicationInterface] used to send commands to and receive responses from
 *                        the VS Code extension.
 * @property fileHandler A [FileHandler] instance for reading file contents.
 *                       Defaults to [CachedIoFileHandler].
 * @property languageSpecification A [VsCodeLanguageSpecification] that provides language-specific logic for
 *                                 identifying potential symbols and handling special connection types.
 *                                 Defaults to [GeneralLanguageSpecification].
 * @property logger An SLF4J [Logger] instance for logging parsing activities and errors.
 */
class VsCodeConnectionParser(
    private val projectPath: String,
    private val commsChannel: LocalCommunicationInterface,
    private val fileHandler: FileHandler = CachedIoFileHandler(),
    private val languageSpecification: VsCodeLanguageSpecification = GeneralLanguageSpecification(fileHandler),
    private val logger: Logger = LoggerFactory.getLogger(VsCodeConnectionParser::class.java)
): ConnectionParser {
    /**
     * Internal helper class responsible for the actual processing of nodes to find connections.
     * It groups nodes by path and uses a [BatchProcessor] to efficiently query the VS Code extension.
     *
     * @property languageSpecification The language-specific rules and helpers.
     * @property projectPath The root path of the project.
     * @property commsChannel The communication channel to the VS Code extension.
     * @property fileHandler Handler for file system operations.
     * @property pathGroupedNodes A map of file paths to the list of [Node]s contained within that file.
     * @property graph The [Graphlike] structure containing all nodes being analyzed.
     * @property logger Logger for this processor.
     */
    private class ConnectionParsingProcessor(
        private val languageSpecification: VsCodeLanguageSpecification,
        private val projectPath: String,
        private val commsChannel: LocalCommunicationInterface,
        private val fileHandler: FileHandler,
        val pathGroupedNodes: Map<String, List<Node>>,
        val graph: Graphlike,
        private val logger: Logger
    )
    {
        /**
         * Creates inbound connections for a given set of nodes.
         * For each node, it queries the VS Code extension for references (i.e., where this node is used).
         * It also allows the [languageSpecification] to contribute special inbound connections.
         *
         * @param nodes A vararg array of [Node] IDs for which to find inbound connections.
         * @return A list of [Connection] objects representing the discovered inbound connections.
         */
        fun createInboundConnectionsForNodes(vararg nodes: String): List<Connection> {
            logger.info("Creating inbound connections for nodes: ${nodes.size}")
            val connections = mutableSetOf<Connection>()
            BatchProcessor(commsChannel).use { batchProcessor ->
                nodes.forEach { nodeId ->
                    val node = graph.nodes[nodeId] ?: return@forEach
                    val absoluteFilePath = absolutizePath(node.filePath, projectPath)
                    languageSpecification.handleSpecialInboundConnections(projectPath, nodeId, graph, connections)
                    if(node.filePath == "" || !fileHandler.isFile(absoluteFilePath)) {
                        return@forEach
                    }
                    batchProcessor.batchSendRequest(
                        VsCodeCommand(
                            "vscode.executeReferenceProvider",
                            listOf(VsCodeLocation(absoluteFilePath, VsCodeRange(node.nameLocation.start.line, node.nameLocation.start.column, node.nameLocation.start.line, node.nameLocation.end.column)))
                        ),
                        callback = { response ->
                            if (response !is VsCodeLocationResponse) {
                                logger.error("Response was not of type VsCodeLocationResponse")
                                return@batchSendRequest
                            }
                            response.locations.forEach { location ->
                                logger.info("Callback creating connection ${nodeId} at ${location.filePath} ${location.range}")
                                val callerId = getBestNodeMatchForLocation(nodeId, relativizePath(location.filePath, projectPath), location.range.toLCRange())
                                if(callerId != null) {
                                    val connectionType = getConnectionType(callerId, nodeId, location.range.startLineColumnPosition())
                                    connections.add(
                                        Connection(
                                            callerId,
                                            nodeId,
                                            connectionType,
                                            relativizePath(location.filePath, projectPath),
                                            location.range.toLCRange()
                                        )
                                    )
                                }
                            }
                        }
                    )
                }
            }
            return connections.toList()
        }

        /**
         * Creates outbound connections for a given set of nodes.
         * For each node, it analyzes its content to find potential symbols and then queries the VS Code extension
         * for the definitions of these symbols (i.e., what these symbols refer to).
         *
         * @param nodes A vararg array of [Node] IDs for which to find outbound connections.
         * @return A list of [Connection] objects representing the discovered outbound connections.
         */
        fun createOutboundConnectionsForNodes(vararg nodes: String): List<Connection> {
            logger.info("Creating outbound connections for nodes: ${nodes.size}, ${nodes.firstOrNull()}")
            val connections = mutableSetOf<Connection>()
            BatchProcessor(commsChannel, maxBatchSize = 100).use { batchProcessor ->
                nodes.mapNotNull { graph.nodes[it] }.forEach { originalNode ->
                    processNodeForOutboundConnection(originalNode, batchProcessor, connections)
                }
            }
            return connections.toList()
        }

        /**
         * Creates outbound connections for all nodes within a given set of files.
         * It reads each file, identifies potential symbols using the [languageSpecification],
         * and then queries the VS Code extension for the definitions of these symbols.
         * Progress updates are sent via the [commsChannel].
         *
         * @param files A vararg array of file paths (can be absolute or relative to [projectPath])
         *              for which to find outbound connections from all contained nodes.
         * @return A list of [Connection] objects representing the discovered outbound connections.
         */
        fun createOutboundConnectionsForAllNodesInFiles(vararg files: String): List<Connection> {
            logger.info("Creating outbound connections for ${files.size} files")
            val connections = mutableSetOf<Connection>()
            BatchProcessor(commsChannel, maxBatchSize = 100).use { batchProcessor ->
                files.forEachIndexed { fileIndex, file ->
                    if(fileIndex % 10 == 0) {
                        logger.info("Files analysed for connections ${fileIndex + 1}/${files.size}")
                        val message = jacksonObjectMapper().writeValueAsString(VsCodeCommand("progressUpdate", 55 + (fileIndex + 1) * 45 / files.size))
                        commsChannel.sendWithoutResponse(message)
                    }
                    val relativePath = relativizePath(file, projectPath)
                    val absolutePath = absolutizePath(file, projectPath)
                    val code = fileHandler.readString(absolutePath)
                    val potentialSymbols = languageSpecification.getPotentialSymbols(projectPath, relativePath, code)

                    handleSpecialOutboundConnectionsForEntireFile(relativePath, connections)

                    potentialSymbols.forEachIndexed { index, potentialSymbol ->
                        val pos = potentialSymbol.startPosition
                        val command = VsCodeCommand(
                            "vscode.executeDefinitionProvider",
                            listOf(
                                VsCodeLocation(
                                    absolutePath,
                                    VsCodeRange(pos.line, pos.column, 0, 0)
                                )
                            )
                        )

                        batchProcessor.batchSendRequest(
                            command,
                            callback = { response ->
                                if(index % 100 == 0) {
                                    logger.info("Processing ${potentialSymbol.name} ${index + 1}/${potentialSymbols.size}")
                                }
                                val originalNodeId = getBestNodeMatchForLocation("", relativePath, LCRange(potentialSymbol.startPosition, potentialSymbol.endPosition)) ?: return@batchSendRequest
                                val originalNode = graph[originalNodeId]
                                if(originalNode == null) {
                                    logger.error("Returned name: $originalNodeId not found. Potential symbol: $potentialSymbol")
                                    return@batchSendRequest
                                }
                                outboundDefinitionsCallback(response, potentialSymbol, originalNode, relativePath, connections)
                            }
                        )
                    }
                }
            }
            return connections.toList()
        }

        /**
         * Handles special outbound connections for an entire file, as defined by the [languageSpecification].
         * This is typically used for file-level dependencies like imports or module declarations.
         * It finds the [Node] representing the file itself and then delegates to the language specification.
         * If a connection's source node can be refined to a more specific node within the file, it's updated.
         *
         * @param relativePath The relative path of the file being processed.
         * @param connections A mutable set to which discovered [Connection]s will be added.
         */
        fun handleSpecialOutboundConnectionsForEntireFile(relativePath: String, connections: MutableSet<Connection>) {
            val fileNode = pathGroupedNodes[relativePath]?.find { it.nodeType == NodeType.File } ?: return
            val initialConnections = mutableSetOf<Connection>()
            languageSpecification.handleSpecialOutboundConnections(
                projectPath,
                fileNode.id,
                graph,
                initialConnections
            )
            initialConnections.forEach {
                val actualDefiner = getBestNodeMatchForLocation("", relativePath, it.location)
                if (actualDefiner != null) {
                    connections.add(it.copy(sourceNodeName = actualDefiner))
                } else connections.add(it)
            }
        }

        /**
         * Processes a single [Node] to find its outbound connections.
         * It allows the [languageSpecification] to contribute special outbound connections first.
         * Then, it reads the code content associated with the node and calls [getDefinitionsOfSymbolsInCode]
         * to find definitions for symbols within that code.
         *
         * @param originalNode The [Node] for which to find outbound connections.
         * @param batchProcessor The [BatchProcessor] to use for sending requests to VS Code.
         * @param connections A mutable set to which discovered [Connection]s will be added.
         */
        private fun processNodeForOutboundConnection(originalNode: Node, batchProcessor: BatchProcessor, connections: MutableSet<Connection>) {
            val originalNodeAbsolutePath = absolutizePath(originalNode.filePath, projectPath)
            languageSpecification.handleSpecialOutboundConnections(projectPath, originalNode.id, graph, connections)
            if(originalNode.filePath == "" || !fileHandler.isFile(originalNodeAbsolutePath)) {
                return
            }
            val code = fileHandler.readString(originalNodeAbsolutePath, originalNode.codeLocation)
            logger.info("Getting Symbols...")
            getDefinitionsOfSymbolsInCode(originalNode, code, originalNode.filePath, connections, batchProcessor)
            //findPossibleNodesInOtherFileAndFindTheirReferences(originalNodeId, code, defCon, graph, connections, batchProcessor, originalNodeAbsolutePath)
        }

        /**
         * Identifies potential symbols within a given code snippet (associated with an [originalNode])
         * and queries the VS Code extension for their definitions to create outbound connections.
         * The positions of symbols are adjusted relative to the start of the [originalNode]'s code location.
         *
         * @param originalNode The [Node] from which the code snippet originates and which will be the source of connections.
         * @param code The actual code string to analyze for symbols.
         * @param originalRelativeFilePath The relative path of the file containing the [originalNode].
         * @param connections A mutable set to which discovered [Connection]s will be added.
         * @param batchProcessor The [BatchProcessor] used for sending definition provider requests.
         */
        private fun getDefinitionsOfSymbolsInCode(originalNode: Node, code: String, originalRelativeFilePath: String, connections: MutableSet<Connection>, batchProcessor: BatchProcessor) {
            val potentialSymbols = languageSpecification.getPotentialSymbols(projectPath, originalRelativeFilePath, code).map {
                it.copy(startPosition = it.startPosition + originalNode.codeLocation.start, endPosition = it.endPosition + originalNode.codeLocation.start)
            }
            logger.info("Found ${potentialSymbols.size} symbols...")
            val absPath = absolutizePath(originalRelativeFilePath, projectPath)

            potentialSymbols.forEachIndexed { index, potentialSymbol ->
                val pos = potentialSymbol.startPosition
                val command = VsCodeCommand(
                    "vscode.executeDefinitionProvider",
                    listOf(
                        VsCodeLocation(
                            absPath,
                            VsCodeRange(pos.line, pos.column, 0, 0)
                        )
                    )
                )

                batchProcessor.batchSendRequest(
                    command,
                    callback = { response ->
                        if(index % 100 == 0) {
                            logger.info("Processing ${potentialSymbol.name} ${index + 1}/${potentialSymbols.size}")
                        }
                        outboundDefinitionsCallback(response, potentialSymbol, originalNode, originalRelativeFilePath, connections)
                    }
                )
            }
        }

        private fun handleOverrides(
            originalNode: Node,
            potentialSymbol: PotentialSymbol,
            connections: MutableSet<Connection>,
        ): Boolean {
            if (originalNode.id.endsWith(".${potentialSymbol.name}()")) {
                graph.nodes.values
                    .filter { it.simpleName == potentialSymbol.name && it.filePath != originalNode.filePath }
                    .mapNotNull { graph.nodes[it.id] }
                    .filter {
                        originalNode.nodeSignature == it.nodeSignature
                    }
                    .forEach {
                        val addedPos = potentialSymbol.startPosition
                        connections.add(
                            Connection(
                                originalNode.id,
                                it.id,
                                ConnectionType.OVERRIDES,
                                originalNode.filePath,
                                LCRange(addedPos, LCPosition(addedPos.line, addedPos.column + potentialSymbol.name.length))
                            )
                        )
                    }
                return true
            }
            return false
        }
        
        /**
         * Handles the callback from the VS Code extension for a definition provider request.
         * It processes the [VsCodeLocationResponse] to identify the target node(s) of a potential symbol.
         * If the definition is within the project, it attempts to find the most specific [Node] matching the location.
         * If the definition is external, it delegates to [handleExternalFileForOutbound].
         * Connections are created with an appropriate [ConnectionType] (e.g., USES).
         *
         * @param response The [NodeRequestResponse] from the VS Code extension, expected to be a [VsCodeLocationResponse].
         * @param potentialSymbol The [PotentialSymbol] for which the definition was requested.
         * @param originalNode The source [Node] from which the [potentialSymbol] originates.
         * @param originalRelativeFilePath The relative file path of the [originalNode].
         * @param connections A mutable set to which new [Connection]s will be added.
         */
        private fun outboundDefinitionsCallback(
            response: NodeRequestResponse,
            potentialSymbol: PotentialSymbol,
            originalNode: Node,
            originalRelativeFilePath: String,
            connections: MutableSet<Connection>,
        ) {
            if (response !is VsCodeLocationResponse) {
                logger.error("Response was not of type VsCodeLocationResponse")
                return
            }
            //BevelLogger.logger.info("Callback ${potentialSymbol.name} creating ${response.locations.size} connections for ${originalNode.id} ")
            response.locations.flatMapIndexed { index, loc ->
                var filePath = loc.filePath
                if(filePath.startsWith("file:")) {
                    filePath = filePath.substring(5)
                    while (filePath.startsWith("/") || filePath.startsWith("\\")) {
                        filePath = filePath.substring(1)
                    }
                }
                val location = loc.copy(filePath = filePath)
                val relativePath = relativizePath(filePath, projectPath)
                if (relativePath.contains("..") || File(relativePath).isAbsolute
                    || (relativePath.contains("node_modules") && relativePath.endsWith(".ts") || relativePath.endsWith(".js"))
                    ) {
                    handleExternalFileForOutbound(location, potentialSymbol, originalNode, originalRelativeFilePath)
                } else {
                    //File in this repo
                    val calleeId = getBestNodeMatchForLocation(
                        originalNode.id,
                        potentialSymbol.name,
                        relativePath,
                        location.range.toLCRange()
                    ) ?: return@flatMapIndexed listOf()
                    if(calleeId == originalNode.id || calleeId == languageSpecification.defaultPackageName) {
                        return@flatMapIndexed listOf()
                    }

                    val callee = graph.nodes[calleeId]
                    if(callee != null && callee.codeLocation.start.line < response.locations[index].range.startLine - 1 && callee.nameLocation.start.line < response.locations[index].range.startLine - 1) {
                        //BevelLogger.logger.info("Resolved potential symbol ${potentialSymbol.name} at ${response.locations[index].range} to ${calleeId} at ${callee.nameLocation} but it is not the correct node")
                        return@flatMapIndexed listOf()
                    }
                    
                    val connectionType = getConnectionType(originalNode.id, calleeId, location.range.startLineColumnPosition())

                    listOf(
                        Connection(
                            sourceNodeName = originalNode.id,
                            targetNodeName = calleeId,
                            connectionType = connectionType,
                            filePath = originalRelativeFilePath,
                            location = LCRange(potentialSymbol.startPosition, potentialSymbol.endPosition)
                        )
                    )
                }
            }.forEach { con ->
                connections.add(con)
            }
        }

        /**
         * Handles cases where a symbol's definition is located in a file external to the current project
         * (e.g., standard library, external dependency not directly part of the analyzed codebase).
         *
         * It first checks if a node with the exact [potentialSymbol].name already exists in the graph (e.g., a pre-defined SDK node).
         * If so, a connection is made to that node.
         * Otherwise, it attempts to find nodes in the graph that reside in a file with the same name as the external file path's
         * file name and also have the same simple name as the [potentialSymbol]. This is a heuristic to link to potential
         * representations of external symbols if they exist in the graph (e.g., from a different parsing mechanism or a partial SDK graph).
         *
         * @param location The [VsCodeLocation] pointing to the external definition.
         * @param potentialSymbol The [PotentialSymbol] whose definition is external.
         * @param originalNode The source [Node] from which the [potentialSymbol] originates.
         * @param originalRelativeFilePath The relative file path of the [originalNode].
         * @return A list of [Connection] objects to the inferred external targets, or an empty list if no suitable target is found.
         */
        private fun handleExternalFileForOutbound(
            location: VsCodeLocation,
            potentialSymbol: PotentialSymbol,
            originalNode: Node,
            originalRelativeFilePath: String,
        ): List<Connection> {
            val connections: List<Connection> =
                if(graph.nodes.contains(potentialSymbol.name)) {
                    listOf(Connection(
                        originalNode.id,
                        potentialSymbol.name,
                        ConnectionType.USES,
                        originalRelativeFilePath,
                        location = LCRange(potentialSymbol.startPosition, potentialSymbol.endPosition)
                    ))
                } else listOf()

            //File not in this repo
            val fileName = Path.of(location.filePath).fileName
            return graph.nodes.values
                .filter { it.simpleName == potentialSymbol.name }
                .filter { it.filePath.contains("/${fileName}") || it.filePath.contains("\\${fileName}") }
                .map {
                    Connection(
                        originalNode.id,
                        it.id,
                        ConnectionType.USES,
                        originalRelativeFilePath,
                        LCRange(potentialSymbol.startPosition, potentialSymbol.endPosition)
                    )
                } + connections
        }

        /**
         * Finds the best matching [Node] for a given location, considering a potential simple name.
         * It prioritizes nodes in the [potentialPath] that match the [potentialSimpleName] and are close to the [potentialLocation].
         * If no such named node is found or is not close enough, it falls back to the overload of
         * [getBestNodeMatchForLocation] that only considers path and location.
         *
         * @param originalNodeId The ID of the node from which the search originates (to avoid self-references in some contexts).
         * @param potentialSimpleName The simple name of the symbol being looked up.
         * @param potentialPath The relative file path where the symbol is expected to be defined.
         * @param potentialLocation The [LCRange] of the symbol's occurrence.
         * @return The ID of the best matching [Node], or `null` if no suitable match is found.
         */
        private fun getBestNodeMatchForLocation(originalNodeId: String, potentialSimpleName: String, potentialPath: String, potentialLocation: LCRange): String? {
            val allPotentialMatches = (pathGroupedNodes[potentialPath] ?: listOf()).filter {
                it.simpleName == potentialSimpleName
            }.sortedBy { abs(it.nameLocation.start.line - potentialLocation.start.line) }
            if (allPotentialMatches.isNotEmpty()
                && (abs(allPotentialMatches[0].nameLocation.start.line - potentialLocation.start.line) < 2
                        || abs(allPotentialMatches[0].codeLocation.start.line - potentialLocation.start.line) < 2)
            ) {
                return allPotentialMatches[0].id
            }
            return getBestNodeMatchForLocation(originalNodeId, potentialPath, potentialLocation)
        }

        /**
         * Finds the best matching [Node] for a given location within a specific file.
         * It searches for nodes in the [potentialPath] whose `codeLocation` encloses the given [potentialLocation].
         * Among these, it selects the most specific (innermost) node that is not the [originalNodeId] itself.
         * If no such enclosing node is found, it defaults to the [VsCodeLanguageSpecification.defaultPackageName],
         * implying the symbol might belong to a global or default namespace if not resolved to a specific node.
         *
         * @param originalNodeId The ID of the node from which the search originates (to avoid self-references).
         * @param potentialPath The relative file path to search within.
         * @param potentialLocation The [LCRange] to find an enclosing node for.
         * @return The ID of the best matching [Node], or the default package name if no suitable enclosing node is found.
         */
        private fun getBestNodeMatchForLocation(originalNodeId: String, potentialPath: String, potentialLocation: LCRange): String? {
                val sourceNode = (pathGroupedNodes[potentialPath] ?: listOf())
                .filter {
                    potentialLocation.start.line >= it.codeLocation.start.line && potentialLocation.end.line <= it.codeLocation.end.line
                            && it.id != originalNodeId
                }
                .fold<Node, Node?>(null) { currentBestSourceConnection, connection ->
                    if(currentBestSourceConnection == null || connection.codeLocation in (currentBestSourceConnection.codeLocation)) {
                        return@fold connection
                    }
                    currentBestSourceConnection
                }
            if (sourceNode != null) {
                if(sourceNode.id == originalNodeId) {
                    return null
                }
                return sourceNode.id
            } else {
                return languageSpecification.defaultPackageName
            }
        }

        /**
         * Determines the [ConnectionType] between a parent and a child node based on their types and the reference location.
         * For example, if both are classes and the child reference appears before the class body of the parent,
         * it might infer an [ConnectionType.INHERITS_FROM] relationship.
         * Otherwise, it defaults to [ConnectionType.USES].
         * Heuristics are used to determine inheritance for classes based on token positions.
         *
         * @param parentNodeId The ID of the potential parent (source) node in the connection.
         * @param childNodeId The ID of the potential child (target) node in the connection.
         * @param childNodeReferencePosition The [LCPosition] where the [childNodeId] is referenced within the context of the [parentNodeId].
         * @return The inferred [ConnectionType].
         */
        private fun getConnectionType(parentNodeId: String, childNodeId: String, childNodeReferencePosition: LCPosition): ConnectionType {
            val parentNode = graph.nodes[parentNodeId]
            val childNode = graph.nodes[childNodeId]
            if (parentNode == null || childNode == null) return ConnectionType.USES
            if (parentNode.nodeType == NodeType.Class && childNode.nodeType == NodeType.Class) {
                val sourceCode = fileHandler.readString(absolutizePath(parentNode.filePath, projectPath), parentNode.codeLocation)
                val token = if(sourceCode.contains("{")) '{'
                else if(sourceCode.contains(":")) ':'
                else '\n'
                var nestingLevel = 0
                var startIndexOfClassBody = parentNode.codeLocation.start
                for (c in sourceCode) {
                    when (c) {
                        token -> {
                            if (nestingLevel == 0) {
                                break
                            }
                            nestingLevel++
                        }
                        '(' -> nestingLevel++
                        ')' -> nestingLevel--
                        '\n' -> startIndexOfClassBody += LCPosition(1, 0)
                    }
                    if(c != '\n')
                        startIndexOfClassBody += 1
                }
                if (nestingLevel == 0) {
                    if(childNodeReferencePosition < startIndexOfClassBody) {
                        return ConnectionType.INHERITS_FROM
                    }
                }
            } else if(parentNode.nodeType == NodeType.Function && childNode.nodeType == NodeType.Function) {
                return ConnectionType.USES
                /*if(sourceNode.name.substring(sourceNode.name.lastIndexOf('.') + 1) == targetNode.name.substring(targetNode.name.lastIndexOf('.') + 1)) {
                    val targetNodeDefinition = graph.connections.findConnectionsToOfType(targetNode.name, ConnectionType.DEFINES).firstOrNull()
                        ?: return ConnectionType.USES
                    val sourceParent = graph.nodes[sourceNodeDefinition.sourceNodeName] ?: return ConnectionType.USES
                    val targetParent = graph.nodes[targetNodeDefinition.sourceNodeName] ?: return ConnectionType.USES
                    if (graph.connections.findConnections(sourceParent.name, targetParent.name, ConnectionType.INHERITS_FROM).isNotEmpty()) {
                        return ConnectionType.OVERRIDES
                    }
                }*/
            }
            return ConnectionType.USES
        }
    }

    /**
     * Creates inbound connections for a given set of nodes.
     * It delegates to the [ConnectionParsingProcessor] to perform the actual processing.
     *
     * @param nodes A vararg array of [Node] IDs for which to find inbound connections.
     * @param graph The [Graphlike] structure containing all nodes being analyzed.
     * @return A list of [Connection] objects representing the discovered inbound connections.
     */
    override fun createInboundConnectionsForNodes(vararg nodes: String, graph: Graphlike): List<Connection> {
        val cachedGraph = if(graph is Graph) graph else Graph(
            graph.nodes.toMap(),
            MutableMapConnectionsBuilder(graph.connections)
        )
        return ConnectionParsingProcessor(
            languageSpecification,
            projectPath,
            commsChannel,
            fileHandler,
            cachedGraph.nodes.values.groupBy { it.filePath },
            cachedGraph,
            logger
        ).createInboundConnectionsForNodes(*nodes)
    }

    /**
     * Creates outbound connections for a given set of nodes.
     * It delegates to the [ConnectionParsingProcessor] to perform the actual processing.
     *
     * @param nodes A vararg array of [Node] IDs for which to find outbound connections.
     * @param graph The [Graphlike] structure containing all nodes being analyzed.
     * @return A list of [Connection] objects representing the discovered outbound connections.
     */
    override fun createOutboundConnectionsForNodes(vararg nodes: String, graph: Graphlike): List<Connection> {
        val cachedGraph = if(graph is Graph) graph else Graph(
            graph.nodes.toMap(),
            MutableMapConnectionsBuilder(graph.connections)
        )
        return ConnectionParsingProcessor(
            languageSpecification,
            projectPath,
            commsChannel,
            fileHandler,
            cachedGraph.nodes.values.groupBy { it.filePath },
            cachedGraph,
            logger
        ).createOutboundConnectionsForNodes(*nodes)
    }

    /**
     * Creates outbound connections for all nodes within a given set of files.
     * It delegates to the [ConnectionParsingProcessor] to perform the actual processing.
     *
     * @param files A vararg array of file paths (can be absolute or relative to [projectPath])
     *              for which to find outbound connections from all contained nodes.
     * @param graph The [Graphlike] structure containing all nodes being analyzed.
     * @return A list of [Connection] objects representing the discovered outbound connections.
     */
    override fun createOutboundConnectionsForAllNodesInFiles(vararg files: String, graph: Graphlike): List<Connection> {
        val cachedGraph = if(graph is Graph) graph else Graph(
            graph.nodes.toMap(),
            MutableMapConnectionsBuilder(graph.connections)
        )
        return ConnectionParsingProcessor(
            languageSpecification,
            projectPath,
            commsChannel,
            fileHandler,
            cachedGraph.nodes.values.groupBy { it.filePath },
            cachedGraph,
            logger
        ).createOutboundConnectionsForAllNodesInFiles(*files)
    }
}