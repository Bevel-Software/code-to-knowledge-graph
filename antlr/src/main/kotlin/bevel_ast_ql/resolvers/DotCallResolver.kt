package software.bevel.code_to_knowledge_graph.antlr.bevel_ast_ql.resolvers

import software.bevel.code_to_knowledge_graph.antlr.AntlrGraphBuilder

/**
 * A functional interface for resolving property-like access or method-like calls
 * on a given value, typically represented as a "dot call" (e.g., `object.property` or `object.method(arg)`).
 *
 * This is used to provide custom logic for interpreting chained accesses in query arguments or converters.
 *
 * @param T The type of the current value on which the dot call is being resolved.
 * @param R The type of the result after resolving the dot call.
 */
fun interface DotCallResolver<T, out R> {

    /**
     * Resolves a dot call operation on the [currentValue].
     *
     * @param currentValue The object or value of type [T] from which to resolve the access.
     * @param args A list of strings representing the parts of the dot call. For example,
     *             for an access like `myObject.property.subProperty`, `args` might be `["property", "subProperty"]`.
     *             For a method-like call `myObject.method(arg1, arg2)`, it might be `["method(arg1, arg2)"]` or
     *             parsed further depending on the resolver's design.
     * @param graph An optional [AntlrGraphBuilder] instance, providing access to the wider knowledge graph context
     *              if the resolution depends on it.
     * @return The result of the dot call resolution, of type [R]. This could be a property value, the result of a method,
     *         or another intermediate value if the chain is longer.
     */
    fun resolveDotCall(currentValue: T, args: List<String>, graph: AntlrGraphBuilder?): R
}