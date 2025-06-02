package software.bevel.code_to_knowledge_graph.vscode

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import software.bevel.code_to_knowledge_graph.vscode.data.*
import software.bevel.code_to_knowledge_graph.vscode.languageSpecs.VsCodeLanguageSpecification
import software.bevel.file_system_domain.LCPosition
import software.bevel.file_system_domain.LCRange
import software.bevel.file_system_domain.absolutizePath
import software.bevel.file_system_domain.services.FileHandler
import software.bevel.file_system_domain.web.LocalCommunicationInterface
import software.bevel.graph_domain.graph.*
import software.bevel.graph_domain.graph.builder.GraphBuilder
import software.bevel.graph_domain.graph.builder.ListConnectionsNavigator
import software.bevel.graph_domain.hashing.LocalitySensitiveHasher
import software.bevel.graph_domain.parsing.PotentialSymbol
import software.bevel.graph_domain.tokenizers.IdentifierTokenizer
import kotlin.test.assertEquals

// Test implementation of FileHandler
class TestFileHandler : FileHandler {
    private val logger: Logger = LoggerFactory.getLogger(TestFileHandler::class.java)
    private val fileContents = mutableMapOf<String, String>()

    override val separator: String = "\\"

    override fun readString(filePath: String): String =
        fileContents[filePath] ?: throw IllegalArgumentException("File not found: $filePath")

    override fun readString(filePath: String, startCharacter: Int, endCharacter: Int): String {
        val content = readString(filePath)
        return content.substring(startCharacter, endCharacter)
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
            val endChar = if (lineIndex == end.line) minOf(end.column, line.length) else line.length
            
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
        val validEndLine = endLine.coerceIn(0, lines.size)
        
        return lines.subList(validStartLine, validEndLine)
    }

    override fun readLines(filePath: String, start: LCPosition, end: LCPosition): List<String> {
        val allLines = readLines(filePath)
        
        // Protect against out of bounds indices
        val validStartLine = start.line.coerceIn(0, allLines.size)
        val validEndLine = minOf(end.line + 1, allLines.size)
        
        if (validStartLine >= validEndLine) return emptyList()
        
        val result = mutableListOf<String>()
        
        // First line - may need to trim from the beginning
        val firstLine = allLines[validStartLine]
        val firstLineStartCol = minOf(start.column, firstLine.length)
        
        // Last line - may need to trim from the end
        val lastLine = allLines[validEndLine - 1]
        val lastLineEndCol = minOf(end.column, lastLine.length)
        
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

    override fun writeString(filePath: String, content: String) {
        fileContents[filePath] = content
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

    override fun exists(filePath: String): Boolean = fileContents.containsKey(filePath)

    override fun isFile(filePath: String): Boolean = exists(filePath)

    override fun delete(filePath: String) {
        fileContents.remove(filePath)
    }

    override fun createFile(filePath: String) {
        if (!exists(filePath)) {
            fileContents[filePath] = ""
        }
    }

    override fun createDirectory(filePath: String) {
        // No-op for test implementation
    }
}

// Test implementation of LocalCommunicationInterface
class TestCommunicationInterface : LocalCommunicationInterface {
    override fun send(message: String): String =
        jacksonObjectMapper().writeValueAsString(
            BatchNodeRequestResponse(listOf(
            VsCodeLocationResponse(listOf(
                VsCodeLocation("C:\\src\\test\\resources\\tmp\\test.kt", VsCodeRange(1, 4, 1, 17))
            ))
        ))
        )

    override fun <T> send(message: T): String {
        return jacksonObjectMapper().writeValueAsString(BatchNodeRequestResponse(listOf(
            VsCodeLocationResponse(listOf(
                VsCodeLocation("C:\\src\\test\\resources\\tmp\\test.kt", VsCodeRange(1, 4, 1, 17))
            ))
        )))
    }

    override fun sendWithoutResponse(message: String) {
        // No-op for test implementation
    }

    override fun isConnected(): Boolean {
        return true
    }

    override fun close() {
    }
}

// Test implementation of VsCodeLanguageSpecification
class TestLanguageSpecification(
    override val vsCodeNodeBuilder: VsCodeNodeBuilder
) : VsCodeLanguageSpecification {
    override var supportedFileEndings: List<String> = listOf(".kt")

    override fun getPotentialSymbols(projectPath: String, filePath: String, code: String): List<PotentialSymbol> =
        listOf(PotentialSymbol("otherFunction", filePath, LCPosition(0, 19), LCPosition(0, 32)))

    override fun addHardcodedSymbols(pathToProject: String, relativePath: String, graph: GraphBuilder) {
        TODO("Not yet implemented")
    }

    override fun handleSpecialOutboundConnections(
        pathToProject: String,
        nodeName: String,
        graph: Graphlike,
        connections: MutableSet<Connection>
    ) {
        // No-op
    }
}

class TestVsCodeConnectionParser {
    private lateinit var fileHandler: FileHandler
    private lateinit var language: VsCodeLanguageSpecification
    private lateinit var communicationInterface: LocalCommunicationInterface
    private lateinit var parser: VsCodeConnectionParser
    private val hasher: LocalitySensitiveHasher = object : LocalitySensitiveHasher {
        override fun hash(input: String): String {
            return input.hashCode().toUInt().toString()
        }

        override fun similarity(hash1: String, hash2: String): Double {
            return 0.0
        }
    }

    @BeforeEach
    fun setup() {
        fileHandler = TestFileHandler()
        language = TestLanguageSpecification(VsCodeNodeBuilder(fileHandler, hasher))
        communicationInterface = TestCommunicationInterface()
    }

    @Test
    fun testCreateOutboundConnectionsForNodes() {
        // Setup test data
        val sourceNodeId = "testNode"
        val targetNodeId = "otherFunction"
        val projectPath = "C:\\src\\test\\resources\\tmp"
        val filePath = "test.kt"
        val absolutePath = "$projectPath\\$filePath"
        
        val nodes = mutableMapOf(
            sourceNodeId to Node(
                sourceNodeId,
                sourceNodeId,
                NodeType.Function,
                definingNodeName = "<global>",
                filePath = filePath,
                codeLocation = LCRange(LCPosition.ZERO, LCPosition(0, 34)),
                nameLocation = LCRange(LCPosition(0, 4), LCPosition(0, 12)),
                codeHash = "0"
            ),
            targetNodeId to Node(targetNodeId,
                targetNodeId,
                NodeType.Function,
                definingNodeName = "<global>",
                filePath = filePath,
                codeLocation = LCRange(LCPosition(1, 0), LCPosition(1, 22)),
                nameLocation = LCRange(LCPosition(1, 4), LCPosition(1, 17)),
                codeHash = "0"
            )
        )
        val definesConnections = ListConnectionsNavigator(mutableListOf())
        val graph = Graph(nodes, definesConnections)

        // Setup test file content
        (fileHandler as TestFileHandler).writeString(absolutePath, "fun testNode() { otherFunction() }\nfun otherFunction() {}")

        // Create parser with test implementations
        parser = VsCodeConnectionParser(
            projectPath,
            communicationInterface,
            fileHandler,
            language,
            LoggerFactory.getLogger(VsCodeConnectionParser::class.java)
        )

        // Execute the test
        val connections = parser.createOutboundConnectionsForNodes(sourceNodeId, graph = graph)

        // Assert that a USES connection was created for otherFunction
        assertEquals(1, connections.size)
        val usesConnection = connections.first()
        assertEquals(sourceNodeId, usesConnection.sourceNodeName)
        assertEquals(targetNodeId, usesConnection.targetNodeName)
        assertEquals(ConnectionType.USES, usesConnection.connectionType)
        assertEquals(filePath, usesConnection.filePath)
        assertEquals(LCRange(LCPosition(0, 19), LCPosition(0, 32)), usesConnection.location)
    }
}
