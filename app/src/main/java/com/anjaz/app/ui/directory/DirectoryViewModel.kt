package com.anjaz.app.ui.directory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anjaz.app.cloud.AuthRepository
import com.anjaz.app.data.DirectoryRepository
import com.anjaz.app.data.db.CampEntity
import com.anjaz.app.data.db.RegionEntity
import com.anjaz.app.data.db.SquareEntity
import com.anjaz.app.domain.Outcome
import com.anjaz.app.domain.Profile
import com.anjaz.app.schema.Choice
import com.anjaz.app.schema.ModuleSchema
import com.anjaz.app.schema.Schemas
import com.anjaz.app.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class DirKind { REGION, SQUARE, CAMP }

data class SquareRow(val square: SquareEntity, val regionName: String, val camps: List<CampEntity>)

data class DirectoryUi(
    val profile: Profile? = null,
    val regions: List<RegionEntity> = emptyList(),
    val squares: List<SquareRow> = emptyList(),
    val canRegion: Boolean = false,
    val canSquare: Boolean = false,
    val canCamp: Boolean = false,
)

data class DirForm(val kind: DirKind, val parentId: String? = null, val values: Map<String, String> = emptyMap(), val errors: Map<String, String> = emptyMap())

@HiltViewModel
class DirectoryViewModel @Inject constructor(
    private val repo: DirectoryRepository,
    auth: AuthRepository,
    private val scheduler: SyncScheduler,
) : ViewModel() {
    private val profile: StateFlow<Profile?> = auth.session.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val form = MutableStateFlow<DirForm?>(null)
    val message = MutableStateFlow<String?>(null)

    val ui: StateFlow<DirectoryUi> = combine(profile, repo.regions(), repo.squares(), repo.camps()) { p, regions, squares, camps ->
        val names = regions.associate { it.id to it.name }
        DirectoryUi(
            p, regions,
            squares.map { s -> SquareRow(s, names[s.regionId].orEmpty(), camps.filter { it.squareId == s.id }) },
            canRegion = p?.let(repo::canCreateRegion) == true, canSquare = p?.let(repo::canCreateSquare) == true, canCamp = p?.let(repo::canCreateCamp) == true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DirectoryUi())

    fun schemaFor(f: DirForm, state: DirectoryUi): ModuleSchema = when (f.kind) {
        DirKind.REGION -> Schemas.region
        DirKind.SQUARE -> Schemas.square
        DirKind.CAMP -> Schemas.camp
    }

    fun regionChoices(state: DirectoryUi) = state.regions.map { Choice(it.id, it.name) }

    fun open(kind: DirKind, parentId: String? = null) { form.value = DirForm(kind, parentId) }
    fun close() { form.value = null }
    fun change(key: String, v: String) = form.update { f -> f?.copy(values = f.values + (key to v), errors = f.errors - key) }
    fun dismissMessage() { message.value = null }

    fun submit() {
        val me = profile.value ?: return
        val f = form.value ?: return
        viewModelScope.launch {
            val r = when (f.kind) {
                DirKind.REGION -> repo.createRegion(me, f.values)
                DirKind.SQUARE -> repo.createSquare(me, f.values["regionId"] ?: f.parentId, f.values)
                DirKind.CAMP -> repo.createCamp(me, f.parentId, f.values)
            }
            when (r) {
                is Outcome.Ok -> { form.value = null; scheduler.kick() }
                is Outcome.Failure -> message.value = r.reason
            }
        }
    }
}
