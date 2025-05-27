package software.bevel.code_to_knowledge_graph.antlr.bevel_ast_ql

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import software.bevel.code_to_knowledge_graph.antlr.bevel_ast_ql.converters.JsonConverter
import software.bevel.code_to_knowledge_graph.antlr.bevel_ast_ql.predicates.JsonPredicate
import software.bevel.code_to_knowledge_graph.antlr.bevel_ast_ql.predicates.QueryMatchPredicate
import software.bevel.code_to_knowledge_graph.antlr.bevel_ast_ql.converters.QueryMatchConverter
import software.bevel.code_to_knowledge_graph.antlr.bevel_ast_ql.predicates.*

/**
 * Represents the top-level structure of a query defined in JSON or YAML format.
 *
 * @property patterns A list of [JsonPatternMetadata] objects, each defining a pattern to be matched,
 *                    along with its associated predicates and converters.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class JsonQuery(
    val patterns: List<JsonPatternMetadata>
)

/**
 * Represents the metadata for a single pattern within a JSON/YAML query.
 * This includes the pattern itself, and optional lists of predicates and converters.
 *
 * @property pattern The [JsonPattern] to be matched.
 * @property predicates An optional list of [JsonPredicate]s that must evaluate to true for the match to be valid.
 * @property converters An optional list of [JsonConverter]s to be applied if the pattern matches and predicates pass.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class JsonPatternMetadata(
    val pattern: JsonPattern,
    val predicates: List<JsonPredicate>? = null,
    val converters: List<JsonConverter>? = null,
)

/**
 * Base sealed class for representing different types of patterns within a JSON/YAML query structure.
 * Uses Jackson's type deduction for deserialization.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.DEDUCTION
)
sealed class JsonPattern {
    /**
     * Represents a pattern that matches a specific parse tree rule (node type) in a JSON/YAML query.
     *
     * @property rule The name of the parse tree rule to match (e.g., "classDeclaration").
     * @property captures An optional list of names to capture the matched [org.snt.inmemantlr.tree.ParseTreeRule] under.
     * @property children An optional list of child [JsonPattern]s that must also match under this node.
     * @property notChildren An optional set of rule names that must *not* be present as direct children of the matched node.
     * @property optional If true (defaults to false), this pattern is optional for the overall match to succeed.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    data class NodePattern(
        val rule: String,
        val captures: List<String>? = null,
        val children: List<JsonPattern>? = null,
        val notChildren: Set<String>? = null,
        val optional: Boolean = false
    ) : JsonPattern()

    /**
     * Represents a pattern where one of several alternative [JsonPattern]s must match in a JSON/YAML query.
     *
     * @property alternatives A list of [JsonPattern]s, one of which must match.
     * @property optional If true (defaults to false), this entire alternatives pattern is optional for the overall match to succeed.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    data class AlternativesPattern(
        val alternatives: List<JsonPattern>,
        val optional: Boolean = false
    ) : JsonPattern()
}

/**
 * Parses a query string (expected to be in YAML format) into a list of [QueryNode.PatternMetadata]
 * objects, which can then be used by [QueryMatcher] to find matches in a parse tree.
 */
class QueryParser {
    companion object {
        /** Jackson ObjectMapper configured for YAML processing and Kotlin module support. */
        private val mapper = ObjectMapper(YAMLFactory()).registerKotlinModule()

        /**
         * Parses the given YAML query string into a list of [QueryNode.PatternMetadata].
         *
         * @param query The YAML string representing the query.
         * @return A list of [QueryNode.PatternMetadata] parsed from the query.
         * @throws IllegalArgumentException if the query string is invalid YAML or does not conform to the expected structure.
         */
        fun parse(query: String): List<QueryNode.PatternMetadata> {
            val jsonQuery = try {
                mapper.readValue(query, JsonQuery::class.java)
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid YAML query: ${e.message}")
            }

            return jsonQuery.patterns.map {
                val predicates = mutableListOf<QueryMatchPredicate>()
                val converters = mutableListOf<QueryMatchConverter>()
                // Handle predicates
                it.predicates?.forEach { predicate ->
                    predicates.add(predicate.toPredicate())
                }

                // Handle actions
                it.converters?.forEach { action ->
                    converters.add(action.toConverter())
                }

                QueryNode.PatternMetadata(
                    pattern = parsePattern(it.pattern),
                    predicates = predicates,
                    converters = converters
                )
            }
        }

        /**
         * Recursively parses a [JsonPattern] from the YAML structure into a [QueryNode.Pattern].
         *
         * @param pattern The [JsonPattern] to parse.
         * @return The corresponding [QueryNode.Pattern].
         */
        private fun parsePattern(pattern: JsonPattern): QueryNode.Pattern {
            return when (pattern) {
                is JsonPattern.NodePattern -> parseNodePattern(pattern)
                is JsonPattern.AlternativesPattern -> parseAlternativesPattern(pattern)
            }
        }

        /**
         * Parses a [JsonPattern.NodePattern] into a [QueryNode.Pattern.NodePattern].
         *
         * @param pattern The [JsonPattern.NodePattern] from the YAML structure.
         * @return The corresponding [QueryNode.Pattern.NodePattern].
         */
        private fun parseNodePattern(pattern: JsonPattern.NodePattern): QueryNode.Pattern {
            val captures = pattern.captures?.toList() ?: emptyList()
            val predicates = mutableListOf<QueryMatchPredicate>()
            val converters = mutableListOf<QueryMatchConverter>()
            val children = mutableListOf<QueryNode.Pattern>()
            val notChildren = pattern.notChildren?.toSet() ?: emptySet()

            // Handle children
            pattern.children?.forEach { child ->
                children.add(parsePattern(child))
            }

            return QueryNode.Pattern.NodePattern(
                rule = pattern.rule,
                captures = captures,
                children = children,
                notChildren = notChildren,
                optional = pattern.optional
            )
        }

        /**
         * Parses a [JsonPattern.AlternativesPattern] into a [QueryNode.Pattern.AlternativesPattern].
         *
         * @param pattern The [JsonPattern.AlternativesPattern] from the YAML structure.
         * @return The corresponding [QueryNode.Pattern.AlternativesPattern].
         */
        private fun parseAlternativesPattern(pattern: JsonPattern.AlternativesPattern): QueryNode.Pattern {
            return QueryNode.Pattern.AlternativesPattern(
                alternatives = pattern.alternatives.map { parsePattern(it) },
                optional = pattern.optional
            )
        }
    }
}
