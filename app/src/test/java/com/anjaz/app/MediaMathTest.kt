package com.anjaz.app

import com.anjaz.app.domain.Username
import com.anjaz.app.media.VideoCompressor
import com.anjaz.app.stt.AudioPcm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaMathTest {
    @Test fun resampleHalvesLengthAndKeepsRange() {
        val x = FloatArray(32_000) { if (it % 2 == 0) .5f else -.5f }
        val y = AudioPcm.resample(x, 32_000, 16_000)
        assertEquals(16_000, y.size)
        assertTrue(y.all { it in -0.5f..0.5f })
        assertEquals(x, AudioPcm.resample(x, 16_000, 16_000))
    }

    @Test fun stereoIsAveragedToMono() {
        val pcm = shortArrayOf(16384, -16384, 16384, 16384)
        val m = AudioPcm.toMono16k(pcm, 16_000, 2)
        assertEquals(listOf(0f, .5f), m.toList())
    }

    @Test fun longAudioIsSplitUnder30Seconds() {
        val parts = AudioPcm.chunks(FloatArray(AudioPcm.RATE * 70))
        assertEquals(3, parts.size)
        assertTrue(parts.all { it.size <= 28 * AudioPcm.RATE })
        assertEquals(1, AudioPcm.chunks(FloatArray(100)).size)
    }

    @Test fun videoBitrateShrinksWithDurationAndStaysUnderTarget() {
        assertEquals(2_500_000, VideoCompressor.videoBitrate(10_000))
        val long = VideoCompressor.videoBitrate(90_000)
        assertTrue(long in 350_000..900_000)
        assertTrue((long + 64_000) * 90L / 8 <= VideoCompressor.TARGET_BYTES)
        assertEquals(720, VideoCompressor.heightFor(2_000_000))
        assertEquals(480, VideoCompressor.heightFor(400_000))
    }

    @Test fun usernameAcceptsSimpleAndEmailRejectsBad() {
        assertTrue(Username.isValid("sajed"))
        assertTrue(Username.isValid("sajed@anjaz.ps"))
        assertFalse(Username.isValid("sajed@anjaz"))
        assertFalse(Username.isValid("Bad Name"))
        assertEquals("sajed@anjaz.ps", Username.emailFor("sajed@anjaz.ps", "users.anjaz.local"))
        assertEquals("sami@users.anjaz.local", Username.emailFor("sami", "users.anjaz.local"))
    }
}
