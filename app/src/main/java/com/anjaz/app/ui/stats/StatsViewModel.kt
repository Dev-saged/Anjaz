package com.anjaz.app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anjaz.app.cloud.AuthRepository
import com.anjaz.app.data.DirectoryRepository
import com.anjaz.app.data.db.CampEntity
import com.anjaz.app.data.db.SquareEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** مربع رئيسي في الإحصائيات؛ camps تشمل مخيمات مربعاته الفرعية، وown مخيماته المباشرة. */
data class SquareStat(
    val square: SquareEntity,
    val camps: List<CampEntity>,
    val own: List<CampEntity> = camps,
    val subs: List<com.anjaz.app.domain.SubSquare> = emptyList(),
) {
    val families: Int get() = camps.sumOf { it.families ?: 0 }
    val missing: Int get() = camps.count { it.families == null }
}

data class RegionChip(val id: String?, val name: String)

data class StatsUi(
    val squares: List<SquareStat> = emptyList(),
    val regions: List<RegionChip> = emptyList(),
    val region: String? = null,
    val loaded: Boolean = false,
) {
    val camps: Int get() = squares.sumOf { it.camps.size }
    val families: Int get() = squares.sumOf { it.families }
    val missing: Int get() = squares.sumOf { it.missing }
    val maxFamilies: Int get() = squares.maxOfOrNull { it.families } ?: 0
    val average: Int get() = if (camps == 0) 0 else families / camps
}

@HiltViewModel
class StatsViewModel @Inject constructor(auth: AuthRepository, repo: DirectoryRepository) : ViewModel() {
    private val region = MutableStateFlow<String?>(null)

    val ui: StateFlow<StatsUi> = combine(auth.session, repo.squares(), repo.camps(), repo.regions(), region) { p, sq, cp, rg, sel ->
        if (p == null) return@combine StatsUi(loaded = true)
        val squares = DirectoryRepository.squaresInScope(p, sq)
        val camps = DirectoryRepository.campsInScope(p, cp)
        val regionIds = squares.map { it.regionId }.distinct()
        val chips = if (regionIds.size > 1) listOf(RegionChip(null, "الكل")) + rg.filter { it.id in regionIds }.map { RegionChip(it.id, it.name) } else emptyList()
        val shown = squares.filter { sel == null || it.regionId == sel }
        StatsUi(
            squares = com.anjaz.app.domain.SquareTree.build(shown, camps).map { t ->
                SquareStat(t.square, t.allCamps, t.camps.sortedByDescending { it.families ?: -1 }, t.subs)
            }.sortedByDescending { it.families },
            regions = chips, region = sel, loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUi())

    fun pickRegion(id: String?) { region.value = id }
}
