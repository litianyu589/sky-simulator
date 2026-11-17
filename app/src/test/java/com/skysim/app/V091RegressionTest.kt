package com.skysim.app

import com.skysim.app.core.Astro
import com.skysim.app.core.DayScheme
import com.skysim.app.core.Planets
import com.skysim.app.core.TimeSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class V091RegressionTest {
    @Test
    fun sunriseSunsetSolverUsesAnchoredGeometryWhenRequested() {
        val earth = Planets.all.first { it.id == "earth" }
        val latitude = 50f
        val dayOfYear = 200
        val rise = Astro.phaseOfSunAltitude(
            earth, latitude, dayOfYear, 0.0, rising = true, anchored = true
        )
        val set = Astro.phaseOfSunAltitude(
            earth, latitude, dayOfYear, 0.0, rising = false, anchored = true
        )
        assertEquals(0.25, rise, 1e-6)
        assertEquals(0.75, set, 1e-6)
        assertTrue(kotlin.math.abs(Astro.sunGeo(earth, latitude, dayOfYear, rise, true).sunAltDeg) < 1e-3f)
        assertTrue(kotlin.math.abs(Astro.sunGeo(earth, latitude, dayOfYear, set, true).sunAltDeg) < 1e-3f)
    }

    @Test
    fun localClockFormattingFieldsRespectDecimalAndSiSchemes() {
        val decimal = TimeSystem.localClockFields(DayScheme.COARSE_DECIMAL, 0.249, 10_087_292.0)
        assertEquals(3, decimal.size)
        assertEquals(2, decimal[0])
        assertEquals(4, decimal[1])
        assertEquals(9, decimal[2])

        val si = TimeSystem.localClockFields(DayScheme.SI_SECONDS, 0.5, 15_203_808.0)
        assertEquals(2111, si[0])
    }
}
