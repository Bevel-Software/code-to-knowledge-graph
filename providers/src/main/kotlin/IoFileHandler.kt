package software.bevel.code_to_knowledge_graph.providers

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import software.bevel.file_system_domain.LCPosition
import software.bevel.file_system_domain.services.FileHandler
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * A standard [FileHandler] implementation that uses Java's `java.io.File` for file operations.
 * This class is open for extension, for example, by a caching file handler.
 *
 * @param logger The logger instance to use for logging messages.
 */
open class IoFileHandler(
    private val logger: Logger = LoggerFactory.getLogger(IoFileHandler::class.java)
): FileHandler {
    /**
     * The system-dependent file separator character (e.g., "/" on UNIX, "\" on Windows).
     */
    override val separator: String
        get() = File.separator

    /**
     * Reads the entire content of the specified file as a single String.
     * Logs an error and returns an empty string if the file does not exist or is not a regular file.
     *
     * @param filePath The path to the file to read.
     * @return The content of the file as a String, or an empty string on failure.
     */
    override fun readString(filePath: String): String {
        val file = File(filePath)
        if(!file.exists() || !file.isFile) {
            logger.error("Could not readString from file: $filePath")
            return ""
        }
        return file.readText()
    }

    /**
     * Reads a portion of the specified file as a String, defined by character offsets.
     * The start and end character offsets are adjusted to be within the bounds of the file content.
     * Logs an error and returns an empty string if any exception occurs during reading or substring extraction.
     *
     * @param filePath The path to the file to read.
     * @param startCharacter The starting character offset (inclusive). Will be clamped to 0 if negative.
     * @param endCharacter The ending character offset (exclusive). Will be clamped to file length if it exceeds it.
     * @return The specified portion of the file content as a String, or an empty string on failure.
     */
    override fun readString(filePath: String, startCharacter: Int, endCharacter: Int): String {
        try {
            val fileContent = readString(filePath)
            return fileContent.substring(max(startCharacter, 0), min(endCharacter, fileContent.length))
        } catch (ex: Exception) {
            logger.error(filePath, ex)
            return ""
        }
    }

    /**
     * Reads a portion of the specified file as a String, defined by start and end [LCPosition] objects.
     * Handles cases where the start/end positions might be outside the actual file content or line boundaries.
     * Logs an error and returns an empty string if any exception occurs.
     *
     * @param filePath The path to the file to read.
     * @param start The starting position ([LCPosition]).
     * @param end The ending position ([LCPosition]).
     * @return The specified portion of the file content as a String, or an empty string on failure.
     */
    override fun readString(filePath: String, start: LCPosition, end: LCPosition): String {
        val lines = readLines(filePath, start.line, end.line)
        val actualEnd = end.copy(line = min(end.line, lines.size - 1))
        if (lines.isEmpty()) return ""
        try {
            if(start.line == actualEnd.line) {
                if(start.column > min(actualEnd.column, lines[0].length)) {
                    logger.error("Attempted to read starting after line end, line ${start.line}, $filePath")
                    return lines[0]
                }
                return lines[0].substring(start.column, min(actualEnd.column, lines[0].length))
            }
            if(filePath.contains("condition-block-state-property")) {
                println("Test")
            }
            return start.column.let {
                if(it > lines.first().length) return@let lines.first()
                lines.first().substring(it)
            } + "\n" +
                    (if(lines.size > 2) lines.subList(1, lines.size - 1).joinToString("") { it + "\n" } else "") +
                    lines.last().substring(0, min(actualEnd.column, lines.last().length))
        } catch (ex: Exception) {
            logger.error("Error reading string from $filePath", ex)
            return ""
        }
    }

    /**
     * Reads all lines from the specified file.
     * Logs an error and returns an empty list if the file does not exist or is not a regular file.
     *
     * @param filePath The path to the file to read.
     * @return A list of strings, where each string is a line from the file, or an empty list on failure.
     */
    override fun readLines(filePath: String): List<String> {
        val file = File(filePath)
        if(!file.exists() || !file.isFile) {
            logger.error("Could not readString from file: $filePath")
            return listOf()
        }
        return file.readLines()
    }

    /**
     * Reads a specific range of lines from the specified file.
     * The end line is adjusted to be within the bounds of the file's line count.
     * Logs an error and returns an empty list if the start line is out of bounds.
     *
     * @param filePath The path to the file to read.
     * @param startLine The starting line number (inclusive, 0-indexed).
     * @param endLine The ending line number (inclusive, 0-indexed).
     * @return A list of strings representing the specified lines from the file, or an empty list on failure or if range is invalid.
     */
    override fun readLines(filePath: String, startLine: Int, endLine: Int): List<String> {
        val allLines = readLines(filePath)
        if(allLines.size < startLine) {
            logger.error("Could not readLines $startLine-$endLine from file: $filePath")
            return listOf()
        }
        return allLines.subList(startLine, min(allLines.size, endLine + 1))
    }

    /**
     * Reads lines from the specified file that fall within the given start and end [LCPosition] objects.
     * This extracts characters from the start position on the first line, all intermediate lines fully,
     * and characters up to the end position on the last line.
     * Handles cases where positions might be outside actual content.
     * Logs an error and returns an empty list if any exception occurs.
     *
     * @param filePath The path to the file to read.
     * @param start The starting position ([LCPosition]).
     * @param end The ending position ([LCPosition]).
     * @return A list of strings representing the lines or partial lines within the specified positions, or an empty list on failure.
     */
    override fun readLines(filePath: String, start: LCPosition, end: LCPosition): List<String> {
        val lines = readLines(filePath, start.line, end.line)
        val actualEnd = end.copy(line = min(end.line, lines.size - 1))
        if (lines.isEmpty()) return listOf()
        try {
            if(start.line == actualEnd.line) {
                if(start.column > min(actualEnd.column, lines[0].length)) {
                    logger.error("Attempted to read starting after line end, line ${start.line}, $filePath")
                    return listOf(lines[0])
                }
                return listOf(lines[0].substring(start.column, min(actualEnd.column, lines[0].length)))
            }
            return listOf(start.column.let {
                if(it > lines.first().length) return@let lines.first()
                lines.first().substring(it)
            }) +
                    (if(lines.size > 2) lines.subList(1, lines.size - 1) else listOf()) +
                    listOf(lines.last().substring(0, min(actualEnd.column, lines.last().length)))
        } catch (ex: Exception) {
            logger.error("Error reading lines from $filePath", ex)
            return listOf()
        }
    }

    /**
     * Checks if a file or directory exists at the specified path.
     *
     * @param filePath The path to check.
     * @return `true` if a file or directory exists at the path, `false` otherwise.
     */
    override fun exists(filePath: String): Boolean {
        return File(filePath).exists()
    }

    /**
     * Checks if the path points to an existing regular file.
     *
     * @param filePath The path to check.
     * @return `true` if the path points to an existing file, `false` otherwise (e.g., it's a directory or does not exist).
     */
    override fun isFile(filePath: String): Boolean {
        return File(filePath).isFile
    }

    /**
     * Deletes the file or directory at the specified path.
     * Note: `java.io.File.delete()` for a directory only succeeds if the directory is empty.
     *
     * @param filePath The path to the file or directory to delete.
     */
    override fun delete(filePath: String) {
        File(filePath).delete()
    }

    /**
     * Creates a new, empty file at the specified path if it does not already exist.
     * Parent directories are NOT automatically created by `java.io.File.createNewFile()`.
     *
     * @param filePath The path where the file should be created.
     */
    override fun createFile(filePath: String) {
        File(filePath).createNewFile()
    }

    /**
     * Creates a directory at the specified path, including any necessary but nonexistent parent directories.
     *
     * @param filePath The path where the directory (and its parents) should be created.
     */
    override fun createDirectory(filePath: String) {
        File(filePath).mkdirs()
    }

    /**
     * Writes the given content to the specified file, overwriting the file if it already exists.
     * If the file does not exist, it will be created (along with parent directories if necessary, due to `File.writeText` behavior).
     *
     * @param filePath The path to the file to write to.
     * @param content The string content to write to the file.
     */
    override fun writeString(filePath: String, content: String) {
        File(filePath).writeText(content)
    }

    /**
     * Gets the extension of the file from its path (including the dot).
     * For example, for "file.txt", it returns ".txt".
     * Returns an empty string if no extension is found (e.g., "file" or ".bashrc").
     *
     * @param filePath The path to the file.
     * @return The file extension (e.g., ".txt", ".kt"), or an empty string if no extension is found.
     */
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
}