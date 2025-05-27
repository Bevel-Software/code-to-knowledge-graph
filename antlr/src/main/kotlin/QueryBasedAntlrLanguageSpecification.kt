package software.bevel.code_to_knowledge_graph.antlr

import org.antlr.v4.runtime.*
import software.bevel.code_to_knowledge_graph.antlr.bevel_ast_ql.QueryNode

/**
 * Extends [AntlrLanguageSpecification] to support query-based ANTLR language processing.
 * This interface is designed for scenarios where language elements are identified or processed
 * based on predefined query patterns.
 *
 * @param PARSER The type of the ANTLR parser.
 * @param LEXER The type of the ANTLR lexer.
 */
interface QueryBasedAntlrLanguageSpecification<PARSER: Parser, LEXER: Lexer>: AntlrLanguageSpecification<PARSER, LEXER> {
    /**
     * An optional base identifier name that might be used in the querying process.
     * Defaults to `null` if not overridden.
     */
    val baseIdentifierName: String?
        get() = null

    /**
     * Retrieves a list of query patterns used by this language specification.
     * These patterns are likely used to match specific structures or elements in the parsed code.
     *
     * @return A list of [QueryNode.PatternMetadata] objects representing the query patterns.
     */
    fun getPatterns(): List<QueryNode.PatternMetadata>
}