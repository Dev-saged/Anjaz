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
) {
    companion object {
        fun defaults() = mapOf("title" to "", "description" to "", "priority" to "NORMAL", "deadline" to "today")
    }
}

@HiltViewModel
class TasksViewModel @Inject constructor(
    private val queries: TaskQueries,
    private val repo: TaskRepository,
    private val auth: AuthRepository,
    private val scheduler: SyncScheduler,
) : ViewModel() {
    private val profile: StateFlow<Profile?> = auth.session.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val team: StateFlow<List<Choice>> = queries.team()
        .map { list -> list.map { Choice(it.id, it.displayName) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    fun setFilter(f: TaskFilter) { filter.value = f }

    fun openForm() { if (profile.value?.canAssign == true) _form.value = NewTaskState(visible = true) }
    fun closeForm() { _form.update { it.copy(visible = false) } }

    fun edit(key: String, value: String) {
        _form.update { it.copy(values = it.values + (key to value), errors = it.errors - key) }
    }

    fun submit() {
        val cur = _form.value
        val p = profile.value ?: return
        if (cur.saving) return
        val errors = SchemaValidator.validate(Schemas.taskWithAssignee(team.value), cur.values)
        if (errors.isNotEmpty()) { _form.update { it.copy(errors = errors) }; return }
        _form.update { it.copy(saving = true) }
        viewModelScope.launch {
            when (val r = repo.create(cur.values, p.uid, p.regionId, p.squareId, cur.values["assigneeId"])) {
                is Outcome.Ok -> { _form.value = NewTaskState(); _message.value = "تم حفظ المهمة"; scheduler.kick() }
                is Outcome.Failure -> { _form.update { it.copy(saving = false) }; _message.value = r.reason }
            }
        }
    }

    fun move(task: TaskEntity, to: TaskStatus) {
        val p = profile.value ?: return
        viewModelScope.launch {
            val r = repo.transition(task.id, to, p.uid)
            if (r is Outcome.Failure) _message.value = r.reason else scheduler.kick()
        }
    }

    fun consumeMessage() { _message.value = null }
}
