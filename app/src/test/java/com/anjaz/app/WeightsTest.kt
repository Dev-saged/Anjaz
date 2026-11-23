package com.anjaz.app

import com.anjaz.app.domain.Weights
import org.junit.Assert.assertEquals
import org.junit.Test

class WeightsTest {
    @Test fun sumAlwaysEqualsTotal() {
        val r = Weights.allocate(1000, listOf(2560, 2120, 3100, 2675))
        assertEquals(1000L, r.sum())
        assertEquals(listOf(245L, 203L, 296L, 256L), r)
    }

    @Test fun remaindersGoToLargestFractions() {
        assertEquals(listOf(4L, 3L, 3L), Weights.allocate(10, listOf(1, 1, 1)))
        assertEquals(listOf(1L, 0L), Weights.allocate(1, listOf(3, 1)))
    }

    @Test fun zeroWeightGetsNothingUnlessAllZero() {
        assertEquals(listOf(5L, 0L), Weights.allocate(5, listOf(7, 0)))
        assertEquals(listOf(2L, 1L), Weights.allocate(3, listOf(0, 0)))
    }

    @Test fun edgeCases() {
        assertEquals(emptyList<Long>(), Weights.allocate(10, emptyList()))
        assertEquals(listOf(0L, 0L), Weights.allocate(0, listOf(1, 2)))
    }

    @Test fun percentFormatting() {
        assertEquals("24.5%", Weights.percent(2560, 10455))
        assertEquals("50%", Weights.percent(1, 2))
        assertEquals("0%", Weights.percent(5, 0))
    }
}
