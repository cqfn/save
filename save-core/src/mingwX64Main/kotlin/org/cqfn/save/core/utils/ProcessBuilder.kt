@file:Suppress("FILE_WILDCARD_IMPORTS")

package org.cqfn.save.core.utils

import okio.Path
import platform.posix.fgets
import platform.posix.pclose
import platform.posix.popen

import kotlinx.cinterop.MemScope
import kotlinx.cinterop.cstr
import kotlinx.cinterop.invoke
import kotlinx.cinterop.placeTo
import kotlinx.cinterop.refTo
import kotlinx.cinterop.toKString

@Suppress("MISSING_KDOC_TOP_LEVEL",
    "MISSING_KDOC_CLASS_ELEMENTS",
    "MISSING_KDOC_ON_FUNCTION"
)
actual class ProcessBuilder {
    actual fun exec(command: List<String>, redirectTo: Path?): ExecutionResult {
        val common = ProcessBuilderInternal()
        val cmd = common.prepare(command)

        val pd = popen!!.invoke((cmd).cstr.placeTo(MemScope()), "r".cstr.placeTo(MemScope()))
            ?: error("Pipe error. Couldn't execute command: `$command`")
        val stdout = buildString {
            val buffer = ByteArray(4096)
            while (fgets(buffer.refTo(0), buffer.size, pd) != null) {
                append(buffer.toKString())
            }
        }
        val status = pclose!!.invoke(pd)

        return common.logAndReturn(stdout, status, redirectTo)
    }
}