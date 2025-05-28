package software.bevel.code_to_knowledge_graph.vscode.languageSpecs

import software.bevel.graph_domain.graph.builder.GraphBuilder
import software.bevel.graph_domain.graph.builder.NodeBuilder
import software.bevel.graph_domain.parsing.PotentialSymbol
import software.bevel.file_system_domain.services.FileHandler
import software.bevel.graph_domain.tokenizers.IdentifierTokenizer
import software.bevel.code_to_knowledge_graph.vscode.data.VsCodeNodeBuilder
import software.bevel.code_to_knowledge_graph.vscode.data.VsCodeRange
import software.bevel.code_to_knowledge_graph.vscode.data.VsCodeSymbolKind

/**
 * Implements [VsCodeLanguageSpecification] for the Kotlin programming language.
 * This class provides Kotlin-specific logic for tokenizing code, identifying symbols,
 * and converting Kotlin symbols from VS Code into graph nodes.
 *
 * @property fileHandler An instance of [FileHandler] for file system operations.
 * @property vsCodeNodeBuilder An instance of [VsCodeNodeBuilder] used to create graph nodes from VS Code symbols.
 * @param tokenizer An [IdentifierTokenizer] used to extract potential symbols from Kotlin code.
 * @property supportedFileEndings A list of file extensions recognized as Kotlin files (e.g., ".kt", ".kts").
 *                                Defaults to `listOf(".kt", ".kts")`.
 */
class KotlinLanguageSpec(
    val fileHandler: FileHandler,
    override val vsCodeNodeBuilder: VsCodeNodeBuilder,
    private val tokenizer: IdentifierTokenizer,
    override var supportedFileEndings: List<String> = listOf(".kt", ".kts"),
): VsCodeLanguageSpecification {

    /**
     * A set of reserved keywords in the Kotlin language.
     * This set is used by the [tokenizer] to avoid misidentifying keywords as symbols.
     */
    private val kotlinKeywords = hashSetOf(
        "fun", "class", "val", "var", "object", "interface", "if", "else", "for", "while",
        "do", "when", "return", "try", "catch", "finally", "throw", "is", "in", "as", "this",
        "super", "true", "false", "null", "break", "continue", "typeof", "import", "package"
    )
    /**
     * A regular expression to identify valid Kotlin identifiers (e.g., function names, class names, variable names).
     * It typically matches sequences starting with a letter or underscore, followed by letters, digits, or underscores.
     */
    private val wordRegex = Regex("[a-zA-Z_][a-zA-Z_0-9]*")

    /**
     * Tokenizes the input Kotlin code string to find potential symbols.
     * It uses the configured [tokenizer], [wordRegex], and the [kotlinKeywords] set to filter out keywords.
     *
     * @param pathToProject The absolute path to the project root.
     * @param relativePath The relative path of the file being processed.
     * @param input The Kotlin code content as a string.
     * @return A list of [PotentialSymbol]s found in the input code.
     */
    override fun getPotentialSymbols(
        pathToProject: String,
        relativePath: String,
        input: String
    ): List<PotentialSymbol> {
        return tokenizer.tokenize(wordRegex, kotlinKeywords, relativePath, input)
    }

    /**
     * Adds hardcoded symbols to the graph. For Kotlin, this method is currently a no-op
     * as there are no common globally recognized symbols that need to be pre-populated in this manner
     * beyond what the [GeneralLanguageSpecification] might provide (e.g., a File node).
     *
     * @param pathToProject The absolute path to the project root.
     * @param relativePath The relative path of the file being processed.
     * @param graph The [GraphBuilder] instance.
     */
    override fun addHardcodedSymbols(pathToProject: String, relativePath: String, graph: GraphBuilder) {
        // No specific hardcoded symbols for Kotlin in this implementation beyond general file node.
    }

    /**
     * Converts a Kotlin symbol (obtained from VS Code) into a [NodeBuilder].
     * This implementation specifically removes any dots (".") from the symbol [name]
     * before creating the node. This can be useful for cleaning up fully qualified names
     * or other dot-separated syntax that might appear in symbol names from the language server.
     *
     * @param name The raw name of the symbol from VS Code.
     * @param kind The [VsCodeSymbolKind] of the symbol.
     * @param relativeFilePath The relative path to the file containing the symbol.
     * @param nameRange The [VsCodeRange] of the symbol's name.
     * @param fullRange The [VsCodeRange] of the symbol's entire extent.
     * @param pathToProject The absolute path to the project root.
     * @param graph The [GraphBuilder] instance (not directly used for node creation here but part of the interface).
     * @return A [NodeBuilder] instance representing the Kotlin symbol.
     */
    override fun convertSymbolFromCodeToNode(
        name: String,
        kind: VsCodeSymbolKind,
        relativeFilePath: String,
        nameRange: VsCodeRange,
        fullRange: VsCodeRange,
        pathToProject: String,
        graph: GraphBuilder
    ): NodeBuilder {
        return vsCodeNodeBuilder.nodeBuilderFromVsCodeSymbol(
            name.replace(".", ""), // Remove dots from symbol names (e.g. from qualified names)
            kind,
            relativeFilePath,
            nameRange,
            fullRange,
            pathToProject
        )
    }
}