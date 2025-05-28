package software.bevel.code_to_knowledge_graph.vscode

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.Logger
import software.bevel.graph_domain.*
import software.bevel.graph_domain.graph.*
import software.bevel.graph_domain.graph.builder.*
import software.bevel.graph_domain.parsing.Parser
import software.bevel.file_system_domain.services.FileHandler
import software.bevel.file_system_domain.web.LocalCommunicationInterface
import software.bevel.code_to_knowledge_graph.vscode.data.*
import software.bevel.code_to_knowledge_graph.vscode.languageSpecs.VsCodeLanguageSpecification
import software.bevel.file_system_domain.*
import software.bevel.graph_domain.parsing.IntermediateFileParser
import kotlin.io.path.Path
import kotlin.io.path.extension

/**
 * A [Parser] implementation that leverages a VS Code extension (via [LocalCommunicationInterface])
 * to parse source code files and build a knowledge graph.
 *
 * It uses a [VsCodeLanguageSpecification] to interpret symbols and their relationships based on information
 * provided by the VS Code document symbol provider and other language server features.
 * The parser can handle multiple project paths and incrementally build a graph.
 * It also includes logic for creating `DEFINES` connections and potentially other connection types
 * by invoking [VsCodeConnectionParser].
 *
 * @property pathToProject The primary path to the project being analyzed. Used for resolving relative paths.
 * @param commsChannel [LocalCommunicationInterface] for communicating with the VS Code extension.
 * @property fileHandler The [FileHandler] used for file system operations.
 * @property languageSpecification The [VsCodeLanguageSpecification] defining how symbols and types are interpreted.
 * @property fileWalker The [FileWalker] used to discover files to parse.
 * @property logger The [Logger] instance for this class.
 * @property connectionVersion A version string to tag connections created by this parser.
 */
class VsCodeParser(
    private val pathToProject: String,
    private val commsChannel: LocalCommunicationInterface,
    private val fileHandler: FileHandler,
    private val languageSpecification: VsCodeLanguageSpecification,
    private val fileWalker: FileWalker,
    override val logger: Logger,
    private val connectionVersion: String
): Parser, IntermediateFileParser {
    /**
     * Keeps track of the total lines of code analyzed across multiple files during a parse operation.
     */
    private var linesOfCodeAnalysed = 0

    /**
     * Parses files from a list of project paths and returns a [GraphBuilder] representing the combined knowledge graph.
     * It uses the configured [fileWalker] to find all relevant files within the given [pathsToProjects].
     *
     * @param pathsToProjects A list of root directory paths for the projects to be parsed.
     * @return A [GraphBuilder] containing the nodes and initial connections derived from the parsed files.
     */
    override fun parseToGraphBuilder(pathsToProjects: List<String>): GraphBuilder {
        val files = pathsToProjects.flatMap { fileWalker.walk(it) }
        return parseFiles(files)
    }

    /**
     * Parses a list of specified files and builds a graph representation.
     * This method orchestrates the parsing process by:
     * 1. Calling [parseNodesInFiles] to get the initial nodes from the file symbols.
     * 2. Calling [createDefinesConnections] to establish `DEFINES` relationships between nodes.
     * 3. Renormalizing node IDs based on their file path, code hash, and defining node's simple name.
     * 4. If the total lines of code are below a threshold (500,000), it invokes [VsCodeConnectionParser]
     *    to discover and add further outbound connections (e.g., `USES`, `INHERITS_FROM`).
     * 5. Sends progress updates via the [commsChannel].
     *
     * @param absoluteFiles A list of absolute file paths to parse.
     * @param initialGraph An optional [GraphBuilder] to extend. If null, a new one is created.
     * @return A [GraphBuilder] populated with nodes and connections from the parsed files.
     */
    override fun parseFiles(absoluteFiles: List<String>, initialGraph: GraphBuilder?): GraphBuilder {
        linesOfCodeAnalysed = 0
        val mergedGraph = parseNodesInFiles(absoluteFiles, initialGraph)
        createDefinesConnections(mergedGraph)
        val newNodes = mutableMapOf<String, NodeBuilder>()
        mergedGraph.nodes.values.filterIsInstance<FullyQualifiedNodeBuilder>().forEach { oldNode ->
            if(oldNode.id == languageSpecification.defaultPackageName) {
                newNodes[oldNode.id] = oldNode.copy()
                return@forEach
            }
            val definingNodeSimpleName = mergedGraph.nodes[oldNode.definingNodeName]?.let { definingNode ->
                if(definingNode is FullyQualifiedNodeBuilder)
                    definingNode.simpleName
                else
                    oldNode.definingNodeName
            } ?: newNodes[oldNode.definingNodeName]?.let { definingNode ->
                if(definingNode is FullyQualifiedNodeBuilder)
                    definingNode.simpleName
                else
                    oldNode.definingNodeName
            } ?: oldNode.definingNodeName
            val newId = "${oldNode.filePath.hashCode()}.${oldNode.codeHash}.${definingNodeSimpleName}.${oldNode.simpleName}"
            //Update old and new graph definingNode with new ID
            mergedGraph.nodes.values.filterIsInstance<FullyQualifiedNodeBuilder>().forEach { node ->
                if(node.definingNodeName == oldNode.id) {
                    node.definingNodeName = newId
                }
            }
            newNodes.values.filterIsInstance<FullyQualifiedNodeBuilder>().forEach { node ->
                if(node.definingNodeName == oldNode.id) {
                    node.definingNodeName = newId
                }
            }
            newNodes[newId] = oldNode.copy(id = newId)
        }
        mergedGraph.nodes = newNodes
        if(linesOfCodeAnalysed < 500_000) {
            val vsCodeConnectionParser = VsCodeConnectionParser(pathToProject, commsChannel, fileHandler, languageSpecification, logger)
            vsCodeConnectionParser.createOutboundConnectionsForAllNodesInFiles(*absoluteFiles.toTypedArray(), graph = mergedGraph.build()).forEach {
                mergedGraph.connectionsBuilder.addConnection(it)
            }
            mergedGraph.nodes.values.filterIsInstance<FullyQualifiedNodeBuilder>().forEach {
                it.inboundConnectionVersion = connectionVersion
                it.outboundConnectionVersion = connectionVersion
            }
        }

        val message = jacksonObjectMapper().writeValueAsString(VsCodeCommand("progressUpdate", 100))
        commsChannel.sendWithoutResponse(message)

        Metrics.getMetrics(mergedGraph)
        return mergedGraph
    }

    /**
     * Parses a list of files to extract nodes (document symbols) and adds them to a [GraphBuilder].
     * It initializes a [GraphBuilder] if one is not provided, including a default package node.
     * It iterates through each file, adds hardcoded symbols defined by the [languageSpecification],
     * and then calls [parseFileIntoGraph] for each file to process its symbols using a [BatchProcessor]
     * for requests to the VS Code extension.
     * It also logs and sends error messages for file extensions that could not be parsed.
     *
     * @param files A list of absolute file paths to parse for nodes.
     * @param initialGraph An optional [GraphBuilder] to add nodes to. If null, a new one is created.
     * @return A [GraphBuilder] populated with nodes extracted from the files.
     */
    fun parseNodesInFiles(files: List<String>, initialGraph: GraphBuilder? = null): GraphBuilder {
        val graph = initialGraph ?: GraphBuilder(mutableMapOf(
            languageSpecification.defaultPackageName to FullyQualifiedNodeBuilder(
                languageSpecification.defaultPackageName,
                languageSpecification.defaultPackageName,
                NodeType.Package,
                definingNodeName = "",
                filePath = "",
                codeLocation = LCRange.empty(),
                nameLocation = LCRange.empty(),
                codeHash = ""
            ),
        ))
        val allFileEndings = mutableSetOf<String>()
        files.forEach { file ->
            val relativeFilePath = relativizePath(file, pathToProject)
            languageSpecification.addHardcodedSymbols(pathToProject, relativeFilePath, graph)
            allFileEndings.add(Path(file).extension)
        }
        BatchProcessor(commsChannel, maxBatchSize = 500).use { batchProcessor ->
            files.mapIndexedNotNull { index, file ->
                if(index % 100 == 0) {
                    logger.info("Parsing: ${index + 1}/${files.size}")
                    val message = jacksonObjectMapper().writeValueAsString(VsCodeCommand("progressUpdate", (index + 1) * 30 / files.size))
                    commsChannel.sendWithoutResponse(message)
                }
                linesOfCodeAnalysed += fileHandler.readLines(absolutizePath(file, pathToProject)).size
                try {
                    val relativeFilePath = relativizePath(file, pathToProject)
                    parseFileIntoGraph(relativeFilePath, batchProcessor, graph)
                } catch (e: Exception) {
                    logger.error("Failed to parse file: $file: \n$e\n${e.stackTraceToString()}")
                    null
                }
            }
        }
        val fileEndingsParsed = graph.nodes.values.filterIsInstance<FullyQualifiedNodeBuilder>().filter { it.nodeType != NodeType.File }.distinctBy { it.filePath }.map { Path(it.filePath).extension }
        allFileEndings.filter { it !in fileEndingsParsed }.forEach {
            logger.info("Files with the following extension could not be parsed: $it")
            val message = jacksonObjectMapper().writeValueAsString(VsCodeCommand("showErrorMessage", "Files with the following extension could not be parsed: '$it'\nPlease install the appropriate extension that adds support for this language."))
            commsChannel.sendWithoutResponse(message)
        }
        return graph
    }

    /**
     * Parses a single file to extract document symbols and convert them into graph nodes.
     * It sends a `vscode.executeDocumentSymbolProvider` command to the VS Code extension via the [batchProcessor].
     * The response ([VsCodeDocumentSymbolResponse] or [VsCodeSymbolInformationResponse]) is then processed by
     * [convertDocumentSymbolsToNodes] or [convertSymbolInformationToNodes] respectively.
     * Newly created nodes are added to the [graph] and also returned in a list.
     * An optional [onNodeAdded] callback can be provided to process each node after its creation but before being added to the graph.
     *
     * @param relativePathToFile The relative path of the file to parse.
     * @param batchProcessor The [BatchProcessor] used to send requests to the VS Code extension.
     * @param graph The [GraphBuilder] to which new nodes will be added.
     * @param onNodeAdded A callback function that can modify or inspect a [NodeBuilder] before it's added to the graph.
     *                    Defaults to an identity function.
     * @return A list of [NodeBuilder] instances that were created and added to the graph from this file.
     */
    fun parseFileIntoGraph(relativePathToFile: String, batchProcessor: BatchProcessor, graph: GraphBuilder, onNodeAdded: (NodeBuilder) -> NodeBuilder = { it }): List<NodeBuilder> {
        val addedNodes = mutableListOf<NodeBuilder>()
        val absolutePathToFile = absolutizePath(relativePathToFile, pathToProject)

        batchProcessor.batchSendRequest(VsCodeCommand("vscode.executeDocumentSymbolProvider", absolutePathToFile)) { parsedResponse ->
            if(parsedResponse is VsCodeDocumentSymbolResponse) {
                val response = parsedResponse.documentSymbols.flatMap { it.flattenChildren() }
                if(response.isNotEmpty()) {
                    convertDocumentSymbolsToNodes(response, graph, relativePathToFile).map(onNodeAdded).forEach {
                        addedNodes.add(it)
                        graph.nodes[it.id] = it
                    }
                    return@batchSendRequest
                }
            } else if(parsedResponse is VsCodeSymbolInformationResponse) {
                val response = parsedResponse.symbolMatches
                if(response.isNotEmpty()) {
                    convertSymbolInformationToNodes(response, graph).map(onNodeAdded).forEach {
                        addedNodes.add(it)
                        graph.nodes[it.id] = it
                    }
                    return@batchSendRequest
                }
            }
        }
        return addedNodes
    }

    /**
     * Converts a list of [VsCodeDocumentSymbol] objects into [NodeBuilder] instances.
     * It filters symbols based on allowed kinds defined in [VsCodeSymbolInformation.allowedKinds].
     * Each valid symbol is then converted to a node using the [languageSpecification].
     *
     * @param documentSymbols The list of [VsCodeDocumentSymbol] to convert.
     * @param graph The [GraphBuilder] context, used by the [languageSpecification] for node creation.
     * @param relativePathToFile The relative path of the file from which the symbols were extracted.
     * @return A list of [NodeBuilder] instances created from the document symbols.
     */
    private fun convertDocumentSymbolsToNodes(documentSymbols: List<VsCodeDocumentSymbol>, graph: GraphBuilder, relativePathToFile: String): List<NodeBuilder> {
        val filteredSymbols = documentSymbols.filter { VsCodeSymbolInformation.allowedKinds.contains(it.kind) }
        return filteredSymbols.mapNotNull { symbolInFile ->
            languageSpecification.convertSymbolFromCodeToNode(
                symbolInFile.name,
                symbolInFile.kind,
                relativePathToFile,
                nameRange = symbolInFile.selectionRange,
                fullRange = symbolInFile.range,
                pathToProject,
                graph
            )
        }
    }

    /**
     * Converts a list of [VsCodeSymbolInformation] objects into [NodeBuilder] instances.
     * It filters symbols based on allowed kinds defined in [VsCodeSymbolInformation.allowedKinds].
     * Each valid symbol is then converted to a node using the [languageSpecification].
     * The file path for each symbol is relativized against the [pathToProject].
     * Note: A TODO exists to better estimate the full range of the symbol.
     *
     * @param symbolsInFile The list of [VsCodeSymbolInformation] to convert.
     * @param graph The [GraphBuilder] context, used by the [languageSpecification] for node creation.
     * @return A list of [NodeBuilder] instances created from the symbol information.
     */
    private fun convertSymbolInformationToNodes(symbolsInFile: List<VsCodeSymbolInformation>, graph: GraphBuilder): List<NodeBuilder> {
        val filteredSymbols = symbolsInFile.filter { VsCodeSymbolInformation.allowedKinds.contains(it.kind) }
        return filteredSymbols.mapNotNull { symbolInFile ->
            languageSpecification.convertSymbolFromCodeToNode(
                symbolInFile.name,
                symbolInFile.kind,
                relativizePath(symbolInFile.location.filePath, pathToProject),
                symbolInFile.location.range,
                symbolInFile.location.range, //TODO: estimate full range
                pathToProject,
                graph
            )
        }
    }

    /**
     * Establishes `DEFINES` connections (parent-child relationships) between nodes in the graph.
     * For each node in the provided list (or all nodes in the graph if not specified),
     * it calls [findParentNode] to determine its defining (parent) node based on code location and containment.
     * The `definingNodeName` property of each [FullyQualifiedNodeBuilder] is updated accordingly.
     * Progress updates are logged and sent via the [commsChannel].
     *
     * @param graph The [GraphBuilder] containing the nodes to process.
     * @param nodes An optional list of [FullyQualifiedNodeBuilder] to create defines connections for.
     *              Defaults to all [FullyQualifiedNodeBuilder] instances in the [graph].
     */
    fun createDefinesConnections(graph: GraphBuilder, nodes: List<FullyQualifiedNodeBuilder> = graph.nodes.values.filterIsInstance<FullyQualifiedNodeBuilder>()) {
        val allNodes = graph.nodes.values.filterIsInstance<FullyQualifiedNodeBuilder>().groupBy { it.filePath }
        logger.info("Creating ${allNodes.size} defines connections")
        var index = 0
        var lastPercentage: Int = 0
        nodes.forEach { node ->
            if(index % 100 == 0) {
                if(lastPercentage != (30 + (index + 1) * 25 / graph.nodes.size)) {
                    logger.info("Defines connections ${index + 1}/${graph.nodes.size}")
                    lastPercentage = 30 + (index + 1) * 25 / graph.nodes.size
                    val message = jacksonObjectMapper().writeValueAsString(
                        VsCodeCommand(
                            "progressUpdate",
                            30 + (index + 1) * 25 / graph.nodes.size
                        )
                    )
                    commsChannel.sendWithoutResponse(message)
                }
            }
            val defCon = findParentNode(allNodes[node.filePath] ?: listOf(), node.id, node.nameLocation)
            node.definingNodeName = defCon
            index++
        }
        logger.info("Created ${allNodes.size} defines connections")
    }

    /**
     * Finds the most specific parent (defining) node for a given [targetNodeId] within a list of candidate [nodes]
     * from the same file.
     * A node is considered a parent if its `codeLocation` encloses the [targetCodeLocation] and it's not the target itself.
     * Among potential parents, the one with the smallest (most specific) enclosing `codeLocation` is chosen.
     * If no suitable parent is found, it defaults to [VsCodeLanguageSpecification.defaultPackageName].
     *
     * @param nodes A list of [FullyQualifiedNodeBuilder] candidates, typically all nodes from the same file as the target.
     * @param targetNodeId The ID of the node for which to find the parent.
     * @param targetCodeLocation The [LCRange] (code location) of the target node.
     * @return The ID of the determined parent node, or the default package name if none is found.
     */
    private fun findParentNode(nodes: List<FullyQualifiedNodeBuilder>, targetNodeId: String, targetCodeLocation: LCRange): String {
        val sourceNode = nodes
            .filter {
                targetCodeLocation in it.codeLocation && it.id != targetNodeId
            }
            .fold<FullyQualifiedNodeBuilder, FullyQualifiedNodeBuilder?>(null) { currentBestSourceNode, node ->
                if(currentBestSourceNode == null || node.codeLocation in currentBestSourceNode.codeLocation) {
                    return@fold node
                }
                currentBestSourceNode
            }
        if (sourceNode != null) {
            if(sourceNode.id == targetNodeId) {
                logger.error("Source node and target node are the same: ${sourceNode.id}")
            }
            return sourceNode.id
        } else {
            return languageSpecification.defaultPackageName
        }
    }
}