package com.anjaz.app

import androidx.test.core.app.ApplicationProvider
import com.anjaz.app.core.SelfTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class RoomSelfTestTest {
    @Test fun allSelfChecksPass() {
        val checks = runBlocking { SelfTest.run(ApplicationProvider.getApplicationContext()) }
        val failed = checks.filterNot { it.ok }
        assertTrue("فشل: $failed", failed.isEmpty())
        assertTrue(checks.size >= 10)
    }
}
