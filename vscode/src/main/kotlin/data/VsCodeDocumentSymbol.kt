package software.bevel.code_to_knowledge_graph.vscode.data

/**
 * Represents a symbol found in a document, typically provided by VS Code's document symbol provider.
 * It captures the hierarchical structure of symbols within a file.
 *
 * @property name The name of the symbol (e.g., class name, method name).
 * @property detail Additional details about the symbol (e.g., signature for a method).
 * @property kind The [VsCodeSymbolKind] indicating the type of the symbol (e.g., Class, Method, Variable).
 * @property range The [VsCodeRange] representing the entire span of the symbol in the document, including its body.
 * @property selectionRange The [VsCodeRange] representing the range of the symbol's name or identifier itself.
 * @property children A list of [VsCodeDocumentSymbol] representing nested symbols. Defaults to an empty list.
 */
data class VsCodeDocumentSymbol(
    val name: String,
    val detail: String,
    val kind: VsCodeSymbolKind,
    val range: VsCodeRange,
    val selectionRange: VsCodeRange,
    val children: List<VsCodeDocumentSymbol> = emptyList(),
) {
    /**
     * Flattens the hierarchy of document symbols into a single list.
     * This method recursively traverses the `children` of the current symbol and its descendants,
     * creating a flat list containing the current symbol followed by all its children and their children, etc.
     *
     * @return A list containing this [VsCodeDocumentSymbol] and all its descendant symbols.
     */
    fun flattenChildren(): List<VsCodeDocumentSymbol> = listOf(this) + children.flatMap { it.flattenChildren() }
}