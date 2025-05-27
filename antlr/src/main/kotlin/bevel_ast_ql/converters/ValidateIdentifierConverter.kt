package software.bevel.code_to_knowledge_graph.antlr.bevel_ast_ql.converters

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.snt.inmemantlr.tree.ParseTreeRule
import software.bevel.code_to_knowledge_graph.antlr.AntlrGraphBuilder
import software.bevel.code_to_knowledge_graph.antlr.bevel_ast_ql.ArgumentResolver.Companion.resolveAndFormatArgument
import software.bevel.code_to_knowledge_graph.antlr.bevel_ast_ql.predicates.JsonPredicate

/**
 * A [JsonAndQueryMatchConverter] that validates the presence of a specified identifier within the [AntlrGraphBuilder].
 *
 * This converter resolves an identifier string (from an expression or capture) and checks if this identifier
 * is part of any existing node ID in the graph (`graph.nodes`) or if it's found within the values of
 * recorded file imports (`graph.fileImports`). If the identifier is not found in either location,
 * a warning message is logged using an SLF4J logger.
 *
 * @property identifier An expression or capture name that resolves to the string identifier to be validated.
 * @property predicates An optional list of [JsonPredicate]s. The [convert] method will only execute if all
 *                      predicates evaluate to true (as determined by [QueryMatchConverter.shouldExecute]).
 * @property logger An SLF4J [Logger] instance, defaulting to the logger for `ValidateIdentifierConverter::class.java`,
 *                  used for logging warnings if the identifier is not found.
 */
class ValidateIdentifierConverter(
    val identifier: String,
    override val predicates: List<JsonPredicate>?,
    val logger: Logger = LoggerFactory.getLogger(ValidateIdentifierConverter::class.java)
) : JsonAndQueryMatchConverter {
    companion object {
        /** Unique identifier for this converter type, typically used in configuration files (e.g., YAML). */
        const val NAME = "validate-identifier"
    }

    /**
     * Executes the identifier validation logic.
     *
     * This method resolves the `identifier` string using [resolveAndFormatArgument]. It then checks if the
     * resolved identifier exists either as a substring within any node ID in `graph.nodes` or as a substring
     * within any value in `graph.fileImports`. If the identifier is not found in either, a warning is logged.
     *
     * @param currentParseTreeRule The ANTLR [ParseTreeRule] that triggered this converter.
     * @param graph The [AntlrGraphBuilder] instance containing the nodes and file imports to check against.
     * @param context A map of named captures from the query match, used by the [resolveAndFormatArgument].
     * @param pathToFile The path to the source file being parsed (not directly used in the validation logic itself).
     */
    override fun convert(
        currentParseTreeRule: ParseTreeRule,
        graph: AntlrGraphBuilder,
        context: Map<String, ParseTreeRule>,
        pathToFile: String
    ) {
        val resolvedIdentifier = resolveAndFormatArgument(currentParseTreeRule, identifier, context, graph)
        if(graph.nodes.values.find { it.id.contains(resolvedIdentifier) } == null && graph.fileImports.find { it.data.values.find { value -> value.contains(resolvedIdentifier) } != null } == null) {
            logger.warn("Identifier not parsed into the graph: $resolvedIdentifier")
        }
    }
}