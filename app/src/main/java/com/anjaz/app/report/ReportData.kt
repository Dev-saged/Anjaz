package com.anjaz.app.report

import com.anjaz.app.core.DayMath
import com.anjaz.app.data.db.TaskEntity
import com.anjaz.app.data.db.UserEntity
import com.anjaz.app.domain.Priority
import com.anjaz.app.domain.TaskStatus

data class ReportTable(val title: String, val subtitle: String, val headers: List<String>, val rows: List<List<String>>, val summary: List<Pair<String, String>>)

object ReportData {
    fun tasks(tasks: List<TaskEntity>, users: List<UserEntity>, generatedAt: Long, scope: String): ReportTable {
        val names = users.associate { it.id to it.displayName }
        val sorted = tasks.filter { !it.archived }.sortedByDescending { it.createdAt }
        val rows = sorted.map { t ->
            listOf(
                t.number?.toString().orEmpty(), t.title,
                TaskStatus.entries.firstOrNull { it.name == t.status }?.label ?: t.status,
                Priority.entries.firstOrNull { it.name == t.priority }?.label ?: t.priority,
                t.assigneeId?.let { names[it] }.orEmpty(),
                t.deadline?.let { DayMath.date(it) } ?: "-",
            )
        }
        val byStatus = sorted.groupingBy { it.status }.eachCount()
        val summary = buildList {
            add("إجمالي المهام" to sorted.size.toString())
            TaskStatus.entries.forEach { st -> byStatus[st.name]?.let { add(st.label to it.toString()) } }
        }
        return ReportTable("تقرير المهام", "$scope · ${DayMath.date(generatedAt)}", listOf("الرقم", "المهمة", "الحالة", "الأولوية", "المكلّف", "الموعد"), rows, summary)
    }
}
