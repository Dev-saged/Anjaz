package com.anjaz.app.domain

import com.anjaz.app.data.db.CampEntity
import com.anjaz.app.data.db.SquareEntity

/** مربع فرعي داخل مربع رئيسي، بمخيماته. */
data class SubSquare(val square: SquareEntity, val camps: List<CampEntity>) {
    val families: Int get() = camps.sumOf { it.families ?: 0 }
}

/** مربع رئيسي: مخيماته المباشرة + مربعاته الفرعية. الأرقام تشمل الفرعية. */
data class TopSquare(val square: SquareEntity, val camps: List<CampEntity>, val subs: List<SubSquare>) {
    val allCamps: List<CampEntity> get() = camps + subs.flatMap { it.camps }
    val families: Int get() = allCamps.sumOf { it.families ?: 0 }
    val campCount: Int get() = allCamps.size
    val missing: Int get() = allCamps.count { it.families == null }
}

object SquareTree {
    /**
     * يبني الشجرة: المربع الذي أبوه موجود ومرئي يصبح فرعياً تحته، وإلا يظهر رئيسياً
     * (فلا يختفي مربع لأن أباه خارج النطاق أو في السلة).
     */
    fun build(squares: List<SquareEntity>, camps: List<CampEntity>): List<TopSquare> {
        val live = squares.filter { it.deletedAt == null }
        val ids = live.map { it.id }.toSet()
        val campsBy = camps.filter { it.deletedAt == null }.groupBy { it.squareId }
        val isSub = { s: SquareEntity -> s.parentId != null && s.parentId != s.id && s.parentId in ids && live.first { it.id == s.parentId }.parentId == null }
        val subsBy = live.filter(isSub).groupBy { it.parentId }
        return live.filterNot(isSub).map { top ->
            TopSquare(
                top, campsBy[top.id].orEmpty(),
                subsBy[top.id].orEmpty().sortedBy { it.name }.map { SubSquare(it, campsBy[it.id].orEmpty()) },
            )
        }
    }

    /** اسم كامل للعرض في القوائم: «سنابل › الأمل». */
    fun fullName(s: SquareEntity, all: List<SquareEntity>): String =
        s.parentId?.let { pid -> all.firstOrNull { it.id == pid && it.deletedAt == null }?.name }?.let { "$it › ${s.name}" } ?: s.name
}
