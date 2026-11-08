package dev.hyperclean.clean

object CleanCommands {
    const val ROOT_CHECK = "id -u"
    const val REBOOT = "reboot"

    private val token = Regex("^/[A-Za-z0-9_./-]+\\*?$")

    fun shellToken(path: String): String {
        require(!path.contains("..")) { "拒绝路径: $path" }
        require(token.matches(path)) { "拒绝路径: $path" }
        return path
    }

    fun measureEntries(paths: List<String>): String {
        val joined = paths.joinToString(" ") { shellToken(it) }
        return "du -sk $joined 2>/dev/null"
    }

    fun anyExists(paths: List<String>): String {
        val checks = paths.joinToString(" || ") { "[ -e ${shellToken(it)} ]" }
        return "if $checks; then echo 1; else echo 0; fi"
    }

    fun remove(paths: List<String>): String {
        return "rm -rf ${paths.joinToString(" ") { shellToken(it) }}"
    }

    fun truncate(paths: List<String>): String {
        return paths.joinToString("; ") { ": > ${shellToken(it)}" }
    }

    fun commandFor(target: CleanTarget): String = when (target.mode) {
        CleanMode.Remove -> remove(target.paths)
        CleanMode.Truncate -> truncate(target.paths)
    }
}
