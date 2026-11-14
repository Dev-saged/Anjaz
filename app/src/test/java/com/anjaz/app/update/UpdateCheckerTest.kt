package com.anjaz.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    private fun rel(v: String, b: Int, code: Int? = null) = AppRelease(v, b, code, "u", "")

    @Test fun versionOrderingIsNumeric() {
        assertTrue(UpdateChecker.isNewer("0.10.0", "0.9.9"))
        assertFalse(UpdateChecker.isNewer("0.3", "0.3.0"))
        assertFalse(UpdateChecker.isNewer("x", "0.3.0"))
    }

    @Test fun codeDecidesWhenPresent() {
        assertTrue(UpdateChecker.isUpdate(rel("0.3.0", 5, 30005), "0.3.0", 4, 30004))
        assertFalse(UpdateChecker.isUpdate(rel("0.3.0", 5, 30004), "0.3.0", 4, 30004))
    }

    @Test fun buildNumberDecidesForSameVersionWithoutCode() {
        assertTrue(UpdateChecker.isUpdate(rel("0.3.0", 9), "0.3.0", 8, 30008))
        assertFalse(UpdateChecker.isUpdate(rel("0.3.0", 8), "0.3.0", 8, 30008))
        assertFalse(UpdateChecker.isUpdate(rel("0.2.9", 99), "0.3.0", 1, 30001))
    }

    @Test fun tagParsing() {
        assertEquals("0.3.0" to 12, UpdateChecker.parseTag("v0.3.0-b12"))
        assertEquals("1.0.0" to 0, UpdateChecker.parseTag("v1.0.0"))
        assertNull(UpdateChecker.parseTag("latest-build"))
    }

    @Test fun versionJsonParsing() {
        val r = UpdateChecker.parseVersionJson(
            """{"version":"0.3.0","build":12,"code":30012,"apk":"Anjaz-v0.3.0-b12.apk","notes":"x","size":123}""", "https://h/r",
        )
        assertEquals("https://h/r/releases/download/v0.3.0-b12/Anjaz-v0.3.0-b12.apk", r.apkUrl)
        assertEquals(30012, r.code)
        assertEquals(123L, r.size)
    }

    @Test fun notesAreCleaned() {
        val out = UpdateChecker.cleanNotes("# عنوان\n## الإضافات\n- **ميزة** `أ`\n> ملاحظة\nنص")
        assertEquals("• ميزة أ\nنص", out)
    }
}
