package software.bevel.code_to_knowledge_graph.antlr.bevel_ast_ql.predicates

import org.snt.inmemantlr.tree.ParseTreeRule
import software.bevel.code_to_knowledge_graph.antlr.AntlrGraphBuilder

/**
 * Defines a predicate that can be evaluated against a query match to determine its validity.
 * Predicates are used in [software.bevel.code_to_knowledge_graph.antlr.bevel_ast_ql.QueryNode.PatternMetadata]
 * to impose additional conditions on a matched pattern beyond its structural definition.
 */
interface QueryMatchPredicate {
    /**
     * Evaluates the predicate condition based on the current match context.
     *
     * @param currentParseTreeRule The [ParseTreeRule] at which the current pattern match started.
     *                             This is the primary node against which the predicate might be evaluated.
     * @param captures A map of named [ParseTreeRule]s that were captured during the matching process
     *                 up to this point. Predicates can use these captures to check relationships or properties
     *                 of other parts of the matched structure.
     * @param graph An optional [AntlrGraphBuilder] instance. If provided, the predicate can query the
     *              existing state of the knowledge graph being built. This allows for context-sensitive
     *              conditions that depend on previously processed elements.
     * @return `true` if the predicate condition is satisfied, `false` otherwise. A `false` return
     *         typically invalidates the current query match.
     */
    fun evaluate(currentParseTreeRule: ParseTreeRule, captures: Map<String, ParseTreeRule>, graph: AntlrGraphBuilder? = null): Boolean
}
