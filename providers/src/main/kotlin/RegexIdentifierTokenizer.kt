package software.bevel.code_to_knowledge_graph.providers

import software.bevel.file_system_domain.LCPosition
import software.bevel.graph_domain.parsing.PotentialSymbol
import software.bevel.graph_domain.tokenizers.IdentifierTokenizer
import java.util.*

/**
 * An implementation of [IdentifierTokenizer] that uses a regular expression to find potential identifiers in text.
 * It calculates the line and column numbers for each found identifier and filters out any words that are present
 * in a provided set of reserved words.
 */
class RegexIdentifierTokenizer: IdentifierTokenizer {
    /**
     * Tokenizes the input string using a provided regular expression to find word-like sequences,
     * then filters out reserved words and converts the remaining matches into [PotentialSymbol] objects.
     *
     * The process involves:
     * 1. Pre-calculating the starting offset of each line in the input string to enable line/column conversion.
     *    Handles `\r\n` and `\n` line endings.
     * 2. Finding all matches of the `wordRegex` in the `input` string.
     * 3. For each match:
     *    a. Converting the matched word to uppercase and checking if it exists in the `reservedWordsSet`.
     *       If it is a reserved word, the match is discarded.
     *    b. If not reserved, determining the start and end character offsets of the match.
     *    c. Converting these character offsets into [LCPosition] (line and column) using the pre-calculated `lineStartOffsets`.
     *    d. Creating a [PotentialSymbol] with the original matched value, the `relativePath`, and the calculated start/end positions.
     * 4. Collecting all valid [PotentialSymbol] objects into a list.
     *
     * @param wordRegex The [Regex] used to identify potential identifiers (words).
     * @param reservedWordsSet A [HashSet] of uppercase reserved words to be ignored.
     * @param relativePath The relative path of the source file, used for context in [PotentialSymbol].
     * @param input The string content to tokenize.
     * @return A list of [PotentialSymbol] objects representing the identified non-reserved identifiers.
     */
    override fun tokenize(wordRegex: Regex, reservedWordsSet: HashSet<String>, relativePath: String, input: String): List<PotentialSymbol> {
        val characters = input.toCharArray()
        val lineStartOffsets = mutableListOf(0)
        var i = 0
        while (i < characters.size) {
            when (characters[i]) {
                '\r' -> {
                    if (i + 1 < characters.size && characters[i + 1] == '\n') {
                        i++ // skip the '\n' part of '\r\n'
                    }
                    lineStartOffsets.add(i + 1)
                }
                '\n' -> {
                    lineStartOffsets.add(i + 1)
                }
            }
            i++
        }

        return wordRegex.findAll(input)
            .mapNotNull { match ->
                val word = match.value.uppercase(Locale.getDefault())
                if (word in reservedWordsSet) return@mapNotNull null
                val start = match.range.first
                val end = match.range.last + 1
                val startPos = findLineColumn(lineStartOffsets, start)
                val endPos = findLineColumn(lineStartOffsets, end)
                PotentialSymbol(match.value, relativePath, startPos, endPos)
            }
            .toList()
    }


    /**
     * Calculates the line and column ([LCPosition]) for a given absolute character position (`pos`)
     * within a text, using a pre-calculated list of line start offsets.
     *
     * @param lineStartOffsets A mutable list where each element is the character offset of the start of a line.
     *                         The list should be sorted and include an offset for the start of the first line (usually 0).
     * @param pos The absolute character position (0-indexed) in the text for which to find the line and column.
     * @return An [LCPosition] object containing the 0-indexed line number and 0-indexed column number.
     */
    private fun findLineColumn(lineStartOffsets: MutableList<Int>, pos: Int): LCPosition {
        val line = lineStartOffsets.binarySearch { if (it <= pos) -1 else 1 }.let {
            if (it < 0) -(it + 2) else it
        }
        val column = pos - lineStartOffsets.getOrElse(line) { 0 }
        return LCPosition(line, column)
    }
}