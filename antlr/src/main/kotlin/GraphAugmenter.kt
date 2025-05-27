package software.bevel.code_to_knowledge_graph.antlr

import software.bevel.graph_domain.graph.NodeType
import software.bevel.graph_domain.graph.Connection
import software.bevel.graph_domain.graph.ConnectionType
import software.bevel.graph_domain.graph.builder.FullyQualifiedNodeBuilder
import software.bevel.graph_domain.graph.builder.GraphBuilder
import software.bevel.graph_domain.graph.builder.NodeBuilder
import software.bevel.graph_domain.graph.builder.DanglingNodeBuilder
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import software.bevel.file_system_domain.LCRange

/**
 * Augments a [GraphBuilder] instance by performing various enhancements and normalizations.
 * This includes fully qualifying node names, merging module nodes, inferring connections,
 * and adding default constructors where necessary.
 *
 * @property logger The logger instance for this class.
 */
class GraphAugmenter(
    val logger: Logger = LoggerFactory.getLogger(GraphAugmenter::class.java)
) {
    //DEPRECATED
    /*fun addParentConnectionsToFullyQualifiedNodes(graph: GraphBuilder) {
        val allNodes = graph.nodes.values.filterIsInstance<FullyQualifiedNodeBuilder>().toMutableList()
        var i = 0
        while (i<allNodes.size) {
            val node = allNodes[i]
            if(graph.getDefiner(node.id) != null){
                i++
                continue
            }
            var definerName = node.id.substringBeforeLast(".", "")
            if(definerName.count { it == '(' } > definerName.count { it == ')' }){
                definerName = definerName.substringBeforeLast("(", "")
            }
            if(definerName == ""){
                i++
                continue
            }
            var doesDefinerExist = false
            if(graph.nodes.containsKey(definerName))
                doesDefinerExist = true

            if(graph.connectionsBuilder.findConnections(definerName, node.id, ConnectionType.DEFINES).isEmpty())
                graph.addConnectionAndMissingNodes(
                    Connection(definerName, node.id, ConnectionType.DEFINES, "", LCRange.empty()),
                    sourceNodeFallback = { FullyQualifiedNodeBuilder(definerName, NodeType.Package) }
                )

            if(!doesDefinerExist)
                allNodes.add(graph.nodes[definerName]!! as FullyQualifiedNodeBuilder)
            i++
        }
    }*/

    /**
     * Iteratively attempts to replace [DanglingNodeBuilder] instances within the graph with [FullyQualifiedNodeBuilder] instances.
     * It uses several strategies to find a fully qualified name: checking the invoker context, the current node's context,
     * imported symbols, and searching the entire graph.
     *
     * The process repeats until no more dangling nodes can be replaced.
     *
     * @param graph The [GraphBuilder] instance to process.
     * @param findFullyQualifiedNodeInImports A function that attempts to find a fully qualified node based on import statements.
     *                                        It takes a [DanglingNodeBuilder], the current [GraphBuilder], and a map of nodes
     *                                        already scheduled for replacement, and returns a [FullyQualifiedNodeBuilder] or null.
     */
    fun fullyQualifyNodeNames(graph: GraphBuilder, findFullyQualifiedNodeInImports: (DanglingNodeBuilder, GraphBuilder, MutableMap<String, String>) -> FullyQualifiedNodeBuilder?) {
        val nodesToReplace = mutableMapOf<String, String>()

        do {
            nodesToReplace.clear()
            val allNodes = graph.nodes.values.filterIsInstance<DanglingNodeBuilder>()
            allNodes.forEach { node ->
                if(graph.nodes[node.id] != null && graph.nodes[node.id] is FullyQualifiedNodeBuilder) {
                    nodesToReplace[node.id] = node.id
                    return@forEach
                }

                // Try to find a matching FullyQualifiedNodeBuilder
                val fqNode = fullyQualifyNodeInInvoker(node, graph, nodesToReplace)
                    ?: findFullyQualifiedNodeInContext(node, graph)
                    ?: findFullyQualifiedNodeInImports(node, graph, nodesToReplace)
                    ?: searchFullyQualifiedNodesInGraph(node, graph)

                if (fqNode != null) {
                    // Schedule the node for replacement
                    nodesToReplace[node.id] = fqNode.id
                }
            }

            // Replace the dangling nodes with fully qualified nodes
            nodesToReplace.forEach { (oldNodeId, newNodeId) ->
                graph.mergeNode(newNodeId, oldNodeId)
                graph.deleteNode(oldNodeId)
            }
            logger.info("Replaced ${nodesToReplace.size} nodes")
        } while (nodesToReplace.isNotEmpty())
    }

    /**
     * Merges [FullyQualifiedNodeBuilder] instances that represent the same module (package) but might have different IDs.
     * It identifies nodes of type [NodeType.Package] and checks if they have an `IS_OF_TYPE` connection to another
     * package node, which is considered the 'actual' module. If so, the original node is merged into the actual module node.
     *
     * @param graph The [GraphBuilder] instance to process.
     */
    fun mergeModuleNodes(graph: GraphBuilder) {
        val nodesToReplace = mutableMapOf<String, String>()
        val allNodes = graph.nodes.values
            .filterIsInstance<FullyQualifiedNodeBuilder>()
            .filter { it.nodeType == NodeType.Package }
        for (node in allNodes) {
            val actualModule = graph.connectionsBuilder.findConnectionsFromOfType(node.id, ConnectionType.IS_OF_TYPE)
                .mapNotNull { graph.nodes[it.targetNodeName] }
                .firstOrNull {
                    it is FullyQualifiedNodeBuilder && it.nodeType == NodeType.Package
                }

            if(actualModule != null) {
                nodesToReplace[node.id] = actualModule.id
            }
        }
        nodesToReplace.forEach { (oldNodeId, newNodeId) ->
            graph.mergeNode(newNodeId, oldNodeId)
            graph.deleteNode(oldNodeId)
        }
        graph.connectionsBuilder.deleteAll(allNodes
            .flatMap { graph.connectionsBuilder.findConnections(it.id, it.id, ConnectionType.IS_OF_TYPE) })
    }

    /**
     * Infers and adds [ConnectionType.USES] connections between module (package) nodes.
     * It iterates through all existing connections in the graph. If a connection exists between two nodes
     * belonging to different modules, and there isn't already a `USES` connection between these modules,
     * a new `USES` connection is added.
     *
     * @param graph The [GraphBuilder] instance to process.
     */
    fun inferModuleConnections(graph: GraphBuilder) {
        val connections = graph.connectionsBuilder.getAllConnections()

        for (connection in connections) {
            if(graph.nodes[connection.sourceNodeName] == null || graph.nodes[connection.targetNodeName] == null)
                continue
            val module1 = getParentPackage(graph.nodes[connection.sourceNodeName]!!, graph)
            val module2 = getParentPackage(graph.nodes[connection.targetNodeName]!!, graph)
            if(module1 != null && module2 != null && module1.id != module2.id && graph.connectionsBuilder.findConnections(module1.id, module2.id).isEmpty()) {
                graph.addConnectionAndMissingNodes(
                    Connection(
                        module1.id,
                        module2.id,
                        ConnectionType.USES,
                        connection.filePath,
                        connection.location
                    )
                )
            }
        }
    }

    /*fun addAngularJsDefaultConstructor(graph: GraphBuilder) {
        graph.nodes.values.filterIsInstance<FullyQualifiedNodeBuilder>().forEach { node ->
            if(node.nodeType == NodeType.Class) {
                val hasConstructor = graph.getChildren(node.id).any { it.id.endsWith("constructor") }
                if(!hasConstructor) {
                    val constructorNode = node.copy(
                        id = "${node.id}()",
                        displayName = "${node.id}()",
                        nodeSignature = "${node.id}()",
                        nodeType = NodeType.Function
                    )
                    graph.nodes[constructorNode.id] = constructorNode
                    graph.addConnectionAndMissingNodes(
                        Connection(
                            node.id,
                            constructorNode.id,
                            ConnectionType.DEFINES,
                            definesConnection?.filePath?: "",
                            definesConnection?.location ?: LCRange.empty()
                        )
                    )
                }
            }
        }
    }*/

    /**
     * Adds a default constructor node to classes that do not explicitly define one.
     * For each [FullyQualifiedNodeBuilder] of type [NodeType.Class], it checks if a constructor
     * (a node whose ID contains "${node.id}(") already exists as a child. If not, a new function node
     * representing the default constructor is created and added to the graph.
     * The new constructor node's `definingNodeName` is set to the class's ID.
     *
     * @param graph The [GraphBuilder] instance to process.
     */
    fun addDefaultConstructor(graph: GraphBuilder) {
        graph.nodes.values.filterIsInstance<FullyQualifiedNodeBuilder>().forEach { node ->
            if(node.nodeType == NodeType.Class) {
                val hasConstructor = graph.getChildren(node.id).any { it.id.contains("${node.id}(") }
                if(!hasConstructor) {
                    val constructorNode = node.copy(
                        id = "${node.id}()",
                        simpleName = "${node.id}()",
                        nodeSignature = "${node.id}()",
                        nodeType = NodeType.Function,
                        definingNodeName = node.id,
                    )
                    graph.nodes[constructorNode.id] = constructorNode
                }
            }
        }
    }

    /**
     * Attempts to forcefully qualify any remaining [DanglingNodeBuilder] instances in the graph.
     * It iterates through all dangling nodes. If a node has an empty ID, it's logged as an error and marked for deletion.
     * Otherwise, it calls [qualifyNode] to attempt to convert it into a [FullyQualifiedNodeBuilder] using its own ID
     * as the fully qualified name.
     *
     * @param graph The [GraphBuilder] instance to process.
     */
    fun forceQualify(graph: GraphBuilder) {
        val nodesToReplace = mutableMapOf<String, String>()
        val nodesToDelete = mutableSetOf<String>()
        val allNodes = graph.nodes.values.filterIsInstance<DanglingNodeBuilder>()
        allNodes.forEach { node ->
            if(node.id  == "") {
                logger.error("Could not force qualify node: ${node.id}")
                nodesToDelete.add(node.id)
                return@forEach
            }
            qualifyNode(node, node.id, graph, nodesToReplace)
        }

        // Replace the dangling nodes with fully qualified nodes
        nodesToReplace.forEach { (oldNodeId, newNodeId) ->
            graph.mergeNode(newNodeId, oldNodeId)
            graph.deleteNode(oldNodeId)
        }

        nodesToDelete.forEach { graph.deleteNode(it) }
    }

    /**
     * Adds a node to a queue for processing, if it hasn't been visited yet.
     *
     * @param queue The queue to add the node to.
     * @param node The [NodeBuilder] to add.
     * @param visited A set of visited node IDs to prevent reprocessing.
     */
    private fun addToQueue(queue: ArrayDeque<NodeBuilder>, node: NodeBuilder, visited: MutableSet<String>) {
        if(!visited.contains(node.id)) {
            queue.add(node)
            visited.add(node.id)
        }
    }

    /**
     * Tries to find a fully qualified name for a [DanglingNodeBuilder] by looking at its invoker's context.
     * The invoker is determined by an incoming [ConnectionType.INVOKES] connection.
     * It checks if the invoker's ID, concatenated with the dangling node's ID, forms a known fully qualified node ID.
     *
     * @param node The [DanglingNodeBuilder] to qualify.
     * @param graph The [GraphBuilder] instance.
     * @param nodesToReplace A map to store nodes that are scheduled for replacement, to avoid re-processing.
     * @return A [FullyQualifiedNodeBuilder] if a match is found, otherwise null.
     */
    private fun fullyQualifyNodeInInvoker(node: DanglingNodeBuilder, graph: GraphBuilder, nodesToReplace: MutableMap<String, String>): FullyQualifiedNodeBuilder? {
        //logger.info("Looking for fully qualified node in invoker: ${node.name}")
        val invokers = graph.connectionsBuilder.findConnectionsFromOfType(node.id, ConnectionType.INVOKED_BY).mapNotNull { graph.nodes[it.targetNodeName] }
            .filterIsInstance<FullyQualifiedNodeBuilder>()
        for(invoker in invokers) {
            val overlap = computeStringOverlap(invoker.id, node.id)
            if(overlap.isNotEmpty()) {
                val fullName = invoker.id + node.id.replaceFirst(overlap, "")
                qualifyNode(node, fullName, graph, nodesToReplace)
                val newNode = graph.nodes[fullName]
                return if(newNode is FullyQualifiedNodeBuilder)
                    newNode
                else
                    null
            }
        }
        return null
    }

    /**
     * Computes the overlap between two strings.
     *
     * @param a The first string.
     * @param b The second string.
     * @return The overlapping part of the strings.
     */
    fun computeStringOverlap(a: String, b: String): String {
        var overlap = b
        while (overlap.isNotEmpty()) {
            if(a.endsWith(overlap)) {
                return overlap
            } else {
                overlap = overlap.substring(0, overlap.length - 1)
            }
        }
        return overlap
    }

    /**
     * Tries to find a fully qualified name for a [DanglingNodeBuilder] by looking at its definer's context.
     * The definer is determined by an incoming [ConnectionType.DEFINES] connection or by the node's `definingNodeName`.
     * It checks if the definer's ID, concatenated with the dangling node's ID, forms a known fully qualified node ID.
     *
     * @param node The [DanglingNodeBuilder] to qualify.
     * @param graph The [GraphBuilder] instance.
     * @return A [FullyQualifiedNodeBuilder] if a match is found, otherwise null.
     */
    private fun findFullyQualifiedNodeInContext(node: DanglingNodeBuilder, graph: GraphBuilder): FullyQualifiedNodeBuilder? {
        //logger.info("Looking for fully qualified node in context: ${node.name}")
        if(node.context == null) return null
        val queue: ArrayDeque<NodeBuilder> = ArrayDeque(listOf(node.context!!))
        val visited: MutableSet<String> = mutableSetOf(node.context!!.id)
        while (queue.isNotEmpty()) {
            val currentContext = queue.removeFirst()
            // Check if current context defines the fully qualified version of the dangling node
            val definedNode = graph.getChildren(currentContext.id).find { it is FullyQualifiedNodeBuilder && it.id.endsWith(".${node.id}") }
            if (definedNode != null && definedNode is FullyQualifiedNodeBuilder) {
                return definedNode
            }

            // Check if the context itself matches the fully qualified node
            if (currentContext is FullyQualifiedNodeBuilder && currentContext.id.endsWith(".${node.id}")) {
                return currentContext
            }

            // Add children of the current context to the queue
            val parent = graph.getParent(currentContext.id)
            val definer = graph.getDefiner(currentContext.id)
            if(parent == null && definer == null) { }
            else if(parent == null) {
                addToQueue(queue, definer!!, visited)
            }
            else if(definer == null) {
                addToQueue(queue, parent, visited)
            }
            else if(parent.id != definer.id) {
                addToQueue(queue, parent, visited)
                addToQueue(queue, definer, visited)
            } else {
                addToQueue(queue, parent, visited)
            }
            graph.connectionsBuilder.findConnectionsFromOfType(currentContext.id, ConnectionType.INHERITS_FROM)
                .mapNotNull {
                    if(!graph.nodes.containsKey(it.targetNodeName))
                        logger.error("Graph does not contain DanglingNode for reference ${it.targetNodeName}")
                    graph.nodes[it.targetNodeName]
                }
                .forEach {
                    addToQueue(queue, it, visited)
                }
        }
        return null
    }

    /**
     * Searches the entire graph for a [FullyQualifiedNodeBuilder] whose ID ends with the [DanglingNodeBuilder]'s ID.
     * This is a broader search strategy when context-specific searches fail.
     *
     * @param node The [DanglingNodeBuilder] to qualify.
     * @param graph The [GraphBuilder] instance.
     * @return A [FullyQualifiedNodeBuilder] if a match is found, otherwise null.
     */
    private fun searchFullyQualifiedNodesInGraph(node: DanglingNodeBuilder, graph: GraphBuilder): FullyQualifiedNodeBuilder? {
        //logger.info("Looking for fully qualified node in graph: ${node.name}")
        // Search for all fully qualified nodes containing the dangling node's name
        val candidates = graph.nodes.values.filter {
            it is FullyQualifiedNodeBuilder && it.id.endsWith(".${node.id}")
        }.map { it as FullyQualifiedNodeBuilder }

        var currentCandidate: FullyQualifiedNodeBuilder? = null
        var currentDepth = Int.MAX_VALUE
        // Check for matching hierarchy with context or imports
        for (candidate in candidates) {
            val matchDepth = hierarchyMatches(node, candidate, graph)
            if (matchDepth != -1 && matchDepth < currentDepth) {
                currentCandidate = candidate
                currentDepth = matchDepth
            }
        }
        if(currentCandidate != null)
            return currentCandidate

        for (candidate in candidates) {
            val nodePackage = getParentPackage(node, graph)
            val candidatePackage = getParentPackage(candidate, graph)
            if(nodePackage == null || candidatePackage == null)
                continue
            if(doPackagesConnect(nodePackage, candidatePackage, graph)) {
                return candidate
            }
        }
        return null
    }

    /**
     * Checks if the hierarchy of a [DanglingNodeBuilder] matches a [FullyQualifiedNodeBuilder].
     * It builds the context chain for the dangling node up to the first package node and checks if the fully qualified node's ID
     * starts with any of the context node IDs.
     *
     * @param node The [DanglingNodeBuilder] to check.
     * @param candidate The [FullyQualifiedNodeBuilder] to check against.
     * @param graph The [GraphBuilder] instance.
     * @return The depth of the match if found, otherwise -1.
     */
    private fun hierarchyMatches(node: DanglingNodeBuilder, candidate: FullyQualifiedNodeBuilder, graph: GraphBuilder): Int {
        // Build the context chain for the DanglingNodeBuilder, up to the first PACKAGE node
        val contextNames = mutableListOf<String>()
        var currentContext = node.context
        while (currentContext != null && !isPackageNode(currentContext)) {
            contextNames.add(currentContext.id)
            currentContext = graph.getParent(currentContext.id)
        }
        if(currentContext != null) contextNames.add(currentContext.id)

        contextNames.forEachIndexed { index, contextName ->
            if(candidate.id.startsWith(contextName))
                return index
        }
        node.importStatements.forEachIndexed { index, importStatement ->
            if(candidate.id.startsWith(importStatement.importedPackage))
                return index
        }

        return -1
    }

    /**
     * Checks if two packages are connected.
     * It performs a depth-first search from the first package to see if it can reach the second package.
     *
     * @param package1 The first package node.
     * @param package2 The second package node.
     * @param graph The [GraphBuilder] instance.
     * @return True if the packages are connected, otherwise false.
     */
    private fun doPackagesConnect(package1: NodeBuilder, package2: NodeBuilder, graph: GraphBuilder): Boolean {
        val visited = mutableSetOf<String>()
        fun dfs(nodeId: String): Boolean {
            visited.add(nodeId)
            val connections = graph.connectionsBuilder.findConnectionsFromOfType(nodeId, ConnectionType.USES)
            if(connections.any { it.targetNodeName == package2.id }) return true
            connections.forEach {
                val nextNode = graph.nodes[it.targetNodeName]
                if(!visited.contains(it.targetNodeName)
                    && nextNode is FullyQualifiedNodeBuilder
                    && nextNode.nodeType == NodeType.Package
                    && dfs(it.targetNodeName))
                    return true
            }
            return false
        }
        return dfs(package1.id)
    }

    /**
     * Finds the parent package [NodeBuilder] for a given node.
     * - If the node is a [DanglingNodeBuilder], it attempts to find a package node whose ID matches the node's `filePath` (enclosed in single quotes).
     * - If the node is a [FullyQualifiedNodeBuilder], it attempts to find a package node whose ID matches the node's `filePath` (enclosed in single quotes).
     * - Otherwise, it returns null.
     *
     * @param node The [NodeBuilder] whose parent package is to be found.
     * @param graph The [GraphBuilder] instance.
     * @return The parent package [NodeBuilder] if found, otherwise null.
     */
    private fun getParentPackage(node: NodeBuilder, graph: GraphBuilder): NodeBuilder? {
        if(node is DanglingNodeBuilder)
            return graph.nodes["'${node.filePath}'"]
        if(node is FullyQualifiedNodeBuilder)
            return graph.nodes["'${node.filePath}'"]
        return null
    }

    /**
     * Checks if a node is a package node.
     *
     * @param node The [NodeBuilder] to check.
     * @return True if the node is a package node, otherwise false.
     */
    private fun isPackageNode(node: NodeBuilder): Boolean {
        // Check if the node is a PACKAGE node
        if (node is FullyQualifiedNodeBuilder) {
            return node.nodeType == NodeType.Package
        }
        return false
    }

    /**
     * Converts a [DanglingNodeBuilder] to a [FullyQualifiedNodeBuilder] using the provided [fullName]
     * and schedules it for replacement in the graph. The new node's type is inferred or defaults to [NodeType.Function].
     *
     * @param node The [DanglingNodeBuilder] to qualify.
     * @param fullName The fully qualified name to use for the new node.
     * @param graph The [GraphBuilder] instance.
     * @param nodesToReplace A map to store nodes that are scheduled for replacement. The key is the old node ID, and the value is the new node ID.
     */
    fun qualifyNode(node: DanglingNodeBuilder, fullName: String, graph: GraphBuilder, nodesToReplace: MutableMap<String, String>) {
        val nodeType = node.nodeType ?: NodeType.Function //if(node.name[0].isUpperCase()) NodeType.CLASS else NodeType.FUNCTION
        val fqNode = FullyQualifiedNodeBuilder(
            fullName,
            fullName,
            nodeType,
            definingNodeName = "",
            codeLocation = LCRange.empty(),
            nameLocation = LCRange.empty(),
            codeHash = "",
            filePath = node.filePath,
        )
        if(graph.nodes[fqNode.id] == null || graph.nodes[fqNode.id] is DanglingNodeBuilder) {
            graph.nodes[fqNode.id] = fqNode
        }
        nodesToReplace[node.id] = fqNode.id
    }
}

/**
 * Configuration for prompts, likely related to AI or template generation.
 *
 * @property systemPrompt The system prompt.
 * @property userPrompt The user prompt.
 * @property exampleInput The example input.
 * @property exampleOutput The example output.
 * @property parameters The prompt parameters.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class PromptsConfig(
    @JsonProperty("system_prompt")
    val systemPrompt: String,
    @JsonProperty("user_prompt")
    val userPrompt: String,
    @JsonProperty("example_input")
    val exampleInput: ExampleInput,
    @JsonProperty("example_output")
    val exampleOutput: String,
    val parameters: PromptParameters
)

/**
 * Represents an example input, typically used in prompt configurations.
 *
 * @property nodeName The name of the node.
 * @property definesConnections The defines connections.
 * @property otherConnections The other connections.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class ExampleInput(
    @JsonProperty("node_name")
    val nodeName: String,
    @JsonProperty("defines_connections")
    val definesConnections: String,
    @JsonProperty("other_connections")
    val otherConnections: String
)

/**
 * Defines parameters for a prompt, including their type, description, and potential enum values.
 *
 * @property temperature The temperature.
 * @property maxTokens The maximum number of tokens.
 * @property topP The top P value.
 * @property frequencyPenalty The frequency penalty.
 * @property presencePenalty The presence penalty.
 * @property defaultType The default type.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class PromptParameters(
    val temperature: Double,
    @JsonProperty("max_tokens")
    val maxTokens: Int,
    @JsonProperty("top_p")
    val topP: Double,
    @JsonProperty("frequency_penalty")
    val frequencyPenalty: Double,
    @JsonProperty("presence_penalty")
    val presencePenalty: Double,
    @JsonProperty("default_type")
    val defaultType: String? = "Default"
)