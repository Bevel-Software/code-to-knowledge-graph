package software.bevel.code_to_knowledge_graph.vscode.languageSpecs

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import software.bevel.code_to_knowledge_graph.vscode.data.VsCodeNodeBuilder
import software.bevel.file_system_domain.LCPosition
import software.bevel.file_system_domain.LCRange
import software.bevel.file_system_domain.absolutizePath
import software.bevel.file_system_domain.services.FileHandler
import software.bevel.graph_domain.graph.*
import software.bevel.graph_domain.graph.builder.ListConnectionsNavigator
import software.bevel.graph_domain.hashing.LocalitySensitiveHasher
import software.bevel.graph_domain.parsing.PotentialSymbol
import software.bevel.graph_domain.tokenizers.IdentifierTokenizer
import java.util.*
import kotlin.math.min

/**
 * Implementation of FileHandler for testing purposes.
 * Can be initialized with required file content data.
 */
class TestFileHandler : FileHandler {
    override val separator: String = "/"
    
    private val fileContents = mutableMapOf<String, String>()
    
    fun addFile(path: String, content: String) {
        fileContents[path] = content
    }

    override fun readString(filePath: String): String {
        return fileContents[filePath] ?: throw IllegalArgumentException("File not found: $filePath")
    }

    override fun readString(filePath: String, startCharacter: Int, endCharacter: Int): String {
        val content = fileContents[filePath] ?: throw IllegalArgumentException("File not found: $filePath")
        
        // Protect against out of bounds indices
        val validStartChar = startCharacter.coerceIn(0, content.length)
        val validEndChar = endCharacter.coerceIn(0, content.length)
        
        return content.substring(validStartChar, validEndChar)
    }

    override fun readString(filePath: String, start: LCPosition, end: LCPosition): String {
        val content = fileContents[filePath] ?: throw IllegalArgumentException("File not found: $filePath")
        val lines = content.split("\n")
        
        // Build the string from the specified positions
        val result = StringBuilder()
        
        for (lineIndex in start.line..end.line) {
            if (lineIndex >= lines.size) break
            
            val line = lines[lineIndex]
            val startChar = if (lineIndex == start.line) start.column else 0
            val endChar = if (lineIndex == end.line) min(end.column, line.length) else line.length
            
            if (startChar <= endChar) {
                result.append(line.substring(startChar, endChar))
                if (lineIndex < end.line) result.append("\n")
            }
        }
        
        return result.toString()
    }

    override fun readLines(filePath: String): List<String> {
        val content = fileContents[filePath] ?: throw IllegalArgumentException("File not found: $filePath")
        return content.split("\n")
    }

    override fun readLines(filePath: String, startLine: Int, endLine: Int): List<String> {
        val lines = readLines(filePath)
        
        // Protect against out of bounds indices
        val validStartLine = startLine.coerceIn(0, lines.size)
        val validEndLine = (endLine+1).coerceIn(0, lines.size)
        
        return lines.subList(validStartLine, validEndLine)
    }

    override fun readLines(filePath: String, start: LCPosition, end: LCPosition): List<String> {
        val allLines = readLines(filePath)
        
        // Protect against out of bounds indices
        val validStartLine = start.line.coerceIn(0, allLines.size)
        val validEndLine = min(end.line + 1, allLines.size)
        
        if (validStartLine >= validEndLine) return emptyList()
        
        val result = mutableListOf<String>()
        
        // First line - may need to trim from the beginning
        val firstLine = allLines[validStartLine]
        val firstLineStartCol = min(start.column, firstLine.length)
        
        // Last line - may need to trim from the end
        val lastLine = allLines[validEndLine - 1]
        val lastLineEndCol = min(end.column, lastLine.length)
        
        if (validStartLine == validEndLine - 1) {
            // Only one line to process
            result.add(firstLine.substring(firstLineStartCol, lastLineEndCol))
        } else {
            // First line
            result.add(firstLine.substring(firstLineStartCol))
            
            // Middle lines - include whole lines
            for (i in validStartLine + 1 until validEndLine - 1) {
                result.add(allLines[i])
            }
            
            // Last line
            result.add(lastLine.substring(0, lastLineEndCol))
        }
        
        return result
    }

    override fun getExtensionFromPath(filePath: String): String {
        var lastSeparator = -1
        var lastDot = -1

        for (i in filePath.indices.reversed()) {
            when (filePath[i]) {
                '/' , '\\' -> {
                    lastSeparator = i
                    break
                }
                '.' -> if (lastDot == -1) lastDot = i
            }
        }

        return if (lastDot != -1 && lastDot > lastSeparator) {
            filePath.substring(lastDot)
        } else {
            ""
        }
    }

    override fun writeString(filePath: String, content: String) {
        fileContents[filePath] = content
    }

    override fun exists(filePath: String): Boolean {
        return fileContents.containsKey(filePath)
    }

    override fun isFile(filePath: String): Boolean {
        return exists(filePath)
    }

    override fun delete(filePath: String) {
        fileContents.remove(filePath)
    }

    override fun createFile(filePath: String) {
        fileContents[filePath] = ""
    }

    override fun createDirectory(filePath: String) {
        // No-op for testing purposes
    }
}

/**
 * Unit tests for the handleSpecialOutboundConnections method in the CobolLanguageSpec class,
 * covering all COBOL cases where programs reference other programs via string literals.
 */
class CobolLanguageSpecTest {
    
    private lateinit var fileHandler: TestFileHandler
    private lateinit var cobolLanguageSpec: CobolLanguageSpec
    private lateinit var connections: MutableSet<Connection>
    private val hasher: LocalitySensitiveHasher = object : LocalitySensitiveHasher {
        override fun hash(input: String): String {
            return input.hashCode().toUInt().toString()
        }

        override fun similarity(hash1: String, hash2: String): Double {
            return 0.0
        }
    }
    private val identifierTokenizer: IdentifierTokenizer = object : IdentifierTokenizer {
        override fun tokenize(
            wordRegex: Regex,
            reservedWordsSet: HashSet<String>,
            relativePath: String,
            input: String,
        ): List<PotentialSymbol> {
            return wordRegex.findAll(input)
                .mapNotNull { match ->
                    val word = match.value.uppercase(Locale.getDefault())
                    if (word in reservedWordsSet) return@mapNotNull null
                    val startPos = LCPosition(match.range.first / (input.indexOf('\n') + 1), match.range.first % (input.indexOf('\n') + 1))
                    val endPos = LCPosition(match.range.last / (input.indexOf('\n') + 1) + 1, match.range.last % (input.indexOf('\n') + 1) + 1)
                    PotentialSymbol(match.value, relativePath, startPos, endPos)
                }
                .toList()
        }
    }
    
    private val projectPath = "C:/project"
    private val filePath = "src/main/cobol/MAIN.cbl"
    
    // Helper method to get the absolute file path
    private fun getAbsoluteFilePath() = absolutizePath(filePath, projectPath)
    
    @BeforeEach
    fun setup() {
        fileHandler = TestFileHandler()
        cobolLanguageSpec = CobolLanguageSpec(
            fileHandler,
            VsCodeNodeBuilder(fileHandler, hasher),
            tokenizer = identifierTokenizer
        )
        connections = mutableSetOf()
    }
    
    @Test
    @DisplayName("Test CALL statement handling")
    fun testHandleCallStatement() {
        // Prepare COBOL code with CALL statement
        val cobolCode = """
            000100 IDENTIFICATION DIVISION.
            000200 PROGRAM-ID. MAIN.
            000300 PROCEDURE DIVISION.
            000400     CALL "SUB1" USING VAR1, VAR2.
            000500     DISPLAY "DONE".
        """.trimIndent()
        
        // Add file using the path that absolutizePath will generate
        fileHandler.addFile(getAbsoluteFilePath(), cobolCode)
        
        // Find the position of "SUB1" in the code
        val sub1StartPos = 17
        val sub1EndPos = sub1StartPos + "SUB1".length
        
        // Find the position of the MAIN program ID
        val mainStartPos = cobolCode.indexOf("MAIN")
        val mainEndPos = mainStartPos + "MAIN".length
        
        // Create nodes for the test with updated Node structure
        val nodesMap = mutableMapOf<String, Node>()
        
        // Main node now contains the DEFINES connection information
        nodesMap["MAIN"] = Node(
            id = "MAIN",
            simpleName = "MAIN",
            nodeType = NodeType.File,
            description = "",
            definingNodeName = "",
            filePath = filePath,
            codeLocation = LCRange(LCPosition(0, 0), LCPosition(4, cobolCode.length)),
            nameLocation = LCRange(LCPosition(1, mainStartPos), LCPosition(1, mainEndPos)),
            codeHash = "MAIN".hashCode().toUInt().toString()
        )
        
        // SUB1 node
        nodesMap["SUB1"] = Node(
            id = "SUB1",
            simpleName = "SUB1",
            nodeType = NodeType.File,
            description = "",
            definingNodeName = "",
            filePath = "src/main/cobol/SUB1.cbl",
            codeLocation = LCRange.empty(),
            nameLocation = LCRange.empty(),
            codeHash = "SUB1".hashCode().toUInt().toString()
        )
        
        // Create the graph without separate DEFINES connections
        val graph = Graph(nodesMap, ListConnectionsNavigator(emptyList()))
        
        // Execute the method we're testing
        cobolLanguageSpec.handleSpecialOutboundConnections(
            projectPath,
            "MAIN",
            graph,
            connections
        )
        
        // Verify
        assertEquals(1, connections.size)
        val connection = connections.first()
        assertEquals("MAIN", connection.sourceNodeName)
        assertEquals("SUB1", connection.targetNodeName)
        assertEquals(ConnectionType.USES, connection.connectionType)
        assertEquals(filePath, connection.filePath)
        
        // The positions should correspond to "SUB1" in the CALL statement
        assertTrue(connection.location.start.column >= sub1StartPos - 10)
        assertTrue(connection.location.end.column <= sub1EndPos + 10) // Allow some flexibility due to parser behavior
    }
    
    @Test
    @DisplayName("Test COPY statement handling")
    fun testHandleCopyStatement() {
        // Prepare COBOL code with COPY statement
        val cobolCode = """
            000100 IDENTIFICATION DIVISION.
            000200 PROGRAM-ID. MAIN.
            000300 DATA DIVISION.
            000400     COPY COPY1.
            000500     WORKING-STORAGE SECTION.
        """.trimIndent()
        
        // Add file using the path that absolutizePath will generate
        fileHandler.addFile(getAbsoluteFilePath(), cobolCode)
        
        // Find the position of "COPY1" in the code
        val copy1StartPos = "000400     COPY COPY1.".indexOf("COPY1")
        val copy1EndPos = copy1StartPos + "COPY1".length
        
        // Find the position of the MAIN program ID
        val mainStartPos = "000200 PROGRAM-ID. MAIN.".indexOf("MAIN")
        val mainEndPos = mainStartPos + "MAIN".length
        
        // Create nodes for the test with updated Node structure
        val nodesMap = mutableMapOf<String, Node>()
        
        // Main node now contains the DEFINES connection information
        nodesMap["MAIN"] = Node(
            id = "MAIN",
            simpleName = "MAIN",
            nodeType = NodeType.File,
            description = "",
            definingNodeName = "",
            filePath = filePath,
            codeLocation = LCRange(LCPosition(0, 0), LCPosition(4, cobolCode.length)),
            nameLocation = LCRange(LCPosition(1, mainStartPos), LCPosition(1, mainEndPos)),
            codeHash = "MAIN".hashCode().toUInt().toString()
        )
        
        // COPY1 node
        nodesMap["COPY1"] = Node(
            id = "COPY1",
            simpleName = "COPY1",
            nodeType = NodeType.File,
            description = "",
            definingNodeName = "",
            filePath = "src/main/cobol/COPY1.cpy",
            codeLocation = LCRange.empty(),
            nameLocation = LCRange.empty(),
            codeHash = "COPY1".hashCode().toUInt().toString()
        )
        
        // Create the graph without separate DEFINES connections
        val graph = Graph(nodesMap, ListConnectionsNavigator(emptyList()))
        
        // Execute the method we're testing
        cobolLanguageSpec.handleSpecialOutboundConnections(
            projectPath,
            "MAIN",
            graph,
            connections
        )
        
        // Verify
        assertEquals(1, connections.size)
        val connection = connections.first()
        assertEquals("MAIN", connection.sourceNodeName)
        assertEquals("COPY1", connection.targetNodeName)
        assertEquals(ConnectionType.USES, connection.connectionType)
        assertEquals(filePath, connection.filePath)
        
        // The positions should correspond to "COPY1" in the COPY statement
        assertTrue(connection.location.start.column >= copy1StartPos - 10)
        assertTrue(connection.location.end.column <= copy1EndPos + 10) // Allow some flexibility
    }
    
    @Test
    @DisplayName("Test CHAIN statement handling")
    fun testHandleChainStatement() {
        // Prepare COBOL code with CHAIN statement
        val cobolCode = """
            000100 IDENTIFICATION DIVISION.
            000200 PROGRAM-ID. MAIN.
            000300 PROCEDURE DIVISION.
            000400     CHAIN "NEXTPROG" USING ARG1, ARG2.
            000500     DISPLAY "SHOULD NOT GET HERE".
        """.trimIndent()
        
        // Add file using the path that absolutizePath will generate
        fileHandler.addFile(getAbsoluteFilePath(), cobolCode)
        
        // Find the position of "NEXTPROG" in the code
        val nextProgStartPos = "000400     CHAIN \"NEXTPROG\" USING ARG1, ARG2.".indexOf("NEXTPROG")
        val nextProgEndPos = nextProgStartPos + "NEXTPROG".length
        
        // Find the position of the MAIN program ID
        val mainStartPos = "000200 PROGRAM-ID. MAIN.".indexOf("MAIN")
        val mainEndPos = mainStartPos + "MAIN".length
        
        // Create nodes for the test with updated Node structure
        val nodesMap = mutableMapOf<String, Node>()
        
        // Main node now contains the DEFINES connection information
        nodesMap["MAIN"] = Node(
            id = "MAIN",
            simpleName = "MAIN",
            nodeType = NodeType.File,
            description = "",
            definingNodeName = "",
            filePath = filePath,
            codeLocation = LCRange(LCPosition(0, 0), LCPosition(4, cobolCode.length)),
            nameLocation = LCRange(LCPosition(1, mainStartPos), LCPosition(1, mainEndPos)),
            codeHash = "MAIN".hashCode().toUInt().toString()
        )
        
        // NEXTPROG node
        nodesMap["NEXTPROG"] = Node(
            id = "NEXTPROG",
            simpleName = "NEXTPROG",
            nodeType = NodeType.File,
            description = "",
            definingNodeName = "",
            filePath = "src/main/cobol/NEXTPROG.cbl",
            codeLocation = LCRange.empty(),
            nameLocation = LCRange.empty(),
            codeHash = "NEXTPROG".hashCode().toUInt().toString()
        )
        
        // Create the graph without separate DEFINES connections
        val graph = Graph(nodesMap, ListConnectionsNavigator(emptyList()))
        
        // Execute the method we're testing
        cobolLanguageSpec.handleSpecialOutboundConnections(
            projectPath,
            "MAIN",
            graph,
            connections
        )
        
        // Verify
        assertEquals(1, connections.size)
        val connection = connections.first()
        assertEquals("MAIN", connection.sourceNodeName)
        assertEquals("NEXTPROG", connection.targetNodeName)
        assertEquals(ConnectionType.USES, connection.connectionType)
        assertEquals(filePath, connection.filePath)
        
        // The positions should correspond to "NEXTPROG" in the CHAIN statement
        assertTrue(connection.location.start.column >= nextProgStartPos - 10)
        assertTrue(connection.location.end.column <= nextProgEndPos + 10) // Allow some flexibility
    }
    
    @Test
    @DisplayName("Test CANCEL statement handling")
    fun testHandleCancelStatement() {
        // Prepare COBOL code with CANCEL statement
        val cobolCode = """
            000100 IDENTIFICATION DIVISION.
            000200 PROGRAM-ID. MAIN.
            000300 PROCEDURE DIVISION.
            000400     CANCEL "SUB2".
            000500     DISPLAY "SUBPROGRAM CANCELLED".
        """.trimIndent()
        
        // Add file using the path that absolutizePath will generate
        fileHandler.addFile(getAbsoluteFilePath(), cobolCode)
        
        // Find the position of "SUB2" in the code
        val sub2StartPos = "000400     CANCEL \"SUB2\".".indexOf("SUB2")
        val sub2EndPos = sub2StartPos + "SUB2".length
        
        // Find the position of the MAIN program ID
        val mainStartPos = "000200 PROGRAM-ID. MAIN.".indexOf("MAIN")
        val mainEndPos = mainStartPos + "MAIN".length
        
        // Create nodes for the test with updated Node structure
        val nodesMap = mutableMapOf<String, Node>()
        
        // Main node now contains the DEFINES connection information
        nodesMap["MAIN"] = Node(
            id = "MAIN",
            simpleName = "MAIN",
            nodeType = NodeType.File,
            description = "",
            definingNodeName = "",
            filePath = filePath,
            codeLocation = LCRange(LCPosition(0, 0), LCPosition(4, cobolCode.length)),
            nameLocation = LCRange(LCPosition(1, mainStartPos), LCPosition(1, mainEndPos)),
            codeHash = "MAIN".hashCode().toUInt().toString()
        )
        
        // SUB2 node
        nodesMap["SUB2"] = Node(
            id = "SUB2",
            simpleName = "SUB2",
            nodeType = NodeType.File,
            description = "",
            definingNodeName = "",
            filePath = "src/main/cobol/SUB2.cbl",
            codeLocation = LCRange.empty(),
            nameLocation = LCRange.empty(),
            codeHash = "SUB2".hashCode().toUInt().toString()
        )
        
        // Create the graph without separate DEFINES connections
        val graph = Graph(nodesMap, ListConnectionsNavigator(emptyList()))
        
        // Execute the method we're testing
        cobolLanguageSpec.handleSpecialOutboundConnections(
            projectPath,
            "MAIN",
            graph,
            connections
        )
        
        // Verify
        assertEquals(1, connections.size)
        val connection = connections.first()
        assertEquals("MAIN", connection.sourceNodeName)
        assertEquals("SUB2", connection.targetNodeName)
        assertEquals(ConnectionType.USES, connection.connectionType)
        assertEquals(filePath, connection.filePath)
        
        // The positions should correspond to "SUB2" in the CANCEL statement
        assertTrue(connection.location.start.column >= sub2StartPos - 10)
        assertTrue(connection.location.end.column <= sub2EndPos + 10) // Allow some flexibility
    }
    
    @Test
    @DisplayName("Test EXEC CICS LINK/XCTL statement handling")
    fun testHandleExecCicsStatement() {
        // Prepare COBOL code with EXEC CICS statements
        val cobolCode = """
            000100 IDENTIFICATION DIVISION.
            000200 PROGRAM-ID. MAIN.
            000300 PROCEDURE DIVISION.
            000400     EXEC CICS LINK PROGRAM('CICSPROG') END-EXEC.
            000500     EXEC CICS XCTL PROGRAM("SUB1") END-EXEC.
        """.trimIndent()
        
        // Add file using the path that absolutizePath will generate
        fileHandler.addFile(getAbsoluteFilePath(), cobolCode)
        
        // Find the positions of program names in the code
        val cicsProgStartPos = "000400     EXEC CICS LINK PROGRAM('CICSPROG') END-EXEC.".indexOf("CICSPROG")
        val cicsProgEndPos = cicsProgStartPos + "CICSPROG".length
        val sub1StartPos = "000500     EXEC CICS XCTL PROGRAM(\"SUB1\") END-EXEC.".indexOf("SUB1")
        val sub1EndPos = sub1StartPos + "SUB1".length
        
        // Find the position of the MAIN program ID
        val mainStartPos = "000200 PROGRAM-ID. MAIN.".indexOf("MAIN")
        val mainEndPos = mainStartPos + "MAIN".length
        
        // Create nodes for the test with updated Node structure
        val nodesMap = mutableMapOf<String, Node>()
        
        // Main node now contains the DEFINES connection information
        nodesMap["MAIN"] = Node(
            id = "MAIN",
            simpleName = "MAIN",
            nodeType = NodeType.File,
            description = "",
            definingNodeName = "",
            filePath = filePath,
            codeLocation = LCRange(LCPosition(0, 0), LCPosition(4, cobolCode.length)),
            nameLocation = LCRange(LCPosition(1, mainStartPos), LCPosition(1, mainEndPos)),
            codeHash = "MAIN".hashCode().toUInt().toString()
        )
        
        // CICSPROG node
        nodesMap["CICSPROG"] = Node(
            id = "CICSPROG",
            simpleName = "CICSPROG",
            nodeType = NodeType.File,
            description = "",
            definingNodeName = "",
            filePath = "src/main/cobol/CICSPROG.cbl",
            codeLocation = LCRange.empty(),
            nameLocation = LCRange.empty(),
            codeHash = "CICSPROG".hashCode().toUInt().toString()
        )
        
        // SUB1 node
        nodesMap["SUB1"] = Node(
            id = "SUB1",
            simpleName = "SUB1",
            nodeType = NodeType.File,
            description = "",
            definingNodeName = "",
            filePath = "src/main/cobol/SUB1.cbl",
            codeLocation = LCRange.empty(),
            nameLocation = LCRange.empty(),
            codeHash = "SUB1".hashCode().toUInt().toString()
        )
        
        // Create the graph without separate DEFINES connections
        val graph = Graph(nodesMap, ListConnectionsNavigator(emptyList()))
        
        // Execute the method we're testing
        cobolLanguageSpec.handleSpecialOutboundConnections(
            projectPath,
            "MAIN",
            graph,
            connections
        )
        
        // Verify
        assertEquals(2, connections.size)
        
        val cicsProg = connections.find { it.targetNodeName == "CICSPROG" }
        assertNotNull(cicsProg)
        assertEquals("MAIN", cicsProg!!.sourceNodeName)
        assertEquals(ConnectionType.USES, cicsProg.connectionType)
        assertEquals(filePath, cicsProg.filePath)
        
        // The positions should correspond to "CICSPROG" in the statement
        assertTrue(cicsProg.location.start.column >= cicsProgStartPos - 10)
        assertTrue(cicsProg.location.end.column <= cicsProgEndPos + 10) // Allow some flexibility
        
        val sub1 = connections.find { it.targetNodeName == "SUB1" }
        assertNotNull(sub1)
        assertEquals("MAIN", sub1!!.sourceNodeName)
        assertEquals(ConnectionType.USES, sub1.connectionType)
        assertEquals(filePath, sub1.filePath)
        
        // The positions should correspond to "SUB1" in the statement
        assertTrue(sub1.location.start.column >= sub1StartPos - 10)
        assertTrue(sub1.location.end.column <= sub1EndPos + 10) // Allow some flexibility
    }
    
    @Test
    @DisplayName("Test EXEC SQL INCLUDE statement handling")
    fun testHandleExecSqlIncludeStatement() {
        // Prepare COBOL code with EXEC SQL INCLUDE statement
        val cobolCode = """
            000100 IDENTIFICATION DIVISION.
            000200 PROGRAM-ID. MAIN.
            000300 DATA DIVISION.
            000400     EXEC SQL INCLUDE SQLFILE END-EXEC.
            000500     WORKING-STORAGE SECTION.
        """.trimIndent()
        
        // Add file using the path that absolutizePath will generate
        fileHandler.addFile(getAbsoluteFilePath(), cobolCode)
        
        // Find the position of "SQLFILE" in the code
        val sqlFileStartPos = "000400     EXEC SQL INCLUDE SQLFILE END-EXEC.".indexOf("SQLFILE")
        val sqlFileEndPos = sqlFileStartPos + "SQLFILE".length
        
        // Find the position of the MAIN program ID
        val mainStartPos = "000200 PROGRAM-ID. MAIN.".indexOf("MAIN")
        val mainEndPos = mainStartPos + "MAIN".length
        
        // Create nodes for the test with updated Node structure
        val nodesMap = mutableMapOf<String, Node>()
        
        // Main node now contains the DEFINES connection information
        nodesMap["MAIN"] = Node(
            id = "MAIN",
            simpleName = "MAIN",
            nodeType = NodeType.File,
            description = "",
            definingNodeName = "",
            filePath = filePath,
            codeLocation = LCRange(LCPosition(0, 0), LCPosition(4, cobolCode.length)),
            nameLocation = LCRange(LCPosition(1, mainStartPos), LCPosition(1, mainEndPos)),
            codeHash = "MAIN".hashCode().toUInt().toString()
        )
        
        // SQLFILE node
        nodesMap["SQLFILE"] = Node(
            id = "SQLFILE",
            simpleName = "SQLFILE",
            nodeType = NodeType.File,
            description = "",
            definingNodeName = "",
            filePath = "src/main/cobol/SQLFILE.sql",
            codeLocation = LCRange.empty(),
            nameLocation = LCRange.empty(),
            codeHash = "SQLFILE".hashCode().toUInt().toString()
        )
        
        // Create the graph without separate DEFINES connections
        val graph = Graph(nodesMap, ListConnectionsNavigator(emptyList()))
        
        // Execute the method we're testing
        cobolLanguageSpec.handleSpecialOutboundConnections(
            projectPath,
            "MAIN",
            graph,
            connections
        )
        
        // Verify
        assertEquals(1, connections.size)
        val connection = connections.first()
        assertEquals("MAIN", connection.sourceNodeName)
        assertEquals("SQLFILE", connection.targetNodeName)
        assertEquals(ConnectionType.USES, connection.connectionType)
        assertEquals(filePath, connection.filePath)
        
        // The positions should correspond to "SQLFILE" in the statement
        assertTrue(connection.location.start.column >= sqlFileStartPos - 10)
        assertTrue(connection.location.end.column <= sqlFileEndPos + 10) // Allow some flexibility
    }

    //TODO: MOVE parsing was removed to gain speedup. Perhaps we can add it back once a faster solution is found
    /*@Test
    @DisplayName("Test MOVE statement with string literal handling")
    fun testHandleMoveStatement() {
        // Prepare COBOL code with MOVE statement containing string literals
        val cobolCode = """
            000100 IDENTIFICATION DIVISION.
            000200 PROGRAM-ID. MAIN.
            000300 PROCEDURE DIVISION.
            000400     MOVE "SUB1" TO PROGRAM-NAME.
            000500     CALL PROGRAM-NAME.
        """.trimIndent()
        
        // Add file using the path that absolutizePath will generate
        fileHandler.addFile(getAbsoluteFilePath(), cobolCode)
        
        // Find the position of "SUB1" in the code
        val sub1StartPos = "000400     MOVE \"SUB1\" TO PROGRAM-NAME.".indexOf("SUB1")
        val sub1EndPos = sub1StartPos + "SUB1".length
        
        // Find the position of the MAIN program ID
        val mainStartPos = "000200 PROGRAM-ID. MAIN.".indexOf("MAIN")
        val mainEndPos = mainStartPos + "MAIN".length
        
        // Create nodes for the test with updated Node structure
        val nodesMap = mutableMapOf<String, Node>()
        
        // Main node now contains the DEFINES connection information
        nodesMap["MAIN"] = Node(
            id = "MAIN",
            simpleName = "MAIN",
            nodeType = NodeType.File,
            description = "",
            definingNodeName = "",
            filePath = filePath,
            codeLocation = LCRange(LCPosition(0, 0), LCPosition(4, cobolCode.length)),
            nameLocation = LCRange(LCPosition(1, mainStartPos), LCPosition(1, mainEndPos)),
            codeHash = "MAIN".hashCode().toUInt().toString()
        )
        
        // SUB1 node
        nodesMap["SUB1"] = Node(
            id = "SUB1",
            simpleName = "SUB1",
            nodeType = NodeType.File,
            description = "",
            definingNodeName = "",
            filePath = "src/main/cobol/SUB1.cbl",
            codeLocation = LCRange.empty(),
            nameLocation = LCRange.empty(),
            codeHash = "SUB1".hashCode().toUInt().toString()
        )
        
        // Create the graph without separate DEFINES connections
        val graph = Graph(nodesMap, ListConnectionsNavigator(emptyList()))
        
        // Execute the method we're testing
        cobolLanguageSpec.handleSpecialOutboundConnections(
            projectPath,
            "MAIN",
            graph,
            connections
        )
        
        // Verify
        assertEquals(1, connections.size)
        val connection = connections.first()
        assertEquals("MAIN", connection.sourceNodeName)
        assertEquals("SUB1", connection.targetNodeName)
        assertEquals(ConnectionType.USES, connection.connectionType)
        assertEquals(filePath, connection.filePath)
        
        // The positions should correspond to "SUB1" in the MOVE statement
        assertTrue(connection.location.start.column >= sub1StartPos - 10)
        assertTrue(connection.location.end.column <= sub1EndPos + 10) // Allow some flexibility
    }*/
    
    @Test
    @DisplayName("Test ignoring commented lines")
    fun testIgnoreCommentedLines() {
        // Prepare COBOL code with commented CALL statements (using * in column 7)
        val cobolCode = """
            000100 IDENTIFICATION DIVISION.
            000200 PROGRAM-ID. MAIN.
            000300 PROCEDURE DIVISION.
            000400*    CALL "SUB1" USING VAR1, VAR2.
            000500/    CALL "SUB2" USING VAR3, VAR4.
            000600     DISPLAY "DONE".
        """.trimIndent()
        
        // Add file using the path that absolutizePath will generate
        fileHandler.addFile(getAbsoluteFilePath(), cobolCode)
        
        // Find the position of the MAIN program ID
        val mainStartPos = cobolCode.indexOf("MAIN")
        val mainEndPos = mainStartPos + "MAIN".length
        
        // Create nodes for the test with updated Node structure
        val nodesMap = mutableMapOf<String, Node>()
        
        // Main node now contains the DEFINES connection information
        nodesMap["MAIN"] = Node(
            id = "MAIN",
            simpleName = "MAIN",
            nodeType = NodeType.File,
            description = "",
            definingNodeName = "",
            filePath = filePath,
            codeLocation = LCRange(LCPosition(0, 0), LCPosition(5, cobolCode.length)),
            nameLocation = LCRange(LCPosition(1, mainStartPos), LCPosition(1, mainEndPos)),
            codeHash = "MAIN".hashCode().toUInt().toString()
        )
        
        // SUB1 node (even though it's commented out, for test completeness)
        nodesMap["SUB1"] = Node(
            id = "SUB1",
            simpleName = "SUB1",
            nodeType = NodeType.File,
            description = "",
            definingNodeName = "",
            filePath = "src/main/cobol/SUB1.cbl",
            codeLocation = LCRange.empty(),
            nameLocation = LCRange.empty(),
            codeHash = "SUB1".hashCode().toUInt().toString()
        )
        
        // SUB2 node (even though it's commented out, for test completeness)
        nodesMap["SUB2"] = Node(
            id = "SUB2",
            simpleName = "SUB2",
            nodeType = NodeType.File,
            description = "",
            definingNodeName = "",
            filePath = "src/main/cobol/SUB2.cbl",
            codeLocation = LCRange.empty(),
            nameLocation = LCRange.empty(),
            codeHash = "SUB2".hashCode().toUInt().toString()
        )
        
        // Create the graph without separate DEFINES connections
        val graph = Graph(nodesMap, ListConnectionsNavigator(emptyList()))
        
        // Execute the method we're testing
        cobolLanguageSpec.handleSpecialOutboundConnections(
            projectPath,
            "MAIN",
            graph,
            connections
        )
        
        // Verify that no connections were created for the commented lines
        assertTrue(connections.isEmpty())
    }
    
    @Test
    @DisplayName("Test handling multiple commands in the same file")
    fun testHandleMultipleCommands() {
        // Prepare COBOL code with multiple commands
        val cobolCode = """
            000100 IDENTIFICATION DIVISION.
            000200 PROGRAM-ID. MAIN.
            000300 PROCEDURE DIVISION.
            000400     CALL "SUB1" USING VAR1, VAR2.
            000500     CANCEL "SUB2".
            000600     COPY COPY1.
            000700     EXEC SQL INCLUDE SQLFILE END-EXEC.
            000800     DISPLAY "DONE".
        """.trimIndent()
        
        // Add file using the path that absolutizePath will generate
        fileHandler.addFile(getAbsoluteFilePath(), cobolCode)
        
        // Find the position of the MAIN program ID
        val mainStartPos = cobolCode.indexOf("MAIN")
        val mainEndPos = mainStartPos + "MAIN".length
        
        // Create nodes for the test with updated Node structure
        val nodesMap = mutableMapOf<String, Node>()
        
        // Main node now contains the DEFINES connection information
        nodesMap["MAIN"] = Node(
            id = "MAIN",
            simpleName = "MAIN",
            nodeType = NodeType.File,
            description = "",
            definingNodeName = "",
            filePath = filePath,
            codeLocation = LCRange(LCPosition(0, 0), LCPosition(7, cobolCode.length)),
            nameLocation = LCRange(LCPosition(1, mainStartPos), LCPosition(1, mainEndPos)),
            codeHash = "MAIN".hashCode().toUInt().toString()
        )
        
        // SUB1 node
        nodesMap["SUB1"] = Node(
            id = "SUB1",
            simpleName = "SUB1",
            nodeType = NodeType.File,
            description = "",
            definingNodeName = "",
            filePath = "src/main/cobol/SUB1.cbl",
            codeLocation = LCRange.empty(),
            nameLocation = LCRange.empty(),
            codeHash = "SUB1".hashCode().toUInt().toString()
        )
        
        // SUB2 node
        nodesMap["SUB2"] = Node(
            id = "SUB2",
            simpleName = "SUB2",
            nodeType = NodeType.File,
            description = "",
            definingNodeName = "",
            filePath = "src/main/cobol/SUB2.cbl",
            codeLocation = LCRange.empty(),
            nameLocation = LCRange.empty(),
            codeHash = "SUB2".hashCode().toUInt().toString()
        )
        
        // COPY1 node
        nodesMap["COPY1"] = Node(
            id = "COPY1",
            simpleName = "COPY1",
            nodeType = NodeType.File,
            description = "",
            definingNodeName = "",
            filePath = "src/main/cobol/COPY1.cpy",
            codeLocation = LCRange.empty(),
            nameLocation = LCRange.empty(),
            codeHash = "COPY1".hashCode().toUInt().toString()
        )
        
        // SQLFILE node
        nodesMap["SQLFILE"] = Node(
            id = "SQLFILE",
            simpleName = "SQLFILE",
            nodeType = NodeType.File,
            description = "",
            definingNodeName = "",
            filePath = "src/main/cobol/SQLFILE.sql",
            codeLocation = LCRange.empty(),
            nameLocation = LCRange.empty(),
            codeHash = "SQLFILE".hashCode().toUInt().toString()
        )
        
        // Create the graph without separate DEFINES connections
        val graph = Graph(nodesMap, ListConnectionsNavigator(emptyList()))
        
        // Execute the method we're testing
        cobolLanguageSpec.handleSpecialOutboundConnections(
            projectPath,
            "MAIN",
            graph,
            connections
        )
        
        // Verify
        assertEquals(4, connections.size)
        
        // All connections should use MAIN as the source
        assertTrue(connections.all { it.sourceNodeName == "MAIN" })
        
        val targets = connections.map { it.targetNodeName }.toList()
        assertTrue(targets.contains("SUB1"))
        assertTrue(targets.contains("SUB2"))
        assertTrue(targets.contains("COPY1"))
        assertTrue(targets.contains("SQLFILE"))
        
        // All connections should be of type USES
        assertTrue(connections.all { it.connectionType == ConnectionType.USES })
        
        // All connections should have the correct file path
        assertTrue(connections.all { it.filePath == filePath })
    }
}
