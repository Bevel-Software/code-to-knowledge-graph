package software.bevel.code_to_knowledge_graph.antlr.bevel_ast_ql.converters

import org.snt.inmemantlr.tree.ParseTreeRule
import software.bevel.graph_domain.graph.builder.ImportStatement
import software.bevel.code_to_knowledge_graph.antlr.AntlrGraphBuilder
import software.bevel.code_to_knowledge_graph.antlr.bevel_ast_ql.ArgumentResolver.Companion.resolveAndFormatArgument
import software.bevel.code_to_knowledge_graph.antlr.bevel_ast_ql.predicates.JsonPredicate

/**
 * A [JsonAndQueryMatchConverter] responsible for processing import statements identified during parsing.
 *
 * This converter resolves the name of the imported package/module and any associated data (like aliases or
 * specific imported members) using [software.bevel.code_to_knowledge_graph.antlr.bevel_ast_ql.ArgumentResolver].
 * It then creates an [ImportStatement] and adds it to the `fileImports` collection in the [AntlrGraphBuilder].
 *
 * @property importedPackage The expression or capture name that resolves to the string representation of the imported package or module.
 * @property importData An optional map for capturing additional details related to the import. Keys are descriptive
 *                      (e.g., "alias", "memberName"), and values are expressions or capture names to be resolved.
 * @property predicates An optional list of [JsonPredicate]s that must all evaluate to true for this converter to be applied.
 */
class ImportConverter(
    private val importedPackage: String,
    private val importData: Map<String, String> = emptyMap(),
    override val predicates: List<JsonPredicate>? = null,
): JsonAndQueryMatchConverter {
    companion object {
        /** Unique identifier for this converter type, typically used in configuration files (e.g., YAML). */
        const val NAME = "import"
    }

    /**
     * Applies the logic to create an [ImportStatement] and add it to the graph builder's file imports.
     *
     * This method resolves the imported package name and any additional import data using the provided
     * context. If any piece of `importData` resolves to an empty string, it is excluded from the final
     * [ImportStatement].
     *
     * @param currentParseTreeRule The current ANTLR [ParseTreeRule] being processed (often the import statement itself).
     * @param graph The [AntlrGraphBuilder] instance where the `fileImports` are stored.
     * @param context A map of named captures from the query match, used by the [software.bevel.code_to_knowledge_graph.antlr.bevel_ast_ql.ArgumentResolver].
     * @param pathToFile The path to the source file being parsed (not directly used by this converter but part of the interface).
     */
    override fun convert(
        currentParseTreeRule: ParseTreeRule,
        graph: AntlrGraphBuilder,
        context: Map<String, ParseTreeRule>,
        pathToFile: String
    ) {
        val impPkg = resolveAndFormatArgument(currentParseTreeRule, importedPackage, context, graph)
        val data = importData.mapNotNull {
            val arg = resolveAndFormatArgument(currentParseTreeRule, it.value, context, graph)
            if (arg == "")
                return@mapNotNull null
            it.key to arg
        }.toMap()
        graph.fileImports.add(ImportStatement(impPkg, data))
    }
}