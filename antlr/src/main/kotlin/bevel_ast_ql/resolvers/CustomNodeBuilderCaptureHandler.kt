package software.bevel.code_to_knowledge_graph.antlr.bevel_ast_ql.resolvers

import org.snt.inmemantlr.tree.ParseTreeRule
import software.bevel.graph_domain.graph.NodeType
import software.bevel.graph_domain.graph.builder.DanglingNodeBuilder
import software.bevel.graph_domain.graph.builder.FullyQualifiedNodeBuilder
import software.bevel.graph_domain.graph.builder.GraphBuilder
import software.bevel.graph_domain.graph.builder.NodeBuilder
import software.bevel.code_to_knowledge_graph.antlr.AntlrGraphBuilder

/**
 * A functional interface for handlers that provide custom logic to resolve special capture names
 * to [NodeBuilder] instances. This allows the query system to support predefined capture names
 * that refer to specific nodes within the constructed knowledge graph (e.g., an enclosing class node).
 *
 * Unlike [CustomParseTreeCaptureHandler] which resolves to raw [ParseTreeRule]s, this interface
 * deals with higher-level [NodeBuilder] representations from the graph domain.
 */
fun interface CustomNodeBuilderCaptureHandler {
    /**
     * Resolves a special capture name to a specific [NodeBuilder] in the graph.
     *
     * @param currentParseTreeRule The [ParseTreeRule] that is the current context of the match or resolution.
     *                             This is often used as a starting point to find a related graph node.
     * @param captureName The special capture name to be resolved. The interpretation of this name
     *                    is up to the implementing handler.
     * @param captures A map of explicitly named [ParseTreeRule] captures from the current query match.
     *                 This might be used by some handlers for more complex contextual resolution.
     * @param graph An [AntlrGraphBuilder] instance, providing access to the constructed knowledge graph
     *              and mappings between parse tree nodes and graph [NodeBuilder]s.
     * @return The resolved [NodeBuilder] if the special capture name can be resolved by this handler;
     *         `null` otherwise, or if the target node is not found.
     */
    fun resolveCapture(currentParseTreeRule: ParseTreeRule, captureName: String, captures: Map<String, ParseTreeRule>, graph: AntlrGraphBuilder?): NodeBuilder?
}

/**
 * A [CustomNodeBuilderCaptureHandler] that resolves a capture to the [NodeBuilder] representing
 * the nearest enclosing class of the given [currentParseTreeRule].
 *
 * It first attempts to find the [NodeBuilder] associated with the parse tree context and then
 * traverses up the graph hierarchy to find the parent class node.
 */
open class ParentClassNodeHandler: CustomNodeBuilderCaptureHandler {
    /**
     * Resolves the capture by finding the [NodeBuilder] for the [currentParseTreeRule] (or its definer)
     * and then searching upwards in the graph for its enclosing class node.
     *
     * @param currentParseTreeRule The parse tree node from which to start searching for an enclosing class.
     * @param captureName Unused by this specific handler, but part of the interface.
     * @param captures Unused by this specific handler.
     * @param graph The [AntlrGraphBuilder] used to map parse tree nodes to [NodeBuilder]s and to traverse the graph.
     * @return The [NodeBuilder] of the enclosing class if found, otherwise `null`.
     */
    override fun resolveCapture(currentParseTreeRule: ParseTreeRule, captureName: String, captures: Map<String, ParseTreeRule>, graph: AntlrGraphBuilder?): NodeBuilder? {
        val parentNode = graph?.getDefinerParseTreeNode(currentParseTreeRule)
        val parentClassNode = findParentClassNode(graph?.nodesInParseTree?.get(parentNode) ?: return null, graph) ?: return null
        return parentClassNode
    }

    /**
     * Traverses up the graph hierarchy from a given [node] to find the nearest ancestor [NodeBuilder]
     * that represents a class.
     *
     * @param node The starting [NodeBuilder] from which to begin the upward search.
     * @param graph The [GraphBuilder] instance used to get parent nodes.
     * @return The [NodeBuilder] of the first encountered parent class, or `null` if no such class is found
     *         in the ancestry.
     */
    protected fun findParentClassNode(node: NodeBuilder, graph: GraphBuilder): NodeBuilder? {
        var currentNode: NodeBuilder? = node
        while (currentNode != null) {
            if ((currentNode is FullyQualifiedNodeBuilder && currentNode.nodeType == NodeType.Class)
                || (currentNode is DanglingNodeBuilder && currentNode.nodeType == NodeType.Class)) {
                return currentNode
            }
            currentNode = graph.getParent(currentNode.id)
        }
        return null
    }
}

/**
 * A [CustomNodeBuilderCaptureHandler] that resolves a capture to the [NodeBuilder] representing
 * the superclass of the nearest enclosing class of the [currentParseTreeRule].
 *
 * It first finds the enclosing class and then attempts to find its superclass node.
 */
class ParentSuperClassNodeHandler: ParentClassNodeHandler() {
    /**
     * Resolves the capture by first finding the [NodeBuilder] for the enclosing class of the [currentParseTreeRule]
     * using the inherited logic from [ParentClassNodeHandler]. Then, it retrieves the supertypes of this class node
     * from the graph and returns the first supertype that is also a class.
     *
     * @param currentParseTreeRule The parse tree node from which to start searching for an enclosing class and its superclass.
     * @param captureName Unused by this specific handler, but part of the interface.
     * @param captures Unused by this specific handler.
     * @param graph The [AntlrGraphBuilder] used for graph traversal and type lookups.
     * @return The [NodeBuilder] of the superclass if found, otherwise `null`.
     */
    override fun resolveCapture(currentParseTreeRule: ParseTreeRule, captureName: String, captures: Map<String, ParseTreeRule>, graph: AntlrGraphBuilder?): NodeBuilder? {
        val parentClassNode = findParentClassNode(graph?.nodesInParseTree?.get(currentParseTreeRule) ?: return null, graph) ?: return null
        val superClassNode = graph.getSuperType(parentClassNode.id).filter {
            it is FullyQualifiedNodeBuilder && it.nodeType == NodeType.Class
                    || it is DanglingNodeBuilder && it.nodeType == NodeType.Class
        }.firstOrNull()
        return superClassNode
    }
}