package software.bevel.code_to_knowledge_graph.vscode.data

import software.bevel.file_system_domain.LCPosition
import software.bevel.file_system_domain.LCRange

/**
 * Represents a range in a text document, defined by start and end line and character offsets.
 * This is typically used to represent the span of a symbol or a selection in VS Code.
 *
 * @property startLine The zero-based line number where the range starts.
 * @property startCharacter The zero-based character offset on the [startLine] where the range starts.
 * @property endLine The zero-based line number where the range ends.
 * @property endCharacter The zero-based character offset on the [endLine] where the range ends.
 */
data class VsCodeRange(
    val startLine: Int,
    val startCharacter: Int,
    val endLine: Int,
    val endCharacter: Int
) {
    /**
     * Converts this [VsCodeRange] to an [LCRange] (Line-Column Range) object.
     * @return The equivalent [LCRange].
     */
    fun toLCRange(): LCRange = LCRange(startLineColumnPosition(), endLineColumnPosition())

    /**
     * Creates an [LCPosition] representing the start of this range.
     * @return The [LCPosition] for the start of the range.
     */
    fun startLineColumnPosition(): LCPosition = LCPosition(startLine, startCharacter)

    /**
     * Creates an [LCPosition] representing the end of this range.
     * @return The [LCPosition] for the end of the range.
     */
    fun endLineColumnPosition(): LCPosition = LCPosition(endLine, endCharacter)

    /**
     * Checks if this range starts before another [VsCodeRange].
     * A range starts before another if its start line is less, or if the start lines are equal
     * and its start character is less.
     *
     * @param other The [VsCodeRange] to compare against.
     * @return `true` if this range starts before the [other] range, `false` otherwise.
     */
    fun startsBefore(other: VsCodeRange): Boolean {
        return this.startLine < other.startLine
                || (this.startLine == other.startLine
                && this.startCharacter < other.startCharacter)
    }

    /**
     * Checks if this range starts at the same position as another [VsCodeRange].
     *
     * @param other The [VsCodeRange] to compare against.
     * @return `true` if both ranges start at the same line and character, `false` otherwise.
     */
    fun startsEqual(other: VsCodeRange): Boolean {
        return this.startLine == other.startLine
                && this.startCharacter == other.startCharacter
    }

    /**
     * Checks if this range ends before another [VsCodeRange].
     * A range ends before another if its end line is less, or if the end lines are equal
     * and its end character is less.
     *
     * @param other The [VsCodeRange] to compare against.
     * @return `true` if this range ends before the [other] range, `false` otherwise.
     */
    fun endsBefore(other: VsCodeRange): Boolean {
        return endLine < other.endLine
                || (endLine == other.endLine
                && endCharacter < other.endCharacter)
    }

    /**
     * Checks if this range ends at the same position as another [VsCodeRange].
     *
     * @param other The [VsCodeRange] to compare against.
     * @return `true` if both ranges end at the same line and character, `false` otherwise.
     */
    fun endsEqual(other: VsCodeRange): Boolean {
        return endLine == other.endLine
                && endCharacter == other.endCharacter
    }

    /**
     * Checks if this range is strictly contained within another [VsCodeRange].
     * Containment means the [other] range starts at or before this range, and this range ends at or before the [other] range,
     * and they are not identical ranges.
     *
     * @param other The [VsCodeRange] to check for containment against.
     * @return `true` if this range is contained within the [other] range, `false` otherwise.
     */
    fun containedWithin(other: VsCodeRange): Boolean {
        return (other.startsBefore(this) || other.startsEqual(this))
                && (this.endsBefore(other) || this.endsEqual(other))
                && !(other.startsEqual(this) && other.endsEqual(this))
    }
}

/**
 * Represents a specific location in a file, combining a file path with a [VsCodeRange].
 *
 * @property filePath The path to the file.
 * @property range The [VsCodeRange] within the file.
 */
data class VsCodeLocation(
    val filePath: String,
    val range: VsCodeRange
)