package software.bevel.code_to_knowledge_graph.vscode.languageSpecs

import software.bevel.code_to_knowledge_graph.providers.MinHasher
import software.bevel.file_system_domain.absolutizePath
import software.bevel.graph_domain.graph.Connection
import software.bevel.graph_domain.graph.Graphlike
import software.bevel.file_system_domain.LCPosition
import software.bevel.graph_domain.graph.builder.GraphBuilder
import software.bevel.graph_domain.graph.builder.NodeBuilder
import software.bevel.graph_domain.parsing.PotentialSymbol
import software.bevel.graph_domain.parsing.SupportedFileExtensions
import software.bevel.file_system_domain.services.FileHandler
import software.bevel.graph_domain.tokenizers.IdentifierTokenizer
import software.bevel.graph_domain.tokenizers.RegexIdentifierTokenizer
import software.bevel.code_to_knowledge_graph.vscode.data.VsCodeNodeBuilder
import software.bevel.code_to_knowledge_graph.vscode.data.VsCodeRange
import software.bevel.code_to_knowledge_graph.vscode.data.VsCodeSymbolKind
import software.bevel.file_system_domain.services.CachedIoFileHandler
import software.bevel.graph_domain.hashing.LocalitySensitiveHasher
import java.io.File

/**
 * A general-purpose implementation of [VsCodeLanguageSpecification] that acts as a primary dispatcher
 * or facade for more specific language implementations (e.g., C#, COBOL, Kotlin).
 *
 * This class maintains a collection of language-specific handlers and delegates operations
 * like symbol tokenization, node conversion, and connection handling to the appropriate handler
 * based on the file extension. If no specific handler matches, it provides a default, generic behavior.
 *
 * @property fileHandler An instance of [FileHandler] for file system operations, passed to specific language specs.
 * @property vsCodeNodeBuilder An instance of [VsCodeNodeBuilder] used for creating graph nodes, also passed to specific language specs.
 *                             Defaults to a new instance with a [MinHasher].
 * @param fileExtensions Optional [SupportedFileExtensions] to customize file endings for specific languages.
 * @param tokenizer An [IdentifierTokenizer] used for generic tokenization if no specific language spec handles the file.
 *                  Defaults to [RegexIdentifierTokenizer]. This tokenizer is also passed to the specific language specs.
 */
class GeneralLanguageSpecification(
    val fileHandler: FileHandler,
    override val vsCodeNodeBuilder: VsCodeNodeBuilder = VsCodeNodeBuilder(
        fileHandler, MinHasher()
    ),
    fileExtensions: SupportedFileExtensions? = null,
    private val tokenizer: IdentifierTokenizer = RegexIdentifierTokenizer()
): VsCodeLanguageSpecification {
    /**
     * A map holding instances of specific language specifications, keyed by language name (e.g., "CSharp", "COBOL").
     * These specific handlers are responsible for language-dependent parsing logic.
     */
    val languageSpecs = mapOf(
        "CSharp" to CSharpLanguageSpec(fileHandler, vsCodeNodeBuilder, tokenizer=tokenizer),
        "COBOL" to CobolLanguageSpec(fileHandler, vsCodeNodeBuilder, tokenizer=tokenizer),
        "Kotlin" to KotlinLanguageSpec(fileHandler, vsCodeNodeBuilder, tokenizer=tokenizer)
    )
    /**
     * A list of all file extensions supported by this general specification and all its contained specific language specifications.
     * Includes generic extensions like ".ts", ".tsx", ".jcl" and aggregates extensions from [languageSpecs].
     */
    override var supportedFileEndings = listOf(
        ".ts", ".tsx",
        ".jcl"
    ) + languageSpecs.values.flatMap { it.supportedFileEndings }
    /**
     * A set of reserved words for the generic tokenizer. This is typically empty as language-specific
     * reserved words are handled by their respective [VsCodeLanguageSpecification] instances.
     */
    private val reservedWordsSet = hashSetOf<String>()
    /**
     * A generic regular expression for identifying words/identifiers if no specific language handler is found.
     * Matches typical variable/function names starting with a letter or underscore, followed by letters, numbers, or underscores.
     */
    private val wordRegex = Regex("[a-zA-Z_][a-zA-Z_0-9]*")

    init {
        if(fileExtensions != null) {
            languageSpecs.forEach { kvPair ->
                if(fileExtensions.supportedFileExtensions.containsKey(kvPair.key)) {
                    languageSpecs[kvPair.key]!!.supportedFileEndings = fileExtensions.supportedFileExtensions[kvPair.key]!!
                }
            }
            supportedFileEndings = fileExtensions.supportedFileExtensions.values.flatten()
        }
    }

    /**
     * Retrieves potential symbols from the input code string.
     * It first attempts to delegate this task to a specific language specification in [languageSpecs]
     * based on the [relativePath]'s file extension. If no matching spec is found, it uses the general [tokenizer].
     *
     * @param pathToProject The absolute path to the project root.
     * @param relativePath The relative path of the file being processed.
     * @param input The code content as a string.
     * @return A list of [PotentialSymbol]s found in the input code.
     */
    override fun getPotentialSymbols(pathToProject: String, relativePath: String, input: String): List<PotentialSymbol> {
        languageSpecs.values.filter { it.checkFileEndings(relativePath) }.forEach { languageSpec ->
            return languageSpec.getPotentialSymbols(pathToProject, relativePath, input)
        }
        return tokenizer.tokenize(wordRegex, reservedWordsSet, relativePath, input)
    }

    /**
     * Adds hardcoded symbols to the graph, such as a top-level file node.
     * This implementation creates a [VsCodeSymbolKind.File] node for the given [relativePath].
     * It then delegates to any matching specific language specification in [languageSpecs] to add further
     * language-specific hardcoded symbols.
     *
     * @param pathToProject The absolute path to the project root.
     * @param relativePath The relative path of the file being processed.
     * @param graph The [GraphBuilder] instance.
     */
    override fun addHardcodedSymbols(pathToProject: String, relativePath: String, graph: GraphBuilder) {
        val fileName = File(relativePath).nameWithoutExtension
        val absolutePath = absolutizePath(relativePath, pathToProject)
        val code = fileHandler.readString(absolutePath)
        val lines = fileHandler.readLines(absolutePath)
        val range = if(code.endsWith('\n')) {
            VsCodeRange(0, 0, lines.size, 0)
        } else {
            if(lines.isEmpty())
                VsCodeRange(0, 0, 0, 0)
            else
                VsCodeRange(0, 0, lines.size - 1, lines.last().length)
        }
        if(lines.isEmpty())
            return
        val fileNode = vsCodeNodeBuilder.nodeBuilderFromVsCodeSymbol(
            fileName,
            VsCodeSymbolKind.File,
            relativePath,
            range,
            range,
            pathToProject,
            "<global>"
        )
        graph.nodes.putAll(mapOf(
            fileNode.id to fileNode,
        ))
        languageSpecs.values.filter { it.checkFileEndings(relativePath) }.forEach { languageSpec ->
            languageSpec.addHardcodedSymbols(pathToProject, relativePath, graph)
        }
    }

    /**
     * Handles special outbound connections for a given node.
     * This method delegates the connection handling to the appropriate specific language specification
     * in [languageSpecs] based on the node's file path extension.
     *
     * @param pathToProject The absolute path to the project root.
     * @param nodeName The name/ID of the source node.
     * @param graph The [Graphlike] view of the current graph state.
     * @param connections A mutable set to which new [Connection]s will be added.
     */
    override fun handleSpecialOutboundConnections(
        pathToProject: String,
        nodeName: String,
        graph: Graphlike,
        connections: MutableSet<Connection>
    ) {
        val node = graph.nodes[nodeName] ?: return
        languageSpecs.values.filter {  it.checkFileEndings(node.filePath) }
            .forEach { languageSpec ->
                languageSpec.handleSpecialOutboundConnections(pathToProject, nodeName, graph, connections)
            }
    }

    /**
     * Handles special inbound connections for a given node.
     * This method delegates the connection handling to the appropriate specific language specification
     * in [languageSpecs] based on the node's file path extension.
     *
     * @param pathToProject The absolute path to the project root.
     * @param nodeName The name/ID of the target node.
     * @param graph The [Graphlike] view of the current graph state.
     * @param connections A mutable set to which new [Connection]s will be added.
     */
    override fun handleSpecialInboundConnections(
        pathToProject: String,
        nodeName: String,
        graph: Graphlike,
        connections: MutableSet<Connection>
    ) {
        val node = graph.nodes[nodeName] ?: return
        languageSpecs.values.filter { it.checkFileEndings(node.filePath) }
            .forEach { languageSpec ->
                languageSpec.handleSpecialInboundConnections(pathToProject, nodeName, graph, connections)
            }
    }

    /**
     * Converts a symbol (obtained from VS Code) into a [NodeBuilder].
     * It first attempts to delegate this task to a specific language specification in [languageSpecs]
     * based on the [relativeFilePath]'s file extension. If no matching spec is found, it uses the general
     * [vsCodeNodeBuilder] for a default conversion.
     *
     * @param name The raw name of the symbol from VS Code.
     * @param kind The [VsCodeSymbolKind] of the symbol.
     * @param relativeFilePath The relative path to the file containing the symbol.
     * @param nameRange The [VsCodeRange] of the symbol's name.
     * @param fullRange The [VsCodeRange] of the symbol's entire extent.
     * @param pathToProject The absolute path to the project root.
     * @param graph The [GraphBuilder] instance.
     * @return A [NodeBuilder] instance representing the symbol, or `null` if the symbol should be skipped.
     */
    override fun convertSymbolFromCodeToNode(
        name: String,
        kind: VsCodeSymbolKind,
        relativeFilePath: String,
        nameRange: VsCodeRange,
        fullRange: VsCodeRange,
        pathToProject: String,
        graph: GraphBuilder
    ): NodeBuilder? {
        languageSpecs.values.filter { it.checkFileEndings(relativeFilePath) }
            .forEach { languageSpec ->
                return languageSpec.convertSymbolFromCodeToNode(
                    name,
                    kind,
                    relativeFilePath,
                    nameRange,
                    fullRange,
                    pathToProject,
                    graph
                )
            }
        return vsCodeNodeBuilder.nodeBuilderFromVsCodeSymbol(
            name,
            kind,
            relativeFilePath,
            nameRange,
            fullRange,
            pathToProject
        )
    }
}