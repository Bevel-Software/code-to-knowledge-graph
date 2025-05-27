package software.bevel.code_to_knowledge_graph.antlr.bevel_ast_ql.resolvers

import org.snt.inmemantlr.tree.ParseTreeRule
import software.bevel.code_to_knowledge_graph.antlr.AntlrGraphBuilder
import software.bevel.code_to_knowledge_graph.antlr.bevel_ast_ql.exceptions.ParentNodeCaptureNotFound

/**
 * A functional interface for handlers that provide custom logic to resolve special capture names
 * to [ParseTreeRule] instances. This allows the query system to support predefined capture names
 * (e.g., for accessing the parent or current node) that are not part of the explicitly named captures
 * in a query pattern.
 */
fun interface CustomParseTreeCaptureHandler {
    /**
     * Resolves a special capture name to a specific [ParseTreeRule].
     *
     * @param currentParseTreeRule The [ParseTreeRule] that is the current context of the match or resolution.
     * @param captureName The special capture name to be resolved (e.g., "parent", "current").
     *                    The interpretation of this name is up to the implementing handler.
     * @param captures A map of explicitly named captures from the current query match. This might be used
     *                 by some handlers for more complex contextual resolution, though often unused for simple cases.
     * @param graph An optional [AntlrGraphBuilder] instance, providing access to the wider knowledge graph context
     *              if the resolution depends on it (e.g., to find a parent node within the graph structure).
     * @return The resolved [ParseTreeRule] if the special capture name can be resolved by this handler;
     *         `null` otherwise, or if the capture is considered optional and not found.
     */
    fun resolveCapture(currentParseTreeRule: ParseTreeRule, captureName: String, captures: Map<String, ParseTreeRule>, graph: AntlrGraphBuilder?): ParseTreeRule?
}

/**
 * A [CustomParseTreeCaptureHandler] that resolves a capture to the parent of the [currentParseTreeRule]
 * within the provided [AntlrGraphBuilder].
 *
 * This handler is typically used for special capture names like "parent" to navigate upwards in the parse tree structure
 * as defined by the graph.
 */
class ParentNodeHandler: CustomParseTreeCaptureHandler {
    /**
     * Attempts to resolve the parent of the [currentParseTreeRule] using the [AntlrGraphBuilder].
     *
     * If the parent node cannot be found in the graph and the [captureName] does not end with a '?'
     * (indicating an optional capture), this method throws a [ParentNodeCaptureNotFound] exception.
     *
     * @param currentParseTreeRule The node whose parent is to be resolved.
     * @param captureName The name of the capture being resolved. Used to check for optionality (if it ends with '?').
     * @param captures Unused by this handler.
     * @param graph The [AntlrGraphBuilder] instance used to look up the parent node.
     * @return The parent [ParseTreeRule] if found, or `null` if not found and the capture is optional.
     * @throws ParentNodeCaptureNotFound if the parent is not found and the capture is not marked as optional.
     */
    override fun resolveCapture(currentParseTreeRule: ParseTreeRule, captureName: String, captures: Map<String, ParseTreeRule>, graph: AntlrGraphBuilder?): ParseTreeRule? {
        val result = graph?.getDefinerParseTreeNode(currentParseTreeRule.parent)
        if(result == null && !captureName.endsWith('?')) {
            throw ParentNodeCaptureNotFound("No parent node found for node: ${currentParseTreeRule.rule}")
        }
        return result
    }
}

/**
 * A [CustomParseTreeCaptureHandler] that resolves a capture to the [currentParseTreeRule] itself.
 *
 * This handler is typically used for special capture names like "current" or "self" to refer to the node
 * at the current point of matching or evaluation.
 */
class CurrentParseTreeNodeHandler: CustomParseTreeCaptureHandler {
    /**
     * Resolves the capture by simply returning the [currentParseTreeRule].
     *
     * @param currentParseTreeRule The node to be returned as the resolved capture.
     * @param captureName Unused by this handler, but part of the interface.
     * @param captures Unused by this handler.
     * @param graph Unused by this handler.
     * @return The [currentParseTreeRule] itself.
     */
    override fun resolveCapture(currentParseTreeRule: ParseTreeRule, captureName: String, captures: Map<String, ParseTreeRule>, graph: AntlrGraphBuilder?): ParseTreeRule {
        return currentParseTreeRule
    }
}