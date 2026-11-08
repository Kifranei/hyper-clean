# 深度清理

HyperOS / MIUI 上的缓存与日志清理应用，界面使用 [Miuix](https://github.com/compose-miuix-ui/miuix)。

**需要 Root 权限。** 清理和重启都通过已授权的 `su` 执行。没有 Root 时只能看到提示，不会改动任何文件。

清理范围限于固定路径：最近任务与使用统计、系统日志、包管理缓存，以及已安装的 AdGuardHome、KernelSU、LSPosed、温控模块日志。`/storage/emulated/0/MIUI/debug_log/powerinfo/result_reason` 只清空内容，文件保留。长按某一项可以查看匹配到的文件和占用。重启前会再清一次最近任务，并清空电源重启记录。

```powershell
.\gradlew.bat :app:assembleRelease
```

Release 签名默认读取 `../Halcyon/release.jks`，也可以用 `RELEASE_STORE_FILE`、`RELEASE_STORE_PASSWORD`、`RELEASE_KEY_ALIAS`、`RELEASE_KEY_PASSWORD` 指定。

本项目以 Apache License 2.0 授权，全文见 [LICENSE.md](LICENSE.md)。
