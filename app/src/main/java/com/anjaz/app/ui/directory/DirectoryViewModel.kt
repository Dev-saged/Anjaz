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

/** قسم منطقة في الدليل؛ region=null للمربعات التي لا منطقة لها. */
data class RegionSection(val region: RegionEntity?, val squares: List<SquareRow>) {
    val camps: Int get() = squares.sumOf { it.camps.size }
    val families: Int get() = squares.sumOf { r -> r.camps.sumOf { it.families ?: 0 } }
}

data class DirectoryUi(
    val profile: Profile? = null,
    val regions: List<RegionEntity> = emptyList(),
    val squares: List<SquareRow> = emptyList(),
    val sections: List<RegionSection> = emptyList(),
    val canRegion: Boolean = false,
    val canSquare: Boolean = false,
    val canCamp: Boolean = false,
)

data class DirForm(
    val kind: DirKind, val parentId: String? = null, val values: Map<String, String> = emptyMap(), val errors: Map<String, String> = emptyMap(),
    val editId: String? = null, val convertFrom: String? = null, val error: String? = null, val busy: Boolean = false,
)

@HiltViewModel
class DirectoryViewModel @Inject constructor(
    private val repo: DirectoryRepository,
    auth: AuthRepository,
    private val scheduler: SyncScheduler,
    private val accounts: com.anjaz.app.data.AccountsRepository,
) : ViewModel() {
    private val profile: StateFlow<Profile?> = auth.session.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val form = MutableStateFlow<DirForm?>(null)
    val message = MutableStateFlow<String?>(null)

    val ui: StateFlow<DirectoryUi> = combine(profile, repo.regions(), repo.squares(), repo.camps()) { p, regions, squares, camps ->
        val names = regions.associate { it.id to it.name }
        val scoped = p?.let { DirectoryRepository.squaresInScope(it, squares) } ?: emptyList()
        val rows = scoped.map { s -> SquareRow(s, names[s.regionId].orEmpty(), camps.filter { it.squareId == s.id && it.deletedAt == null }) }
        val visibleRegions = if (p?.roleEnum == com.anjaz.app.domain.Role.CENTRAL) regions else regions.filter { it.id == p?.regionId }
        val sections = visibleRegions.map { r -> RegionSection(r, rows.filter { it.square.regionId == r.id }) } +
            rows.filter { it.square.regionId !in names }.takeIf { it.isNotEmpty() }?.let { listOf(RegionSection(null, it)) }.orEmpty()
        DirectoryUi(
            p, regions,
            rows, sections,
            canRegion = p?.let(repo::canCreateRegion) == true, canSquare = p?.let(repo::canCreateSquare) == true, canCamp = p?.let(repo::canCreateCamp) == true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DirectoryUi())

    /** النموذج حسب الحالة: منطقة عند إضافة/نقل مربع (للمركزي)، ومربع عند نقل مخيم. */
    fun schemaFor(f: DirForm, state: DirectoryUi): ModuleSchema {
        val central = state.profile?.roleEnum == com.anjaz.app.domain.Role.CENTRAL
        return when (f.kind) {
            DirKind.REGION -> Schemas.region.copy(title = if (f.editId != null) "تعديل المنطقة" else "منطقة جديدة")
            DirKind.SQUARE -> {
                val title = when { f.convertFrom != null -> "تحويل إلى مربع"; f.editId != null -> "تعديل المربع"; else -> "مربع جديد" }
                val regions = regionChoices(state).filter { it.key != f.convertFrom }
                val withRegion = central && (f.editId != null || f.convertFrom != null || f.parentId == null)
                val extra = if (withRegion) listOf(com.anjaz.app.schema.FieldSchema("regionId", if (f.editId != null) "المنطقة (غيّرها لنقل المربع)" else "المنطقة التابع لها", com.anjaz.app.schema.FieldType.CHOICE, required = true, choices = regions)) else emptyList()
                Schemas.square.copy(title = title, fields = extra + Schemas.square.fields)
            }
            DirKind.CAMP -> {
                val extra = if (f.editId != null && state.squares.size > 1) listOf(com.anjaz.app.schema.FieldSchema("squareId", "المربع (غيّره لنقل المخيم)", com.anjaz.app.schema.FieldType.CHOICE, required = true,
                    choices = state.squares.map { Choice(it.square.id, it.square.name + if (it.regionName.isNotEmpty() && central) " — " + it.regionName else "") })) else emptyList()
                Schemas.camp.copy(title = if (f.editId != null) "تعديل المخيم" else "مخيم جديد", fields = extra + Schemas.camp.fields)
            }
        }
    }

    fun regionChoices(state: DirectoryUi) = state.regions.map { Choice(it.id, it.name) }

    fun open(kind: DirKind, parentId: String? = null) {
        form.value = DirForm(kind, parentId, values = if (kind == DirKind.SQUARE && parentId != null) mapOf("regionId" to parentId) else emptyMap())
    }

    fun openConvert(region: RegionEntity) {
        form.value = DirForm(DirKind.SQUARE, convertFrom = region.id, values = mapOf("name" to region.name))
    }
    fun close() { form.value = null }

    fun openEdit(kind: DirKind, id: String) {
        val u = ui.value
        val values: Map<String, String>? = when (kind) {
            DirKind.SQUARE -> u.squares.firstOrNull { it.square.id == id }?.square?.let {
                mapOf("name" to it.name, "managerName" to it.managerName.orEmpty(), "idNumber" to it.idNumber.orEmpty(), "phone" to it.phone.orEmpty(), "regionId" to it.regionId)
            }
            DirKind.CAMP -> u.squares.flatMap { it.camps }.firstOrNull { it.id == id }?.let {
                mapOf("name" to it.name, "managerName" to it.managerName.orEmpty(), "families" to (it.families?.toString() ?: ""), "idNumber" to it.idNumber.orEmpty(), "phone" to it.phone.orEmpty(), "squareId" to it.squareId)
            }
            DirKind.REGION -> u.regions.firstOrNull { it.id == id }?.let { mapOf("name" to it.name, "phone" to it.phone.orEmpty()) }
        }
        values?.let { form.value = DirForm(kind, values = it, editId = id) }
    }

    fun trashEditing() {
        val me = profile.value ?: return
        val f = form.value ?: return
        val id = f.editId ?: return
        viewModelScope.launch {
            val r = when (f.kind) {
                DirKind.SQUARE -> repo.trashSquare(me, id)
                DirKind.CAMP -> repo.trashCamp(me, id)
                DirKind.REGION -> repo.trashRegion(me, id)
            }
            when (r) {
                is Outcome.Ok -> { form.value = null; message.value = "نُقل إلى سلة المهملات"; scheduler.kick() }
                is Outcome.Failure -> form.update { it?.copy(error = r.reason) }
            }
        }
    }
    fun change(key: String, v: String) = form.update { f -> f?.copy(values = f.values + (key to v), errors = f.errors - key, error = null) }
    fun dismissFormError() = form.update { it?.copy(error = null) }
    fun dismissMessage() { message.value = null }

    fun submit() {
        val me = profile.value ?: return
        val f = form.value ?: return
        if (f.busy) return
        val schema = schemaFor(f, ui.value)
        val errors = com.anjaz.app.schema.SchemaValidator.validate(schema, f.values)
        if (errors.isNotEmpty()) { form.update { it?.copy(errors = errors, error = "أكمل الحقول المطلوبة المعلَّمة باللون الأحمر") }; return }
        form.update { it?.copy(busy = true) }
        viewModelScope.launch {
            val movedFrom = f.editId?.takeIf { f.kind == DirKind.SQUARE }?.let { repo.squareById(it)?.regionId }
            val r: Outcome<*> = when {
                f.convertFrom != null -> repo.convertRegionToSquare(me, f.convertFrom, f.values["regionId"], f.values)
                f.editId != null -> when (f.kind) {
                    DirKind.SQUARE -> repo.updateSquare(me, f.editId, f.values)
                    DirKind.CAMP -> repo.updateCamp(me, f.editId, f.values)
                    DirKind.REGION -> repo.updateRegion(me, f.editId, f.values)
                }
                else -> when (f.kind) {
                    DirKind.REGION -> repo.createRegion(me, f.values)
                    DirKind.SQUARE -> repo.createSquare(me, f.values["regionId"] ?: f.parentId, f.values)
                    DirKind.CAMP -> repo.createCamp(me, f.parentId, f.values)
                }
            }
            when (r) {
                is Outcome.Ok -> {
                    form.value = null
                    message.value = when {
                        f.convertFrom != null -> "حُوّلت إلى مربع، ونُقلت المنطقة القديمة إلى السلة"
                        f.editId != null -> "حُفظت التعديلات"
                        else -> "أُضيف بنجاح"
                    }
                    val target = f.values["regionId"]
                    if (f.kind == DirKind.SQUARE && f.editId != null && movedFrom != null && target != null && target != movedFrom) moveAccounts(f.editId, target)
                    scheduler.kick()
                }
                is Outcome.Failure -> form.update { it?.copy(busy = false, error = r.reason) }
            }
        }
    }

    /** حسابات المربع المنقول تنتقل معه إلى المنطقة الجديدة. */
    private suspend fun moveAccounts(squareId: String, regionId: String) {
        val users = repo.usersOfSquare(squareId)
        var failed = 0
        users.forEach { u ->
            val r = accounts.update(u.id, mapOf("displayName" to u.displayName, "role" to u.role, "regionId" to regionId, "squareId" to squareId))
            if (r is Outcome.Failure) failed++
        }
        if (users.isNotEmpty()) message.value = if (failed == 0) "نُقل المربع ومخيماته و${users.size} حساب إلى المنطقة الجديدة" else "نُقل المربع؛ تعذّر نقل $failed حساب، عدّلها من شاشة الحسابات"
    }
}
