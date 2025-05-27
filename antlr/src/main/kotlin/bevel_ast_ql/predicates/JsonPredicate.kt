package software.bevel.code_to_knowledge_graph.antlr.bevel_ast_ql.predicates

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

/**
 * A sealed interface representing predicates that can be defined in and deserialized from JSON format.
 * This interface uses Jackson annotations to enable polymorphic deserialization based on the `"function"`
 * property in the JSON object. Each subtype corresponds to a specific predicate logic (e.g., equals, matches, exists).
 *
 * The primary role of this interface is to serve as a common type for various predicate configurations
 * loaded from external sources (like JSON query definitions) and to provide a mechanism to convert
 * these configurations into executable [QueryMatchPredicate] instances.
 *
 * It is annotated with [JsonInclude.Include.NON_NULL] to exclude null fields during serialization,
 * which can be useful for keeping JSON definitions clean.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "function"
)
@JsonSubTypes(
    JsonSubTypes.Type(value = EqualsPredicate::class, name = EqualsPredicate.NAME),
    JsonSubTypes.Type(value = NotEqualsPredicate::class, name = NotEqualsPredicate.NAME),
    JsonSubTypes.Type(value = MatchPredicate::class, name = MatchPredicate.NAME),
    JsonSubTypes.Type(value = NotMatchPredicate::class, name = NotMatchPredicate.NAME),
    JsonSubTypes.Type(value = AnyOfPredicate::class, name = AnyOfPredicate.NAME),
    JsonSubTypes.Type(value = ExistsPredicate::class, name = ExistsPredicate.NAME),
    JsonSubTypes.Type(value = NotExistsPredicate::class, name = NotExistsPredicate.NAME),
    JsonSubTypes.Type(value = NotPredicate::class, name = NotPredicate.NAME),
    JsonSubTypes.Type(value = EndsWithPredicate::class, name = EndsWithPredicate.NAME),
    JsonSubTypes.Type(value = NotEndsWithPredicate::class, name = NotEndsWithPredicate.NAME),
    JsonSubTypes.Type(value = ContainsPredicate::class, name = ContainsPredicate.NAME),
    JsonSubTypes.Type(value = NotContainsPredicate::class, name = NotContainsPredicate.NAME),
)
@JsonInclude(JsonInclude.Include.NON_NULL)
sealed interface JsonPredicate {
    /**
     * Converts this JSON-defined predicate configuration into an executable [QueryMatchPredicate].
     * Each concrete implementation of [JsonPredicate] will provide its own logic for this conversion,
     * effectively transforming the declarative JSON structure into a functional predicate object
     * that can be used by the query matching engine.
     *
     * @return The corresponding [QueryMatchPredicate] instance that implements the logic defined
     *         by this JSON predicate configuration.
     */
    abstract fun toPredicate(): QueryMatchPredicate
}
