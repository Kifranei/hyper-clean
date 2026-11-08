package dev.hyperclean

import dev.hyperclean.clean.CleanCatalog
import dev.hyperclean.clean.CleanCommands
import dev.hyperclean.clean.CleanMode
import dev.hyperclean.clean.CleanRepository
import dev.hyperclean.clean.RootShell
import dev.hyperclean.clean.ShellResult
import dev.hyperclean.clean.formatBytes
import dev.hyperclean.clean.parseDuListing
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CleanLogicTest {
    @Test
    fun formatBytesUsesBinaryUnits() {
        assertEquals("0 B", formatBytes(0))
        assertEquals("1023 B", formatBytes(1023))
        assertEquals("1 KB", formatBytes(1024))
        assertEquals("1.5 KB", formatBytes(1536))
        assertEquals("2 MB", formatBytes(2L * 1024 * 1024))
    }

    @Test
    fun shellTokenRejectsInjection() {
        assertEquals("/data/anr/*", CleanCommands.shellToken("/data/anr/*"))
        assertFails { CleanCommands.shellToken("/data/anr/*;reboot") }
        assertFails { CleanCommands.shellToken("/data/../etc") }
        assertFails { CleanCommands.shellToken("reboot") }
    }

    @Test
    fun commandsMatchCatalogModes() {
        val thermal = CleanCatalog.require("thermal")
        assertEquals(CleanMode.Truncate, thermal.mode)
        assertTrue(CleanCommands.commandFor(thermal).startsWith(": > /data/adb/modules/MiuiVariableThermal/"))
        assertTrue(CleanCommands.commandFor(CleanCatalog.require("system_logs")).startsWith("rm -rf /data/anr/*"))
        assertTrue(CleanCommands.anyExists(listOf("/data/adb/lspd")).contains("[ -e /data/adb/lspd ]"))
    }

    @Test
    fun scanSkipsSizeWhenModuleIsAbsent() = runBlocking {
        val shell = FakeShell { command ->
            when {
                command == CleanCommands.ROOT_CHECK -> ShellResult(0, "0\n", "")
                command.contains("/data/adb/agh") && command.startsWith("if ") -> ShellResult(0, "0\n", "")
                command.startsWith("if ") -> ShellResult(0, "1\n", "")
                command.startsWith("du ") -> ShellResult(0, "4\t/data/anr/traces.txt\n", "")
                else -> error(command)
            }
        }
        val snapshot = CleanRepository(shell).scan()
        assertTrue(snapshot.rootGranted)
        val agh = snapshot.rows.first { it.id == "agh" }
        assertFalse(agh.present)
        assertEquals(null, agh.bytes)
        val logs = snapshot.rows.first { it.id == "system_logs" }
        assertEquals(4L * 1024, logs.bytes)
        assertEquals("/data/anr/traces.txt", logs.files.single().path)
        assertTrue(shell.commands.none { it.contains("/data/adb/agh/history.log") })
    }

    @Test
    fun parseDuListingKeepsOnlyAbsolutePaths() {
        val files = parseDuListing("4\t/data/anr/traces.txt\nbad line\n8 relative/path\n12 /data/tombstones\n")
        assertEquals(listOf("/data/anr/traces.txt", "/data/tombstones"), files.map { it.path })
        assertEquals(12L * 1024, files[1].bytes)
    }

    @Test
    fun rebootWipesRecentTasksBeforeReboot() = runBlocking {
        val shell = FakeShell { ShellResult(0, "", "") }
        val result = CleanRepository(shell).reboot()
        assertTrue(result.started)
        assertEquals(CleanCommands.remove(CleanCatalog.recentTaskPaths), shell.commands[0])
        assertEquals(CleanCommands.truncate(listOf(CleanCatalog.POWERINFO_REASON)), shell.commands[1])
        assertEquals(CleanCommands.REBOOT, shell.commands[2])
    }

    @Test
    fun missingRootDoesNotTouchData() = runBlocking {
        val shell = FakeShell { ShellResult(1, "", "permission denied") }
        val snapshot = CleanRepository(shell).scan()
        assertFalse(snapshot.rootGranted)
        assertEquals(listOf(CleanCommands.ROOT_CHECK), shell.commands)
    }

    private fun assertFails(block: () -> Unit) {
        try {
            block()
        } catch (_: IllegalArgumentException) {
            return
        }
        error("expected IllegalArgumentException")
    }
}

private class FakeShell(
    private val handler: (String) -> ShellResult,
) : RootShell {
    val commands = mutableListOf<String>()

    override fun execute(command: String): ShellResult {
        commands += command
        return handler(command)
    }
}
