package com.anjaz.app

import com.anjaz.app.data.db.TaskEntity
import com.anjaz.app.domain.DashboardBuilder
import com.anjaz.app.domain.Profile
import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardTest {
    private val now = 1_000_000L
    private fun t(id: String, status: String, region: String, square: String, who: String?, deadline: Long? = null) =
        TaskEntity(id, null, id, "", "u", region, square, who, "NORMAL", status, deadline, 0, 0, false)

    private val tasks = listOf(
        t("1", "NEW", "A", "A1", "s1", now - 5), t("2", "APPROVED", "A", "A1", "s1"), t("3", "AWAITING_REVIEW", "A", "A2", "s2"),
        t("4", "IN_PROGRESS", "B", "B1", "s3", now + 9), t("5", "CANCELLED", "A", "A1", "s1", now - 5),
    )
    private val names = mapOf("A" to "غزة", "B" to "الشمال", "A1" to "مربع 1", "A2" to "مربع 2", "s1" to "أحمد", "s2" to "سالم", "s3" to "ليلى")

    @Test fun centralGroupsByRegionAndCountsOverdueOnlyForOpen() {
        val d = DashboardBuilder.build(Profile("c", "c", "c", "CENTRAL"), tasks, names, names, names, now)
        assertEquals(setOf("غزة", "الشمال"), d.slices.map { it.name }.toSet())
        assertEquals(1, d.overdue.size)
        assertEquals(1, d.slices.first { it.name == "غزة" }.overdue)
    }

    @Test fun regionManagerSeesOnlyOwnRegionBySquare() {
        val d = DashboardBuilder.build(Profile("r", "r", "r", "REGION_MANAGER", regionId = "A"), tasks, names, names, names, now)
        assertEquals(setOf("مربع 1", "مربع 2"), d.slices.map { it.name }.toSet())
        assertEquals(1, d.slices.first { it.name == "مربع 2" }.review)
    }

    @Test fun fieldStaffSeesOnlyOwnTasks() {
        val d = DashboardBuilder.build(Profile("s1", "s1", "أحمد", "FIELD_STAFF", regionId = "A", squareId = "A1"), tasks, names, names, names, now)
        assertEquals(1, d.slices.size)
        assertEquals(2, d.totals.total)
        assertEquals(1, d.totals.done)
    }
}
