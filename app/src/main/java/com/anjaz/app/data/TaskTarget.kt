package com.anjaz.app.data

enum class TargetKind(val label: String, val needsPick: Boolean = false) {
    ALL_SQUARES("كل المربعات"),
    ALL_CAMPS("كل المخيمات"),
    SQUARE("مربع محدد", true),
    CAMP("مخيم محدد", true),
    PERSONS("أشخاص محددون", true),
}

data class TaskTarget(val kind: TargetKind, val ids: List<String> = emptyList())

object Trash {
    const val RETENTION_MS = 30L * 24 * 60 * 60 * 1000
    /** حذف نهائي: يُعلَّم بطابع قديم فتنظّفه الدورة اليومية ولا تعيده المزامنة. */
    const val EXPIRED = 1L

    fun cutoff(now: Long) = now - RETENTION_MS
    fun expired(deletedAt: Long?, now: Long = System.currentTimeMillis()) = deletedAt != null && deletedAt < cutoff(now)
    fun daysLeft(deletedAt: Long, now: Long): Int = (((deletedAt + RETENTION_MS) - now) / (24L * 60 * 60 * 1000)).toInt().coerceAtLeast(0)
}
