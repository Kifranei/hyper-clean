package dev.hyperclean.clean

enum class Section(val title: String) {
    Ui("交互与统计"),
    System("系统日志"),
    Package("包管理与缓存"),
    Module("第三方模块"),
}

enum class CleanMode {
    Remove,
    Truncate,
}

data class CleanTarget(
    val id: String,
    val section: Section,
    val title: String,
    val detail: String,
    val paths: List<String>,
    val probes: List<String> = emptyList(),
    val mode: CleanMode = CleanMode.Remove,
    val rebootHint: Boolean = false,
)

object CleanCatalog {
    const val POWERINFO_REASON = "/storage/emulated/0/MIUI/debug_log/powerinfo/result_reason"

    val recentTaskPaths = listOf(
        "/data/system_ce/0/recent_images",
        "/data/system_ce/0/recent_tasks",
        "/data/system_ce/0/snapshots",
        "/data/system_ce/999/recent_images",
        "/data/system_ce/999/recent_tasks",
        "/data/system_ce/999/snapshots",
    )

    val targets: List<CleanTarget> = listOf(
        CleanTarget(
            id = "ui_stats",
            section = Section.Ui,
            title = "最近任务与使用统计",
            detail = "主用户和分身的最近任务、快照、使用统计",
            paths = recentTaskPaths + listOf(
                "/data/system_ce/0/usagestats",
                "/data/system_ce/999/usagestats",
            ),
        ),
        CleanTarget(
            id = "system_logs",
            section = Section.System,
            title = "系统日志与崩溃记录",
            detail = "ANR、tombstone、dropbox、电池和图形统计",
            paths = listOf(
                "/data/anr/*",
                "/data/local/bootlog/*",
                "/data/system/dropbox/*",
                "/data/system/shutdown-checkpoints/*",
                "/data/tombstones/*",
                "/data/system/procstats/*",
                "/data/system/graphicsstats/*",
                "/data/system/battery-history/*",
                "/data/system/battery-usage-stats/*",
                "/data/system/batterystats.bin",
                "/data/system/log-files.xml",
            ),
        ),
        CleanTarget(
            id = "powerinfo",
            section = Section.System,
            title = "电源重启记录",
            detail = "清空 result_reason 的内容，保留文件",
            mode = CleanMode.Truncate,
            paths = listOf(POWERINFO_REASON),
        ),
        CleanTarget(
            id = "package_cache",
            section = Section.Package,
            title = "包管理缓存",
            detail = "删除后需要重启才生效",
            rebootHint = true,
            paths = listOf(
                "/data/system/package_cache",
                "/data/system/package-usage.list",
                "/data/system/package-dex-usage.pb",
                "/data/system/package-dex-usage.list",
                "/data/system/package-dcl.list",
                "/data/system/package-cstats.list",
            ),
        ),
        CleanTarget(
            id = "agh",
            section = Section.Module,
            title = "AdGuardHome",
            detail = "日志与查询历史",
            probes = listOf("/data/adb/agh"),
            paths = listOf(
                "/data/adb/agh/history.log",
                "/data/adb/agh/bin.log*",
                "/data/adb/agh/bin/data/querylog.json*",
            ),
        ),
        CleanTarget(
            id = "ksu",
            section = Section.Module,
            title = "KernelSU",
            detail = "KernelSU 与 susfs 日志",
            probes = listOf("/data/adb/ksu"),
            paths = listOf(
                "/data/adb/ksu/log",
                "/data/adb/ksu/susfs4ksu/logs",
            ),
        ),
        CleanTarget(
            id = "lsposed",
            section = Section.Module,
            title = "LSPosed",
            detail = "框架日志",
            probes = listOf(
                "/data/adb/lspd",
                "/data/adb/modules/zygisk_lsposed",
            ),
            paths = listOf(
                "/data/adb/lspd/log",
                "/data/adb/lspd/log.old",
            ),
        ),
        CleanTarget(
            id = "thermal",
            section = Section.Module,
            title = "温控模块",
            detail = "清空日志内容，不删除模块",
            probes = listOf("/data/adb/modules/MiuiVariableThermal"),
            mode = CleanMode.Truncate,
            paths = listOf(
                "/data/adb/modules/MiuiVariableThermal/log.log",
                "/data/adb/modules/MiuiVariableThermal/current.txt",
            ),
        ),
    )

    fun require(id: String): CleanTarget = targets.first { it.id == id }
}
