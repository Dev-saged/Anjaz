package com.anjaz.app.domain

enum class TaskStatus(val label: String, val terminal: Boolean = false) {
    NEW("جديدة"),
    SEEN("تم الاطلاع"),
    IN_PROGRESS("قيد المتابعة"),
    AWAITING_REVIEW("بانتظار الاعتماد"),
    APPROVED("منجزة ومعتمدة", terminal = true),
    REJECTED("مرفوضة", terminal = true),
    CANCELLED("ملغاة", terminal = true);

    fun canMoveTo(next: TaskStatus): Boolean {
        if (terminal) return false
        if (next == CANCELLED) return true
        return when (this) {
            NEW -> next == SEEN || next == IN_PROGRESS
            SEEN -> next == IN_PROGRESS
            IN_PROGRESS -> next == AWAITING_REVIEW
            AWAITING_REVIEW -> next == APPROVED || next == IN_PROGRESS || next == REJECTED
            else -> false
        }
    }

    companion object {
        fun parse(value: String): TaskStatus = entries.firstOrNull { it.name == value } ?: NEW

        fun isOverdue(status: TaskStatus, deadline: Long?, now: Long): Boolean =
            !status.terminal && deadline != null && deadline < now
    }
}

enum class Priority(val label: String) {
    NORMAL("عادية"), HIGH("مهمة"), URGENT("عاجلة");

    companion object {
        fun parse(value: String): Priority = entries.firstOrNull { it.name == value } ?: NORMAL
    }
}
