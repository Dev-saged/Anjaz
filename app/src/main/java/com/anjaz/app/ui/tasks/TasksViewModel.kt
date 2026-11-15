package com.anjaz.app.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anjaz.app.cloud.AuthRepository
import com.anjaz.app.domain.Profile
import com.anjaz.app.schema.Choice
import com.anjaz.app.sync.SyncScheduler
import kotlinx.coroutines.flow.map
import com.anjaz.app.data.TaskQueries
import com.anjaz.app.data.TaskRepository
import com.anjaz.app.data.db.TaskEntity
import com.anjaz.app.domain.Outcome
import com.anjaz.app.domain.TaskStatus
import com.anjaz.app.schema.SchemaValidator
import com.anjaz.app.schema.Schemas
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class TaskFilter(val label: String, val match: (TaskStatus) -> Boolean) {
    ALL("الكل", { true }),
    OPEN("مفتوحة", { !it.terminal && it != TaskStatus.AWAITING_REVIEW }),
    REVIEW("بانتظار الاعتماد", { it == TaskStatus.AWAITING_REVIEW }),
    DONE("منتهية", { it.terminal }),
}

data class TasksUiState(
    val loading: Boolean = true,
    val filter: TaskFilter = TaskFilter.ALL,
    val tasks: List<TaskEntity> = emptyList(),
    val now: Long = 0L,
)

data class NewTaskState(
    val visible: Boolean = false,
    val values: Map<String, String> = defaults(),
    val errors: Map<String, String> = emptyMap(),
    val saving: Boolean = false,
    val deadline: Long? = null,
    val kind: com.anjaz.app.data.TargetKind? = null,
    val targets: Set<String> = emptySet(),
) {
    companion object {
        fun defaults() = mapOf("title" to "", "description" to "", "priority" to "NORMAL")
    }
}

@HiltViewModel
class TasksViewModel @Inject constructor(
    private val queries: TaskQueries,
    private val repo: TaskRepository,
    private val auth: AuthRepository,
    private val scheduler: SyncScheduler,
    private val engine: com.anjaz.app.sync.SyncEngine,
    private val directory: com.anjaz.app.data.DirectoryRepository,
) : ViewModel() {
    private val profile: StateFlow<Profile?> = auth.session.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val team: StateFlow<List<Choice>> = queries.team()
        .map { list -> list.map { Choice(it.id, it.displayName) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val squares: StateFlow<List<com.anjaz.app.data.db.SquareEntity>> = combine(profile, directory.squares()) { p, all ->
        p?.let { com.anjaz.app.data.DirectoryRepository.squaresInScope(it, all) }.orEmpty()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val camps: StateFlow<List<com.anjaz.app.data.db.CampEntity>> = combine(profile, directory.camps()) { p, all ->
        p?.let { com.anjaz.app.data.DirectoryRepository.campsInScope(it, all) }.orEmpty()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** أسماء الجهات والأشخاص لعرض «لمن هذه المهمة» على البطاقات. */
    val names: StateFlow<Map<String, String>> = combine(queries.team(), squares, camps) { users, sq, cp ->
        users.associate { it.id to it.displayName } + sq.associate { it.id to it.name } + cp.associate { it.id to it.name }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val canDelete: Boolean get() = profile.value?.let(repo::canDelete) == true

    private val _undo = MutableStateFlow<String?>(null)
    val undo: StateFlow<String?> = _undo

    private val filter = MutableStateFlow(TaskFilter.ALL)
    private val _form = MutableStateFlow(NewTaskState())
    private val _message = MutableStateFlow<String?>(null)

    val form: StateFlow<NewTaskState> = _form
    val message: StateFlow<String?> = _message

    val state: StateFlow<TasksUiState> = combine(queries.all(), filter) { all, f ->
        TasksUiState(
            loading = false, filter = f, now = queries.now(),
            tasks = all.filter { f.match(TaskStatus.parse(it.status)) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TasksUiState())

    private var eventsJob: kotlinx.coroutines.Job? = null
    private val watched = MutableStateFlow<String?>(null)

    /** سجل أحداث المهمة المفتوحة: يُسحب من السحابة ويُقرأ محلياً. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val history: StateFlow<List<com.anjaz.app.data.db.TaskEventEntity>> = watched
        .flatMapLatest { id -> if (id == null) kotlinx.coroutines.flow.flowOf(emptyList()) else queries.events(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun watch(taskId: String?) {
        eventsJob?.cancel(); watched.value = taskId
        eventsJob = taskId?.let { engine.listenTask(viewModelScope, it) }
    }

    fun setFilter(f: TaskFilter) { filter.value = f }

    fun openForm() { if (profile.value?.canAssign == true) _form.value = NewTaskState(visible = true) }
    fun closeForm() { _form.update { it.copy(visible = false) } }

    fun setDeadline(ms: Long?) { _form.update { it.copy(deadline = ms, errors = it.errors - "deadline") } }

    fun pickKind(k: com.anjaz.app.data.TargetKind) {
        _form.update { it.copy(kind = k, targets = emptySet(), errors = it.errors - "target") }
    }

    fun toggleTarget(id: String) {
        _form.update { f ->
            val multi = f.kind == com.anjaz.app.data.TargetKind.PERSONS
            val next = if (multi) (if (id in f.targets) f.targets - id else f.targets + id) else setOf(id)
            f.copy(targets = next, errors = f.errors - "target")
        }
    }

    fun duplicate(t: TaskEntity) {
        if (profile.value?.canAssign != true) return
        _form.value = NewTaskState(
            visible = true,
            values = mapOf("title" to t.title, "description" to t.description, "priority" to t.priority),
        )
    }

    fun edit(key: String, value: String) {
        _form.update { it.copy(values = it.values + (key to value), errors = it.errors - key) }
    }

    fun submit() {
        val cur = _form.value
        val p = profile.value ?: return
        if (cur.saving) return
        val errors = SchemaValidator.validate(Schemas.task, cur.values).toMutableMap()
        val kind = cur.kind
        if (kind == null) errors["target"] = "حدّد المكلّف بالمهمة"
        else if (kind.needsPick && cur.targets.isEmpty()) errors["target"] = "اختر من القائمة"
        if (errors.isNotEmpty()) { _form.update { it.copy(errors = errors) }; return }
        _form.update { it.copy(saving = true) }
        viewModelScope.launch {
            val target = com.anjaz.app.data.TaskTarget(kind!!, cur.targets.toList())
            when (val r = repo.createBatch(cur.values, p, cur.deadline, target)) {
                is Outcome.Ok -> {
                    _form.value = NewTaskState()
                    _message.value = if (r.value > 1) "تم إنشاء ${r.value} مهمة" else "تم حفظ المهمة"
                    scheduler.kick()
                }
                is Outcome.Failure -> { _form.update { it.copy(saving = false) }; _message.value = r.reason }
            }
        }
    }

    fun delete(task: TaskEntity) {
        val p = profile.value ?: return
        viewModelScope.launch {
            when (val r = repo.delete(task.id, p)) {
                is Outcome.Ok -> { _undo.value = task.id; scheduler.kick() }
                is Outcome.Failure -> _message.value = r.reason
            }
        }
    }

    fun undoDelete(id: String) {
        viewModelScope.launch { repo.restore(id); scheduler.kick(); _message.value = "أُعيدت المهمة" }
    }

    fun consumeUndo() { _undo.value = null }

    fun move(task: TaskEntity, to: TaskStatus) {
        val p = profile.value ?: return
        viewModelScope.launch {
            val r = repo.transition(task.id, to, p.uid)
            if (r is Outcome.Failure) _message.value = r.reason else scheduler.kick()
        }
    }

    fun consumeMessage() { _message.value = null }
}
