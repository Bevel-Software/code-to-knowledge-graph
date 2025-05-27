package software.bevel.code_to_knowledge_graph.antlr.bevel_ast_ql.converters

import org.snt.inmemantlr.tree.ParseTreeRule
import software.bevel.code_to_knowledge_graph.antlr.AntlrGraphBuilder
import software.bevel.code_to_knowledge_graph.antlr.bevel_ast_ql.predicates.QueryMatchPredicate

/**
 * Defines the contract for converters that are executed when a query matches a specific ANTLR [ParseTreeRule].
 *
 * Implementations of this interface are responsible for performing actions, typically modifying the
 * [AntlrGraphBuilder], based on the matched parse tree rule and its context (captured elements).
 * Execution can be made conditional using [QueryMatchPredicate]s.
 */
interface QueryMatchConverter {
    /**
     * Executes the primary logic of this converter.
     *
     * This method is called when a query has matched and, if applicable, all [conditions] have been met.
     * Implementations will typically use the provided parameters to interact with the [AntlrGraphBuilder]
     * or perform other actions based on the parsed code structure.
     *
     * @param currentParseTreeRule The ANTLR [ParseTreeRule] that triggered this converter's execution due to a query match.
     * @param graph The [AntlrGraphBuilder] instance, which is the primary target for modifications (e.g., adding nodes/edges).
     * @param context A map containing named captures from the query match. Keys are capture names (String),
     *                and values are the corresponding [ParseTreeRule]s, providing access to specific parts of the matched tree.
     * @param pathToFile The file system path to the source code file being processed.
     */
    fun convert(currentParseTreeRule: ParseTreeRule, graph: AntlrGraphBuilder, context: Map<String, ParseTreeRule>, pathToFile: String)

    /**
     * An optional list of [QueryMatchPredicate]s that must all evaluate to true for the [convert] method to be executed.
     *
     * If this list is null or empty, the [convert] method is considered eligible for execution by default
     * (assuming the main query that led to this converter has matched).
     * The evaluation of these conditions is handled by the [shouldExecute] method.
     */
    val conditions: List<QueryMatchPredicate>?

    /**
     * Determines whether the [convert] method should be executed based on the [conditions].
     *
     * This method iterates through the [conditions] list. If the list is null or empty, it returns `true`.
     * Otherwise, it returns `true` only if all predicates in the list evaluate to `true`.
     * The evaluation short-circuits: if any predicate returns `false`, subsequent predicates are not checked,
     * and this method returns `false` immediately.
     *
     * @param currentParseTreeRule The current ANTLR [ParseTreeRule] context for predicate evaluation.
     * @param context A map of named captures from the query match, available for predicate evaluation.
     * @param graph The [AntlrGraphBuilder] instance, potentially used by predicates for context.
     * @return `true` if all conditions are met or if there are no conditions; `false` otherwise.
     */
    fun shouldExecute(currentParseTreeRule: ParseTreeRule, context: Map<String, ParseTreeRule>, graph: AntlrGraphBuilder?): Boolean {
        return conditions?.all { predicate ->
            predicate.evaluate(currentParseTreeRule, context, graph)
        } ?: true // If no predicates, always execute
    }
}