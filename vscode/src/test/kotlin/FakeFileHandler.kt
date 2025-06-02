package software.bevel.code_to_knowledge_graph.vscode

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import software.bevel.file_system_domain.LCPosition
import software.bevel.file_system_domain.services.FileHandler
import software.bevel.graph_domain.graph.*
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * A fake implementation of FileHandler for testing purposes.
 * Can be initialized with a map of file paths to their content.
 */
class FakeFileHandler(private val fileContents: MutableMap<String, String> = mutableMapOf()) : FileHandler {
    private val logger: Logger = LoggerFactory.getLogger(FakeFileHandler::class.java)
    override val separator: String = "/"
    
    /**
     * Add a file with content to the fake file system
     */
    fun addFile(filePath: String, content: String) {
        fileContents[filePath] = content
    }
    
    /**
     * Clear all files from the fake file system
     */
    fun clearFiles() {
        fileContents.clear()
    }

    override fun readString(filePath: String): String {
        val normalizedPath = normalizePath(filePath)
        return fileContents[normalizedPath] ?: run {
            // Try with variations of the path (with/without file: prefix, different separators)
            val content = fileContents.entries.firstOrNull { 
                normalizedPath.endsWith(it.key.substringAfterLast("/")) || 
                it.key.endsWith(normalizedPath.substringAfterLast("/"))
            }?.value
            
            if (content != null) {
                return content
            }
            
            logger.error("Could not readString from file: $filePath")
            ""
        }
    }

    override fun readString(filePath: String, startCharacter: Int, endCharacter: Int): String {
        try {
            val fileContent = readString(filePath)
            return fileContent.substring(max(startCharacter, 0), min(endCharacter, fileContent.length))
        } catch (ex: Exception) {
            logger.error("Error reading string from $filePath", ex)
            return ""
        }
    }

    override fun readString(filePath: String, start: LCPosition, end: LCPosition): String {
        val lines = readLines(filePath, start.line, end.line)
        if (lines.isEmpty()) return ""
        try {
            if (start.line == end.line) return lines[0].substring(start.column, min(end.column, lines[0].length))
            return start.column.let {
                if (it > lines.first().length) return@let lines.first()
                lines.first().substring(it)
            } + "\n" +
                    lines.subList(1, lines.size - 1).joinToString("\n") + "\n" +
                    lines.last().substring(0, min(end.column, lines.last().length))
        } catch (ex: Exception) {
            logger.error("Error reading string from $filePath", ex)
            return ""
        }
    }

    override fun readLines(filePath: String): List<String> {
        val content = readString(filePath)
        return if (content.isEmpty()) emptyList() else content.split('\n')
    }

    override fun readLines(filePath: String, startLine: Int, endLine: Int): List<String> {
        val allLines = readLines(filePath)
        return if (allLines.isEmpty()) emptyList() 
               else allLines.subList(max(0, startLine), min(allLines.size, endLine + 1))
    }

    override fun readLines(filePath: String, start: LCPosition, end: LCPosition): List<String> {
        val lines = readLines(filePath, start.line, end.line)
        if (lines.isEmpty()) return emptyList()
        try {
            if (start.line == end.line) return listOf(
                lines[0].substring(start.column, min(end.column, lines[0].length))
            )
            
            val result = mutableListOf<String>()
            
            // First line starting from the column
            if (start.column <= lines.first().length) {
                result.add(lines.first().substring(start.column))
            } else {
                result.add("") // Empty if column is beyond line length
            }
            
            // Middle lines (unchanged)
            if (lines.size > 2) {
                result.addAll(lines.subList(1, lines.size - 1))
            }
            
            // Last line up to the column
            if (lines.size > 1) {
                result.add(lines.last().substring(0, min(end.column, lines.last().length)))
            }
            
            return result
        } catch (ex: Exception) {
            logger.error("Error reading lines from $filePath", ex)
            return emptyList()
        }
    }

    override fun writeString(filePath: String, content: String) {
        fileContents[normalizePath(filePath)] = content
    }

    override fun getExtensionFromPath(filePath: String): String {
        return File(filePath).extension
    }

    override fun exists(filePath: String): Boolean {
        return fileContents.containsKey(normalizePath(filePath)) || 
               fileContents.any { normalizePath(filePath).endsWith(it.key.substringAfterLast("/")) ||
                                 it.key.endsWith(normalizePath(filePath).substringAfterLast("/")) }
    }

    override fun isFile(filePath: String): Boolean {
        return exists(filePath)
    }

    override fun delete(filePath: String) {
        fileContents.remove(normalizePath(filePath))
    }

    override fun createFile(filePath: String) {
        if (!exists(filePath)) {
            fileContents[normalizePath(filePath)] = ""
        }
    }

    override fun createDirectory(filePath: String) {
        // No-op in fake implementation as we don't track directories separately
    }
    
    private fun normalizePath(filePath: String): String {
        // Normalize path to handle different separators and file: prefix
        return filePath.replace("\\", "/").replace("file:", "")
    }
}
