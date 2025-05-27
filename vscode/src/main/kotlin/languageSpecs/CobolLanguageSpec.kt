package software.bevel.code_to_knowledge_graph.vscode.languageSpecs

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import software.bevel.graph_domain.graph.Connection
import software.bevel.file_system_domain.LCPosition
import software.bevel.file_system_domain.LCRange
import software.bevel.file_system_domain.absolutizePath
import software.bevel.graph_domain.graph.builder.GraphBuilder
import software.bevel.graph_domain.graph.builder.NodeBuilder
import software.bevel.graph_domain.parsing.PotentialSymbol
import software.bevel.file_system_domain.services.FileHandler
import software.bevel.graph_domain.graph.ConnectionType
import software.bevel.graph_domain.graph.Graphlike
import software.bevel.graph_domain.graph.NodeType
import software.bevel.graph_domain.tokenizers.IdentifierTokenizer
import software.bevel.graph_domain.tokenizers.RegexIdentifierTokenizer
import software.bevel.code_to_knowledge_graph.vscode.data.VsCodeNodeBuilder
import software.bevel.code_to_knowledge_graph.vscode.data.VsCodeRange
import software.bevel.code_to_knowledge_graph.vscode.data.VsCodeSymbolKind

/**
 * Implements [VsCodeLanguageSpecification] for the COBOL programming language.
 * This class provides COBOL-specific logic for tokenizing code, identifying symbols,
 * handling COBOL statements like `COPY`, `CALL`, etc., to create connections,
 * and converting COBOL symbols from VS Code into graph nodes.
 *
 * @property fileHandler An instance of [FileHandler] for file system operations.
 * @property vsCodeNodeBuilder An instance of [VsCodeNodeBuilder] used to create graph nodes from VS Code symbols.
 * @property supportedFileEndings A list of file extensions recognized as COBOL files (e.g., ".cbl", ".cpy").
 *                                Defaults to a comprehensive list of common COBOL extensions.
 * @property tokenizer An [IdentifierTokenizer] used to extract potential symbols from COBOL code. Defaults to [RegexIdentifierTokenizer].
 * @param logger An SLF4J [Logger] instance for logging messages specific to COBOL processing.
 */
class CobolLanguageSpec(
    val fileHandler: FileHandler,
    override val vsCodeNodeBuilder: VsCodeNodeBuilder,
    override var supportedFileEndings: List<String> = listOf(".cbl", ".cob", ".ccp", ".cobol", ".cpy", ".cpb", ".cblcpy", ".mf"),
    val tokenizer: IdentifierTokenizer = RegexIdentifierTokenizer(),
    private val logger: Logger = LoggerFactory.getLogger(CobolLanguageSpec::class.java)
): VsCodeLanguageSpecification {

    /**
     * A comprehensive set of reserved words in the COBOL language.
     * This set is used by the [tokenizer] to avoid misidentifying keywords as program or data names.
     */
    private val reservedCobolWordsSet = hashSetOf(
        "ACCEPT", "ACCESS", "ADD", "ADDRESS", "ADVANCING", "AFTER", "ALLOCATE",
        "ALPHABET", "ALPHABETIC", "ALPHABETIC-LOWER", "ALPHABETIC-UPPER",
        "ALPHANUMERIC", "ALPHANUMERIC-EDITED", "ALSO", "ALTER", "ALTERNATE",
        "AND", "APPLY", "ARE", "AREA", "AREAS", "ASCENDING", "ASSIGN", "AT",
        "AUTHOR", "BASIS", "BEFORE", "BEGINNING", "BINARY", "BLANK", "BLOCK",
        "BOTTOM", "BY", "BYTE-LENGTH", "CALL", "CANCEL", "CBL", "CHARACTER",
        "CHARACTERS", "CLASS", "CLASS-ID", "CLOSE", "COBOL", "CODE", "CODE-SET",
        "COLLATING", "COM-REG", "COMMA", "COMMON", "COMP", "COMP-1", "COMP-2",
        "COMP-3", "COMP-4", "COMP-5", "COMPUTATIONAL", "COMPUTATIONAL-1",
        "COMPUTATIONAL-2", "COMPUTATIONAL-3", "COMPUTATIONAL-4", "COMPUTATIONAL-5",
        "COMPUTE", "CONFIGURATION", "CONTAINS", "CONTENT", "CONTINUE", "CONVERTING",
        "COPY", "CORR", "CORRESPONDING", "COUNT", "CURRENCY", "DATE",
        "DATE-COMPILED", "DATE-WRITTEN", "DAY", "DAY-OF-WEEK", "DBCS",
        "DEBUG-CONTENTS", "DEBUG-ITEM", "DEBUG-LINE", "DEBUG-NAME", "DEBUG-SUB-1",
        "DEBUG-SUB-2", "DEBUG-SUB-3", "DEBUGGING", "DECIMAL-POINT", "DECLARATIVES",
        "DEFAULT", "DELETE", "DELIMITED", "DELIMITER", "DEPENDING", "DESCENDING",
        "DISPLAY", "DISPLAY-1", "DIVIDE", "DIVISION", "DOWN", "DUPLICATES",
        "DYNAMIC", "EJECT", "ELSE", "END", "END-ADD", "END-CALL", "END-COMPUTE",
        "END-DELETE", "END-DIVIDE", "END-EVALUATE", "END-EXEC", "END-IF",
        "END-INVOKE", "END-JSON", "END-MULTIPLY", "END-OF-PAGE", "END-PERFORM",
        "END-READ", "END-RETURN", "END-REWRITE", "END-SEARCH", "END-START",
        "END-STRING", "END-SUBTRACT", "END-UNSTRING", "END-WRITE", "END-XML",
        "ENDING", "ENTER", "ENTRY", "ENVIRONMENT", "EOP", "EQUAL", "ERROR",
        "EVALUATE", "EVERY", "EXCEPTION", "EXEC", "EXECUTE", "EXIT", "EXTEND",
        "EXTERNAL", "FACTORY", "FALSE", "FD", "FILE", "FILE-CONTROL", "FILLER",
        "FIRST", "FOOTING", "FOR", "FREE", "FROM", "FUNCTION", "FUNCTION-POINTER",
        "GENERATE", "GIVING", "GLOBAL", "GO", "GOBACK", "GREATER", "GROUP-USAGE",
        "HIGH-VALUE", "HIGH-VALUES", "I-O", "I-O-CONTROL", "ID",
        "IF", "IN", "INDEX", "INDEXED", "INHERITS", "INITIAL", "INITIALIZE",
        "INPUT", "INPUT-OUTPUT", "INSERT", "INSPECT", "INSTALLATION", "INTO",
        "INVALID", "INVOKE", "IS", "JAVA", "JNIENVPTR", "JSON", "JSON-CODE",
        "JSON-STATUS", "JUST", "JUSTIFIED", "KANJI", "KEY", "LABEL", "LEADING",
        "LEFT", "LENGTH", "LESS", "LIMIT", "LINAGE", "LINAGE-COUNTER", "LINE",
        "LINES", "LOCAL-STORAGE", "LOCK", "LOW-VALUE", "LOW-VALUES",
        "MEMORY", "MERGE", "METHOD", "METHOD-ID", "MODE", "MODULES", "MORE-LABELS",
        "MOVE", "MULTIPLE", "MULTIPLY", "NATIONAL", "NATIONAL-EDITED", "NATIVE",
        "NEGATIVE", "NEXT", "NO", "NOT", "NULL", "NULLS", "NUMERIC",
        "NUMERIC-EDITED", "OBJECT", "OBJECT-COMPUTER", "OCCURS", "OF", "OFF",
        "OMITTED", "ON", "OPEN", "OPTIONAL", "OR", "ORDER", "ORGANIZATION",
        "OTHER", "OUTPUT", "OVERFLOW", "OVERRIDE", "PACKED-DECIMAL", "PADDING",
        "PAGE", "PASSWORD", "PERFORM", "PIC", "PICTURE", "POINTER", "POINTER-32",
        "POSITION", "POSITIVE", "PROCEDURE-POINTER", "PROCEDURES",
        "PROCEED", "PROCESSING", "PROGRAM", "PROGRAM-ID", "PROTOTYPE", "QUOTE",
        "QUOTES", "RANDOM", "READ", "READY", "RECORD", "RECORDING", "RECORDS",
        "RECURSIVE", "REDEFINES", "REEL", "REFERENCE", "REFERENCES", "RELATIVE",
        "RELEASE", "RELOAD", "REMAINDER", "REMOVAL", "RENAMES", "REPLACE",
        "REPLACING", "REPOSITORY", "RERUN", "RESERVE", "RESET", "RETURN",
        "RETURN-CODE", "RETURNING", "REVERSED", "REWIND", "REWRITE", "RIGHT",
        "ROUNDED", "RUN", "SAME", "SD", "SEARCH", "SECTION", "SECURITY", "SELECT",
        "SELF", "SENTENCE", "SEPARATE", "SEQUENCE", "SEQUENTIAL", "SERVICE",
        "SET", "SHIFT-IN", "SHIFT-OUT", "SIGN", "SIZE", "SKIP1", "SKIP2", "SKIP3",
        "SORT", "SORT-CONTROL", "SORT-CORE-SIZE", "SORT-FILE-SIZE", "SORT-MERGE",
        "SORT-MESSAGE", "SORT-MODE-SIZE", "SORT-RETURN", "SOURCE-COMPUTER",
        "SPACE", "SPACES", "SPECIAL-NAMES", "SQL", "SQLIMS", "STANDARD",
        "STANDARD-1", "STANDARD-2", "START", "STATUS", "STOP", "STRING",
        "SUBTRACT", "SUPER", "SUPPRESS", "SYMBOLIC", "SYNC", "SYNCHRONIZED",
        "TALLY", "TALLYING", "TAPE", "TEST", "THAN", "THEN", "THROUGH", "THRU",
        "TIME", "TIMES", "TITLE", "TO", "TOP", "TRACE", "TRAILING", "TRUE",
        "TYPE", "UNIT", "UNSTRING", "UNTIL", "UP", "UPON", "USAGE", "USE",
        "USING", "UTF-8", "VALUE", "VALUES", "VARYING", "VOLATILE", "WHEN",
        "WHEN-COMPILED", "WITH", "WORDS", "WRITE", "WRITE-ONLY",
        "XML", "XML-CODE", "XML-EVENT", "XML-INFORMATION", "XML-NAMESPACE",
        "XML-NAMESPACE-PREFIX", "XML-NNAMESPACE", "XML-NNAMESPACE-PREFIX",
        "XML-NTEXT", "XML-SCHEMA", "XML-TEXT", "ZERO", "ZEROES", "ZEROS"
    )
    /**
     * A regular expression to identify valid COBOL identifiers (program names, data names, paragraph names, etc.).
     * It typically matches sequences starting with a letter, followed by letters, digits, or hyphens.
     */
    private val wordRegex = Regex("[a-zA-Z][a-zA-Z0-9-]*")

    /**
     * Tokenizes the input COBOL code string to find potential symbols.
     * It uses the configured [tokenizer], [wordRegex], and the [reservedCobolWordsSet] to filter out keywords.
     *
     * @param pathToProject The absolute path to the project root.
     * @param relativePath The relative path of the file being processed.
     * @param input The COBOL code content as a string.
     * @return A list of [PotentialSymbol]s found in the input code.
     */
    override fun getPotentialSymbols(pathToProject: String, relativePath: String, input: String): List<PotentialSymbol> {
        return tokenizer.tokenize(wordRegex, reservedCobolWordsSet, relativePath, input)
    }

    /**
     * Adds hardcoded symbols to the graph. For COBOL, this method is currently a no-op
     * as there are no common globally recognized symbols that need to be pre-populated in this manner.
     *
     * @param pathToProject The absolute path to the project root.
     * @param relativePath The relative path of the file being processed.
     * @param graph The [GraphBuilder] instance.
     */
    override fun addHardcodedSymbols(pathToProject: String, relativePath: String, graph: GraphBuilder) {
        // No hardcoded symbols for COBOL in this implementation
    }

    /**
     * Converts a COBOL symbol (obtained from VS Code) into a [NodeBuilder].
     * It performs COBOL-specific name cleaning:
     * - Skips symbols containing "COPY" (as these are handled by `handleSpecialOutboundConnections`).
     * - Removes prefixes like "PROGRAM-ID.", "SECTION", and "DIVISION".
     * - Removes trailing periods.
     *
     * @param name The raw name of the symbol from VS Code.
     * @param kind The [VsCodeSymbolKind] of the symbol.
     * @param relativeFilePath The relative path to the file containing the symbol.
     * @param nameRange The [VsCodeRange] of the symbol's name.
     * @param fullRange The [VsCodeRange] of the symbol's entire extent.
     * @param pathToProject The absolute path to the project root.
     * @param graph The [GraphBuilder] instance (not directly used for node creation here but part of the interface).
     * @return A [NodeBuilder] instance representing the COBOL symbol, or `null` if the symbol should be skipped (e.g., a COPY statement).
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
        var symbol = name
        // COPY statements are handled as connections, not nodes themselves from this method.
        if(symbol.contains("COPY", ignoreCase = true)) {
            return null
        }

        // Clean common COBOL prefixes from symbol names
        if(symbol.contains("PROGRAM-ID.", ignoreCase = true))
            symbol = symbol.replace("PROGRAM-ID.", "", ignoreCase = true).trim()
        if(symbol.contains("SECTION", ignoreCase = true))
            symbol = symbol.replace("SECTION", "", ignoreCase = true).trim()
        if(symbol.contains("DIVISION", ignoreCase = true))
            symbol = symbol.replace("DIVISION", "", ignoreCase = true).trim()
        symbol = symbol.replace(".", "").trim() // Remove any remaining periods often found at the end of COBOL names

        return vsCodeNodeBuilder.nodeBuilderFromVsCodeSymbol(
            symbol,
            kind,
            relativeFilePath,
            nameRange,
            fullRange,
            pathToProject
        )
    }

    /**
     * Handles special outbound connections for COBOL programs and copybooks.
     * This method scans the lines of code within the given [nodeName]'s definition for COBOL statements
     * like `COPY`, `CALL`, `CHAIN`, `CANCEL`, and `EXEC SQL INCLUDE`.
     * For each such statement, it extracts the target program name or copybook name and attempts to find
     * a corresponding file node in the graph. If a match is found, a [ConnectionType.USES] connection
     * is created from the current [nodeName] to the target file node.
     *
     * COBOL comment lines (starting with '*' or '/' at character 0 or 6 in fixed format) are ignored.
     * Target names can be literals (enclosed in quotes) or identifiers.
     * The method attempts to match the target name against existing file nodes by simple name or by file path (with or without extension).
     *
     * @param pathToProject The absolute path to the project root.
     * @param nodeName The name/ID of the source COBOL node (e.g., a program) for which connections are being identified.
     * @param graph The [Graphlike] view of the current graph state, used to find target nodes.
     * @param connections A mutable set to which new [Connection]s will be added.
     */
    override fun handleSpecialOutboundConnections(
        pathToProject: String,
        nodeName: String,
        graph: Graphlike,
        connections: MutableSet<Connection>
    ) {
        val node = graph.nodes[nodeName] ?: return
        val lines = fileHandler.readLines(absolutizePath(node.filePath, pathToProject), node.codeLocation.start.line, node.codeLocation.end.line)
        lines.mapIndexed { index, it -> (node.codeLocation.start.line + index) to it }
            // Filter out COBOL comment lines (fixed format: '*' or '/' in column 1 or 7)
            .filter { (it.second.isEmpty() || (it.second[0] != '*' && it.second[0] != '/'))
                    && (it.second.length < 7 || (it.second[6] != '*' && it.second[6] != '/')) }
            .mapNotNull {
                var symbol: String? = null
                val regex = "\\b(\"[^\"]+\"|'[^']+'|[a-zA-Z][a-zA-Z0-9-]*)\\b".toRegex()
                var symbolStartIndex: Int = 0
                if(Regex("\\bCOPY\\b", RegexOption.IGNORE_CASE).containsMatchIn(it.second)) {
                    symbolStartIndex = it.second.indexOf("COPY", ignoreCase = true) + "COPY".length + 1
                } else if(Regex("\\bCALL\\b", RegexOption.IGNORE_CASE).containsMatchIn(it.second)) {
                    symbolStartIndex = it.second.indexOf("CALL", ignoreCase = true) + "CALL".length + 1
                } else if(Regex("\\bCHAIN\\b", RegexOption.IGNORE_CASE).containsMatchIn(it.second)) {
                    symbolStartIndex = it.second.indexOf("CHAIN", ignoreCase = true) + "CHAIN".length + 1
                } else if(Regex("\\bCANCEL\\b", RegexOption.IGNORE_CASE).containsMatchIn(it.second)) {
                    symbolStartIndex = it.second.indexOf("CANCEL", ignoreCase = true) + "CANCEL".length + 1
                } else if(Regex("\\bEXEC\\b", RegexOption.IGNORE_CASE).containsMatchIn(it.second)) {
                    if(it.second.contains("\"") || it.second.contains("'")) {
                        val regex2 = "(\"[^\"\n]+\"|'[^'\n]+')".toRegex()
                        val matchResult = regex2.find(it.second)
                        if(matchResult != null) {
                            symbol = matchResult.value.trim()
                                .removeSuffix("\"")
                                .removePrefix("\"")
                                .removeSuffix("'")
                                .removePrefix("'")
                        } else {
                            return@mapNotNull null
                        }
                    } else if (Regex("\\bEXEC\\s+SQL\\s+INCLUDE\\b", RegexOption.IGNORE_CASE).containsMatchIn(it.second)) {
                        symbolStartIndex = it.second.indexOf("EXEC SQL INCLUDE", ignoreCase = true) + "EXEC SQL INCLUDE".length + 1
                    } else {
                        return@mapNotNull null
                    }
                } /*else if(Regex("\\bMOVE\\b", RegexOption.IGNORE_CASE).containsMatchIn(it.second)) {
                    if(it.second.contains("\"") || it.second.contains("'")) {
                        val regex2 = "\"[^\"\n]+\"|'[^'\n]+'".toRegex()
                        val matchResult = regex2.find(it.second)
                        if(matchResult != null) {
                            symbol = matchResult.value.trim()
                                .removeSuffix("\"")
                                .removePrefix("\"")
                                .removeSuffix("'")
                                .removePrefix("'")
                        } else {
                            return@mapNotNull null
                        }
                    } else {
                        return@mapNotNull null
                    }
                }*/ else {
                    return@mapNotNull null
                }
                if(symbol == null) {
                    if(symbolStartIndex <= -1 || symbolStartIndex >= it.second.length) {
                        return@mapNotNull null
                    }
                    val matchResult = regex.find(it.second, symbolStartIndex)
                    if(matchResult != null) {
                        symbol = matchResult.groupValues[1].trim()
                            .removeSuffix("\"")
                            .removePrefix("\"")
                            .removeSuffix("'")
                            .removePrefix("'")
                    } else {
                        return@mapNotNull null
                    }
                }

                val startIndex = LCPosition(it.first, it.second.indexOf(symbol))
                val endIndex = LCPosition(it.first, it.second.indexOf(symbol) + symbol.length)
                return@mapNotNull symbol to (startIndex to endIndex)
            }
            .forEach { sbl ->
                var symbol = sbl.first
                while (symbol.startsWith("..")) {
                    symbol = symbol.removePrefix("../").removePrefix("..\\")
                }

                val nodes = graph.nodes.values

                val s1 = symbol.replace("/", "\\")
                val s2 = symbol.replace("\\", "/")
                val possibleFiles = nodes.firstOrNull {
                    val pathWithoutExtension = it.filePath.removeSuffix("." + fileHandler.getExtensionFromPath(it.filePath))
                    (it.nodeType == NodeType.File || it.nodeType == NodeType.Class)
                        && (it.simpleName == symbol || pathWithoutExtension.endsWith(s1) || pathWithoutExtension.endsWith(s2)
                            || it.filePath.endsWith(s1) || it.filePath.endsWith(s2))
                }

                if(possibleFiles == null) {
                    logger.warn("$symbol could not be linked to a file")
                    return@forEach
                }

                connections.add(Connection(
                    nodeName,
                    possibleFiles.id,
                    ConnectionType.USES,
                    node.filePath,
                    LCRange(sbl.second.first, sbl.second.second)
                ))
            }
        super.handleSpecialOutboundConnections(pathToProject, nodeName, graph, connections)
    }
}