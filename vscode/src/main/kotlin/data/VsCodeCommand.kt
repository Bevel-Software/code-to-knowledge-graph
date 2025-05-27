package software.bevel.code_to_knowledge_graph.vscode.data

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

/**
 * Represents a command to be sent to the VS Code extension.
 *
 * @property command The name of the command to execute (e.g., "vscode.executeDocumentSymbolProvider").
 * @property args A list of arguments for the command.
 */
data class VsCodeCommand(
    val command: String,
    val args: List<Any> = listOf()
) {
    /**
     * Secondary constructor to allow passing arguments as varargs.
     *
     * @param command The name of the command to execute.
     * @param arguments The arguments for the command.
     */
    constructor(command: String, vararg arguments: Any) : this(command, arguments.toList())
}

/**
 * Represents a batch response containing multiple [NodeRequestResponse] objects.
 * This is typically used when multiple requests were sent to the VS Code extension in a batch,
 * and this class wraps their individual responses.
 *
 * @property commands A list of [NodeRequestResponse] objects, where each can be null if a specific request in the batch failed or had no response.
 */
class BatchNodeRequestResponse(val commands: List<NodeRequestResponse?>)

/**
 * A sealed interface representing various types of responses that can be received
 * from the VS Code extension in relation to node or symbol information requests.
 * The `@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)` annotation allows Jackson
 * to automatically deduce the concrete type during deserialization based on the fields present.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.DEDUCTION
)
sealed interface NodeRequestResponse

/**
 * Represents a response from a VS Code symbol provider request that returns a list of [VsCodeSymbolInformation].
 * This is often used for workspace-wide symbol searches.
 *
 * @property symbolMatches A list of [VsCodeSymbolInformation] objects matching the query.
 */
data class VsCodeSymbolInformationResponse(
    val symbolMatches: List<VsCodeSymbolInformation>
): NodeRequestResponse

/**
 * Represents a response from a VS Code document symbol provider request, returning a list of [VsCodeDocumentSymbol]s.
 * This is used to get the hierarchical structure of symbols within a single document.
 *
 * @property documentSymbols A list of [VsCodeDocumentSymbol] objects found in the document.
 */
data class VsCodeDocumentSymbolResponse(
    val documentSymbols: List<VsCodeDocumentSymbol>
): NodeRequestResponse

/**
 * Represents a batch response for location-based queries, containing multiple [VsCodeBatchLocationResponse] objects.
 *
 * @property locationsList A list of [VsCodeBatchLocationResponse], each corresponding to a specific query in a batch request.
 */
data class VsCodeBatchLocationsResponse(
    val locationsList: List<VsCodeBatchLocationResponse>
): NodeRequestResponse

/**
 * Represents the locations found for a specific node name within a batch location request.
 *
 * @property nodeName The name of the node for which locations were requested.
 * @property locations A list of [VsCodeLocation] objects where the node or its references are found.
 */
data class VsCodeBatchLocationResponse(
    val nodeName: String,
    val locations: List<VsCodeLocation>
)

/**
 * Represents a request object used in batch operations, associating a [nodeName] with a [VsCodeSymbolInformation].
 * This structure might be used when batching requests for definitions or references related to specific symbols.
 * Note: This implements [NodeRequestResponse], which might be unconventional if it's purely a request object.
 * Consider if it should be a standalone data class if not used as a response type.
 *
 * @property nodeName An identifier for the node associated with this request part.
 * @property symbol The [VsCodeSymbolInformation] relevant to this part of the batch request.
 */
data class VsCodeBatchRequest(
    val nodeName: String,
    val symbol: VsCodeSymbolInformation,
): NodeRequestResponse

/**
 * Represents a response containing a list of [VsCodeLocation]s.
 * This is typically returned when querying for definitions or references of a symbol.
 *
 * @property locations A list of [VsCodeLocation] objects.
 */
data class VsCodeLocationResponse(
    val locations: List<VsCodeLocation>
): NodeRequestResponse