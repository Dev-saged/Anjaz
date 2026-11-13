package com.anjaz.app

import com.anjaz.app.core.DayMath
import com.anjaz.app.core.toHex
import com.anjaz.app.domain.Role
import com.anjaz.app.domain.TaskStatus
import com.anjaz.app.schema.SchemaValidator
import com.anjaz.app.schema.Schemas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoleTest {
    @Test fun higherRolesCreateLower() {
        assertTrue(Role.CENTRAL.canCreate(Role.REGION_MANAGER))
        assertTrue(Role.REGION_MANAGER.canCreate(Role.FIELD_STAFF))
    }

    @Test fun lowerOrEqualRolesCannotCreate() {
        assertFalse(Role.SQUARE_MANAGER.canCreate(Role.REGION_ADMIN))
        assertFalse(Role.REGION_MANAGER.canCreate(Role.REGION_MANAGER))
        assertFalse(Role.FIELD_STAFF.canCreate(Role.FIELD_STAFF))
    }

    @Test fun onlyCentralCreatesCentral() {
        assertTrue(Role.CENTRAL.canCreate(Role.CENTRAL))
        assertFalse(Role.REGION_MANAGER.canCreate(Role.CENTRAL))
    }
}

class TaskStatusTest {
    @Test fun happyPath() {
        assertTrue(TaskStatus.NEW.canMoveTo(TaskStatus.IN_PROGRESS))
        assertTrue(TaskStatus.IN_PROGRESS.canMoveTo(TaskStatus.AWAITING_REVIEW))
        assertTrue(TaskStatus.AWAITING_REVIEW.canMoveTo(TaskStatus.APPROVED))
    }

    @Test fun cannotSkipReview() {
        assertFalse(TaskStatus.NEW.canMoveTo(TaskStatus.APPROVED))
        assertFalse(TaskStatus.IN_PROGRESS.canMoveTo(TaskStatus.APPROVED))
    }

    @Test fun terminalStatesAreFinal() {
        TaskStatus.entries.filter { it.terminal }.forEach { t ->
            TaskStatus.entries.forEach { assertFalse("$t -> $it", t.canMoveTo(it)) }
        }
    }

    @Test fun rejectedReviewReturnsToWork() {
        assertTrue(TaskStatus.AWAITING_REVIEW.canMoveTo(TaskStatus.IN_PROGRESS))
    }

    @Test fun overdueIgnoresFinishedTasks() {
        assertTrue(TaskStatus.isOverdue(TaskStatus.IN_PROGRESS, 10L, 20L))
        assertFalse(TaskStatus.isOverdue(TaskStatus.APPROVED, 10L, 20L))
        assertFalse(TaskStatus.isOverdue(TaskStatus.NEW, null, 20L))
    }
}

class SchemaValidatorTest {
    private val ok = mapOf("title" to "مهمة", "description" to "", "priority" to "HIGH", "deadline" to "today")

    @Test fun validTaskHasNoErrors() = assertTrue(SchemaValidator.validate(Schemas.task, ok).isEmpty())

    @Test fun requiredTitle() =
        assertTrue(SchemaValidator.validate(Schemas.task, ok + ("title" to "   ")).containsKey("title"))

    @Test fun rejectsUnknownChoice() =
        assertTrue(SchemaValidator.validate(Schemas.task, ok + ("priority" to "X")).containsKey("priority"))

    @Test fun rejectsTooLong() =
        assertTrue(SchemaValidator.validate(Schemas.task, ok + ("title" to "ا".repeat(121))).containsKey("title"))

    @Test fun phoneAndDigits() {
        val sq = mapOf("name" to "مربع", "idNumber" to "12a", "phone" to "x1")
        val e = SchemaValidator.validate(Schemas.square, sq)
        assertTrue(e.containsKey("idNumber") && e.containsKey("phone"))
        assertTrue(SchemaValidator.validate(Schemas.square, mapOf("name" to "م", "phone" to "+970 599 123456")).isEmpty())
    }

    @Test fun sanitizeStripsControlAndCollapsesSpaces() =
        assertEquals("مهمة جديدة", SchemaValidator.sanitize("  مهمة\u0007   جديدة "))

    @Test fun cleanReturnsAllFields() = assertEquals(Schemas.task.fields.map { it.key }, SchemaValidator.clean(Schemas.task, ok).keys.toList())
}

class BrandTest {
    @Test fun navyMatchesIdentityHex() =
        assertEquals("#102e52", BrandConfig.Palette.navy.light.toColor().toHex())
}

class DayMathTest {
    @Test fun addDaysThenStartOfDayIsStable() {
        val now = 1_760_000_000_000L
        val s = DayMath.startOfDay(now)
        assertEquals(s, DayMath.startOfDay(s + 3_600_000L))
        assertTrue(DayMath.addDays(s, 1) > s)
    }

    @Test fun dueLabelNullForNoDeadline() = assertEquals(null, DayMath.dueLabel(null, 0L))

    @Test fun dueLabelSaysToday() {
        val now = 1_760_000_000_000L
        assertTrue(DayMath.dueLabel(now, now)!!.startsWith("اليوم"))
    }
}
