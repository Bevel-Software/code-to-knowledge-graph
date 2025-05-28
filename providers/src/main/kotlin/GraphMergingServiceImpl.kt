package software.bevel.code_to_knowledge_graph.providers

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import software.bevel.graph_domain.hashing.LocalitySensitiveHasher
import software.bevel.file_system_domain.absolutizePath
import software.bevel.file_system_domain.services.FileHandler
import software.bevel.graph_domain.GraphMergingService
import software.bevel.graph_domain.graph.Graph
import software.bevel.graph_domain.graph.Graphlike
import software.bevel.graph_domain.graph.Node
import software.bevel.graph_domain.graph.builder.MutableMapConnectionsBuilder

/**
 * Implementation of [GraphMergingService] that merges graph-like structures.
 *
 * This service handles the merging of node descriptions and connections from one graph into another,
 * taking into account potential conflicts and outdated information.
 *
 * @property hasher The [LocalitySensitiveHasher] used for comparing code similarity.
 * @property fileHandler The [FileHandler] used for file system operations, like checking if a file exists.
 * @property logger The [Logger] for logging messages and errors.
 */
class GraphMergingServiceImpl(
    private val hasher: LocalitySensitiveHasher,
    private val fileHandler: FileHandler,
    private val logger: Logger = LoggerFactory.getLogger(GraphMergingServiceImpl::class.java)
) : GraphMergingService {

    /**
     * Merges two description strings, marking them as outdated and needing a manual merge.
     *
     * @param currentDescription The current description string.
     * @param otherDescription The other description string.
     * @return A merged string indicating a conflict that needs resolution.
     */
    fun mergeDescriptions(currentDescription: String, otherDescription: String): String {
        return """[OUTDATED] [NEEDS MERGE]
        <<<<<<< Current
        $currentDescription
        =======
        $otherDescription
        >>>>>>> Incoming
        """.trimIndent()
    }

    /**
     * Determines the correct description for a node based on the descriptions of the current and other nodes.
     *
     * It handles cases where one or both descriptions are empty, or if the other node's description
     * is outdated.
     *
     * @param currentNode The current node.
     * @param otherNode The other node (from the graph being merged).
     * @return The determined correct description string.
     */
    fun determineCorrectDescription(currentNode: Node, otherNode: Node): String {
        if(otherNode.description == "" && currentNode.description == "") {
            return ""
        }
        if(currentNode.description == "") {
            return if(otherNode.description.startsWith("[OUTDATED]") || currentNode.codeHash == otherNode.codeHash)
                otherNode.description
            else
                "[OUTDATED]\n" + otherNode.description
        } else {
            if(otherNode.description != "" && !currentNode.description.contains(otherNode.description)) {
                return mergeDescriptions(currentNode.description, otherNode.description)
            }
            return currentNode.description
        }
    }

    /**
     * Merges node descriptions and connections from another graph ([otherGraph]) into the [currentGraph].
     *
     * The merging process involves:
     * 1. Identifying nodes in the `currentGraph` that still exist in the project.
     * 2. Matching nodes from `otherGraph` to `currentGraph` based on ID (perfect match).
     * 3. For unmatched nodes in `otherGraph` with descriptions, attempting to match them with remaining nodes
     *    in `currentGraph` based on file path and signature, then file path and simple name, and finally
     *    by code similarity using the [LocalitySensitiveHasher].
     * 4. Merging descriptions using [determineCorrectDescription].
     * 5. Merging connections from `otherGraph` if the outbound connection version is not "Not generated"
     *    and the target node exists in the merged set of nodes.
     *
     * @param currentGraph The graph to merge into.
     * @param otherGraph The graph to merge from.
     * @param projectPath The path of the project, used for context (e.g., checking file existence).
     * @return A new [Graphlike] instance representing the merged graph. Returns `currentGraph` if an error occurs.
     */
    override fun mergeNodeDescriptionsAndConnectionsFromOtherIntoCurrentGraph(currentGraph: Graphlike, otherGraph: Graphlike, projectPath: String): Graphlike {
        try {
            val nodesToProcess = currentGraph.nodes.filter {
                it.value.filePath == "" || fileHandler.exists(absolutizePath(it.value.filePath, projectPath))
            }.toMutableMap()
            val mergedNodes = currentGraph.nodes.toMutableMap()
            val mergedConnections = MutableMapConnectionsBuilder(currentGraph.connections)

            otherGraph.nodes.values.filter { it.description != "" }.forEach { otherNode ->
                val perfectMatch = nodesToProcess[otherNode.id]
                if(perfectMatch != null) {
                    nodesToProcess.remove(otherNode.id)
                    mergedNodes[perfectMatch.id] = perfectMatch.copy(description = determineCorrectDescription(perfectMatch, otherNode))
                }
            }

            val groupedOtherNodes = otherGraph.nodes.values.groupBy { it.filePath }

            groupedOtherNodes.forEach { (filePath, otherNodes) ->
                otherNodes.filter { !currentGraph.nodes.containsKey(it.id) && it.description != "" }.forEach forEachNodeInFile@{ otherNode ->
                    val remainingNodesInFile = nodesToProcess.values.filter { it.filePath == filePath }
                    val remainingNodesWithSameSignature = remainingNodesInFile.filter {
                        it.nodeSignature == otherNode.nodeSignature
                    }
                    if(remainingNodesWithSameSignature.size == 1) {
                        mergedNodes[remainingNodesWithSameSignature.first().id] = remainingNodesWithSameSignature.first().copy(description = determineCorrectDescription(remainingNodesWithSameSignature.first(), otherNode))
                        nodesToProcess.remove(remainingNodesWithSameSignature.first().id)
                        return@forEachNodeInFile
                    }

                    val remainingNodesWithSameName = remainingNodesInFile.filter {
                        it.simpleName == otherNode.simpleName
                    }
                    if(remainingNodesWithSameName.size == 1) {
                        mergedNodes[remainingNodesWithSameName.first().id] = remainingNodesWithSameName.first().copy(description = determineCorrectDescription(remainingNodesWithSameName.first(), otherNode))
                        nodesToProcess.remove(remainingNodesWithSameName.first().id)
                        return@forEachNodeInFile
                    }
                    remainingNodesInFile.maxByOrNull { hasher.similarity(otherNode.codeHash, it.codeHash) }?.let { remainingNode ->
                        mergedNodes[remainingNode.id] = remainingNode.copy(description = determineCorrectDescription(remainingNode, otherNode))
                        nodesToProcess.remove(remainingNode.id)
                    }
                }
            }

            val currentMergedNodes = mergedNodes.values.toList()
            currentMergedNodes.forEach { mergedNode ->
                val otherNode = otherGraph[mergedNode.id] ?: return@forEach
                /*mergedNodes[mergedNode.id] = mergedNode.copy(inboundConnectionVersion = "Not generated")
                if(mergedNode.codeHash != otherNode.codeHash) {
                    mergedNodes[mergedNode.id] = mergedNode.copy(outboundConnectionVersion = "Not generated")
                    return@forEach
                }*/
                if(otherNode.outboundConnectionVersion != "Not generated") {
                    otherGraph.connections.findConnectionsFrom(mergedNode.id).filter {
                        mergedNodes.containsKey(it.targetNodeName)
                    }.forEach { mergedConnections.addConnection(it) }
                    mergedNodes[mergedNode.id] = mergedNode.copy(outboundConnectionVersion = otherNode.outboundConnectionVersion)
                }
            }

            // Create a merged graph
            val mergedGraph = Graph(
                nodes = mergedNodes,
                connections = mergedConnections
            )
            return mergedGraph
        } catch (e: Exception) {
            logger.error("Error merging graphs", e)
            return currentGraph
        }
    }
}