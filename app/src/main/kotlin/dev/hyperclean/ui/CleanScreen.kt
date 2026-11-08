package dev.hyperclean.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hyperclean.clean.Section
import dev.hyperclean.clean.formatBytes
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.PullToRefreshDefaults
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.ToolbarPosition
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close2
import top.yukonga.miuix.kmp.icon.extended.SelectAll
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

private enum class ActiveDialog {
    Clean,
    Reboot,
}

private val RefreshTexts = listOf(
    "下拉刷新",
    "松开刷新",
    "正在刷新",
    "刷新完成",
)

@Composable
fun CleanScreen(viewModel: CleanViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var activeDialog by remember { mutableStateOf<ActiveDialog?>(null) }
    var rebootAfterClean by remember { mutableStateOf(false) }
    var detailItemId by remember { mutableStateOf<String?>(null) }
    val scrollBehavior = MiuixScrollBehavior()
    val pageColor = pageCanvasColor()
    val cardColor = cardSurfaceColor()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is CleanEvent.Message -> snackbarHostState.showSnackbar(event.text)
                CleanEvent.AskReboot -> activeDialog = ActiveDialog.Reboot
            }
        }
    }

    val presentItems = state.items.filter { it.present }
    val allSelected = presentItems.isNotEmpty() && presentItems.all { it.selected }
    val canClean = state.rootGranted && state.selectedPresent.isNotEmpty() && !state.busy
    val canReboot = state.rootGranted && !state.busy
    val detailItem = state.items.firstOrNull { it.id == detailItemId }

    Scaffold(
        containerColor = pageColor,
        topBar = {
            TopAppBar(
                title = "深度清理",
                scrollBehavior = scrollBehavior,
                color = pageColor,
                actions = {
                    if (state.rootGranted) {
                        IconButton(
                            onClick = viewModel::toggleAll,
                            enabled = presentItems.isNotEmpty() && !state.busy,
                        ) {
                            Icon(
                                imageVector = MiuixIcons.SelectAll,
                                contentDescription = if (allSelected) "取消全选" else "全选",
                                modifier = Modifier.size(26.dp),
                                tint = MiuixTheme.colorScheme.onSurface,
                            )
                        }
                        IconButton(
                            onClick = { activeDialog = ActiveDialog.Reboot },
                            enabled = canReboot,
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Close2,
                                contentDescription = "重启",
                                modifier = Modifier.size(26.dp),
                                tint = MiuixTheme.colorScheme.onSurface,
                            )
                        }
                    }
                },
            )
        },
        floatingToolbar = {
            if (state.rootGranted) {
                TextButton(
                    text = if (state.cleaning) "清理中" else "清理所选",
                    onClick = {
                        rebootAfterClean = false
                        activeDialog = ActiveDialog.Clean
                    },
                    enabled = canClean,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        },
        floatingToolbarPosition = ToolbarPosition.BottomCenter,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        PullToRefresh(
            isRefreshing = state.scanning,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding,
            topAppBarScrollBehavior = scrollBehavior,
            refreshTexts = RefreshTexts,
            refreshTextStyle = PullToRefreshDefaults.refreshTextStyle.copy(
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            ),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    bottom = innerPadding.calculateBottomPadding() + 88.dp,
                ),
            ) {
                item {
                    SummaryCard(state, cardColor)
                }
                if (!state.rootGranted && state.hasScanned) {
                    item {
                        SmallTitle(text = "需要 Root")
                        InfoCard(cardColor) {
                            Text(
                                text = "清理 /data 下的日志和缓存需要已授权的 su。当前结果：${state.rootMessage ?: "没有 Root 权限"}",
                                style = MiuixTheme.textStyles.body1,
                            )
                        }
                    }
                }
                Section.entries.forEach { section ->
                    val rows = state.items.filter { it.section == section }
                    if (rows.isNotEmpty()) {
                        item(key = section.name) {
                            SmallTitle(text = section.title)
                            Card(
                                modifier = Modifier
                                    .padding(horizontal = 12.dp)
                                    .padding(bottom = 12.dp),
                                colors = CardDefaults.defaultColors(color = cardColor),
                            ) {
                                rows.forEach { item ->
                                    CleanItemRow(
                                        item = item,
                                        enabled = item.present && !state.busy,
                                        onToggle = { viewModel.toggle(item.id) },
                                        onLongPress = { detailItemId = item.id },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        OverlayDialog(
            show = activeDialog == ActiveDialog.Clean,
            title = "清理所选项目？",
            summary = "预计释放 ${formatBytes(state.selectedBytes)}。此操作不可恢复。",
            onDismissRequest = { if (!state.cleaning) activeDialog = null },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "清理后重启", style = MiuixTheme.textStyles.body1)
                        Text(
                            text = "重启前会再清一次最近任务",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    Switch(
                        checked = rebootAfterClean,
                        onCheckedChange = { rebootAfterClean = it },
                        enabled = !state.cleaning,
                    )
                }
                DialogActions(
                    cancelText = "取消",
                    confirmText = if (rebootAfterClean) "清理并重启" else "开始清理",
                    onCancel = { activeDialog = null },
                    onConfirm = {
                        activeDialog = null
                        viewModel.clean(rebootAfterClean)
                    },
                    enabled = !state.cleaning,
                )
            }
        }

        OverlayDialog(
            show = activeDialog == ActiveDialog.Reboot,
            title = "立即重启？",
            summary = "会先清掉最近任务和电源重启记录，然后重启手机。",
            onDismissRequest = { if (!state.rebooting) activeDialog = null },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (state.rebooting) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                }
                DialogActions(
                    cancelText = "取消",
                    confirmText = "重启",
                    onCancel = { activeDialog = null },
                    onConfirm = {
                        activeDialog = null
                        viewModel.reboot()
                    },
                    enabled = !state.rebooting,
                )
            }
        }

        OverlayDialog(
            show = detailItem != null,
            title = detailItem?.title ?: "",
            summary = detailItem?.let { item ->
                when {
                    !item.present -> "未检测到"
                    item.files.isEmpty() -> "没有匹配到文件"
                    else -> "共 ${formatBytes(item.files.sumOf { it.bytes })}"
                }
            },
            onDismissRequest = { detailItemId = null },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                val files = detailItem?.files.orEmpty()
                if (files.isEmpty()) {
                    Text(
                        text = detailItem?.detail ?: "",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        files.forEach { file ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Text(
                                    text = file.path,
                                    modifier = Modifier.weight(1f),
                                    style = MiuixTheme.textStyles.footnote1,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = formatBytes(file.bytes),
                                    style = MiuixTheme.textStyles.body2,
                                )
                            }
                        }
                    }
                }
                DialogActions(
                    cancelText = "关闭",
                    confirmText = "关闭",
                    onCancel = { detailItemId = null },
                    onConfirm = { detailItemId = null },
                    showConfirm = false,
                )
            }
        }
    }
}

@Composable
private fun pageCanvasColor(): Color {
    val dark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    return if (dark) Color(0xFF101014) else Color(0xFFF4F4F7)
}

@Composable
private fun cardSurfaceColor(): Color {
    val dark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    return if (dark) Color(0xFF242424) else Color.White
}

@Composable
private fun SummaryCard(state: UiState, cardColor: Color) {
    Card(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .padding(top = 8.dp, bottom = 12.dp)
            .fillMaxWidth(),
        insideMargin = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        colors = CardDefaults.defaultColors(color = cardColor),
    ) {
        Text(
            text = "预计可释放",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Text(
            text = if (state.rootGranted) formatBytes(state.selectedBytes) else "—",
            modifier = Modifier.padding(top = 4.dp),
            color = MiuixTheme.colorScheme.primary,
            style = MiuixTheme.textStyles.title1,
        )
        Text(
            text = when {
                state.rebooting -> "正在重启"
                state.cleaning -> "正在清理"
                state.scanning && !state.hasScanned -> "正在统计"
                state.rootGranted -> "已选 ${state.selectedPresent.size} 项，长按查看文件"
                else -> "等待 Root 授权"
            },
            modifier = Modifier.padding(top = 6.dp),
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun InfoCard(cardColor: Color, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp),
        insideMargin = PaddingValues(20.dp),
        colors = CardDefaults.defaultColors(color = cardColor),
    ) {
        content()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CleanItemRow(
    item: ItemUi,
    enabled: Boolean,
    onToggle: () -> Unit,
    onLongPress: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                enabled = true,
                onClick = { if (enabled) onToggle() },
                onLongClick = onLongPress,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MiuixTheme.textStyles.headline1,
                color = if (enabled) {
                    MiuixTheme.colorScheme.onSurface
                } else {
                    MiuixTheme.colorScheme.disabledOnSurface
                },
            )
            Text(
                text = itemSummary(item),
                modifier = Modifier.padding(top = 2.dp),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        Checkbox(
            state = if (item.selected && item.present) ToggleableState.On else ToggleableState.Off,
            onClick = null,
            enabled = enabled,
        )
    }
}

@Composable
private fun DialogActions(
    cancelText: String,
    confirmText: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    enabled: Boolean = true,
    showConfirm: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(
            text = cancelText,
            onClick = onCancel,
            enabled = enabled,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.textButtonColors(
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                textColor = MiuixTheme.colorScheme.onSurface,
            ),
        )
        if (showConfirm) {
            TextButton(
                text = confirmText,
                onClick = onConfirm,
                enabled = enabled,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}

private fun itemSummary(item: ItemUi): String = when {
    !item.present -> "未检测到。${item.detail}"
    item.bytes == null -> "无法计算大小。${item.detail}"
    else -> "${formatBytes(item.bytes)} · ${item.detail}"
}
