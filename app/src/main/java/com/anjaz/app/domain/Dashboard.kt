package com.anjaz.app.domain

import com.anjaz.app.data.db.TaskEntity

data class Slice(val id: String, val name: String, val open: Int, val overdue: Int, val review: Int, val done: Int) {
    val total: Int get() = open + review + done
    val rate: Float get() = if (total == 0) 0f else done.toFloat() / total
}

data class Dashboard(val title: String, val sliceLabel: String, val slices: List<Slice>, val overdue: List<TaskEntity>) {
    val totals: Slice get() = Slice("", "", slices.sumOf { it.open }, slices.sumOf { it.overdue }, slices.sumOf { it.review }, slices.sumOf { it.done })
}

/** لوحة حسب الدور: المركزي بالمناطق، مدير/إداري المنطقة بالمربعات، مدير المربع بالموظفين، والموظف مهامه فقط. */
object DashboardBuilder {
    fun build(p: Profile, tasks: List<TaskEntity>, regions: Map<String, String>, squares: Map<String, String>, users: Map<String, String>, now: Long): Dashboard {
        val role = p.roleEnum
        val scoped = tasks.filter { !it.archived }.filter {
            when (role) {
                Role.CENTRAL -> true
                Role.REGION_MANAGER, Role.REGION_ADMIN -> it.regionId == p.regionId
                Role.SQUARE_MANAGER -> it.squareId == p.squareId
                Role.FIELD_STAFF -> it.assigneeId == p.uid
            }
        }
        val (title, label, key, names) = when (role) {
            Role.CENTRAL -> Quad("نظرة عامة على المناطق", "المنطقة", { t: TaskEntity -> t.regionId }, regions)
            Role.REGION_MANAGER, Role.REGION_ADMIN -> Quad("متابعة المربعات", "المربع", { t: TaskEntity -> t.squareId }, squares)
            Role.SQUARE_MANAGER -> Quad("متابعة فريق المربع", "الموظف", { t: TaskEntity -> t.assigneeId }, users)
            Role.FIELD_STAFF -> Quad("مهامي", "المهام", { _: TaskEntity -> p.uid }, mapOf(p.uid to "مهامي"))
        }
        val slices = scoped.groupBy { key(it) ?: "" }.map { (id, list) ->
            val st = list.map { it to TaskStatus.parse(it.status) }
            Slice(
                id, names[id] ?: "غير مسند",
                open = st.count { (_, s) -> !s.terminal && s != TaskStatus.AWAITING_REVIEW },
                overdue = st.count { (t, s) -> isOverdue(t, s, now) },
                review = st.count { (_, s) -> s == TaskStatus.AWAITING_REVIEW },
                done = st.count { (_, s) -> s == TaskStatus.APPROVED },
            )
        }.sortedWith(compareByDescending<Slice> { it.overdue }.thenByDescending { it.open })
        val overdue = scoped.filter { isOverdue(it, TaskStatus.parse(it.status), now) }.sortedBy { it.deadline }
        return Dashboard(title, label, slices, overdue)
    }

    private fun isOverdue(t: TaskEntity, s: TaskStatus, now: Long) = !s.terminal && t.deadline != null && t.deadline < now

    private data class Quad(val a: String, val b: String, val c: (TaskEntity) -> String?, val d: Map<String, String>)
}
