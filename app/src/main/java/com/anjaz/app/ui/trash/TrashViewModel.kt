package com.anjaz.app.ui.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anjaz.app.data.Clock
import com.anjaz.app.data.DirectoryRepository
import com.anjaz.app.data.TaskRepository
import com.anjaz.app.data.Trash
import com.anjaz.app.data.db.AnjazDatabase
import com.anjaz.app.domain.Outcome
import com.anjaz.app.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class TrashKind(val label: String) { TASK("مهمة"), SQUARE("مربع"), CAMP("مخيم") }

data class TrashItem(val kind: TrashKind, val id: String, val title: String, val detail: String, val deletedAt: Long)

data class TrashUi(val items: List<TrashItem> = emptyList(), val now: Long = 0L)

@HiltViewModel
class TrashViewModel @Inject constructor(
    db: AnjazDatabase,
    private val tasks: TaskRepository,
    private val directory: DirectoryRepository,
    private val scheduler: SyncScheduler,
    private val clock: Clock,
) : ViewModel() {
    val message = MutableStateFlow<String?>(null)

    val ui: StateFlow<TrashUi> = combine(
        db.tasks().observeTrashed(), db.directory().observeTrashedSquares(), db.directory().observeTrashedCamps(),
    ) { t, s, c ->
        val list = t.map { TrashItem(TrashKind.TASK, it.id, it.title, "", it.deletedAt ?: 0L) } +
            s.map { TrashItem(TrashKind.SQUARE, it.id, it.name, it.managerName.orEmpty(), it.deletedAt ?: 0L) } +
            c.map { TrashItem(TrashKind.CAMP, it.id, it.name, it.managerName.orEmpty(), it.deletedAt ?: 0L) }
        TrashUi(list.sortedByDescending { it.deletedAt }, clock.now())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrashUi())

    fun restore(i: TrashItem) = run("أُعيد «${i.title}»") {
        when (i.kind) {
            TrashKind.TASK -> tasks.restore(i.id)
            TrashKind.SQUARE -> directory.restoreSquare(i.id)
            TrashKind.CAMP -> directory.restoreCamp(i.id)
        }
    }

    fun purge(i: TrashItem) = run("حُذف نهائياً") { purgeOne(i) }

    fun empty() = run("أُفرغت السلة") { ui.value.items.forEach { purgeOne(it) }; Outcome.Ok(Unit) }

    private suspend fun purgeOne(i: TrashItem): Outcome<Unit> = when (i.kind) {
        TrashKind.TASK -> tasks.purge(i.id)
        TrashKind.SQUARE -> directory.purgeSquare(i.id)
        TrashKind.CAMP -> directory.purgeCamp(i.id)
    }

    private fun run(done: String, block: suspend () -> Outcome<Unit>) {
        viewModelScope.launch {
            when (val r = block()) {
                is Outcome.Ok -> { message.value = done; scheduler.kick() }
                is Outcome.Failure -> message.value = r.reason
            }
        }
    }

    fun daysLeft(i: TrashItem, now: Long) = Trash.daysLeft(i.deletedAt, now)
    fun dismiss() { message.value = null }
}
