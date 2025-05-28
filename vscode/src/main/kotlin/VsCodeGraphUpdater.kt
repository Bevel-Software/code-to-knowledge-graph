package software.bevel.code_to_knowledge_graph.vscode

import software.bevel.file_system_domain.absolutizePath
import software.bevel.graph_domain.graph.Graph
import software.bevel.graph_domain.graph.Graphlike
import software.bevel.graph_domain.graph.builder.GraphBuilder
import software.bevel.graph_domain.graph.builder.ListConnectionsNavigator
import software.bevel.graph_domain.graph.builder.MutableMapConnectionsBuilder
import software.bevel.graph_domain.parsing.GraphUpdateParser
import software.bevel.file_system_domain.relativizePath
import software.bevel.graph_domain.GraphMergingService
import software.bevel.graph_domain.parsing.IntermediateFileParser

/**
 * Implements [GraphUpdateParser] to handle updates to a knowledge graph based on file changes
 * (additions, deletions, modifications) using a [IntermediateFileParser].
 *
 * This class orchestrates the parsing of changed files and integrates the results into an existing graph,
 * leveraging a [GraphMergingService] to reconcile differences.
 *
 * @property projectPath The absolute path to the root of the project being analyzed.
 *                       Used for relativizing and absolutizing file paths.
 * @property parser The [IntermediateFileParser] instance used to parse or re-parse files to generate graph structures.
 * @property graphMergingService The [GraphMergingService] used to merge new or updated graph information
 *                               with the existing graph.
 */
class VsCodeGraphUpdater(
    private val projectPath: String,
    private val parser: IntermediateFileParser,
    private val graphMergingService: GraphMergingService,
): GraphUpdateParser {

    /**
     * Reparses a list of specified files and updates the [currentGraph] with the new information.
     * This involves:
     * 1. Removing the existing nodes and connections related to the [filesToUpdate] from a copy of the [currentGraph].
     * 2. Parsing the [filesToUpdate] again using [addFiles] to get their new graph representation.
     * 3. Merging this newly parsed graph segment with the [currentGraph] using the [graphMergingService].
     * 4. Filtering out any connections that may now be dangling due to node changes.
     *
     * @param filesToUpdate A list of absolute or relative file paths that have been modified and need reparsing.
     * @param currentGraph The current [Graphlike] state of the knowledge graph.
     * @return An updated [Graphlike] instance reflecting the changes from the reparsed files.
     */
    override fun reparseFiles(filesToUpdate: List<String>, currentGraph: Graphlike): Graphlike {
        val relativeFilesToDelete = filesToUpdate.map { relativizePath(it, projectPath) }

        val deletedGraph = Graph(
            nodes = currentGraph.nodes.filterValues { it.filePath !in relativeFilesToDelete },
            connections = currentGraph.connections
        )

        val addedAndDeletedGraph = addFiles(
            filesToUpdate,
            deletedGraph
        )

        val mergedGraph = graphMergingService.mergeNodeDescriptionsAndConnectionsFromOtherIntoCurrentGraph(addedAndDeletedGraph, currentGraph, projectPath)

        return Graph(
            nodes = mergedGraph.nodes,
            connections = MutableMapConnectionsBuilder(ListConnectionsNavigator(mergedGraph.connections.getAllConnections().filter {
                mergedGraph.nodes.containsKey(it.sourceNodeName) && mergedGraph.nodes.containsKey(it.targetNodeName)
            }))
        )
    }

    /**
     * Removes nodes and associated connections related to a list of specified files from the [currentGraph].
     *
     * @param filesToDelete A list of absolute or relative file paths whose corresponding nodes and connections
     *                      should be removed from the graph.
     * @param currentGraph The current [Graphlike] state of the knowledge graph.
     * @return A new [Graphlike] instance with the specified files' content removed.
     */
    override fun deleteFiles(filesToDelete: List<String>, currentGraph: Graphlike): Graphlike {
        val relativeFilesToDelete = filesToDelete.map { relativizePath(it, projectPath) }
        val nodesToDelete = currentGraph.nodes.values.filter {
            it.filePath in relativeFilesToDelete
        }

        val newGraph = Graph(
            nodes = currentGraph.nodes.filterValues { it.filePath !in relativeFilesToDelete },
            connections = MutableMapConnectionsBuilder(ListConnectionsNavigator(currentGraph.connections.getAllConnections().filter { connection ->
                nodesToDelete.all { it.id != connection.sourceNodeName && it.id != connection.targetNodeName }
            }))
        )

        return newGraph
    }

    /**
     * Parses a list of specified files and adds their graph representations to the [currentGraph].
     * The [parser] is used to parse the files, building upon the [currentGraph] (passed as a [GraphBuilder]).
     *
     * @param filesToAdd A list of absolute or relative file paths to be parsed and added to the graph.
     * @param currentGraph The current [Graphlike] state of the knowledge graph, which serves as a base.
     * @return A new [Graphlike] instance including the graph structures from the newly added files.
     */
    override fun addFiles(filesToAdd: List<String>, currentGraph: Graphlike): Graphlike {
        val absoluteFileToAdd = filesToAdd.map { absolutizePath(it, projectPath) }
        val newGraph = parser.parseFiles(absoluteFileToAdd, GraphBuilder(currentGraph))

        return newGraph.build()
    }
}