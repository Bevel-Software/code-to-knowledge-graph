package software.bevel.code_to_knowledge_graph.providers

import software.bevel.file_system_domain.BevelFilesPathResolver
import software.bevel.file_system_domain.FileWalker
import software.bevel.file_system_domain.services.FileHandler
import software.bevel.graph_domain.parsing.LanguageSpecification
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.pathString

/**
 * A [FileWalker] implementation that is aware of `.gitignore` files and rules.
 * It traverses a directory structure, processing files that are not excluded by applicable `.gitignore` rules.
 *
 * @property languageSpec The [LanguageSpecification] used to determine which files should be parsed based on their extension or other criteria.
 * @property fileHandler The [FileHandler] responsible for processing the files that are visited and not ignored.
 */
class GitignoreAwareFileWalker(
    val languageSpec: LanguageSpecification,
    val fileHandler: FileHandler
): FileWalker {
    /**
     * Represents a single rule parsed from a `.gitignore` file.
     * This class handles the logic for matching a given path against the gitignore pattern.
     *
     * @property pattern The raw gitignore pattern string (e.g., "*.log", "!important.txt", "/build/").
     * @property separator The file separator string (e.g., "/" or "\\") used in the paths being checked.
     *                     This is used to normalize paths before matching.
     */
    private class GitignoreRule(val pattern: String, val separator: String) {
        private val isNegation = pattern.startsWith("!")
        private val cleanPattern = pattern.removePrefix("!").trim()
        private val isDirectory = pattern.endsWith("/")
        private val hasWildcard = pattern.contains("*")
        
        /**
         * Checks if a given file or directory path matches this gitignore rule.
         *
         * @param path The path string of the file or directory to check. This path should be relative to the
         *             directory containing the `.gitignore` file from which this rule was parsed, or relative to the root
         *             of the walk if the rule is from a global gitignore.
         * @param isDir A boolean indicating whether the path refers to a directory (`true`) or a file (`false`).
         * @return `true` if the path matches the pattern according to gitignore semantics (considering negation, directory-only, wildcards), `false` otherwise.
         *         Returns `false` if the pattern is blank or a comment.
         */
        fun matches(path: String, isDir: Boolean): Boolean {
            if (pattern.isBlank() || pattern.startsWith("#")) return false
            
            val pathToCheck = path.replace(separator, "/")
            
            // Handle directory-only patterns
            if (isDirectory && !isDir) return false
            
            val processedPattern = pattern.removePrefix("!")
                .trim()
                .replace(separator, "/")
                .let {
                    when {
                        it.startsWith("/") -> it.substring(1) // Remove leading slash for root-relative paths
                        !it.startsWith("**") -> "**/$it"     // Make pattern match anywhere in path
                        else -> it
                    }
                }

            val regex = processedPattern
                .replace(".", "\\.")
                // Use placeholders for ** patterns to protect them from further processing
                .replace("**/", "__DOUBLE_STAR_SLASH__")
                .replace("**", "__DOUBLE_STAR__")
                // Handle single * patterns
                .replace("*", "[^/]*")
                .replace("?", ".")
                // Restore ** patterns
                .replace("__DOUBLE_STAR_SLASH__", ".*")
                .replace("__DOUBLE_STAR__", ".*")
                .let { 
                    if (processedPattern.endsWith("/")) {
                        // For directory patterns, match the directory and all its contents
                        it.removeSuffix("/") + "(/.*)?$"
                    } else {
                        "$it$"
                    }
                }
                .let { "^$it" }
                .toRegex()
            
            return regex.matches(pathToCheck)
        }

        /**
         * Determines if a path should be ignored based on this rule, considering negation.
         *
         * @param path The path string to check.
         * @param isDir A boolean indicating if the path is a directory.
         * @return `true` if the path should be ignored, `false` otherwise.
         */
        fun shouldIgnore(path: String, isDir: Boolean): Boolean {
            val matches = matches(path, isDir)
            return matches != isNegation
        }
    }
    
    /**
     * Manages a collection of [GitignoreRule]s for a specific directory context (typically a directory containing a .gitignore file).
     *
     * @property separator The file separator string used for path normalization within this context's rules.
     */
    private class GitignoreContext(val separator: String) {
        val rules = mutableListOf<GitignoreRule>()
        
        /**
         * Parses the content of a `.gitignore` file and adds the resulting [GitignoreRule]s to this context.
         * Blank lines and lines starting with '#' (comments) are ignored.
         *
         * @param gitignoreContent The full string content of a `.gitignore` file.
         */
        fun addRules(gitignoreContent: String) {
            rules.addAll(gitignoreContent.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .map { GitignoreRule(it, separator) }
            )
        }
        
        /**
         * Checks if a given path should be ignored based on the rules in this context.
         * The last matching rule in the .gitignore file determines the outcome. If a path matches a negation rule (`!pattern`),
         * it will not be ignored, even if it matched a previous non-negation rule.
         *
         * @param path The path string to check, relative to the directory of this context.
         * @param isDir A boolean indicating if the path is a directory.
         * @return `true` if the path should be ignored, `false` otherwise. Returns `false` if no rules match.
         */
        fun shouldIgnore(path: String, isDir: Boolean): Boolean {
            val matchingPattern = rules.lastOrNull { it.matches(path, isDir) }
            return matchingPattern?.shouldIgnore(path, isDir) ?: false
        }

        /**
         * Checks if this context contains any gitignore rules.
         *
         * @return `true` if no rules have been added, `false` otherwise.
         */
        fun isEmpty(): Boolean = rules.isEmpty()
    }

    /**
     * A [SimpleFileVisitor] that navigates the file tree, respecting `.gitignore` rules loaded into [GitignoreContext]s.
     * It collects paths of files that are not ignored and match the language specification.
     *
     * @property rootPath The root [Path] from which the file walk started.
     * @property result A mutable list to which the paths of visited and non-ignored files are added.
     */
    private inner class GitignoreFileVisitor(
        private val rootPath: Path,
        private val result: MutableList<String>
    ) : SimpleFileVisitor<Path>() {
        val contexts = mutableMapOf<Path, GitignoreContext>()

        /**
         * Called before visiting a directory. If the directory itself is ignored by an applicable `.gitignore` rule,
         * its subtree is skipped. Otherwise, it loads any `.gitignore` file present in this directory into a new [GitignoreContext]
         * or adds to an existing one for this path.
         */
        override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
            // Check if directory should be ignored before entering it
            if (shouldIgnorePath(dir, rootPath, contexts, true)) {
                return FileVisitResult.SKIP_SUBTREE
            }

            // Initialize .gitignore for this directory if it exists and isn't ignored
            val gitignorePath = dir.resolve(".gitignore")
            if (fileHandler.exists(gitignorePath.pathString) && !shouldIgnorePath(gitignorePath, rootPath, contexts, false)) {
                initializeGitignore(dir, contexts)
            }
            return FileVisitResult.CONTINUE
        }

        /**
         * Called when a file is visited. If the file is not ignored by any applicable `.gitignore` rule,
         * its path is added to the [result] list.
         */
        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
            if (!shouldIgnorePath(file, rootPath, contexts, false)) {
                result.add(file.toString())
            }
            return FileVisitResult.CONTINUE
        }
    }

    /**
     * Companion object for [GitignoreAwareFileWalker].
     */
    companion object {
        /**
         * Default ignore rules applied to all walks, primarily to ignore the `.git/` directory.
         */
        private val DEFAULT_IGNORE_RULES = listOf(
            ".git/",  // Ignore .git directory by default
        )
    }

    /**
     * Walks the file tree starting from the given [directory] path.
     * It respects `.gitignore` files found in the directory hierarchy and a global `.bevelignore` file.
     * Files are processed by the [fileHandler] if they are not ignored and match the [languageSpec].
     *
     * @param directory The absolute path string of the root directory to start the walk from.
     * @return A list of path strings for all files that were visited, not ignored, and matched the file endings criteria.
     */
    override fun walk(directory: String): List<String> {
        val rootPath = Paths.get(directory)
        val results = mutableListOf<String>()
        val visitor = GitignoreFileVisitor(rootPath, results)
        
        // Initialize root context with both default rules and .bevelignore
        val rootContext = GitignoreContext(fileHandler.separator)

        // Add default ignore rules
        rootContext.addRules(DEFAULT_IGNORE_RULES.joinToString("\n"))
        
        // Add .bevelignore rules if file exists
        val bevelignorePath = BevelFilesPathResolver.bevelIgnoreFilePath(directory)
        if (fileHandler.exists(bevelignorePath.pathString)) {
            rootContext.addRules(fileHandler.readString(bevelignorePath.pathString))
        }
        
        // Only add the context if it has rules
        if (!rootContext.isEmpty()) {
            visitor.contexts[rootPath] = rootContext
        }
        
        Files.walkFileTree(rootPath, visitor)
        return results.filter { checkFileEndings(it) }
    }

    /**
     * Checks if a given file name matches the file ending criteria defined by the [languageSpec].
     *
     * @param fileName The name of the file to check.
     * @return `true` if the file name matches the criteria, `false` otherwise.
     */
    private fun checkFileEndings(fileName: String): Boolean {
        return languageSpec.checkFileEndings(fileName)
    }
    
    /**
     * Initializes or updates a [GitignoreContext] for a given directory by reading its `.gitignore` file.
     * If a `.gitignore` file exists in the specified directory, its rules are parsed and added to the context
     * associated with that directory path.
     *
     * @param directory The [Path] of the directory for which to initialize the gitignore context.
     * @param contexts A mutable map holding [GitignoreContext]s, keyed by their directory [Path].
     */
    private fun initializeGitignore(directory: Path, contexts: MutableMap<Path, GitignoreContext>) {
        val gitignorePath = directory.resolve(".gitignore")
        if (fileHandler.exists(gitignorePath.pathString)) {
            if(!contexts.containsKey(directory)) {
                contexts[directory] = GitignoreContext(fileHandler.separator)
            }
            contexts[directory]?.addRules(fileHandler.readString(gitignorePath.pathString))
        }
    }
    
    /**
     * Determines if a given path should be ignored by checking against the [GitignoreContext]s
     * of its parent directories, up to the root of the walk.
     * It also explicitly ignores paths containing ".bevel".
     *
     * @param path The [Path] to check.
     * @param rootPath The root [Path] of the current file walk.
     * @param contexts A map of directory paths to their corresponding [GitignoreContext]s.
     * @param isDirectory A boolean indicating if the path refers to a directory.
     * @return `true` if the path should be ignored, `false` otherwise.
     */
    private fun shouldIgnorePath(
        path: Path,
        rootPath: Path,
        contexts: Map<Path, GitignoreContext>,
        isDirectory: Boolean
    ): Boolean {
        if(path.pathString.contains(".bevel"))
            return true
        // Get relative path from root
        val relativePath = rootPath.relativize(path).toString().replace(fileHandler.separator, "/")
        
        // Check each parent directory's .gitignore rules
        var current = path.parent
        while (current != null && current.startsWith(rootPath)) {
            contexts[current]?.let { context ->
                if (context.shouldIgnore(relativePath, isDirectory)) {
                    return true
                }
            }
            current = current.parent
        }
        return false
    }
}