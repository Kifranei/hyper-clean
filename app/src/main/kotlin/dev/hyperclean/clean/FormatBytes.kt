package dev.hyperclean.clean

import java.util.Locale

data class FileUsage(
    val path: String,
    val bytes: Long,
)

fun parseDuListing(stdout: String): List<FileUsage> {
    return stdout.lineSequence().mapNotNull { raw ->
        val line = raw.trim()
        val splitAt = line.indexOfFirst { it.isWhitespace() }
        if (splitAt <= 0) return@mapNotNull null
        val kilobytes = line.substring(0, splitAt).toLongOrNull() ?: return@mapNotNull null
        val path = line.substring(splitAt).trim()
        if (!path.startsWith("/")) return@mapNotNull null
        FileUsage(path = path, bytes = kilobytes * 1024)
    }.toList()
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = -1
    while (value >= 1024.0 && unit < units.lastIndex) {
        value /= 1024.0
        unit++
    }
    val digits = if (value >= 10.0 || value % 1.0 == 0.0) 0 else 1
    return String.format(Locale.US, "%.${digits}f %s", value, units[unit])
}
