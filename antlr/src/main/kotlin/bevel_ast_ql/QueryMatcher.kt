package software.bevel.code_to_knowledge_graph.antlr.bevel_ast_ql

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.snt.inmemantlr.tree.ParseTreeRule
import software.bevel.code_to_knowledge_graph.antlr.bevel_ast_ql.converters.QueryMatchConverter
import software.bevel.code_to_knowledge_graph.antlr.bevel_ast_ql.exceptions.ParentNodeCaptureNotFound
import software.bevel.code_to_knowledge_graph.antlr.bevel_ast_ql.predicates.QueryMatchPredicate

/**
 * Represents a node or a structure within a query pattern used for matching against parse trees.
 * This sealed class encompasses different types of patterns and metadata associated with them.
 */
sealed class QueryNode {
    /**
     * Holds a [Pattern] along with its associated [predicates] and [converters].
     *
     * @property pattern The core [Pattern] to be matched.
     * @property predicates A list of [QueryMatchPredicate]s that must evaluate to true for the match to be valid.
     * @property converters A list of [QueryMatchConverter]s to be applied if the pattern matches and predicates pass.
     */
    data class PatternMetadata(
        val pattern: Pattern,
        val predicates: List<QueryMatchPredicate>,
        val converters: List<QueryMatchConverter>,
    )

    /**
     * Defines the structure of a pattern that can be matched against parse tree nodes.
     * This can be a specific node type, a set of alternatives, or other structural constraints.
     */
    sealed class Pattern {

        /**
         * Represents a pattern that matches a specific parse tree rule (node type).
         *
         * @property rule The name of the parse tree rule to match (e.g., "classDeclaration").
         *              Special values: "." matches any single rule, ".+" matches one or more rules (TODO: full .+ handling).
         * @property captures A list of names to capture the matched [ParseTreeRule] under.
         * @property children A list of child [Pattern]s that must also match under this node.
         * @property notChildren A set of rule names that must *not* be present as direct children of the matched node.
         * @property optional If true, this pattern is optional for the overall match to succeed.
         */
        data class NodePattern(
            val rule: String,
            val captures: List<String>,
            val children: List<Pattern>,
            val notChildren: Set<String>,
            val optional: Boolean
        ) : Pattern()

        /**
         * Represents a pattern where one of several alternative [Pattern]s must match.
         *
         * @property alternatives A list of [Pattern]s, one of which must match.
         * @property optional If true, this entire alternatives pattern is optional for the overall match to succeed.
         */
        data class AlternativesPattern(
            val alternatives: List<Pattern>,
            val optional: Boolean
        ) : Pattern()
    }
}

/**
 * Represents a successful match of a [QueryNode.PatternMetadata] against a [ParseTreeRule].
 *
 * @property startingParseTreeRule The [ParseTreeRule] where the match started.
 * @property patternData The [QueryNode.PatternMetadata] that was matched.
 * @property captures A map where keys are capture names (defined in the pattern or implicitly) and
 *                    values are the corresponding [ParseTreeRule]s captured during the match.
 */
data class QueryMatch(
    val startingParseTreeRule: ParseTreeRule,
    val patternData: QueryNode.PatternMetadata,
    val captures: Map<String, ParseTreeRule>
)

/**
 * Matches query patterns, defined by [QueryNode.PatternMetadata], against ANTLR [ParseTreeRule]s.
 * It can find individual matches or construct a tree of matches reflecting the parse tree structure.
 *
 * @param logger An SLF4J [Logger] instance for logging matching activities and potential issues.
 */
class QueryMatcher(
    private val logger: Logger = LoggerFactory.getLogger(QueryMatcher::class.java)
) {
    /**
     * Represents a node in a tree of [QueryMatch]es.
     * This structure helps organize matches hierarchically, similar to the parse tree itself.
     *
     * @property match The [QueryMatch] associated with this tree node.
     * @property children A list of child [QueryMatchTreeNode]s, representing matches found under this node's [ParseTreeRule].
     * @property context A map for carrying contextual information through the match tree (currently not fully utilized in `findMatchesAsTree`).
     */
    data class QueryMatchTreeNode(
        val match: QueryMatch,
        val children: MutableList<QueryMatchTreeNode>,
        val context: Map<String, ParseTreeRule> = mutableMapOf()
    )

    /**
     * Attempts to find a match for the given [pattern] starting at the specified [parseTreeRule].
     * This function is the core of the matching logic for a single pattern at a specific node.
     *
     * @param parseTreeRule The [ParseTreeRule] to attempt to match against.
     * @param pattern The [QueryNode.Pattern] to match.
     * @return A list of capture maps. Each map represents a successful match and contains the names
     *         and corresponding [ParseTreeRule]s that were captured. Returns an empty list if no match is found.
     */
    private fun findMatchStartingAt(parseTreeRule: ParseTreeRule, pattern: QueryNode.Pattern): List<Map<String, ParseTreeRule>> {
        when (pattern) {
            is QueryNode.Pattern.NodePattern -> {
                if (pattern.rule != "." && pattern.rule != ".+" && pattern.rule != parseTreeRule.rule) {
                    return emptyList()
                }

                if (!checkNotChildren(parseTreeRule, pattern.notChildren)) {
                    return emptyList()
                }

                val captures = mutableMapOf<String, ParseTreeRule>()
                if(pattern.captures.isEmpty()) {
                    captures[pattern.rule] = parseTreeRule
                }
                pattern.captures.forEach { captureName ->
                    captures[captureName] = parseTreeRule
                }

                val extraCaptures = if(pattern.rule == ".+") {
                    parseTreeRule.children.flatMap { findMatchStartingAt(it, pattern) }
                } else {
                    emptyList()
                }

                // Match children and get all possible combinations
                val childMatches = matchChildren(parseTreeRule, pattern.children, captures)
                if (childMatches.isEmpty()) {
                    return extraCaptures
                }

                return childMatches.map { captureMap ->
                    captureMap
                } + extraCaptures
            }
            is QueryNode.Pattern.AlternativesPattern -> {
                return pattern.alternatives.flatMap { alternative ->
                    findMatchStartingAt(parseTreeRule, alternative)
                }
            }
        }
    }

    /**
     * Recursively matches a list of [childPatterns] against the children of a given [parseTreeRule].
     * It attempts to find combinations of child matches that satisfy all patterns in [childPatterns].
     *
     * @param parseTreeRule The parent [ParseTreeRule] whose children are being matched.
     * @param childPatterns The list of [QueryNode.Pattern]s to match against the children.
     * @param captures The current map of captures accumulated from parent matches.
     * @param startIndex The index within [parseTreeRule].children to start matching from.
     * @return A list of capture maps. Each map represents a successful combination of child matches,
     *         extending the initial [captures]. Returns an empty list if not all patterns can be matched.
     */
    private fun matchChildren(
        parseTreeRule: ParseTreeRule,
        childPatterns: List<QueryNode.Pattern>,
        captures: Map<String, ParseTreeRule>,
        startIndex: Int = 0
    ): List<Map<String, ParseTreeRule>> {
        if (childPatterns.isEmpty()) {
            return listOf(captures.toMap())
        }

        val results = mutableListOf<Map<String, ParseTreeRule>>()
        val currentPattern = childPatterns[0]
        val remainingPatterns = childPatterns.drop(1)

        for (i in startIndex until parseTreeRule.children.size) {
            val child = parseTreeRule.children[i]
            val childMatchCaptures = findMatchStartingAt(child, currentPattern)
            childMatchCaptures.forEach { childMatchCapture ->
                val newCaptures = captures.toMutableMap()
                childMatchCapture.forEach {
                    if(!newCaptures.containsKey(it.key) || it.key != it.value.sourceCode)
                        newCaptures[it.key] = it.value
                }
                
                if (remainingPatterns.isEmpty()) {
                    results.add(newCaptures)
                } else {
                    val subResults = matchChildren(parseTreeRule, remainingPatterns, newCaptures, i + 1)
                    results.addAll(subResults)
                }
            }
        }

        if(results.isEmpty()
            && (currentPattern is QueryNode.Pattern.NodePattern && currentPattern.optional
                || currentPattern is QueryNode.Pattern.AlternativesPattern && currentPattern.optional)) {
            val subResults = matchChildren(parseTreeRule, remainingPatterns, captures, startIndex)
            results.addAll(subResults)
        }

        return results
    }

    /**
     * Checks if a [ParseTreeRule] does *not* contain any children specified in the [notChildren] set.
     *
     * @param parseTreeRule The [ParseTreeRule] to check.
     * @param notChildren A set of rule names that should not be present as direct children.
     * @return True if none of the [notChildren] are found among the direct children of [parseTreeRule], false otherwise.
     */
    private fun checkNotChildren(parseTreeRule: ParseTreeRule, notChildren: Set<String>): Boolean {
        return parseTreeRule.children.none { child -> notChildren.contains(child.rule) }
    }

    /**
     * Finds all occurrences of a [pattern] within the given [parseTreeRule] and its descendants.
     * It tries to match the pattern at the current node and then recursively in its children.
     *
     * @param parseTreeRule The [ParseTreeRule] to search within.
     * @param pattern The [QueryNode.PatternMetadata] to find matches for.
     * @return A list of all [QueryMatch]es found.
     */
    private fun findMatches(parseTreeRule: ParseTreeRule, pattern: QueryNode.PatternMetadata): List<QueryMatch> {
        val matches = mutableListOf<QueryMatch>()
        
        // Try matching at current node
        findMatchStartingAt(parseTreeRule, pattern.pattern).forEach{
            matches.add(QueryMatch(parseTreeRule, pattern, it))
        }
        
        // Try matching at child nodes
        parseTreeRule.children.forEach { child ->
            matches.addAll(findMatches(child, pattern))
        }
        
        return matches
    }

    /**
     * Finds all matches for a list of [patterns] within a given [parseTreeRule] and its descendants,
     * and organizes these matches into a tree structure ([QueryMatchTreeNode]).
     * Predicates associated with patterns are evaluated during this process.
     *
     * @param parseTreeRule The root [ParseTreeRule] to start matching from.
     * @param patterns A list of [QueryNode.PatternMetadata] to match against the tree.
     * @return A list of [QueryMatchTreeNode]s representing the root(s) of the match tree(s).
     *         The tree structure reflects the hierarchy of successful, predicate-passing matches.
     */
    fun findMatchesAsTree(parseTreeRule: ParseTreeRule, patterns: List<QueryNode.PatternMetadata>): List<QueryMatchTreeNode> {
        val allMatches = patterns.flatMap { findMatches(parseTreeRule, it) }

        val nodeToMatch = allMatches.groupBy { it.startingParseTreeRule }

        fun traverseTree(node: ParseTreeRule, parentMatchTreeNode: QueryMatchTreeNode) {
            val matches = nodeToMatch[node]
            var lastParseTreeNode = parentMatchTreeNode
            if (matches != null) {
                for (match in matches) {
                    val newNode = QueryMatchTreeNode(
                        match,
                        mutableListOf(),
                        /*lastParseTreeNode.context
                                + match.captures.filterKeys { it.startsWith("context_alias.") }
                                + mapOf("parent" to lastParseTreeNode.match.startingParseTreeNode)*/
                    )
                    if(match.patternData.pattern is QueryNode.Pattern.NodePattern && !match.patternData.predicates.all {
                        try {
                            it.evaluate(match.startingParseTreeRule, match.captures)
                        } catch (ex: ParentNodeCaptureNotFound) {
                            logger.info("Could not evaluate predicate during matching, passing it through: $match")
                            true
                        }
                    }) {
                        continue
                    }
                    lastParseTreeNode.children.add(newNode)
                    lastParseTreeNode = newNode
                }
            }
            node.children.forEach {
                traverseTree(it, lastParseTreeNode)
            }
        }
        val rootMatchTreeNode = QueryMatchTreeNode(QueryMatch(parseTreeRule, patterns[0], emptyMap()), mutableListOf())
        traverseTree(parseTreeRule, rootMatchTreeNode)

        return rootMatchTreeNode.children
    }
}
