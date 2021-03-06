/**
 * Quick & dirty utilities for logging.
 * FixMe: Use proper logging solution once it's available for kotlin/native.
 */

package org.cqfn.save.core.logging

import org.cqfn.save.core.config.OutputStreamType
import org.cqfn.save.core.utils.writeToStream

import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Is debug logging enabled
 */
var isDebugEnabled: Boolean = false

/**
 * Whether to add time stamps to log messages
 */
var isTimeStampsEnabled: Boolean = false

/**
 * Log a message to the [stream] with timestamp and specific [level]
 *
 * @param level log level
 * @param msg a message string
 * @param stream output stream (file, stdout, stderr)
 */
fun logMessage(
    level: String,
    msg: String,
    stream: OutputStreamType = OutputStreamType.STDOUT
) {
    val currentTime = if (isTimeStampsEnabled) {
        val currentTimeInstance = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        " ${currentTimeInstance.date} ${currentTimeInstance.hour}:${currentTimeInstance.minute}:${currentTimeInstance.second}"
    } else {
        ""
    }
    writeToStream("[$level]$currentTime: $msg", stream)
}

/**
 * Log a message with debug level
 *
 * @param msg a message string
 */
fun logDebug(msg: String) {
    if (isDebugEnabled) {
        logMessage("DEBUG", msg)
    }
}

/**
 * Log a message with info level
 *
 * @param msg a message string
 */
fun logInfo(msg: String): Unit = logMessage("INFO", msg)

/**
 * Log a message with warn level
 *
 * @param msg a message string
 */
fun logWarn(msg: String): Unit = logMessage("WARN", msg, OutputStreamType.STDERR)

/**
 * Log a message with error level
 *
 * @param msg a message string
 */
fun logError(msg: String): Unit = logMessage("ERROR", msg, OutputStreamType.STDERR)
