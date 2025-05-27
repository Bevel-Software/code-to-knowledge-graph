package software.bevel.code_to_knowledge_graph.vscode.data

import software.bevel.graph_domain.graph.NodeType

//TODO("Fix this")
/**
 * Represents information about a symbol in the workspace, as provided by VS Code's symbol provider (e.g., workspace symbols).
 *
 * @property name The name of the symbol.
 * @property kind The [VsCodeSymbolKind] of the symbol.
 * @property containerName The name of the container holding this symbol (e.g., class name for a method, or file name for a top-level symbol).
 * @property location The [VsCodeLocation] where the symbol is defined.
 */
data class VsCodeSymbolInformation(
    val name: String,
    val kind: VsCodeSymbolKind,
    val containerName: String,
    val location: VsCodeLocation
) {
    /**
     * Companion object for [VsCodeSymbolInformation].
     */
    companion object {
        /**
         * A predefined list of [VsCodeSymbolKind]s that are generally considered relevant for parsing and graph construction.
         * This list can be used to filter symbols obtained from VS Code.
         */
        val allowedKinds = listOf(
            VsCodeSymbolKind.File,
            VsCodeSymbolKind.Method,
            VsCodeSymbolKind.Constructor,
            VsCodeSymbolKind.Function,
            VsCodeSymbolKind.Class,
            VsCodeSymbolKind.Struct,
            VsCodeSymbolKind.Interface,
            VsCodeSymbolKind.Object,
            VsCodeSymbolKind.Variable,
            VsCodeSymbolKind.Property,
            VsCodeSymbolKind.Field,
        )
    }
}

/**
 * Enumerates the kinds of symbols that can be reported by VS Code's language server protocol.
 * Each kind is associated with an integer value, which corresponds to the LSP specification.
 *
 * @property value The integer representation of the symbol kind as defined by the Language Server Protocol.
 */
enum class VsCodeSymbolKind(val value: Int) {
    File(0),
    Module(1),
    Namespace(2),
    Package(3),
    Class(4),
    Method(5),
    Property(6),
    Field(7),
    Constructor(8),
    Enum(9),
    Interface(10),
    Function(11),
    Variable(12),
    Constant(13),
    String(14),
    Number(15),
    Boolean(16),
    Array(17),
    Object(18),
    Key(19),
    Null(20),
    EnumMember(21),
    Struct(22),
    Event(23),
    Operator(24),
    TypeParameter(25);

    /**
     * Maps this [VsCodeSymbolKind] to a corresponding [NodeType] used in the knowledge graph.
     * This helps in categorizing symbols from VS Code into a more abstract representation for the graph.
     *
     * @return The [NodeType] that best represents this symbol kind.
     * @throws IllegalStateException if the symbol kind is not recognized or cannot be mapped to a [NodeType].
     */
    fun getNodeType(): NodeType {
        return when (this) {
            VsCodeSymbolKind.File -> NodeType.File
            VsCodeSymbolKind.Method -> NodeType.Function
            VsCodeSymbolKind.Constructor -> NodeType.Function
            VsCodeSymbolKind.Function -> NodeType.Function
            VsCodeSymbolKind.Class -> NodeType.Class
            VsCodeSymbolKind.Struct -> NodeType.Class
            VsCodeSymbolKind.Interface -> NodeType.Class
            VsCodeSymbolKind.Object -> NodeType.Class
            VsCodeSymbolKind.Variable -> NodeType.Property
            VsCodeSymbolKind.Property -> NodeType.Property
            VsCodeSymbolKind.Field -> NodeType.Property
            // Other kinds might not directly map or might be too granular for the current graph NodeType system.
            else -> throw IllegalStateException("Unknown or unmapped VsCodeSymbolKind: $this")
        }
    }
}