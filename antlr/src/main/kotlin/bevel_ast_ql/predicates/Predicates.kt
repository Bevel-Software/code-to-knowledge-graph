package software.bevel.code_to_knowledge_graph.antlr.bevel_ast_ql.predicates

import org.snt.inmemantlr.tree.ParseTreeRule
import software.bevel.code_to_knowledge_graph.antlr.AntlrGraphBuilder
import software.bevel.code_to_knowledge_graph.antlr.bevel_ast_ql.ArgumentResolver.Companion.resolveAndFormatArgument

/**
 * A predicate that checks for equality between a resolved value and a resolved target.
 * Both `value` and `target` are resolved using [resolveAndFormatArgument],
 * allowing them to be literals, capture names (e.g., "@captureName"), or simple field access on captures (e.g., "@captureName.text").
 *
 * @property value The first string argument to be resolved and compared.
 * @property target The second string argument to be resolved and compared against the first.
 */
class EqualsPredicate(
    /**
     * The first string argument to be resolved and compared.
     */
    val value: String,
    /**
     * The second string argument to be resolved and compared against the first.
     */
    val target: String
) : JsonPredicate, QueryMatchPredicate {
    /**
     * Companion object for [EqualsPredicate].
     */
    companion object {
        /**
         * The name identifier for this predicate type in JSON configurations.
         */
        const val NAME = "eq"
    }

    /**
     * Evaluates if the resolved `value` string is equal to the resolved `target` string.
     *
     * @param currentParseTreeRule The [ParseTreeRule] context for argument resolution.
     * @param captures A map of captured [ParseTreeRule]s for argument resolution.
     * @param graph An optional [AntlrGraphBuilder] for context-aware argument resolution.
     * @return `true` if the resolved strings are equal, `false` otherwise.
     */
    override fun evaluate(currentParseTreeRule: ParseTreeRule, captures: Map<String, ParseTreeRule>, graph: AntlrGraphBuilder?): Boolean {
        val resolvedValue = resolveAndFormatArgument(currentParseTreeRule, value, captures, graph)
        val resolvedTarget = resolveAndFormatArgument(currentParseTreeRule, target, captures, graph)
        return resolvedValue == resolvedTarget
    }

    /**
     * Returns this instance as it already implements [QueryMatchPredicate].
     * @return This [EqualsPredicate] instance.
     */
    override fun toPredicate() = this
}

/**
 * Represents the logical negation of an [EqualsPredicate].
 * It checks if a resolved value is *not* equal to a resolved target.
 *
 * @param value The first string argument for the underlying [EqualsPredicate].
 * @param target The second string argument for the underlying [EqualsPredicate].
 */
class NotEqualsPredicate(
    /**
     * The first string argument for the underlying [EqualsPredicate].
     */
    val value: String,
    /**
     * The second string argument for the underlying [EqualsPredicate].
     */
    val target: String
) : NotPredicate<EqualsPredicate>(EqualsPredicate(value, target)) {
    /**
     * Companion object for [NotEqualsPredicate].
     */
    companion object {
        /**
         * The name identifier for this predicate type in JSON configurations.
         */
        const val NAME = "not-eq"
    }
}

/**
 * A predicate that checks if a resolved `value` string ends with a resolved `suffix` string.
 * Both `value` and `suffix` are resolved using [resolveAndFormatArgument].
 *
 * @property value The string to be checked.
 * @property suffix The expected suffix string.
 */
class EndsWithPredicate(
    /**
     * The string to be checked.
     */
    val value: String,
    /**
     * The expected suffix string.
     */
    val suffix: String
) : JsonPredicate, QueryMatchPredicate {
    /**
     * Companion object for [EndsWithPredicate].
     */
    companion object {
        /**
         * The name identifier for this predicate type in JSON configurations.
         */
        const val NAME = "ends-with"
    }

    /**
     * Evaluates if the resolved `value` string ends with the resolved `suffix` string.
     *
     * @param currentParseTreeRule The [ParseTreeRule] context for argument resolution.
     * @param captures A map of captured [ParseTreeRule]s for argument resolution.
     * @param graph An optional [AntlrGraphBuilder] for context-aware argument resolution.
     * @return `true` if the resolved `value` ends with the resolved `suffix`, `false` otherwise.
     */
    override fun evaluate(currentParseTreeRule: ParseTreeRule, captures: Map<String, ParseTreeRule>, graph: AntlrGraphBuilder?): Boolean {
        val resolvedValue = resolveAndFormatArgument(currentParseTreeRule, value, captures, graph)
        val resolvedSuffix = resolveAndFormatArgument(currentParseTreeRule, suffix, captures, graph)
        return resolvedValue.endsWith(resolvedSuffix)
    }

    /**
     * Returns this instance as it already implements [QueryMatchPredicate].
     * @return This [EndsWithPredicate] instance.
     */
    override fun toPredicate() = this
}

/**
 * Represents the logical negation of an [EndsWithPredicate].
 * It checks if a resolved `value` string does *not* end with a resolved `suffix` string.
 *
 * @param value The string argument for the underlying [EndsWithPredicate].
 * @param suffix The suffix string argument for the underlying [EndsWithPredicate].
 */
class NotEndsWithPredicate(
    /**
     * The string argument for the underlying [EndsWithPredicate].
     */
    value: String,
    /**
     * The suffix string argument for the underlying [EndsWithPredicate].
     */
    suffix: String
) : NotPredicate<EndsWithPredicate>(EndsWithPredicate(value, suffix)) {
    /**
     * Companion object for [NotEndsWithPredicate].
     */
    companion object {
        /**
         * The name identifier for this predicate type in JSON configurations.
         */
        const val NAME = "not-ends-with"
    }
}

/**
 * A predicate that checks if a resolved `value` string matches a given regular expression `pattern`.
 * Both `value` and `pattern` are resolved using [resolveAndFormatArgument].
 *
 * @property value The string to be matched against the pattern.
 * @property pattern The regular expression string to match against the value.
 */
class MatchPredicate(
    /**
     * The string to be matched against the pattern.
     */
    val value: String,
    /**
     * The regular expression string to match against the value.
     */
    val pattern: String
) : JsonPredicate, QueryMatchPredicate {
    /**
     * Companion object for [MatchPredicate].
     */
    companion object {
        /**
         * The name identifier for this predicate type in JSON configurations.
         */
        const val NAME = "match"
    }

    /**
     * Evaluates if the resolved `value` string matches the resolved regular expression `pattern`.
     *
     * @param currentParseTreeRule The [ParseTreeRule] context for argument resolution.
     * @param captures A map of captured [ParseTreeRule]s for argument resolution.
     * @param graph An optional [AntlrGraphBuilder] for context-aware argument resolution.
     * @return `true` if the resolved `value` matches the resolved `pattern`, `false` otherwise.
     */
    override fun evaluate(currentParseTreeRule: ParseTreeRule, captures: Map<String, ParseTreeRule>, graph: AntlrGraphBuilder?): Boolean {
        val resolvedValue = resolveAndFormatArgument(currentParseTreeRule, value, captures, graph)
        val resolvedPattern = resolveAndFormatArgument(currentParseTreeRule, pattern, captures, graph)
        return resolvedValue.matches(resolvedPattern.toRegex())
    }

    /**
     * Returns this instance as it already implements [QueryMatchPredicate].
     * @return This [MatchPredicate] instance.
     */
    override fun toPredicate() = this
}

/**
 * Represents the logical negation of a [MatchPredicate].
 * It checks if a resolved `value` string does *not* match a given regular expression `pattern`.
 *
 * @param value The string argument for the underlying [MatchPredicate].
 * @param pattern The regular expression string argument for the underlying [MatchPredicate].
 */
class NotMatchPredicate(
    /**
     * The string argument for the underlying [MatchPredicate].
     */
    val value: String,
    /**
     * The regular expression string argument for the underlying [MatchPredicate].
     */
    val pattern: String
) : NotPredicate<MatchPredicate>(MatchPredicate(value, pattern)) {
    /**
     * Companion object for [NotMatchPredicate].
     */
    companion object {
        /**
         * The name identifier for this predicate type in JSON configurations.
         */
        const val NAME = "not-match"
    }
}

/**
 * A predicate that checks if a resolved `value` string is equal to any of the resolved strings in the `options` list.
 * The `value` and each string in `options` are resolved using [resolveAndFormatArgument].
 *
 * @property value The string to be checked against the list of options.
 * @property options A list of string options. The predicate is true if the resolved `value` matches any resolved option.
 */
class AnyOfPredicate(
    /**
     * The string to be checked against the list of options.
     */
    val value: String,
    /**
     * A list of string options. The predicate is true if the resolved `value` matches any resolved option.
     */
    val options: List<String>
) : JsonPredicate, QueryMatchPredicate {
    /**
     * Companion object for [AnyOfPredicate].
     */
    companion object {
        /**
         * The name identifier for this predicate type in JSON configurations.
         */
        const val NAME = "any-of"
    }

    /**
     * Evaluates if the resolved `value` string is equal to any of the resolved strings in the `options` list.
     *
     * @param currentParseTreeRule The [ParseTreeRule] context for argument resolution.
     * @param captures A map of captured [ParseTreeRule]s for argument resolution.
     * @param graph An optional [AntlrGraphBuilder] for context-aware argument resolution.
     * @return `true` if the resolved `value` matches at least one resolved option, `false` otherwise.
     */
    override fun evaluate(currentParseTreeRule: ParseTreeRule, captures: Map<String, ParseTreeRule>, graph: AntlrGraphBuilder?): Boolean {
        val resolvedValue = resolveAndFormatArgument(currentParseTreeRule, value, captures, graph)
        return options.any { option -> 
            resolveAndFormatArgument(currentParseTreeRule, option, captures, graph) == resolvedValue
        }
    }

    /**
     * Returns this instance as it already implements [QueryMatchPredicate].
     * @return This [AnyOfPredicate] instance.
     */
    override fun toPredicate() = this
}

/**
 * A predicate that checks if a specified `capture` name exists in the current map of captures.
 * The `capture` name can be prefixed with '@' and suffixed with '?', which are handled during evaluation.
 * If dot notation is used (e.g., "@captureName.field"), only the base capture name ("captureName") is checked for existence.
 *
 * @property capture The name of the capture to check for existence.
 */
class ExistsPredicate(
    /**
     * The name of the capture to check for existence.
     */
    val capture: String
) : JsonPredicate, QueryMatchPredicate {
    /**
     * Companion object for [ExistsPredicate].
     */
    companion object {
        /**
         * The name identifier for this predicate type in JSON configurations.
         */
        const val NAME = "exists"
    }

    /**
     * Evaluates if the given `capture` name (after processing prefixes/suffixes and dot notation) exists as a key in the `captures` map.
     *
     * @param currentParseTreeRule Unused in this predicate, but part of the interface.
     * @param captures A map of captured [ParseTreeRule]s to check against.
     * @param graph Unused in this predicate, but part of the interface.
     * @return `true` if the processed capture name is a key in the `captures` map, `false` otherwise.
     */
    override fun evaluate(currentParseTreeRule: ParseTreeRule, captures: Map<String, ParseTreeRule>, graph: AntlrGraphBuilder?): Boolean {
        var captureName = capture
        if (captureName.startsWith("@")) {
            captureName = captureName.substring(1)
            captureName = captureName.split('.').first()
            if(captureName.endsWith('?'))
                captureName = captureName.substring(0, captureName.length - 1)
        }
        return captures.containsKey(captureName)
    }

    /**
     * Returns this instance as it already implements [QueryMatchPredicate].
     * @return This [ExistsPredicate] instance.
     */
    override fun toPredicate() = this
}

/**
 * Represents the logical negation of an [ExistsPredicate].
 * It checks if a specified `capture` name does *not* exist in the current map of captures.
 *
 * @param capture The name of the capture to check for non-existence, passed to the underlying [ExistsPredicate].
 */
class NotExistsPredicate(
    /**
     * The name of the capture to check for non-existence, passed to the underlying [ExistsPredicate].
     */
    val capture: String
): NotPredicate<ExistsPredicate>(ExistsPredicate(capture)) {
    /**
     * Companion object for [NotExistsPredicate].
     */
    companion object {
        /**
         * The name identifier for this predicate type in JSON configurations.
         */
        const val NAME = "not-exists"
    }
}

/**
 * A predicate that checks if a resolved `value` string contains a resolved `substring`.
 * Both `value` and `substring` are resolved using [resolveAndFormatArgument].
 *
 * @property value The string to be checked for containing the substring.
 * @property substring The substring to look for within the value.
 */
class ContainsPredicate(
    /**
     * The string to be checked for containing the substring.
     */
    val value: String,
    /**
     * The substring to look for within the value.
     */
    val substring: String
) : JsonPredicate, QueryMatchPredicate {
    /**
     * Companion object for [ContainsPredicate].
     */
    companion object {
        /**
         * The name identifier for this predicate type in JSON configurations.
         */
        const val NAME = "contains"
    }

    /**
     * Evaluates if the resolved `value` string contains the resolved `substring`.
     *
     * @param currentParseTreeRule The [ParseTreeRule] context for argument resolution.
     * @param captures A map of captured [ParseTreeRule]s for argument resolution.
     * @param graph An optional [AntlrGraphBuilder] for context-aware argument resolution.
     * @return `true` if the resolved `value` contains the resolved `substring`, `false` otherwise.
     */
    override fun evaluate(currentParseTreeRule: ParseTreeRule, captures: Map<String, ParseTreeRule>, graph: AntlrGraphBuilder?): Boolean {
        val resolvedValue = resolveAndFormatArgument(currentParseTreeRule, value, captures, graph)
        val resolvedSubstring = resolveAndFormatArgument(currentParseTreeRule, substring, captures, graph)
        return resolvedValue.contains(resolvedSubstring)
    }

    /**
     * Returns this instance as it already implements [QueryMatchPredicate].
     * @return This [ContainsPredicate] instance.
     */
    override fun toPredicate() = this
}

/**
 * Represents the logical negation of a [ContainsPredicate].
 * It checks if a resolved `value` string does *not* contain a resolved `substring`.
 *
 * @param value The string argument for the underlying [ContainsPredicate].
 * @param substring The substring argument for the underlying [ContainsPredicate].
 */
class NotContainsPredicate(
    /**
     * The string argument for the underlying [ContainsPredicate].
     */
    value: String,
    /**
     * The substring argument for the underlying [ContainsPredicate].
     */
    substring: String
) : NotPredicate<ContainsPredicate>(ContainsPredicate(value, substring)) {
    /**
     * Companion object for [NotContainsPredicate].
     */
    companion object {
        /**
         * The name identifier for this predicate type in JSON configurations.
         */
        const val NAME = "not-contains"
    }
}

/**
 * A generic predicate that negates the result of an inner [JsonPredicate].
 * This class serves as a base for specific "Not" predicate types (e.g., [NotEqualsPredicate]).
 *
 * @param T The type of the [JsonPredicate] being negated.
 * @property predicate The inner [JsonPredicate] whose evaluation result will be inverted.
 */
open class NotPredicate<T: JsonPredicate>(
    /**
     * The inner [JsonPredicate] whose evaluation result will be inverted.
     */
    private val predicate: T
) : JsonPredicate, QueryMatchPredicate {
    /**
     * Companion object for [NotPredicate].
     */
    companion object {
        /**
         * General name identifier for a negation operation, used by implementing classes for their specific "not-*" names.
         */
        const val NAME = "not"
    }

    /**
     * Evaluates the inner [predicate] and returns the logical negation of its result.
     *
     * @param currentParseTreeRule The [ParseTreeRule] context passed to the inner predicate.
     * @param captures A map of captured [ParseTreeRule]s passed to the inner predicate.
     * @param graph An optional [AntlrGraphBuilder] passed to the inner predicate.
     * @return `true` if the inner predicate evaluates to `false`, and `false` if it evaluates to `true`.
     */
    override fun evaluate(currentParseTreeRule: ParseTreeRule, captures: Map<String, ParseTreeRule>, graph: AntlrGraphBuilder?): Boolean {
        return !predicate.toPredicate().evaluate(currentParseTreeRule, captures, graph)
    }

    /**
     * Returns this instance as it already implements [QueryMatchPredicate].
     * @return This [NotPredicate] instance.
     */
    override fun toPredicate() = this
}
