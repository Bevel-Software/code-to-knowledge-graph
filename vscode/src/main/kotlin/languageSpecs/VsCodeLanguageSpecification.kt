package software.bevel.code_to_knowledge_graph.vscode.languageSpecs

import software.bevel.graph_domain.graph.Connection
import software.bevel.graph_domain.graph.Graphlike
import software.bevel.graph_domain.graph.builder.GraphBuilder
import software.bevel.graph_domain.graph.builder.NodeBuilder
import software.bevel.graph_domain.parsing.LanguageSpecification
import software.bevel.graph_domain.parsing.PotentialSymbol
import software.bevel.code_to_knowledge_graph.vscode.data.VsCodeNodeBuilder
import software.bevel.code_to_knowledge_graph.vscode.data.VsCodeRange
import software.bevel.code_to_knowledge_graph.vscode.data.VsCodeSymbolKind

/**
 * Defines a contract for language-specific processing logic tailored for VS Code integration.
 * This interface extends the general [LanguageSpecification] and adds functionalities
 * specific to how symbols and structures are represented or obtained from VS Code's language services.
 *
 * Implementations of this interface are responsible for providing language-specific rules for:
 * - Identifying potential symbols in code.
 * - Adding language-specific hardcoded symbols (e.g., built-in types or global objects).
 * - Handling special types of connections (inbound and outbound) that may not be generically discoverable.
 * - Converting symbols reported by VS Code into a standardized [NodeBuilder] format for the knowledge graph.
 */
interface VsCodeLanguageSpecification: LanguageSpecification {
    /**
     * An instance of [VsCodeNodeBuilder] used by implementations to create graph nodes
     * from symbols provided by VS Code or identified by the language specification.
     */
    val vsCodeNodeBuilder: VsCodeNodeBuilder

    /**
     * Provides a default package name to be used for symbols that are not explicitly part of any package
     * (e.g., top-level functions or variables in some languages).
     * Defaults to `"<global>"`.
     */
    val defaultPackageName: String
        get() = "<global>"

    /**
     * Analyzes the given code [input] for a specific file and returns a list of [PotentialSymbol]s.
     * Implementations should use language-specific rules (e.g., keywords, syntax) to identify these symbols.
     *
     * @param pathToProject The absolute path to the root of the project.
     * @param relativePath The relative path of the file being analyzed within the project.
     * @param input The textual content of the code file.
     * @return A list of [PotentialSymbol]s identified in the code.
     */
    fun getPotentialSymbols(pathToProject: String, relativePath: String, input: String): List<PotentialSymbol>

    /**
     * Allows language specifications to add hardcoded or predefined symbols to the graph.
     * This can include built-in types, global objects, or other entities that are implicitly part of the language
     * but might not be explicitly declared in every source file.
     *
     * @param pathToProject The absolute path to the root of the project.
     * @param relativePath The relative path of the file currently being processed (can be used for context).
     * @param graph The [GraphBuilder] instance to which new nodes can be added.
     */
    fun addHardcodedSymbols(pathToProject: String, relativePath: String, graph: GraphBuilder)

    /**
     * Handles the creation of special outbound connections for a given [nodeName].
     * This method is intended for language-specific connection types that require custom logic beyond
     * standard symbol resolution (e.g., COBOL `COPY` statements, dependency injection frameworks).
     * The default implementation is a no-op.
     *
     * @param pathToProject The absolute path to the root of the project.
     * @param nodeName The name/ID of the source node for which outbound connections are being processed.
     * @param graph A [Graphlike] view of the current graph, allowing querying of existing nodes.
     * @param connections A mutable set where new [Connection]s should be added.
     */
    fun handleSpecialOutboundConnections(pathToProject: String, nodeName: String, graph: Graphlike, connections: MutableSet<Connection>) {}

    /**
     * Handles the creation of special inbound connections to a given [nodeName].
     * Similar to [handleSpecialOutboundConnections], this is for language-specific scenarios.
     * For example, identifying implicit usages or framework-specific entry points.
     * The default implementation is a no-op.
     *
     * @param pathToProject The absolute path to the root of the project.
     * @param nodeName The name/ID of the target node for which inbound connections are being processed.
     * @param graph A [Graphlike] view of the current graph, allowing querying of existing nodes.
     * @param connections A mutable set where new [Connection]s should be added.
     */
    fun handleSpecialInboundConnections(pathToProject: String, nodeName: String, graph: Graphlike, connections: MutableSet<Connection>) {}

    /**
     * Converts a symbol, as identified by VS Code's language services, into a [NodeBuilder].
     * This method allows for language-specific adjustments to the symbol's properties (name, kind, etc.)
     * before it's turned into a graph node.
     * The default implementation directly uses the [vsCodeNodeBuilder] to perform the conversion without modification.
     *
     * @param name The name of the symbol as reported by VS Code.
     * @param kind The [VsCodeSymbolKind] (e.g., Class, Function, Variable) of the symbol.
     * @param relativeFilePath The relative path to the file where the symbol is defined.
     * @param nameRange The [VsCodeRange] indicating the location of the symbol's name in the file.
     * @param fullRange The [VsCodeRange] indicating the full extent of the symbol's definition in the file.
     * @param pathToProject The absolute path to the root of the project.
     * @param graph The [GraphBuilder] instance (can be used for context if needed, e.g., to check for existing nodes).
     * @return A [NodeBuilder] instance representing the converted symbol, or `null` if the symbol should be ignored.
     */
    fun convertSymbolFromCodeToNode(
        name: String,
        kind: VsCodeSymbolKind,
        relativeFilePath: String,
        nameRange: VsCodeRange,
        fullRange: VsCodeRange,
        pathToProject: String,
        graph: GraphBuilder
    ): NodeBuilder? = vsCodeNodeBuilder.nodeBuilderFromVsCodeSymbol(
        name,
        kind,
        relativeFilePath,
        nameRange,
        fullRange,
        pathToProject
    )
}