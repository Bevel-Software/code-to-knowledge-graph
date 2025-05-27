package software.bevel.code_to_knowledge_graph.antlr.bevel_ast_ql.converters

import org.snt.inmemantlr.tree.ParseTreeRule
import software.bevel.graph_domain.graph.ConnectionType
import software.bevel.graph_domain.graph.NodeType
import software.bevel.code_to_knowledge_graph.antlr.AntlrGraphBuilder
import software.bevel.code_to_knowledge_graph.antlr.bevel_ast_ql.predicates.ExistsPredicate
import software.bevel.code_to_knowledge_graph.antlr.bevel_ast_ql.predicates.JsonPredicate

/**
 * A macro converter that establishes a [ConnectionType.CALLED_WITH] connection from a caller to an argument.
 *
 * This converter delegates its [JsonAndQueryMatchConverter] functionality to an internally configured
 * [ConnectionConverter]. The source node of the connection is derived from the `callerCapture` by taking
 * the last part after splitting by dots (e.g., `"some.object.method"` becomes `"method"`).
 * An [ExistsPredicate] is implicitly added to ensure the `callerCapture` exists.
 *
 * @property callerCapture The capture name for the calling entity (e.g., a method call).
 * @property argumentCapture The capture name for the argument entity.
 * @property positionAstRule Optional capture name for the AST rule to use for the connection's source position.
 *                           If null, `callerCapture` is used.
 */
class ArgumentConnectionConverter(
    val callerCapture: String,
    val argumentCapture: String,
    val positionAstRule: String?,
): JsonAndQueryMatchConverter by ConnectionConverter(
    fromNode = "$callerCapture.splitDot.last",
    fromFallbackType = null,
    toNode = argumentCapture,
    toFallbackType = null,
    connectionType = ConnectionType.CALLED_WITH.toString(),
    positionAstRule = positionAstRule ?: callerCapture,
    predicates = listOf(ExistsPredicate(callerCapture)),
) {
    companion object {
        /** Unique identifier for this converter type, typically used in configuration files. */
        const val NAME = "argument-connection"
    }
}

/**
 * A macro converter intended to create a node and then establish a [ConnectionType.DEFINES] connection
 * from a specified parent node to this newly created node.
 *
 * Note: The connection creation part of this converter is currently marked as `TODO() //DEPRECATED`
 * and the relevant code is commented out.
 *
 * @property nodeName The expression or capture name that resolves to the name of the node to be created.
 * @property nodeType The [NodeType] (as a string) for the new node.
 * @property parent Optional expression or capture name for the parent node. If the connection logic were active,
 *                  it would default to `"@parentNode.node"` if this is null.
 * @property representingAndPositionAstRule Optional capture name for the AST rule that represents the new node
 *                                          and determines its source code position.
 * @property predicates An optional list of [JsonPredicate]s that must all evaluate to true for this converter to be applied.
 */
class NodeWithParentConverter(
    val nodeName: String,
    val nodeType: String,
    val parent: String? = null,
    val representingAndPositionAstRule: String? = null,
    override val predicates: List<JsonPredicate> = emptyList()
): JsonAndQueryMatchConverter {
    companion object {
        /** Unique identifier for this converter type, typically used in configuration files. */
        const val NAME = "node-with-parent"
    }

    /**
     * Applies the logic to create a node. The intended connection to a parent is currently deprecated.
     *
     * This method instantiates a [NodeConverter] to create the primary node.
     * The logic to create a [ConnectionConverter] for a `DEFINES` relationship to a parent node
     * is commented out and marked as `TODO() //DEPRECATED`.
     *
     * @param currentParseTreeRule The current ANTLR [ParseTreeRule] being processed.
     * @param graph The [AntlrGraphBuilder] instance.
     * @param context A map of named captures from the query match.
     * @param pathToFile The path to the source file being parsed.
     */
    override fun convert(
        currentParseTreeRule: ParseTreeRule,
        graph: AntlrGraphBuilder,
        context: Map<String, ParseTreeRule>,
        pathToFile: String
    ) {
        val nodeConverter = NodeConverter(
            nodeName = nodeName,
            nodeType = nodeType,
            representingParseTreeNode = representingAndPositionAstRule,
            predicates = predicates
        )

        TODO() //DEPRECATED
        /*val connectionConverter = ConnectionConverter(
            fromNode = parent ?: "@parentNode.node",
            fromFallbackType = null,
            toNode = nodeName,
            toFallbackType = null,
            connectionType = ConnectionType.DEFINES.toString(),
            positionAstRule = representingAndPositionAstRule,
            predicates = predicates
        )

        nodeConverter.convert(currentParseTreeRule, graph, context, pathToFile)
        connectionConverter.convert(currentParseTreeRule, graph, context, pathToFile)*/
    }
}

/**
 * A macro converter for declaring a function. It creates two nodes:
 * 1. A node for the function's simple name.
 * 2. A node for the function's name including parameters (signature).
 * It then establishes an [ConnectionType.OVERLOADED_BY] connection from the simple name node to the
 * signature node. Both nodes are created as children of a specified parent scope using [NodeWithParentConverter].
 *
 * @property name The expression or capture name for the simple name of the function.
 * @property nameWithParams The expression or capture name for the function name including its parameters (signature).
 * @property parent Optional expression or capture name for the parent/containing scope of the function.
 * @property representingAndPositionAstRule Optional capture name for the AST rule that represents the function
 *                                          declaration and determines its source code position.
 * @property predicates An optional list of [JsonPredicate]s that must all evaluate to true for this converter to be applied.
 */
class FunctionDeclarationConverter(
    val name: String,
    val nameWithParams: String,
    val parent: String? = null,
    val representingAndPositionAstRule: String? = null,
    override val predicates: List<JsonPredicate> = emptyList()
): JsonAndQueryMatchConverter {
    companion object {
        /** Unique identifier for this converter type, typically used in configuration files. */
        const val NAME = "function-declaration"
    }

    /**
     * Applies the logic to declare a function by creating nodes for its simple name and signature,
     * connecting them, and linking them to a parent scope.
     *
     * Instantiates and calls `convert` on:
     * - A [NodeWithParentConverter] for the function's simple name.
     * - A [NodeWithParentConverter] for the function's name with parameters.
     * - A [ConnectionConverter] to create an [ConnectionType.OVERLOADED_BY] link between the two.
     *
     * @param currentParseTreeRule The current ANTLR [ParseTreeRule] being processed.
     * @param graph The [AntlrGraphBuilder] instance.
     * @param context A map of named captures from the query match.
     * @param pathToFile The path to the source file being parsed.
     */
    override fun convert(
        currentParseTreeRule: ParseTreeRule,
        graph: AntlrGraphBuilder,
        context: Map<String, ParseTreeRule>,
        pathToFile: String
    ) {
        val nodeConverter = NodeWithParentConverter(
            nodeName = name,
            nodeType = NodeType.Function.toString(),
            parent = parent,
            representingAndPositionAstRule = representingAndPositionAstRule,
            predicates = predicates
        )

        val nodeConverter2 = NodeWithParentConverter(
            nodeName = nameWithParams,
            nodeType = NodeType.Function.toString(),
            parent = parent,
            representingAndPositionAstRule = representingAndPositionAstRule,
            predicates = predicates
        )

        val connectionConverter = ConnectionConverter(
            fromNode = name,
            fromFallbackType = null,
            toNode = nameWithParams,
            toFallbackType = null,
            connectionType = ConnectionType.OVERLOADED_BY.toString(),
            positionAstRule = representingAndPositionAstRule,
            predicates = predicates
        )

        nodeConverter.convert(currentParseTreeRule, graph, context, pathToFile)
        nodeConverter2.convert(currentParseTreeRule, graph, context, pathToFile)
        connectionConverter.convert(currentParseTreeRule, graph, context, pathToFile)
    }
}