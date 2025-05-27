package software.bevel.code_to_knowledge_graph.antlr.bevel_ast_ql.converters

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import software.bevel.code_to_knowledge_graph.antlr.bevel_ast_ql.predicates.JsonPredicate

/**
 * A sealed interface representing a converter that can be defined in a JSON or YAML configuration.
 * It uses Jackson's polymorphic type handling to deserialize into specific converter implementations
 * based on the `function` property in the JSON/YAML data.
 *
 * The `@JsonTypeInfo` annotation specifies that the `function` property in the input data
 * will determine the concrete subtype to instantiate.
 * The `@JsonSubTypes` annotation lists all known concrete converter implementations and their
 * corresponding names (e.g., "node" maps to [NodeConverter]).
 * The `@JsonInclude(JsonInclude.Include.NON_NULL)` annotation ensures that properties with null values
 * are not included if an instance of this type is serialized.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "function"
)
@JsonSubTypes(
    JsonSubTypes.Type(value = NodeConverter::class, name = NodeConverter.NAME),
    JsonSubTypes.Type(value = ConnectionConverter::class, name = ConnectionConverter.NAME),
    JsonSubTypes.Type(value = ImportConverter::class, name = ImportConverter.NAME),
    JsonSubTypes.Type(value = ArgumentConnectionConverter::class, name = ArgumentConnectionConverter.NAME),
    JsonSubTypes.Type(value = NodeWithParentConverter::class, name = NodeWithParentConverter.NAME),
    JsonSubTypes.Type(value = ValidateIdentifierConverter::class, name = ValidateIdentifierConverter.NAME),
    JsonSubTypes.Type(value = FunctionDeclarationConverter::class, name = FunctionDeclarationConverter.NAME),
)
@JsonInclude(JsonInclude.Include.NON_NULL)
sealed interface JsonConverter {
    /**
     * An optional list of [JsonPredicate]s associated with this converter.
     * These predicates are conditions, defined in the JSON/YAML configuration, that must be met
     * for this converter's logic to be applied.
     * Concrete implementations must provide this property.
     */
    val predicates: List<JsonPredicate>?

    /**
     * Converts this JSON-defined converter configuration into an executable [QueryMatchConverter].
     *
     * This method allows the query matching engine to use the converter instance after it has been
     * deserialized from JSON/YAML.
     * Concrete implementations must provide this transformation.
     * @return An executable [QueryMatchConverter] instance.
     */
    fun toConverter(): QueryMatchConverter
}