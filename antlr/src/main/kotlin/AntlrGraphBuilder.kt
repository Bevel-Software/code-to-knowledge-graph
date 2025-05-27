package software.bevel.code_to_knowledge_graph.antlr

import org.snt.inmemantlr.tree.ParseTreeRule
import software.bevel.graph_domain.graph.builder.GraphBuilder
import software.bevel.graph_domain.graph.builder.ImportStatement
import software.bevel.graph_domain.graph.builder.NodeBuilder

/**
 * A specialized [GraphBuilder] for constructing knowledge graphs from ANTLR parse trees.
 * It extends the general [GraphBuilder] functionality to include ANTLR-specific context,
 * such as mapping parse tree nodes to graph nodes and managing file imports.
 *
 * @param nodes Initial map of node UIDs to [NodeBuilder] instances. Defaults to an empty mutable map.
 * @param parent An optional parent [GraphBuilder] for creating a hierarchical graph structure. Defaults to null.
 */
class AntlrGraphBuilder(
    nodes: MutableMap<String, NodeBuilder> = mutableMapOf(),
    projectPath: String,
    parent: GraphBuilder? = null
) : GraphBuilder(nodes=nodes, parent=parent) {
    /**
     * A map associating ANTLR [ParseTreeRule] instances with their corresponding [NodeBuilder] instances in the graph.
     * This allows for direct lookups from a specific point in the parse tree to its graph representation.
     */
    val nodesInParseTree: MutableMap<ParseTreeRule, NodeBuilder> = mutableMapOf()
    /**
     * A list of [ImportStatement] objects extracted from the parsed file.
     * This is used to track dependencies and relationships established through import declarations.
     */
    var fileImports: MutableList<ImportStatement> = mutableListOf()

    /**
     * Traverses up the ANTLR parse tree from the given [astNode] to find the nearest ancestor
     * [ParseTreeRule] that has an associated [NodeBuilder] in the [nodesInParseTree] map.
     *
     * This method helps identify the "definer" or owning graph node for a specific AST node.
     *
     * @param astNode The ANTLR [ParseTreeRule] from which to start the upward search.
     * @return The [NodeBuilder] associated with the defining ancestor, or `null` if no such ancestor is found.
     */
    fun getDefiner(astNode: ParseTreeRule): NodeBuilder? {
        var currentNode: ParseTreeRule? = astNode
        while (currentNode != null && !nodesInParseTree.containsKey(currentNode)) {
            currentNode = currentNode.parent
        }
        return nodesInParseTree[currentNode]
    }

    /**
     * Traverses up the ANTLR parse tree from the given [astNode] to find the nearest ancestor
     * [ParseTreeRule] that has an associated [NodeBuilder] in the [nodesInParseTree] map.
     *
     * This method is similar to [getDefiner] but returns the [ParseTreeRule] of the definer itself,
     * rather than its associated [NodeBuilder].
     *
     * @param astNode The ANTLR [ParseTreeRule] from which to start the upward search.
     * @return The defining ancestor [ParseTreeRule], or `null` if no such ancestor is found.
     */
    fun getDefinerParseTreeNode(astNode: ParseTreeRule): ParseTreeRule? {
        var currentNode: ParseTreeRule? = astNode
        while (currentNode != null && !nodesInParseTree.containsKey(currentNode)) {
            currentNode = currentNode.parent
        }
        return currentNode
    }

    /**
     * Adds all nodes and connections from another [GraphBuilder] to this one.
     * If the `other` builder is also an [AntlrGraphBuilder], this method additionally merges
     * the ANTLR-specific properties: [nodesInParseTree] and [fileImports].
     *
     * @param other The [GraphBuilder] whose contents are to be added to this builder.
     */
    override fun addAll(other: GraphBuilder) {
        super.addAll(other)
        if(other is AntlrGraphBuilder) {
            nodesInParseTree.putAll(other.nodesInParseTree)
            fileImports.addAll(other.fileImports)
        }
    }
}