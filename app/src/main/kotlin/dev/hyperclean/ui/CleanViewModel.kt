package dev.hyperclean.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.hyperclean.clean.CleanCatalog
import dev.hyperclean.clean.CleanRepository
import dev.hyperclean.clean.FileUsage
import dev.hyperclean.clean.Section
import dev.hyperclean.clean.SuRootShell
import dev.hyperclean.clean.formatBytes
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ItemUi(
    val id: String,
    val section: Section,
    val title: String,
    val detail: String,
    val present: Boolean,
    val bytes: Long?,
    val selected: Boolean,
    val rebootHint: Boolean,
    val files: List<FileUsage> = emptyList(),
)

data class UiState(
    val scanning: Boolean = true,
    val cleaning: Boolean = false,
    val rebooting: Boolean = false,
    val hasScanned: Boolean = false,
    val rootGranted: Boolean = false,
    val rootMessage: String? = null,
    val items: List<ItemUi> = emptyList(),
) {
    val busy: Boolean get() = scanning || cleaning || rebooting

    val selectedPresent: List<ItemUi> get() = items.filter { it.selected && it.present }

    val selectedBytes: Long get() = selectedPresent.sumOf { it.bytes ?: 0L }
}

sealed interface CleanEvent {
    data class Message(val text: String) : CleanEvent

    data object AskReboot : CleanEvent
}

class CleanViewModel(
    private val repository: CleanRepository = CleanRepository(SuRootShell()),
) : ViewModel() {

    private val stateFlow = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = stateFlow.asStateFlow()

    private val eventFlow = MutableSharedFlow<CleanEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<CleanEvent> = eventFlow.asSharedFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { refreshInternal() }
    }

    fun toggle(id: String) {
        stateFlow.update { current ->
            current.copy(
                items = current.items.map { item ->
                    if (item.id == id && item.present && !current.busy) {
                        item.copy(selected = !item.selected)
                    } else {
                        item
                    }
                },
            )
        }
    }

    fun toggleAll() {
        stateFlow.update { current ->
            if (current.busy) return@update current
            val present = current.items.filter { it.present }
            val select = present.any { !it.selected }
            current.copy(
                items = current.items.map { item ->
                    if (item.present) item.copy(selected = select) else item
                },
            )
        }
    }

    fun clean(rebootAfter: Boolean) {
        val current = stateFlow.value
        val selected = current.selectedPresent
        if (selected.isEmpty() || current.busy || !current.rootGranted) return
        val freed = current.selectedBytes
        val packageCleaned = selected.any { it.rebootHint }
        viewModelScope.launch {
            stateFlow.update { it.copy(cleaning = true) }
            val report = repository.clean(selected.map { it.id })
            stateFlow.update { it.copy(cleaning = false) }
            if (report.failed.isEmpty()) {
                eventFlow.emit(CleanEvent.Message("已清理 ${formatBytes(freed)}"))
            } else {
                eventFlow.emit(CleanEvent.Message("部分项目清理失败：${report.failed.joinToString("、")}"))
            }
            refreshInternal()
            if (report.failed.isEmpty() && rebootAfter) {
                rebootInternal()
            } else if (report.failed.isEmpty() && packageCleaned) {
                eventFlow.emit(CleanEvent.AskReboot)
            }
        }
    }

    fun reboot() {
        val current = stateFlow.value
        if (!current.rootGranted || current.busy) return
        viewModelScope.launch { rebootInternal() }
    }

    private suspend fun refreshInternal() {
        val current = stateFlow.value
        if (current.cleaning || current.rebooting) return
        stateFlow.update { it.copy(scanning = true) }
        val snapshot = repository.scan()
        stateFlow.update { old ->
            old.copy(
                scanning = false,
                hasScanned = true,
                rootGranted = snapshot.rootGranted,
                rootMessage = snapshot.message,
                items = snapshot.rows.map { row ->
                    val spec = CleanCatalog.require(row.id)
                    val previous = old.items.firstOrNull { it.id == row.id }
                    ItemUi(
                        id = spec.id,
                        section = spec.section,
                        title = spec.title,
                        detail = spec.detail,
                        present = row.present,
                        bytes = row.bytes,
                        selected = previous?.selected ?: row.present,
                        rebootHint = spec.rebootHint,
                        files = row.files.sortedByDescending { it.bytes },
                    )
                },
            )
        }
    }

    private suspend fun rebootInternal() {
        stateFlow.update { it.copy(rebooting = true) }
        val result = repository.reboot()
        stateFlow.update { it.copy(rebooting = false) }
        if (!result.started) {
            eventFlow.emit(CleanEvent.Message(result.message ?: "重启失败"))
        }
    }
}
