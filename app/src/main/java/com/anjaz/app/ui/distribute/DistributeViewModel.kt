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
data class SubShare(val square: SquareEntity, val families: Int, val weight: String, val amount: Long, val camps: List<CampShare>)
data class SquareShare(val square: SquareEntity, val families: Int, val weight: String, val amount: Long, val camps: List<CampShare>, val included: Boolean, val subs: List<SubShare> = emptyList())

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
        // التوزيع على المربعات الرئيسية فقط؛ الفرعية تأخذ من حصة رئيسيها
        val tree = com.anjaz.app.domain.SquareTree.build(squares, camps)
        val included = tree.filter { it.square.id !in inp.excluded }
        val includedFamilies = included.sumOf { it.families }
        val amounts = Weights.allocate(total, included.map { it.families })
        val bySquare = included.indices.associate { included[it].square.id to amounts[it] }
        fun campShares(list: List<CampEntity>, amount: Long): List<CampShare> {
            val sorted = list.sortedByDescending { it.families ?: 0 }
            val split = Weights.allocate(amount, sorted.map { it.families ?: 0 })
            return sorted.mapIndexed { i, c -> CampShare(c, c.families ?: 0, split.getOrElse(i) { 0L }) }
        }
        val shares = tree.map { t ->
            val amount = bySquare[t.square.id] ?: 0L
            // داخل الرئيسي: مخيماته المباشرة ومربعاته الفرعية وحدات بأوزان أسرها
            val own = t.camps.sortedByDescending { it.families ?: 0 }
            val units = own.map { it.families ?: 0 } + t.subs.map { it.families }
            val split = Weights.allocate(amount, units)
            val ownShares = own.mapIndexed { i, c -> CampShare(c, c.families ?: 0, split.getOrElse(i) { 0L }) }
            val subShares = t.subs.mapIndexed { k, sub ->
                val a = split.getOrElse(own.size + k) { 0L }
                SubShare(sub.square, sub.families, Weights.percent(sub.families, t.families), a, campShares(sub.camps, a))
            }
            SquareShare(t.square, t.families, Weights.percent(t.families, includedFamilies), amount, ownShares, t.square.id !in inp.excluded, subShares)
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
            s.subs.forEach { sub ->
                appendLine("  ${sub.square.name}: ${Plural.grouped(sub.amount.toInt())} (${sub.weight} من ${s.square.name})")
                sub.camps.forEach { c -> appendLine("    • ${c.camp.name}: ${Plural.grouped(c.amount.toInt())} (${Plural.grouped(c.families)} أسرة)") }
            }
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
                if (s.camps.isNotEmpty() || s.subs.isNotEmpty()) appendLine("التوزيع:")
                s.camps.forEach { c -> appendLine("• ${c.camp.name}: ${Plural.grouped(c.amount.toInt())} (${Plural.grouped(c.families)} أسرة)") }
                s.subs.forEach { sub ->
                    appendLine("• مربع ${sub.square.name}: ${Plural.grouped(sub.amount.toInt())}")
                    sub.camps.forEach { c -> appendLine("   - ${c.camp.name}: ${Plural.grouped(c.amount.toInt())} (${Plural.grouped(c.families)} أسرة)") }
                }
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
