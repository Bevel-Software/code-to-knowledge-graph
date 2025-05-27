package software.bevel.code_to_knowledge_graph.antlr.bevel_ast_ql.converters

import software.bevel.code_to_knowledge_graph.antlr.bevel_ast_ql.predicates.QueryMatchPredicate

/**
 * An interface that bridges [JsonConverter] and [QueryMatchConverter].
 *
 * Implementations of this interface can be deserialized from a JSON/YAML structure (via `JsonConverter`)
 * and then directly used as an executable `QueryMatchConverter` in the query matching process.
 * It assumes that implementing classes will also provide a `predicates: List<JsonPredicate>?` property,
 * typically inherited or defined as part of fulfilling the `JsonConverter` contract.
 */
interface JsonAndQueryMatchConverter: JsonConverter, QueryMatchConverter {
    /**
     * Overrides the `conditions` property (likely from [QueryMatchConverter]) to provide executable predicates.
     *
     * It maps the `predicates` (expected to be a list of [software.bevel.code_to_knowledge_graph.antlr.bevel_ast_ql.predicates.JsonPredicate])
     * to a list of [QueryMatchPredicate] instances.
     * Returns null if `predicates` is null.
     */
    override val conditions: List<QueryMatchPredicate>?
        get() = predicates?.map { it.toPredicate() }

    /**
     * Overrides the `toConverter` method (likely from [JsonConverter]) to affirm its identity as a [QueryMatchConverter].
     *
     * @return This instance, as it already fulfills the [QueryMatchConverter] contract.
     */
    override fun toConverter(): QueryMatchConverter = this
}