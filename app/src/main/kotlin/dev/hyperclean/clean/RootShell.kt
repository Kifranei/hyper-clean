package dev.hyperclean.clean

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

data class ShellResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)

interface RootShell {
    fun execute(command: String): ShellResult
}

class SuRootShell : RootShell {
    override fun execute(command: String): ShellResult {
        val process = try {
            ProcessBuilder("su", "-c", command)
                .redirectErrorStream(false)
                .start()
        } catch (error: IOException) {
            return ShellResult(127, "", error.message ?: "找不到 su")
        }

        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        val stdoutThread = thread(name = "hyperclean-su-out", isDaemon = true) {
            process.inputStream.use { it.copyTo(stdout) }
        }
        val stderrThread = thread(name = "hyperclean-su-err", isDaemon = true) {
            process.errorStream.use { it.copyTo(stderr) }
        }
        val finished = process.waitFor(60, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
        }
        stdoutThread.join(1_000)
        stderrThread.join(1_000)
        val code = if (finished) process.exitValue() else 124
        val errorText = if (finished) {
            stderr.toString(Charsets.UTF_8.name())
        } else {
            "命令超时"
        }
        return ShellResult(
            exitCode = code,
            stdout = stdout.toString(Charsets.UTF_8.name()),
            stderr = errorText,
        )
    }
}
