package com.anjaz.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anjaz.app.core.DayMath
import com.anjaz.app.data.DashboardCounts
import com.anjaz.app.data.DashboardRepository
import com.anjaz.app.data.db.TaskEntity
import com.anjaz.app.ui.components.DayChip
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val loading: Boolean = true,
    val counts: DashboardCounts = DashboardCounts(0, 0, 0, 0, 0),
    val days: List<DayChip> = emptyList(),
    val selectedDay: Int = 0,
    val dayTasks: List<TaskEntity> = emptyList(),
    val unsynced: Int = 0,
    val now: Long = 0L,
    val openSos: Int = 0,
)

@HiltViewModel
class HomeViewModel @Inject constructor(private val repo: DashboardRepository) : ViewModel() {
    private val selected = MutableStateFlow(0)
    private val start = DayMath.startOfDay(repo.now())

    val state: StateFlow<HomeUiState> = combine(
        repo.counts(), repo.week(start), repo.unsynced(), selected, repo.openSos(),
    ) { counts, week, unsynced, sel, openSos ->
        val chips = (0..6).map { i ->
            val day = DayMath.addDays(start, i)
            val next = DayMath.addDays(start, i + 1)
            DayChip(i, DayMath.weekday(day), DayMath.dayOfMonth(day), week.any { (it.deadline ?: 0L) in day until next })
        }
        val from = DayMath.addDays(start, sel)
        val to = DayMath.addDays(start, sel + 1)
        HomeUiState(
            loading = false, counts = counts, days = chips, selectedDay = sel,
            dayTasks = week.filter { (it.deadline ?: 0L) in from until to }, unsynced = unsynced, now = repo.now(), openSos = openSos,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun select(offset: Int) { selected.value = offset }
}
