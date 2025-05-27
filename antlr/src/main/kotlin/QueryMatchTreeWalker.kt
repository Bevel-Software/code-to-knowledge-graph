package software.bevel.code_to_knowledge_graph.antlr

import org.snt.inmemantlr.tree.ParseTreeRule
import software.bevel.code_to_knowledge_graph.antlr.bevel_ast_ql.QueryMatcher
import software.bevel.graph_domain.graph.builder.FullyQualifiedNodeBuilder

/**
 * Walks a tree of query matches ([QueryMatcher.QueryMatchTreeNode]) to process ANTLR parse tree rules
 * and build an [AntlrGraphBuilder]. It applies predicates and converters defined in the query patterns
 * to transform parse tree information into graph nodes and connections.
 *
 * @property intermediateGraph The [AntlrGraphBuilder] instance that is populated during the walk.
 * @property pathToFile The path to the file currently being processed, used for context in converters.
 */
class QueryMatchTreeWalker(
    private var intermediateGraph: AntlrGraphBuilder,
    private val pathToFile: String
) {
    companion object {
        /**
         * Static factory method to initiate the walk process.
         * It creates an instance of [QueryMatchTreeWalker] and processes the provided query matches.
         *
         * Note: This method currently contains a `TODO()` and may not be fully implemented.
         * The commented-out code suggests it previously initialized a root package node.
         *
         * @param root The root [ParseTreeRule] of the ANTLR parse tree.
         * @param matches A list of [QueryMatcher.QueryMatchTreeNode] representing the query matches to process.
         * @param rootPackageName The name of the root package for the current file.
         * @param pathToFile The path to the file being parsed.
         * @param graph The initial [AntlrGraphBuilder] to be populated.
         * @return The populated [AntlrGraphBuilder] after processing all matches.
         */
        fun walk(root: ParseTreeRule, matches: List<QueryMatcher.QueryMatchTreeNode>, rootPackageName: String, pathToFile: String, graph: AntlrGraphBuilder): AntlrGraphBuilder {
            TODO()
            val walker = QueryMatchTreeWalker(graph, pathToFile)
            //DEPRECATED
            /*walker.intermediateGraph.nodes[rootPackageName] = FullyQualifiedNodeBuilder(
                name = rootPackageName,
                nodeType = NodeType.Package,
            )*/
            walker.intermediateGraph.nodesInParseTree[root] = walker.intermediateGraph.nodes[rootPackageName] as FullyQualifiedNodeBuilder
            for (match in matches) {
                walker.walkRecursively(match)
            }
            return walker.intermediateGraph
        }
    }

    /**
     * Recursively walks the query match tree node and its children.
     * For each node, it evaluates predicates and executes converters defined in the associated query pattern.
     * Predicates can halt processing for a branch if they evaluate to false.
     * Converters transform parse tree information into graph elements within the [intermediateGraph].
     *
     * @param currentTreeNode The current [QueryMatcher.QueryMatchTreeNode] to process.
     */
    private fun walkRecursively(currentTreeNode: QueryMatcher.QueryMatchTreeNode) {
        currentTreeNode.match.patternData.predicates.forEach { predicate ->
            if(!predicate.evaluate(currentTreeNode.match.startingParseTreeRule, currentTreeNode.context + currentTreeNode.match.captures, intermediateGraph))
                return
        }
        currentTreeNode.match.patternData.converters.forEach { converter ->
            if(converter.shouldExecute(currentTreeNode.match.startingParseTreeRule, currentTreeNode.context + currentTreeNode.match.captures, intermediateGraph))
                converter.convert(currentTreeNode.match.startingParseTreeRule, intermediateGraph, currentTreeNode.context + currentTreeNode.match.captures, pathToFile)
        }

        for (child in currentTreeNode.children) {
            walkRecursively(child)
        }
    }
}