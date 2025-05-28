package software.bevel.code_to_knowledge_graph.providers

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import software.bevel.file_system_domain.services.CachedFileHandler
import java.io.File
import java.util.LinkedList

/**
 * A [FileHandler] implementation that uses an in-memory cache to store file contents and metadata.
 * This class extends [IoFileHandler] and implements [CachedFileHandler].
 * The cache has a maximum size and uses a FIFO (First-In, First-Out) eviction policy.
 *
 * @param logger The logger instance to use for logging messages.
 */
class CachedIoFileHandler(
    private val logger: Logger = LoggerFactory.getLogger(CachedIoFileHandler::class.java)
): CachedFileHandler, IoFileHandler() {
    /**
     * Companion object for [CachedIoFileHandler].
     */
    companion object {
        /** Maximum number of files to store in each cache (content and lines). */
        private const val MAX_CACHE_SIZE = 10
    }

    // Cache structures to store file content
    /** Cache for storing file content as a single string, along with its last modified timestamp. */
    private val fileContentCache = mutableMapOf<String, Pair<String, Long>>() // filePath -> (content, lastModified)
    /** Cache for storing file content as a list of strings (lines), along with its last modified timestamp. */
    private val fileLinesCache = mutableMapOf<String, Pair<List<String>, Long>>() // filePath -> (lines, lastModified)
    /** Cache for storing file extensions. */
    private val extensionCache = mutableMapOf<String, String>() // Simple cache for file extensions
    
    // FIFO queues to track insertion order
    /** Queue to maintain the order of insertion for the [fileContentCache] for FIFO eviction. */
    private val contentCacheQueue = LinkedList<String>() // Queue of file paths in order of insertion
    /** Queue to maintain the order of insertion for the [fileLinesCache] for FIFO eviction. */
    private val linesCacheQueue = LinkedList<String>() // Queue of file paths in order of insertion

    /**
     * Clears all caches (file content, file lines, and extensions) and their respective tracking queues.
     */
    override fun clearCache() {
        fileContentCache.clear()
        fileLinesCache.clear()
        extensionCache.clear()
        contentCacheQueue.clear()
        linesCacheQueue.clear()
        logger.debug("File cache cleared")
    }

    /**
     * Reads the entire content of the specified file as a single String.
     * Attempts to retrieve the content from the cache first. If not found or outdated,
     * it reads from the file system, updates the cache, and returns the content.
     * Implements a FIFO eviction strategy if the cache size limit is reached.
     *
     * @param filePath The path to the file to read.
     * @return The content of the file as a String, or an empty string if the file does not exist or cannot be read.
     */
    override fun readString(filePath: String): String {
        val file = File(filePath)
        if(!file.exists() || !file.isFile) {
            logger.error("Could not readString from file: $filePath")
            return ""
        }
        
        val lastModified = file.lastModified()
        val cached = fileContentCache[filePath]
        
        return if (cached != null && cached.second == lastModified) {
            // Cache hit
            cached.first
        } else {
            // Cache miss or outdated
            val content = super<IoFileHandler>.readString(filePath)
            
            // If the file is already in the cache, remove it from the queue to add it again later (refresh position)
            if (fileContentCache.containsKey(filePath)) {
                contentCacheQueue.remove(filePath)
            }
            
            // Check if cache limit is reached and evict oldest entry if needed
            if (contentCacheQueue.size >= MAX_CACHE_SIZE && !fileContentCache.containsKey(filePath)) {
                val oldestFilePath = contentCacheQueue.poll()
                oldestFilePath?.let {
                    fileContentCache.remove(it)
                    logger.debug("Evicted oldest content cache entry: $it")
                }
            }
            
            // Add to cache and queue
            fileContentCache[filePath] = Pair(content, lastModified)
            contentCacheQueue.add(filePath)
            
            content
        }
    }

    /**
     * Reads all lines from the specified file.
     * Attempts to retrieve the lines from the cache first. If not found or outdated,
     * it reads from the file system, updates the cache, and returns the lines.
     * Implements a FIFO eviction strategy if the cache size limit is reached.
     *
     * @param filePath The path to the file to read.
     * @return A list of strings representing the lines of the file, or an empty list if the file does not exist or cannot be read.
     */
    override fun readLines(filePath: String): List<String> {
        val file = File(filePath)
        if(!file.exists() || !file.isFile) {
            logger.error("Could not readLines from file: $filePath")
            return listOf()
        }
        
        val lastModified = file.lastModified()
        val cached = fileLinesCache[filePath]
        
        return if (cached != null && cached.second == lastModified) {
            // Cache hit
            cached.first
        } else {
            // Cache miss or outdated
            val lines = super<IoFileHandler>.readLines(filePath)
            
            // If the file is already in the cache, remove it from the queue to add it again later (refresh position)
            if (fileLinesCache.containsKey(filePath)) {
                linesCacheQueue.remove(filePath)
            }
            
            // Check if cache limit is reached and evict oldest entry if needed
            if (linesCacheQueue.size >= MAX_CACHE_SIZE && !fileLinesCache.containsKey(filePath)) {
                val oldestFilePath = linesCacheQueue.poll()
                oldestFilePath?.let {
                    fileLinesCache.remove(it)
                    logger.debug("Evicted oldest lines cache entry: $it")
                }
            }
            
            // Add to cache and queue
            fileLinesCache[filePath] = Pair(lines, lastModified)
            linesCacheQueue.add(filePath)
            
            lines
        }
    }
    
    /**
     * Gets the extension of the file from its path.
     * Uses a simple cache to store and retrieve extensions for previously accessed paths.
     *
     * @param filePath The path to the file.
     * @return The file extension (e.g., "txt", "kt"), or an empty string if no extension is found.
     */
    override fun getExtensionFromPath(filePath: String): String {
        // Simple cache lookup
        extensionCache[filePath]?.let { return it }
        
        // Cache miss - compute and cache the result
        val extension = super.getExtensionFromPath(filePath)
        extensionCache[filePath] = extension
        return extension
    }
    
    /**
     * Writes the given content to the specified file.
     * After writing, it invalidates any cached entries for this file to ensure subsequent reads fetch fresh data.
     *
     * @param filePath The path to the file to write to.
     * @param content The string content to write to the file.
     */
    override fun writeString(filePath: String, content: String) {
        super.writeString(filePath, content)
        
        // Invalidate cache for this file
        fileContentCache.remove(filePath)
        contentCacheQueue.remove(filePath)
        fileLinesCache.remove(filePath)
        linesCacheQueue.remove(filePath)
        extensionCache.remove(filePath)
    }
    
    /**
     * Deletes the specified file.
     * After deletion, it invalidates any cached entries for this file.
     *
     * @param filePath The path to the file to delete.
     */
    override fun delete(filePath: String) {
        super.delete(filePath)
        
        // Invalidate cache
        fileContentCache.remove(filePath)
        contentCacheQueue.remove(filePath)
        fileLinesCache.remove(filePath)
        linesCacheQueue.remove(filePath)
        extensionCache.remove(filePath)
    }
}