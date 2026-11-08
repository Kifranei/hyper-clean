package dev.hyperclean.clean

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ScannedRow(
    val id: String,
    val present: Boolean,
    val bytes: Long?,
    val files: List<FileUsage> = emptyList(),
)

data class ScanSnapshot(
    val rootGranted: Boolean,
    val message: String?,
    val rows: List<ScannedRow>,
)

data class CleanReport(
    val cleaned: List<String>,
    val failed: List<String>,
)

data class RebootResult(
    val started: Boolean,
    val message: String?,
)

class CleanRepository(
    private val shell: RootShell,
) {
    suspend fun scan(): ScanSnapshot = withContext(Dispatchers.IO) {
        val identity = shell.execute(CleanCommands.ROOT_CHECK)
        val rootGranted = identity.exitCode == 0 && identity.stdout.trim() == "0"
        if (!rootGranted) {
            val message = identity.stderr.trim().ifBlank { identity.stdout.trim() }
                .ifBlank { "没有 Root 权限" }
            return@withContext ScanSnapshot(rootGranted = false, message = message, rows = emptyList())
        }

        val rows = CleanCatalog.targets.map { target ->
            val present = if (target.probes.isEmpty()) {
                true
            } else {
                shell.execute(CleanCommands.anyExists(target.probes)).stdout.trim() == "1"
            }
            val files = if (present) {
                parseDuListing(shell.execute(CleanCommands.measureEntries(target.paths)).stdout)
            } else {
                emptyList()
            }
            val bytes = if (present) files.sumOf { it.bytes } else null
            ScannedRow(id = target.id, present = present, bytes = bytes, files = files)
        }
        ScanSnapshot(rootGranted = true, message = null, rows = rows)
    }

    suspend fun clean(ids: List<String>): CleanReport = withContext(Dispatchers.IO) {
        val cleaned = mutableListOf<String>()
        val failed = mutableListOf<String>()
        ids.distinct().forEach { id ->
            val target = CleanCatalog.require(id)
            val result = shell.execute(CleanCommands.commandFor(target))
            if (result.exitCode == 0) cleaned += target.title else failed += target.title
        }
        CleanReport(cleaned = cleaned, failed = failed)
    }

    suspend fun reboot(): RebootResult = withContext(Dispatchers.IO) {
        val wiped = shell.execute(CleanCommands.remove(CleanCatalog.recentTaskPaths))
        if (wiped.exitCode != 0) {
            val message = wiped.stderr.trim().ifBlank { "重启前清理最近任务失败" }
            return@withContext RebootResult(started = false, message = message)
        }
        val cleared = shell.execute(CleanCommands.truncate(listOf(CleanCatalog.POWERINFO_REASON)))
        if (cleared.exitCode != 0) {
            val message = cleared.stderr.trim().ifBlank { "清空电源重启记录失败" }
            return@withContext RebootResult(started = false, message = message)
        }
        val reboot = shell.execute(CleanCommands.REBOOT)
        val started = reboot.exitCode == 0
        RebootResult(
            started = started,
            message = if (started) null else reboot.stderr.trim().ifBlank { "重启失败" },
        )
    }
}
