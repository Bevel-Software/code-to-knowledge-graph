package software.bevel.code_to_knowledge_graph.providers

import software.bevel.graph_domain.tokenizers.SemanticTokenizer

/**
 * A [SemanticTokenizer] that tokenizes input code based on character types and simple operator rules.
 * It identifies sequences of alphanumeric characters (including underscores) as single tokens.
 * Whitespace characters (space, tab, newline, carriage return) act as delimiters.
 * Other characters are treated as individual tokens, with special handling for common two-character operators
 * like `==`, `!=`, `<=`, `>=`, `&&`, and `||`.
 */
class CharSemanticTokenizer: SemanticTokenizer {

    /**
     * Tokenizes the given input string into a list of semantic tokens.
     *
     * The tokenization process is as follows:
     * 1. Iterates through the input string character by character.
     * 2. Alphanumeric characters and underscores are accumulated into a current token buffer.
     * 3. Whitespace characters flush the current token buffer (if not empty) and are then discarded.
     * 4. Other characters (operators, punctuation) flush the current token buffer (if not empty).
     *    - If the character, combined with the next character, forms a known two-character operator
     *      (e.g., `==`, `!=`), that two-character sequence is added as a single token, and the iterator advances an extra step.
     *    - Otherwise, the single character is added as a token.
     * 5. After iterating through all characters, if the current token buffer is not empty, its content is added as the final token.
     *
     * @param input The string of code to tokenize.
     * @return A list of strings, where each string is a semantic token extracted from the input.
     */
    override fun tokenizeCode(input: String): List<String> {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        val charArray = input.toCharArray()

        var i = 0
        while (i < charArray.size) {
            when (val c = charArray[i]) {
                in 'a'..'z', in 'A'..'Z', in '0'..'9', '_' -> {
                    current.append(c)
                }
                ' ', '\t', '\n', '\r' -> {
                    if (current.isNotEmpty()) {
                        tokens.add(current.toString())
                        current.clear()
                    }
                }
                else -> {
                    if (current.isNotEmpty()) {
                        tokens.add(current.toString())
                        current.clear()
                    }
                    if (i + 1 < charArray.size) {
                        when (val twoCharOp = "$c${charArray[i + 1]}") {
                            "==", "!=", "<=", ">=", "&&", "||" -> {
                                tokens.add(twoCharOp)
                                i++
                            }
                            else -> {
                                tokens.add(c.toString())
                            }
                        }
                    } else {
                        tokens.add(c.toString())
                    }
                }
            }
            i++
        }

        if (current.isNotEmpty()) {
            tokens.add(current.toString())
        }

        return tokens
    }
}