package software.bevel.code_to_knowledge_graph.antlr

import org.antlr.v4.runtime.*
import software.bevel.graph_domain.graph.builder.ImportStatement

/**
 * Extends the [AntlrLanguageSpecification] interface to support a converter-based approach
 * for transforming ANTLR parse trees into graph structures using [AntlrTreeWalker].
 * Implementations of this interface define how to generate a list of [AntlrTreeWalker.TreeToGraphConverter]
 * rules specific to a language.
 *
 * @param PARSER The type of the ANTLR `Parser` specific to the language, inherited from [AntlrLanguageSpecification].
 * @param LEXER The type of the ANTLR `Lexer` specific to the language, inherited from [AntlrLanguageSpecification].
 */
interface ConverterBasedAntlrLanguageSpecification<PARSER: Parser, LEXER: Lexer>:
    AntlrLanguageSpecification<PARSER, LEXER> {

    /**
     * Declares and returns a list of [AntlrTreeWalker.TreeToGraphConverter] instances tailored for the specific language.
     * These converters define patterns in the ANTLR parse tree and the logic to transform them into graph nodes and relationships.
     *
     * @param pathToFile The absolute path to the file currently being parsed. This can be used by converters
     *                   to create file-specific nodes or context.
     * @param fileImports A mutable list of [ImportStatement]s extracted from the current file. Converters might use
     *                    this information for resolving symbols or understanding dependencies.
     * @param startIndex An integer representing the starting index for generating unique IDs for nodes created by
     *                   the converters. This helps in ensuring ID uniqueness across multiple files or parsing sessions.
     * @return A list of [AntlrTreeWalker.TreeToGraphConverter] to be used by the [AntlrTreeWalker].
     */
    fun declareConverters(pathToFile: String, fileImports: MutableList<ImportStatement>, startIndex: Int): List<AntlrTreeWalker.TreeToGraphConverter>
}