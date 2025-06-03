package software.bevel.code_to_knowledge_graph.vscode

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.turbo.TurboFilter
import ch.qos.logback.core.spi.FilterReply
import org.slf4j.Marker

/**
 * A custom Logback TurboFilter that throws exceptions when ERROR level logs are encountered during tests.
 * This helps ensure that error logging paths can be properly tested by causing test failures
 * when log.error() is called.
 */
class ErrorToExceptionTurboFilter : TurboFilter() {

    override fun decide(
        marker: Marker?,
        logger: Logger?,
        level: Level?,
        format: String?,
        params: Array<out Any>?,
        t: Throwable?
    ): FilterReply {
        // Only throw exceptions for ERROR level logs
        if (level == Level.ERROR) {
            val errorMessage = if (format != null && params != null) {
                formatMessage(format, params)
            } else {
                format ?: "Unknown error"
            }
            
            val exception = t ?: RuntimeException("Error logged: $errorMessage")
            
            // Throw a runtime exception with the log message and original cause
            throw RuntimeException("Error logged via SLF4J: $errorMessage", exception)
        }
        
        // For all other log levels, continue normal processing
        return FilterReply.NEUTRAL
    }
    
    /**
     * Simple formatter for log messages that contain parameters
     */
    private fun formatMessage(format: String, params: Array<out Any>): String {
        var result = format
        params.forEach { param ->
            result = result.replaceFirst("{}", param.toString())
        }
        return result
    }
}
