package software.bevel.code_to_knowledge_graph.vscode

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import software.bevel.file_system_domain.web.LocalCommunicationInterface
import software.bevel.code_to_knowledge_graph.vscode.data.BatchNodeRequestResponse
import software.bevel.code_to_knowledge_graph.vscode.data.NodeRequestResponse
import software.bevel.code_to_knowledge_graph.vscode.data.VsCodeCommand
import java.io.Closeable

/**
 * Processes and sends requests to a VS Code extension in batches.
 * This class helps in managing multiple [VsCodeCommand] requests by grouping them
 * and sending them together to optimize communication, especially when dealing with a large number of small requests.
 * It implements [Closeable] to ensure that any pending batches are flushed when the processor is closed.
 *
 * @property commsChannel The [LocalCommunicationInterface] used to send batched requests to the VS Code extension.
 * @property maxBatchSize The maximum number of requests to accumulate in a batch before automatically flushing it.
 *                        Defaults to 1000.
 * @property logger An SLF4J [Logger] instance for logging batch processing activities and errors.
 */
class BatchProcessor(
    private val commsChannel: LocalCommunicationInterface,
    private val maxBatchSize: Int = 1000,
    private val logger: Logger = LoggerFactory.getLogger(BatchProcessor::class.java)
): Closeable {
    /**
     * A mutable list holding pairs of [VsCodeCommand] and their corresponding callback functions.
     * Each callback expects a [NodeRequestResponse] and will be invoked when the response for its command is received.
     * This list accumulates requests until [flushBatch] is called, either manually or automatically when [maxBatchSize] is reached.
     */
    private val nodeBatch: MutableList<Pair<VsCodeCommand, (NodeRequestResponse) -> Unit>> = mutableListOf()

    /**
     * Adds a [VsCodeCommand] and its associated callback to the current batch.
     * If the size of the batch exceeds [maxBatchSize] after adding the new request,
     * [flushBatch] is automatically called to send the current batch.
     *
     * @param command The [VsCodeCommand] to be added to the batch.
     * @param callback A lambda function that will be invoked with the [NodeRequestResponse] corresponding to the command.
     */
    fun batchSendRequest(command: VsCodeCommand, callback: (NodeRequestResponse) -> Unit) {
        nodeBatch.add(command to callback)
        if(nodeBatch.size > maxBatchSize) {
            flushBatch()
        }
    }

    /**
     * Sends all currently batched requests to the VS Code extension via the [commsChannel].
     * This method processes the `nodeBatch` list:
     * 1. Clears the current batch to prepare for new requests.
     * 2. Sends the commands to the communication channel.
     * 3. Parses the [BatchNodeRequestResponse] received.
     * 4. Invokes the appropriate callback for each command in the batch with its corresponding response.
     * 5. If an error occurs during sending or parsing, it attempts to resend the batch in smaller chunks (of 100).
     * This process repeats until the `nodeBatch` is empty.
     */
    fun flushBatch() {
        while(nodeBatch.size > 0) {
            val oldBatch = nodeBatch.toList()
            nodeBatch.clear()
            try {
                val resp = commsChannel.send(oldBatch.map { it.first })
                val result = jacksonObjectMapper().readValue(resp, BatchNodeRequestResponse::class.java)
                result.commands.forEachIndexed { index, response ->
                    if (response != null)
                        oldBatch[index].second(response)
                    else {
                        logger.info("Got a null response in flushBatch")
                    }
                }
            } catch (ex: Exception) {
                logger.error("Error caught, retrying: ", ex)
                oldBatch.chunked(100).forEach { chunk ->
                    val resp = commsChannel.send(chunk.map { it.first })
                    val result = jacksonObjectMapper().readValue(resp, BatchNodeRequestResponse::class.java)
                    result.commands.forEachIndexed { index, response ->
                        if (response != null)
                            chunk[index].second(response)
                        else {
                            logger.info("Got a null response in chunked flushBatch")
                        }
                    }
                }
            }
        }
    }

    /**
     * Flushes any remaining requests in the batch when the [BatchProcessor] is closed.
     * This ensures that all pending commands are sent before the processor is disposed of.
     */
    override fun close() {
        flushBatch()
    }
}