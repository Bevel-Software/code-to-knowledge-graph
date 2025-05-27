package software.bevel.code_to_knowledge_graph.antlr.bevel_ast_ql.converters

import org.snt.inmemantlr.tree.ParseTreeRule
import software.bevel.graph_domain.graph.NodeType
import software.bevel.graph_domain.graph.builder.NodeBuilder
import software.bevel.code_to_knowledge_graph.antlr.AntlrGraphBuilder
import software.bevel.code_to_knowledge_graph.antlr.bevel_ast_ql.ArgumentResolver.Companion.resolveAndFormatArgument
import software.bevel.code_to_knowledge_graph.antlr.bevel_ast_ql.predicates.JsonPredicate
import java.util.*

/**
 * A [JsonAndQueryMatchConverter] responsible for creating a new node in the [AntlrGraphBuilder].
 *
 * The node's name is resolved using [software.bevel.code_to_knowledge_graph.antlr.bevel_ast_ql.ArgumentResolver].
 * The created node can be associated with a specific ANTLR [ParseTreeRule] from the query captures
 * or with the `currentParseTreeRule`.
 *
 * Note: The core logic for node creation and registration in the graph is currently marked as `TODO() //Deprecated`
 * and the relevant code is commented out.
 *
 * @property nodeName The expression or capture name that resolves to the unique name/ID of the new node.
 * @property nodeType The type of the node to be created (e.g., "CLASS", "METHOD"), corresponding to a [NodeType].
 * @property representingParseTreeNode Optional capture name (expected to start with '@', e.g., "@myCapture") of a
 *                                   [ParseTreeRule] from the query context. If provided, the new graph node
 *                                   is associated with this specific parse tree node. If null, the
 *                                   `currentParseTreeRule` is used for this association (if the logic were active).
 * @property predicates An optional list of [JsonPredicate]s that must all evaluate to true for this converter to be applied.
 */
open class NodeConverter(
    val nodeName: String,
    val nodeType: String,
    val representingParseTreeNode: String? = null,
    override val predicates: List<JsonPredicate>? = null
): JsonAndQueryMatchConverter {
    companion object {
        /** Unique identifier for this converter type, typically used in configuration files (e.g., YAML). */
        const val NAME = "node"
    }

    /**
     * Applies the logic to create a new node and (intends to) register it in the graph.
     *
     * This method resolves the node's name and type. The actual instantiation of a [NodeBuilder]
     * (e.g., `FullyQualifiedNodeBuilder`), its association with a parse tree node in `graph.nodesInParseTree`,
     * and its addition to `graph.nodes` are currently commented out and marked as `TODO() //Deprecated`.
     *
     * If the commented-out logic were active and `representingParseTreeNode` was provided but not found
     * in the `context`, an [IllegalArgumentException] would be thrown.
     *
     * @param currentParseTreeRule The current ANTLR [ParseTreeRule] being processed.
     * @param graph The [AntlrGraphBuilder] instance where the node should be added.
     * @param context A map of named captures from the query match, used by the [software.bevel.code_to_knowledge_graph.antlr.bevel_ast_ql.ArgumentResolver].
     * @param pathToFile The path to the source file being parsed (not directly used by the current active logic).
     */
    override fun convert(
        currentParseTreeRule: ParseTreeRule,
        graph: AntlrGraphBuilder,
        context: Map<String, ParseTreeRule>,
        pathToFile: String
    ) {
        val nodeType = NodeType.valueOf(nodeType.uppercase(Locale.getDefault()))
        val node: NodeBuilder
        val name = resolveAndFormatArgument(currentParseTreeRule, nodeName, context, graph)
        TODO() //Deprecated
        /*node = FullyQualifiedNodeBuilder(
            name = name,
            nodeType = nodeType,
        )
        if(representingParseTreeNode != null) {
            val parseTreeNode = context[representingParseTreeNode.substring(1)]
                ?: throw IllegalArgumentException("No capture found for name: ${representingParseTreeNode.substring(1)}")
            graph.nodesInParseTree[parseTreeNode] = node
        } else {
            graph.nodesInParseTree[currentParseTreeRule] = node
        }

        graph.nodes[node.id] = node*/
    }
}