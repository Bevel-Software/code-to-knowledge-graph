package software.bevel.code_to_knowledge_graph.antlr

import org.snt.inmemantlr.tree.ParseTree
import org.snt.inmemantlr.tree.ParseTreeRule
import software.bevel.graph_domain.graph.builder.GraphBuilder
import software.bevel.graph_domain.graph.builder.FullyQualifiedNodeBuilder
import software.bevel.graph_domain.graph.builder.NodeBuilder

/**
 * Traverses an ANTLR [ParseTree] and applies a series of [TreeToGraphConverter] rules
 * to build or augment an [AntlrGraphBuilder].
 *
 * @property converters A list of [TreeToGraphConverter] rules to be applied during the tree walk.
 * @property intermediateGraph The [AntlrGraphBuilder] instance that is populated or modified during the traversal.
 */
class AntlrTreeWalker(
    private var converters: List<TreeToGraphConverter>,
    private var intermediateGraph: AntlrGraphBuilder
) {
    /**
     * Defines a rule for converting a specific pattern of ANTLR [ParseTreeRule]s into a [NodeBuilder]
     * within a [GraphBuilder].
     *
     * @property pattern A list of strings representing a sequence of ANTLR rule names. This pattern is matched
     *                   against the current node and its ancestors in the parse tree. The special string "*"
     *                   can be used as a wildcard to match any sequence of parent nodes until the next
     *                   specified rule in the pattern is found (or the root if "*" is the first element).
     * @property convert A lambda function that is executed when the [pattern] successfully matches a part of the
     *                   parse tree. It receives the definer [NodeBuilder] for the matched context, a list of
     *                   matched [ParseTreeRule]s (astStack), and the current [GraphBuilder]. It should return
     *                   a [NodeBuilder] to be associated with the primary matched AST node, or `null` if no new
     *                   node is created by this conversion.
     */
    data class TreeToGraphConverter(
        val pattern: List<String>,
        val convert: (definer: NodeBuilder?, astStack: List<ParseTreeRule>, currentGraph: GraphBuilder) -> NodeBuilder?
    )

    companion object {
        /**
         * Initiates the traversal of an ANTLR [ParseTree] to build a graph using the provided converters.
         * It sets up an [AntlrTreeWalker] instance and starts the recursive walk from the tree's root.
         *
         * Note: This method currently contains a `TODO()` marker. As a result, all code following
         * the `TODO()` call within this method is currently unreachable and will not be executed.
         * The method also contains a commented-out section marked as "DEPRECATED".
         *
         * @param tree The ANTLR [ParseTree] to walk.
         * @param converters A list of [TreeToGraphConverter] rules to apply.
         * @param rootPackageName The name to be used for the root package node in the graph.
         * @param graph The initial [AntlrGraphBuilder] to populate.
         * @return The populated [AntlrGraphBuilder] after the tree walk and conversions.
         */
        fun walk(tree: ParseTree, converters: List<TreeToGraphConverter>, rootPackageName: String, graph: AntlrGraphBuilder): GraphBuilder {
            TODO()
            val walker = AntlrTreeWalker(converters, graph)
            //DEPRECATED
            /*walker.intermediateGraph.nodes[rootPackageName] = FullyQualifiedNodeBuilder(
                name = rootPackageName,
                nodeType = NodeType.Package,
            )*/
            walker.intermediateGraph.nodesInParseTree[tree.root] = walker.intermediateGraph.nodes[rootPackageName] as FullyQualifiedNodeBuilder
            walker.walkRecursively(tree.root)
            return walker.intermediateGraph
        }
    }

    /**
     * Recursively walks the ANTLR parse tree starting from the [currentTreeNode].
     * For each node, it attempts to match and apply [TreeToGraphConverter] rules.
     * If a converter matches and its `convert` lambda returns a new [NodeBuilder], that builder
     * is associated with the primary matched AST node in the `intermediateGraph.nodesInParseTree` map.
     * The process then continues recursively for all children of the [currentTreeNode].
     *
     * @param currentTreeNode The current [ParseTreeRule] being visited.
     */
    private fun walkRecursively(currentTreeNode: ParseTreeRule) {
        val converters = converters.filter { it.pattern.last() == currentTreeNode.rule }
        for (converter in converters) {
            val matchedNodes = checkNode(currentTreeNode, converter).reversed()
            if(matchedNodes.isNotEmpty()) {
                val definer = intermediateGraph.getDefiner(matchedNodes[matchedNodes.size - 1])
                val newNode = converter.convert(definer, matchedNodes, intermediateGraph)
                if(intermediateGraph.nodes.containsKey("$")) {
                    print("")
                }
                if(newNode != null) {
                    intermediateGraph.nodesInParseTree[matchedNodes[0]] = newNode
                    break
                }
            }
        }

        for (child in currentTreeNode.children) {
            walkRecursively(child)
        }
    }

    /**
     * Checks if the given [converter]'s pattern matches the [ParseTreeRule] [node] and its ancestors.
     * The pattern is a list of ANTLR rule names. The special string "*" acts as a wildcard,
     * matching any sequence of parent nodes until the next rule in the pattern or the tree root.
     * The match proceeds from the last element of the pattern against the current [node], upwards.
     *
     * @param node The current [ParseTreeRule] to start matching against.
     * @param converter The [TreeToGraphConverter] whose pattern is to be matched.
     * @return A list of [ParseTreeRule]s that form the matched sequence, corresponding to the pattern.
     *         The list is ordered from the node matching the first pattern element to the node matching the last.
     *         Returns an empty list if the pattern does not match.
     */
    private fun checkNode(node: ParseTreeRule, converter: TreeToGraphConverter): List<ParseTreeRule> {
        var currentTreeNode: ParseTreeRule? = node
        val matchedNodes = mutableListOf<ParseTreeRule>()
        var i = converter.pattern.size-1
        while(i >= 0) {
            if(currentTreeNode == null)
                return listOf()

            if(converter.pattern[i] == "*") {
                if(i == 0) break
                while (currentTreeNode != null && converter.pattern[i-1] != currentTreeNode.rule) {
                    currentTreeNode = currentTreeNode.parent
                }
                if(currentTreeNode == null)
                    return listOf()
                i--
            }

            if(converter.pattern[i] == currentTreeNode.rule)
                matchedNodes.add(currentTreeNode)
            else
                return listOf()

            currentTreeNode = currentTreeNode.parent
            i--
        }
        return matchedNodes
    }
}