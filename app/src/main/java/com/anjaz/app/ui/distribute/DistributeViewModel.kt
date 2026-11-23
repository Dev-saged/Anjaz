package com.anjaz.app.ui.distribute

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anjaz.app.cloud.AuthRepository
import com.anjaz.app.data.DirectoryRepository
import com.anjaz.app.data.TaskRepository
import com.anjaz.app.data.db.CampEntity
import com.anjaz.app.data.db.SquareEntity
import com.anjaz.app.domain.Outcome
import com.anjaz.app.domain.Plural
import com.anjaz.app.domain.Weights
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

data class CampShare(val camp: CampEntity, val families: Int, val amount: Long)
data class SquareShare(val square: SquareEntity, val families: Int, val weight: String, val amount: Long, val camps: List<CampShare>, val included: Boolean)

data class DistributeInput(
    val item: String = "",
    val quantity: String = "",
    val unit: String = "",
    val excluded: Set<String> = emptySet(),
    val deadline: Long? = null,
    val urgent: Boolean = false,
)

data class DistributeUi(
    val input: DistributeInput = DistributeInput(),
    val shares: List<SquareShare> = emptyList(),
    val total: Long = 0,
    val families: Int = 0,
    val canSend: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
) {
    val allocated: Long get() = shares.filter { it.included }.sumOf { it.amount }
}

@HiltViewModel
class DistributeViewModel @Inject constructor(
    auth: AuthRepository,
    directory: DirectoryRepository,
    private val tasks: TaskRepository,
    private val scheduler: SyncScheduler,
) : ViewModel() {
    private val profile = auth.session.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val input = MutableStateFlow(DistributeInput())
    private val status = MutableStateFlow(Triple<Boolean, String?, String?>(false, null, null))

    val ui: StateFlow<DistributeUi> = combine(profile, directory.squares(), directory.camps(), input, status) { p, sq, cp, inp, st ->
        if (p == null) return@combine DistributeUi()
        val squares = DirectoryRepository.squaresInScope(p, sq)
        val camps = DirectoryRepository.campsInScope(p, cp)
        val total = com.anjaz.app.domain.Username.cleanSecret(inp.quantity).filter(Char::isDigit).take(12).toLongOrNull() ?: 0L
        val fam = squares.associate { s -> s.id to camps.filter { it.squareId == s.id }.sumOf { it.families ?: 0 } }
        val included = squares.filter { it.id !in inp.excluded }
        val includedFamilies = included.sumOf { fam[it.id] ?: 0 }
        val amounts = Weights.allocate(total, included.map { fam[it.id] ?: 0 })
        val bySquare = included.indices.associate { included[it].id to amounts[it] }
        val shares = squares.map { s ->
            val own = camps.filter { it.squareId == s.id }.sortedByDescending { it.families ?: 0 }
            val amount = bySquare[s.id] ?: 0L
            val split = Weights.allocate(amount, own.map { it.families ?: 0 })
            SquareShare(
                s, fam[s.id] ?: 0, Weights.percent(fam[s.id] ?: 0, includedFamilies), amount,
                own.mapIndexed { i, c -> CampShare(c, c.families ?: 0, split.getOrElse(i) { 0L }) }, s.id !in inp.excluded,
            )
        }.sortedByDescending { it.families }
        DistributeUi(inp, shares, total, includedFamilies, p.canAssign, st.first, st.second, st.third)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DistributeUi())

    fun edit(f: (DistributeInput) -> DistributeInput) { input.update(f); status.value = Triple(false, null, null) }
    fun toggle(id: String) = edit { it.copy(excluded = if (id in it.excluded) it.excluded - id else it.excluded + id) }
    fun dismiss() { status.value = Triple(false, null, null) }

    private fun label(u: DistributeUi): String = listOf(u.input.item.trim().ifEmpty { "الكمية" }, u.input.unit.trim()).filter { it.isNotEmpty() }.joinToString(" ")

    /** نص الخطة للمشاركة (واتساب أو غيره). */
    fun shareText(u: DistributeUi): String = buildString {
        appendLine("خطة توزيع: ${Plural.grouped(u.total.toInt())} ${label(u)}")
        appendLine("حسب عدد الأسر (${Plural.grouped(u.families)} أسرة)")
        u.shares.filter { it.included }.forEach { s ->
            appendLine()
            appendLine("${s.square.name}: ${Plural.grouped(s.amount.toInt())} (${s.weight} — ${Plural.grouped(s.families)} أسرة)")
            s.camps.forEach { c -> appendLine("  • ${c.camp.name}: ${Plural.grouped(c.amount.toInt())} (${Plural.grouped(c.families)} أسرة)") }
        }
    }

    fun send() {
        val u = ui.value
        val p = profile.value ?: return
        when {
            u.total <= 0 -> { status.value = Triple(false, null, "اكتب الكمية المراد توزيعها"); return }
            u.families == 0 -> { status.value = Triple(false, null, "لا يوجد عدد أسر مسجّل للمربعات المختارة؛ أكمل أعداد الأسر في المخيمات أولاً"); return }
        }
        status.value = Triple(true, null, null)
        val name = label(u)
        val items = u.shares.filter { it.included && it.amount > 0 }.map { s ->
            val desc = buildString {
                appendLine("حصة ${s.square.name}: ${Plural.grouped(s.amount.toInt())} $name من أصل ${Plural.grouped(u.total.toInt())}، بوزن ${s.weight} حسب عدد الأسر.")
                if (s.camps.isNotEmpty()) appendLine("التوزيع على المخيمات:")
                s.camps.forEach { c -> appendLine("• ${c.camp.name}: ${Plural.grouped(c.amount.toInt())} (${Plural.grouped(c.families)} أسرة)") }
            }.trim()
            Triple(s.square, "توزيع ${Plural.grouped(s.amount.toInt())} $name", desc)
        }
        viewModelScope.launch {
            when (val r = tasks.createDistribution(p, items, u.input.deadline, u.input.urgent)) {
                is Outcome.Ok -> { status.value = Triple(false, "أُرسلت ${r.value} مهمة توزيع إلى المربعات", null); scheduler.kick() }
                is Outcome.Failure -> status.value = Triple(false, null, r.reason)
            }
        }
    }
}
