package software.bevel.code_to_knowledge_graph.antlr.bevel_ast_ql.exceptions

/**
 * Exception thrown when a predicate or converter attempts to access a captured parent node
 * (e.g., via a special capture name like "parent" or "context_alias.some_name") but the expected
 * capture is not found in the current match context.
 *
 * This typically indicates an issue with the query structure, the matching logic for establishing
 * context, or an assumption in a predicate/converter that a parent capture would be available.
 *
 * @param message A descriptive message explaining the context of the error.
 */
class ParentNodeCaptureNotFound(message: String): IllegalArgumentException(message)