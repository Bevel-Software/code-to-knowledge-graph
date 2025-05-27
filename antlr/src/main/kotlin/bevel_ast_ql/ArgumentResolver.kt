package software.bevel.code_to_knowledge_graph.antlr.bevel_ast_ql

import org.snt.inmemantlr.tree.ParseTreeRule
import software.bevel.graph_domain.graph.builder.NodeBuilder
import software.bevel.code_to_knowledge_graph.antlr.AntlrGraphBuilder
import software.bevel.code_to_knowledge_graph.antlr.bevel_ast_ql.resolvers.*

/**
 * A utility class responsible for resolving and formatting arguments or expressions,
 * typically used in the context of a query language or template system for ANTLR parse trees.
 * It handles various types of captures (e.g., ParseTreeRule, NodeBuilder),
 * dot notation for property access and function calls, and string manipulations.
 */
class ArgumentResolver {
    /**
     * Companion object for ArgumentResolver, providing static methods and properties for resolving and formatting arguments.
     */
    companion object {
        /**
         * Map of custom handlers for resolving specific named captures as [ParseTreeRule] instances.
         * Keys are capture names (e.g., "parentNode"), and values are [CustomParseTreeCaptureHandler] implementations.
         */
        private val customParseTreeCaptures = mapOf<String, CustomParseTreeCaptureHandler>(
            "parentNode" to ParentNodeHandler(),
            "current" to CurrentParseTreeNodeHandler(),
        )

        /**
         * Map of custom handlers for resolving specific named captures as [NodeBuilder] instances.
         * Keys are capture names (e.g., "parentClassNode"), and values are [CustomNodeBuilderCaptureHandler] implementations.
         */
        private val customNodeBuilderCaptures = mapOf<String, CustomNodeBuilderCaptureHandler>(
            "parentClassNode" to ParentClassNodeHandler(),
            "parentSuperClassNode" to ParentSuperClassNodeHandler()
        )

        /**
         * Map of resolvers for dot-call operations on [String] instances.
         * Keys are function names (e.g., "splitDot"), and values are [DotCallResolver] implementations for strings.
         */
        private val stringDotCallResolvers = mapOf<String, DotCallResolver<String, Any?>>(
            "splitDot" to DotCallResolver<String, Any> { str, _, _ -> str.split(".") },
            "hashCode" to DotCallResolver<String, Any> { str, _, _ -> str.hashCode() },
            "getUntil" to DotCallResolver<String, Any> { str, args, _ -> str.substring(0, str.indexOf(args[0]) + args[0].length) },
        )

        /**
         * Map of resolvers for dot-call operations on [List] instances.
         * Keys are function names (e.g., "last"), and values are [DotCallResolver] implementations for lists.
         */
        private val listDotCallResolvers = mapOf<String, DotCallResolver<List<*>, Any?>>(
            "last" to DotCallResolver<List<*>, Any?> { list, _, _ -> list.last() },
        )

        /**
         * Map of resolvers for dot-call operations on [ParseTreeRule] instances.
         * Keys are function names (e.g., "node" to get the associated NodeBuilder), and values are [DotCallResolver] implementations.
         */
        private val parseTreeDotCallResolvers = mapOf<String, DotCallResolver<ParseTreeRule, Any?>>(
            "node" to DotCallResolver<ParseTreeRule, Any?> { rule, _, graph -> graph?.nodesInParseTree?.get(rule) },
        )

        /**
         * Map of resolvers for dot-call operations on [NodeBuilder] instances.
         * Currently empty, but can be extended with custom resolvers.
         */
        private val nodeBuilderDotCallResolvers = mapOf<String, DotCallResolver<NodeBuilder, Any?>>(

        )

        /**
         * Resolves a full argument string, potentially composed of multiple parts separated by '+'.
         * Each part is resolved, and the results are concatenated into a single string.
         * Handles different resolved types (String, List, ParseTreeRule, NodeBuilder) by converting them to string representations.
         *
         * @param currentParseTreeRule The current [ParseTreeRule] context for resolution.
         * @param arg The argument string to resolve (e.g., "@capture.property + 'literal'").
         * @param captures A map of named captures to their corresponding [ParseTreeRule] instances.
         * @param graph An optional [AntlrGraphBuilder] instance for context, e.g., to look up nodes.
         * @return The resolved and formatted argument as a single string, with newlines removed.
         */
        fun resolveAndFormatArgument(currentParseTreeRule: ParseTreeRule, arg: String, captures: Map<String, ParseTreeRule>, graph: AntlrGraphBuilder? = null): String {
            val parts = splitExpression(arg, '+')

            return removeNewlines(parts.mapNotNull { part ->
                when(val result = resolvePartOfArgument(part, currentParseTreeRule, captures, graph)) {
                    is String -> result
                    is List<*> -> result.joinToString(".")
                    is ParseTreeRule -> result.sourceCode
                    is NodeBuilder -> result.id
                    else -> result?.toString() ?: ""
                }
            }.joinToString(""))
        }

        /**
         * Resolves a single part of an argument string.
         * If the part starts with '@', it's treated as a capture name, potentially with dot notation for property/method access.
         * Otherwise, it's treated as a literal string (stripping quotes if present).
         *
         * @param part The part of the argument string to resolve.
         * @param currentParseTreeRule The current [ParseTreeRule] context.
         * @param captures A map of named captures.
         * @param graph An optional [AntlrGraphBuilder] for context.
         * @return The resolved value (can be String, List, ParseTreeRule, NodeBuilder, or other types from dot-call resolvers).
         */
        fun resolvePartOfArgument(part: String, currentParseTreeRule: ParseTreeRule, captures: Map<String, ParseTreeRule>, graph: AntlrGraphBuilder? = null): Any? {
            if (!part.startsWith("@")) {
                return if (part.startsWith("\"") && part.endsWith("\"") || part.startsWith("'") && part.endsWith("'")) {
                    part.substring(1, part.length - 1)
                } else {
                    part
                }
            }
            val captureName = part.substring(1)
            val dotNotation = splitExpression(captureName, '.')
            if(dotNotation.size == 1) {
                return resolveCaptureAsParseTree(currentParseTreeRule, captureName, captures, graph)?.sourceCode
            }
            val startIndex: Int
            var value: Any
            if(dotNotation[1] != "node") {
                value = resolveCaptureAsParseTree(currentParseTreeRule, dotNotation[0], captures, graph)
                    ?: return null
                startIndex = 1
            } else {
                value = resolveCaptureAsNodeBuilder(currentParseTreeRule, dotNotation[0], captures, graph)
                    ?: return null
                startIndex = 2
            }
            for(i in startIndex until dotNotation.size) {
                value = resolveDotCall(value, dotNotation[i], currentParseTreeRule, captures, graph)
            }
            return value
        }

        /**
         * Resolves a dot-call operation on a given value.
         * Parses the function name and arguments from the [dotCallFunction] string.
         *
         * @param currentValue The object on which the dot-call is performed.
         * @param dotCallFunction The string representing the function call (e.g., "functionName(arg1,arg2)").
         * @param currentParseTreeRule Current parse tree context.
         * @param captures Map of named captures.
         * @param graph Optional graph context.
         * @return The result of the dot-call operation.
         */
        private fun resolveDotCall(currentValue: Any, dotCallFunction: String, currentParseTreeRule: ParseTreeRule, captures: Map<String, ParseTreeRule>, graph: AntlrGraphBuilder? = null): Any {
            val leftParan = dotCallFunction.indexOf('(')
            val rightParan = dotCallFunction.lastIndexOf(')')
            val functionName: String
            val args: List<String>
            if(leftParan != -1 && rightParan != -1 && leftParan < rightParan) {
                functionName = dotCallFunction.substring(0, leftParan)
                val allArgs = dotCallFunction.substring(leftParan + 1, rightParan)
                val argParts = splitExpression(allArgs, ',')
                args = argParts.map { resolveAndFormatArgument(currentParseTreeRule, it, captures, graph) }
            } else {
                functionName = dotCallFunction
                args = emptyList()
            }
            return resolveDotCall(currentValue, functionName, args, currentParseTreeRule, captures, graph)
        }

        /**
         * Overloaded version of [resolveDotCall] that takes pre-parsed function name and arguments.
         * It dispatches to the appropriate resolver based on the type of [currentValue]
         * (String, List, ParseTreeRule, NodeBuilder, or fallback to String representation).
         *
         * @param currentValue The object on which the dot-call is performed.
         * @param dotCallFunction The name of the function to call.
         * @param args A list of resolved argument strings for the function call.
         * @param currentParseTreeRule Current parse tree context.
         * @param captures Map of named captures.
         * @param graph Optional graph context.
         * @return The result of the dot-call operation, or [currentValue] if no resolver is found.
         */
        private fun resolveDotCall(currentValue: Any, dotCallFunction: String, args: List<String>, currentParseTreeRule: ParseTreeRule, captures: Map<String, ParseTreeRule>, graph: AntlrGraphBuilder? = null): Any {
            return when(currentValue) {
                is String -> stringDotCallResolvers[dotCallFunction]?.resolveDotCall(currentValue, args, graph)
                    ?: currentValue
                is List<*> -> listDotCallResolvers[dotCallFunction]?.resolveDotCall(currentValue, args, graph)
                    ?: resolveDotCall(currentValue.joinToString("."), dotCallFunction, args, currentParseTreeRule, captures, graph)
                is ParseTreeRule -> parseTreeDotCallResolvers[dotCallFunction]?.resolveDotCall(currentValue, args, graph)
                    ?: resolveDotCall(currentValue.sourceCode, dotCallFunction, args, currentParseTreeRule, captures, graph)
                is NodeBuilder -> nodeBuilderDotCallResolvers[dotCallFunction]?.resolveDotCall(currentValue, args, graph)
                    ?: resolveDotCall(currentValue.id, dotCallFunction, args, currentParseTreeRule, captures, graph)
                else -> resolveDotCall(currentValue.toString(), dotCallFunction, args, currentParseTreeRule, captures, graph)
            }
        }

        /**
         * Resolves a named capture to a [ParseTreeRule].
         * It first checks custom handlers in [customParseTreeCaptures].
         * If not found, it looks up the name in the provided [captures] map.
         * Handles optional captures (ending with '?').
         *
         * @param currentParseTreeRule The current [ParseTreeRule] context.
         * @param captureName The name of the capture to resolve.
         * @param captures A map of named captures.
         * @param graph An optional [AntlrGraphBuilder] for context.
         * @return The resolved [ParseTreeRule], or null if an optional capture is not found.
         * @throws IllegalArgumentException if a non-optional capture is not found.
         */
        fun resolveCaptureAsParseTree(currentParseTreeRule: ParseTreeRule, captureName: String, captures: Map<String, ParseTreeRule>, graph: AntlrGraphBuilder?): ParseTreeRule? {
            val name = if(captureName.endsWith('?')) captureName.substring(0, captureName.length - 1) else captureName
            if(customParseTreeCaptures.containsKey(name)) {
                return customParseTreeCaptures[name]?.resolveCapture(currentParseTreeRule, captureName, captures, graph)
            }
            if(captures[name] == null && !captureName.endsWith('?'))
                throw IllegalArgumentException("No capture found for name: $name")
            return captures[name]
        }

        /**
         * Resolves a named capture to a [NodeBuilder].
         * It checks custom handlers in [customNodeBuilderCaptures] and [customParseTreeCaptures] (then looking up the node in the graph).
         * If not found through custom handlers, it resolves the capture as a [ParseTreeRule] using [resolveCaptureAsParseTree]
         * and then attempts to find the corresponding [NodeBuilder] in the [graph]'s `nodesInParseTree` map.
         * Handles optional captures (ending with '?').
         *
         * @param currentParseTreeRule The current [ParseTreeRule] context.
         * @param captureName The name of the capture to resolve.
         * @param captures A map of named captures.
         * @param graph An optional [AntlrGraphBuilder] for context, necessary for looking up nodes.
         * @return The resolved [NodeBuilder], or null if not found (especially for optional captures or if graph is null).
         * @throws IllegalArgumentException if a non-optional capture is not found and cannot be resolved to a NodeBuilder.
         */
        fun resolveCaptureAsNodeBuilder(currentParseTreeRule: ParseTreeRule, captureName: String, captures: Map<String, ParseTreeRule>, graph: AntlrGraphBuilder?): NodeBuilder? {
            val name = if(captureName.endsWith('?')) captureName.substring(0, captureName.length - 1) else captureName
            if(customNodeBuilderCaptures.containsKey(name)) {
                return customNodeBuilderCaptures[name]?.resolveCapture(currentParseTreeRule, captureName, captures, graph)
            }
            if(customParseTreeCaptures.containsKey(name)) {
                return graph?.nodesInParseTree?.get(customParseTreeCaptures[name]?.resolveCapture(currentParseTreeRule, captureName, captures, graph))
            }
            if(captures[name] == null && !captureName.endsWith('?'))
                throw IllegalArgumentException("No capture found for name: $name")
            return graph?.nodesInParseTree?.get(captures[name])
        }

        /**
         * Splits an expression string by a given separator, respecting quotes and parentheses.
         * Characters within quotes or parentheses are not considered for splitting.
         *
         * @param expression The expression string to split.
         * @param separator The character to split by.
         * @return A list of strings, representing the parts of the expression, with leading/trailing whitespace trimmed.
         */
        private fun splitExpression(expression: String, separator: Char): List<String> {
            val parts = mutableListOf<String>()
            var currentPart = StringBuilder()
            var inQuotes = false
            var quoteChar: Char? = null
            var numParanthesis = 0

            for (i in expression.indices) {
                val char = expression[i]
                when {
                    char == '"' || char == '\'' -> {
                        if (inQuotes) {
                            if (quoteChar == char) {
                                inQuotes = false
                                quoteChar = null
                            }
                        } else {
                            inQuotes = true
                            quoteChar = char
                        }
                        currentPart.append(char)
                    }
                    char == '(' && !inQuotes -> {
                        numParanthesis++
                        currentPart.append(char)
                    }
                    char == ')' && !inQuotes -> {
                        numParanthesis--
                        currentPart.append(char)
                    }
                    char == separator && !inQuotes && numParanthesis == 0 -> {
                        if (currentPart.isNotEmpty()) {
                            parts.add(currentPart.toString().trim())
                            currentPart = StringBuilder()
                        }
                    }
                    else -> currentPart.append(char)
                }
            }

            if (currentPart.isNotEmpty()) {
                parts.add(currentPart.toString().trim())
            }
            return parts
        }

        /**
         * Removes newline characters from an expression string.
         *
         * @param expression The string from which to remove newlines.
         * @return The expression string without newline characters.
         */
        private fun removeNewlines(expression: String): String {
            return expression.replace("\n", "")
        }
    }
}