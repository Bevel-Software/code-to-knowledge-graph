package software.bevel.code_to_knowledge_graph.vscode.data

import software.bevel.file_system_domain.absolutizePath
import software.bevel.file_system_domain.LCPosition
import software.bevel.file_system_domain.LCRange
import software.bevel.graph_domain.graph.builder.FullyQualifiedNodeBuilder
import software.bevel.graph_domain.hashing.LocalitySensitiveHasher
import software.bevel.file_system_domain.services.FileHandler

/**
 * Responsible for constructing [FullyQualifiedNodeBuilder] instances from VS Code symbol information.
 * This class utilizes a [FileHandler] to read file contents and a [LocalitySensitiveHasher]
 * to generate hashes for code snippets, which are essential for identifying nodes.
 *
 * @property fileHandler An instance of [FileHandler] used for file system operations like reading file content.
 * @property hasher An instance of [LocalitySensitiveHasher] used to compute hashes of code blocks.
 */
class VsCodeNodeBuilder(private val fileHandler: FileHandler, private val hasher: LocalitySensitiveHasher) {
    /**
     * Creates a [FullyQualifiedNodeBuilder] from the provided VS Code symbol information.
     *
     * This method processes symbol details, reads relevant code from the file, computes a hash,
     * determines accurate name and code ranges, and constructs a node builder instance.
     * It handles adjustments for ranges that might extend beyond file boundaries and attempts to find
     * a precise name location if not explicitly provided.
     *
     * @param simpleName The simple name of the symbol (e.g., method name, class name).
     * @param kind The [VsCodeSymbolKind] of the symbol.
     * @param relativeFilePath The relative path to the file containing the symbol.
     * @param nameRange An optional [VsCodeRange] specifying the exact location of the symbol's name. If null, it's inferred.
     * @param fullRange The [VsCodeRange] specifying the entire extent of the symbol, including its body.
     * @param pathToProject The absolute path to the project root, used to resolve the [relativeFilePath].
     * @param definingNodeName The fully qualified name of the node that defines this symbol (e.g., a class defining a method). Defaults to an empty string.
     * @param definingNodeSimpleName The simple name of the defining node. Defaults to an empty string.
     * @return A [FullyQualifiedNodeBuilder] populated with details derived from the input symbol information.
     */
    fun nodeBuilderFromVsCodeSymbol(
        simpleName: String,
        kind: VsCodeSymbolKind,
        relativeFilePath: String,
        nameRange: VsCodeRange?,
        fullRange: VsCodeRange,
        pathToProject: String,
        definingNodeName: String = "",
        definingNodeSimpleName: String = "",
    ): FullyQualifiedNodeBuilder {
        val absolutePath = absolutizePath(relativeFilePath, pathToProject)
        val code = fileHandler.readString(absolutePath, fullRange.startLineColumnPosition(), fullRange.endLineColumnPosition())
        val hash = hasher.hash(code)
        val lines = fileHandler.readLines(absolutePath)
        val fileRange = if(code.endsWith('\n')) {
            VsCodeRange(0, 0, lines.size, 0)
        } else {
            VsCodeRange(0, 0, lines.size - 1, lines.last().length)
        }

        val actualFullRange = if(fullRange.endLine > fileRange.endLine) fullRange.copy(endLine = fileRange.endLine, endCharacter = fileRange.endCharacter) else fullRange
        val actualNameRange = nameRange?.let {
            if(nameRange.endLine > fileRange.endLine)
                nameRange.copy(endLine = fileRange.endLine, endCharacter = fileRange.endCharacter)
            else nameRange
        }

        val firstLineContainingSimpleName = fileHandler.readLines(absolutePath, actualFullRange.startLine, actualFullRange.endLine)
            .firstOrNull { it.contains(simpleName) }
        val nodeSignature = firstLineContainingSimpleName?.trim() ?: simpleName

        val nodeNameRange = actualNameRange?.toLCRange()
            ?: fileHandler.readLines(absolutePath, actualFullRange.startLine, actualFullRange.endLine)
                .mapIndexed { index, line -> index + actualFullRange.startLine to line }
                .firstOrNull { it.second.contains(simpleName) }
                ?.let {
                    val startColumn = it.second.indexOf(simpleName)
                    LCRange(
                        LCPosition(it.first, startColumn),
                        LCPosition(it.first, startColumn + simpleName.length)
                    )
                } ?: actualFullRange.toLCRange()

        val codeLocation = actualFullRange.toLCRange()
        val parent = definingNodeSimpleName.ifEmpty {
            "${kind.getNodeType()}.${nodeNameRange.start}"
        }
        return FullyQualifiedNodeBuilder(
            id = "${relativeFilePath.hashCode()}.$hash.$parent.$simpleName",
            simpleName = simpleName,
            nodeType = kind.getNodeType(),
            definingNodeName = definingNodeName,
            filePath = relativeFilePath,
            codeLocation = codeLocation,
            nameLocation = nodeNameRange,
            codeHash = hash,
            nodeSignature = nodeSignature
        )
    }
}