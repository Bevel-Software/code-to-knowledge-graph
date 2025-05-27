package software.bevel.code_to_knowledge_graph.antlr.bevel_ast_ql.converters

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.snt.inmemantlr.tree.ParseTreeRule
import software.bevel.graph_domain.graph.NodeType
import software.bevel.graph_domain.graph.builder.DanglingNodeBuilder
import software.bevel.graph_domain.graph.builder.FullyQualifiedNodeBuilder
import software.bevel.code_to_knowledge_graph.antlr.AntlrGraphBuilder
import software.bevel.code_to_knowledge_graph.antlr.bevel_ast_ql.ArgumentResolver.Companion.resolveAndFormatArgument
import software.bevel.code_to_knowledge_graph.antlr.bevel_ast_ql.ArgumentResolver.Companion.resolveCaptureAsParseTree
import software.bevel.code_to_knowledge_graph.antlr.bevel_ast_ql.ArgumentResolver.Companion.resolvePartOfArgument
import software.bevel.code_to_knowledge_graph.antlr.bevel_ast_ql.predicates.JsonPredicate
import java.util.*

/**
 * A [JsonAndQueryMatchConverter] that creates a directed connection (edge) between two specified nodes
 * in the [AntlrGraphBuilder].
 *
 * Node identifiers and types are resolved using [software.bevel.code_to_knowledge_graph.antlr.bevel_ast_ql.ArgumentResolver].
 * Fallback types can be provided for nodes that might need to be created as dangling nodes.
 *
 * @property fromNode Identifier for the source node of the connection. This can be a literal name or a capture name.
 * @property fromFallbackType Optional [NodeType] (as a string, e.g., "CLASS") for the `fromNode` if it's created as a dangling node.
 * @property toNode Identifier for the target node of the connection. This can be a literal name or a capture name.
 * @property toFallbackType Optional [NodeType] (as a string, e.g., "METHOD") for the `toNode` if it's created as a dangling node.
 * @property connectionType The type of the connection to be created (e.g., "CALLS", "IMPLEMENTS").
 * @property positionAstRule Optional name of a captured AST rule. The source code position of this rule will be used
 *                           for the connection. If null, the `currentParseTreeRule`'s position is used.
 * @property predicates An optional list of [JsonPredicate]s that must all evaluate to true for this converter to be applied.
 * @param logger The SLF4J logger instance used for logging within this converter.
 */
open class ConnectionConverter(
    val fromNode: String,
    val fromFallbackType: String?,
    val toNode: String,
    val toFallbackType: String?,
    val connectionType: String,
    val positionAstRule: String?,
    override val predicates: List<JsonPredicate>? = null,
    private val logger: Logger = LoggerFactory.getLogger(ConnectionConverter::class.java)
): JsonAndQueryMatchConverter {
    companion object {
        /** Unique identifier for this converter type, typically used in configuration files (e.g., YAML). */
        const val NAME = "connection"
    }

    /**
     * Applies the logic to create a connection in the graph.
     *
     * This method resolves the source and target node names and types, determines the connection's
     * source code position, and (intends to) add the connection to the [AntlrGraphBuilder].
     * It checks for and prevents self-connections.
     *
     * Note: The current implementation contains a `TODO()` marker, indicating that the final logic for
     * adding the connection to the graph might be incomplete or under review.
     *
     * @param currentParseTreeRule The current ANTLR [ParseTreeRule] being processed.
     * @param graph The [AntlrGraphBuilder] instance to which the connection should be added.
     * @param context A map of named captures from the query match, where keys are capture names and
     *                values are the corresponding [ParseTreeRule]s.
     * @param pathToFile The path to the source file being parsed.
     * @throws IllegalArgumentException if `fromNode` or `toNode` cannot be resolved to a valid name (are empty).
     */
    override fun convert(
        currentParseTreeRule: ParseTreeRule,
        graph: AntlrGraphBuilder,
        context: Map<String, ParseTreeRule>,
        pathToFile: String
    ) {
        val fromNodeName = resolveAndFormatArgument(currentParseTreeRule, fromNode, context, graph)
        val fromNodeType = fromFallbackType?.let { NodeType.valueOf(it.uppercase(Locale.getDefault())) }
        val toNodeName = resolveAndFormatArgument(currentParseTreeRule, toNode, context, graph)
        val toNodeType = toFallbackType?.let { NodeType.valueOf(it.uppercase(Locale.getDefault())) }
        val parentNode = graph.nodesInParseTree[resolveCaptureAsParseTree(currentParseTreeRule, "parentNode", context, graph)]
        val positionNode: ParseTreeRule = positionAstRule?.let {
            val posResult = resolvePartOfArgument(it, currentParseTreeRule, context, graph)
            if(posResult is ParseTreeRule) {
                posResult
            } else {
                null
            }
        } ?: currentParseTreeRule
        if(graph.nodes.containsKey(fromNodeName)) {
            val node = graph.nodes[fromNodeName]!!
            if(node.id.endsWith(toNodeName.removePrefix("$fromNodeName."))
                && (toNodeType == null
                    || (node is DanglingNodeBuilder && node.nodeType == toNodeType
                        || node is FullyQualifiedNodeBuilder && node.nodeType == toNodeType))){
                logger.info("$currentParseTreeRule tried adding a connection from a node to itself")
                return
            }
        }
        if(fromNodeName == "") {
            throw IllegalArgumentException("No capture found for name: $fromNode")
        } else if(toNodeName == "") {
            throw IllegalArgumentException("No capture found for name: $toNode")
        }
        TODO()
        //Deprecated
        /*graph.addConnectionAndMissingNodes(
            Connection(
                fromNodeName,
                toNodeName,
                ConnectionType.valueOf(connectionType),
                pathToFile,
                positionNode.sidx,
                positionNode.eidx
            ),
            { DanglingNodeBuilder(fromNodeName, pathToFile, context = parentNode, nodeType = fromNodeType) },
            { DanglingNodeBuilder(toNodeName, pathToFile, context = parentNode, nodeType = toNodeType) },
            parentNode
        )*/
    }
}