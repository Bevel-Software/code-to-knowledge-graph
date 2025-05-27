package software.bevel.code_to_knowledge_graph.vscode.languageSpecs

import software.bevel.file_system_domain.LCRange
import software.bevel.file_system_domain.absolutizePath
import software.bevel.graph_domain.graph.*
import software.bevel.graph_domain.graph.builder.FullyQualifiedNodeBuilder
import software.bevel.graph_domain.graph.builder.GraphBuilder
import software.bevel.graph_domain.graph.builder.NodeBuilder
import software.bevel.graph_domain.parsing.PotentialSymbol
import software.bevel.file_system_domain.services.FileHandler
import software.bevel.graph_domain.tokenizers.IdentifierTokenizer
import software.bevel.graph_domain.tokenizers.RegexIdentifierTokenizer
import software.bevel.code_to_knowledge_graph.vscode.data.VsCodeNodeBuilder
import software.bevel.code_to_knowledge_graph.vscode.data.VsCodeRange
import software.bevel.code_to_knowledge_graph.vscode.data.VsCodeSymbolKind
import java.nio.file.Path
import kotlin.io.path.pathString

/**
 * Implements [VsCodeLanguageSpecification] for the C# programming language.
 * This class provides C#-specific logic for tokenizing code, identifying symbols,
 * handling language-specific patterns (like MediatR), and converting symbols to graph nodes.
 *
 * @property fileHandler An instance of [FileHandler] for file system operations.
 * @property vsCodeNodeBuilder An instance of [VsCodeNodeBuilder] used to create graph nodes from VS Code symbols.
 * @property supportedFileEndings A list of file extensions recognized as C# files (e.g., ".cs"). Defaults to listOf(".cs").
 * @param tokenizer An [IdentifierTokenizer] used to extract potential symbols from C# code. Defaults to [RegexIdentifierTokenizer].
 */
class CSharpLanguageSpec(
    val fileHandler: FileHandler,
    override val vsCodeNodeBuilder: VsCodeNodeBuilder,
    override var supportedFileEndings: List<String> = listOf(".cs"),
    private val tokenizer: IdentifierTokenizer = RegexIdentifierTokenizer()
): VsCodeLanguageSpecification {
    /**
     * A set of reserved words in C#. Currently, this set is not populated but is available for future use
     * to prevent common keywords from being identified as symbols.
     */
    private val reservedWordsSet = hashSetOf<String>()
    /**
     * A regular expression to identify valid C# identifiers. It matches typical C# variable, method, and class names,
     * including those starting with '@' (verbatim identifiers).
     */
    private val wordRegex = Regex("[a-zA-Z_@][a-zA-Z_0-9]*")

    /**
     * Tokenizes the input C# code string to find potential symbols.
     * It uses the configured [tokenizer] and [wordRegex].
     * Special handling is applied for "Send" and "Publish" tokens, which are assumed to be MediatR calls
     * and are prefixed with "Mediatr." and suffixed with "()" to form a unique identifier.
     *
     * @param pathToProject The absolute path to the project root.
     * @param relativePath The relative path of the file being processed.
     * @param input The C# code content as a string.
     * @return A list of [PotentialSymbol]s found in the input code.
     */
    override fun getPotentialSymbols(pathToProject: String, relativePath: String, input: String): List<PotentialSymbol> {
        return tokenizer.tokenize(wordRegex, reservedWordsSet, relativePath, input)
            .map {
                if(it.name == "Send" || it.name == "Publish") it.copy(name = "Mediatr.${it.name}()")
                else it
            }
    }

    /**
     * Adds hardcoded symbols, specifically for MediatR's Send and Publish methods, to the graph.
     * These are added as global function nodes.
     *
     * @param pathToProject The absolute path to the project root.
     * @param relativePath The relative path of the file being processed (not directly used for these global symbols).
     * @param graph The [GraphBuilder] instance to which the symbols will be added.
     */
    override fun addHardcodedSymbols(pathToProject: String, relativePath: String, graph: GraphBuilder) {
        graph.nodes.putAll(mapOf(
            "Mediatr.Send()" to FullyQualifiedNodeBuilder(
                id = "Mediatr.Send()",
                simpleName = "Mediatr.Send()",
                nodeType = NodeType.Function,
                definingNodeName = "<global>",
                filePath = "", // Global, not tied to a specific file
                codeLocation = LCRange.empty(),
                nameLocation = LCRange.empty(),
                codeHash = "",
            ),
            "Mediatr.Publish()" to FullyQualifiedNodeBuilder(
                id = "Mediatr.Publish()",
                simpleName = "Mediatr.Publish()",
                nodeType = NodeType.Function,
                definingNodeName = "<global>",
                filePath = "", // Global, not tied to a specific file
                codeLocation = LCRange.empty(),
                nameLocation = LCRange.empty(),
                codeHash = "",
            ),
        ))
    }

    /**
     * Handles special outbound connections for C# symbols, particularly for MediatR patterns.
     * If the [nodeName] corresponds to MediatR's Send or Publish methods (and is a global, non-file-bound symbol),
     * it creates `OVERRIDES` connections from MediatR handler methods (ending with "Handle()") to these MediatR methods,
     * and `USES` connections from the MediatR methods to the handlers.
     *
     * @param pathToProject The absolute path to the project root.
     * @param nodeName The name/ID of the source node for which special outbound connections are being considered.
     * @param graph The [Graphlike] view of the current graph state.
     * @param connections A mutable set to which new [Connection]s should be added.
     */
    override fun handleSpecialOutboundConnections(
        pathToProject: String,
        nodeName: String,
        graph: Graphlike,
        connections: MutableSet<Connection>
    ) {
        val node = graph.nodes[nodeName] ?: return
        val absolutePath = Path.of(absolutizePath(node.filePath, pathToProject))
        // Check if the node is one of the global Mediatr symbols
        if(node.filePath == "" || !fileHandler.isFile(absolutePath.pathString)) {
            if(nodeName == "Mediatr.Send()" || nodeName == "Mediatr.Publish()") {
                graph.nodes.filterValues { it.id.endsWith("Handle()") }.forEach {
                    connections.add(Connection(
                        it.value.id,
                        nodeName,
                        ConnectionType.OVERRIDES,
                        node.filePath,
                        node.nameLocation
                    ))
                    connections.add(Connection(
                        nodeName,
                        it.value.id,
                        ConnectionType.USES,
                        node.filePath,
                        node.nameLocation
                    ))
                }
            }
        }
    }

    /**
     * Handles special inbound connections for C# symbols, particularly for MediatR handler methods.
     * If the [nodeName] corresponds to a MediatR handler (ends with "Handle()"),
     * it creates `OVERRIDES` connections from the MediatR Send/Publish methods to this handler,
     * and `USES` connections from this handler to the MediatR Send/Publish methods.
     * This is somewhat symmetric to [handleSpecialOutboundConnections] for MediatR.
     *
     * @param pathToProject The absolute path to the project root.
     * @param nodeName The name/ID of the target node for which special inbound connections are being considered.
     * @param graph The [Graphlike] view of the current graph state.
     * @param connections A mutable set to which new [Connection]s should be added.
     */
    override fun handleSpecialInboundConnections(
        pathToProject: String,
        nodeName: String,
        graph: Graphlike,
        connections: MutableSet<Connection>
    ) {
        if(nodeName.endsWith("Handle()")) {
            val node = graph.nodes[nodeName] ?: return
            listOf("Mediatr.Send()", "Mediatr.Publish()").mapNotNull { graph.nodes[it] }.forEach {
                connections.add(Connection(
                    it.id,
                    nodeName,
                    ConnectionType.OVERRIDES,
                    node.filePath,
                    node.nameLocation
                ))
                connections.add(Connection(
                    nodeName,
                    it.id,
                    ConnectionType.USES,
                    node.filePath,
                    node.nameLocation
                ))
            }
        }
    }

    /**
     * Converts a C# symbol (obtained from VS Code) into a [NodeBuilder].
     * It performs C#-specific name adjustments, such as replacing ".ctor" with "constructor"
     * and removing dots from names (which might be namespace or class separators in raw VS Code symbols
     * but are not part of the simple name for the node builder).
     *
     * @param name The raw name of the symbol from VS Code.
     * @param kind The [VsCodeSymbolKind] of the symbol.
     * @param relativeFilePath The relative path to the file containing the symbol.
     * @param nameRange The [VsCodeRange] of the symbol's name.
     * @param fullRange The [VsCodeRange] of the symbol's entire extent.
     * @param pathToProject The absolute path to the project root.
     * @param graph The [GraphBuilder] instance (not directly used in this specific implementation but part of the interface).
     * @return A [NodeBuilder] instance representing the C# symbol.
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
            name.replace(".ctor", "constructor").replace(".", ""),
            kind,
            relativeFilePath,
            nameRange,
            fullRange,
            pathToProject
        )
    }
}