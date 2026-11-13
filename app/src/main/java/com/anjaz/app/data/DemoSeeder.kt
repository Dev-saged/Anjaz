package com.anjaz.app.data

import com.anjaz.app.domain.Outcome
import com.anjaz.app.domain.TaskStatus
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DemoSeeder @Inject constructor(private val repo: TaskRepository) {
    private data class Seed(val title: String, val desc: String, val priority: String, val deadline: String, val path: List<TaskStatus>)

    private val seeds = listOf(
        Seed("توزيع السلال الغذائية على مخيم الأمل", "مطابقة الكشوف قبل التسليم وتوثيق المستلمين.", "HIGH", "today",
            listOf(TaskStatus.SEEN, TaskStatus.IN_PROGRESS)),
        Seed("فحص خزانات المياه في المربع الثالث", "قياس مستوى الخزانات وتسجيل أي تسرب.", "URGENT", "today",
            listOf(TaskStatus.IN_PROGRESS, TaskStatus.AWAITING_REVIEW)),
        Seed("جرد المستلزمات الطبية في عيادة المخيم", "إعداد قائمة النواقص وإرسالها للمركز.", "NORMAL", "tomorrow", emptyList()),
        Seed("تحديث كشف الأسر المستفيدة", "إضافة الأسر الجديدة وحذف المكرر.", "NORMAL", "3d", listOf(TaskStatus.SEEN)),
        Seed("صيانة إنارة الممر الرئيسي", "استبدال ثلاث وحدات تالفة.", "HIGH", "tomorrow",
            listOf(TaskStatus.IN_PROGRESS, TaskStatus.AWAITING_REVIEW, TaskStatus.APPROVED)),
        Seed("تقرير الزيارة الأسبوعية للمربع الأول", "ملخص الملاحظات والصور.", "NORMAL", "none", emptyList()),
    )

    suspend fun seed(): Int {
        var created = 0
        seeds.forEach { s ->
            val id = repo.create(
                mapOf("title" to s.title, "description" to s.desc, "priority" to s.priority, "deadline" to s.deadline),
                Session.LOCAL_ACTOR,
            )
            if (id is Outcome.Ok) {
                created++
                s.path.forEach { repo.transition(id.value, it, Session.LOCAL_ACTOR) }
            }
        }
        return created
    }
}
