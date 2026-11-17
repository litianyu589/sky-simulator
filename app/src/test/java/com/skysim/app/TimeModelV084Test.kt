package com.skysim.app

import com.skysim.app.core.DayScheme
import com.skysim.app.core.Planets
import com.skysim.app.core.SpinState
import com.skysim.app.core.StarCatalog
import com.skysim.app.core.StarData
import com.skysim.app.core.TimeSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v0.8.4：行星季节日序、卫星绕日周期、空间站时钟回归测试。 */
class TimeModelV084Test {

    @Test
    fun satelliteSolarDaysUseHostPlanetYearRatherThanMoonPeriod() {
        val expected = mapOf(
            "moon" to 29.5306,
            "io" to 1.7697,
            "europa" to 3.5539,
            "ganymede" to 7.1668,
            "callisto" to 16.7538,
            "titan" to 15.9687,
            "enceladus" to 1.3702,
            "iapetus" to 79.92,
            "titania" to 8.7085,
            "miranda" to 1.4131,
            "charon" to 6.3875,
            "triton" to 5.8764
        )
        for ((id, days) in expected) {
            val body = Planets.byId(id)
            assertTrue("$id 必须有母行星绕恒星周期", body.heliocentricPeriodDays > 0.0)
            assertEquals("$id 太阳日", days, body.solarDaySeconds / 86400.0, days * 0.015)
            assertTrue("$id 太阳日必须有限", body.solarDaySeconds.isFinite())
            assertEquals("$id 应按母行星轨道判断自转共振", SpinState.SYNCHRONOUS, body.spinState)
            assertFalse("$id 锁定的是母行星，不是恒星", body.isSynchronous)
            assertEquals("$id 一年日出次数应按母行星年计算",
                body.heliocentricPeriodDays / (body.solarDaySeconds / 86400.0),
                body.sunrisesPerYear, 1e-8)
        }
    }

    @Test
    fun everyCatalogSatelliteHasAnExplicitHeliocentricReference() {
        val satellites = Planets.all.filter { it.isSatellite }
        assertTrue("目录中应包含卫星", satellites.isNotEmpty())
        for (body in satellites) {
            assertTrue("${body.id} 缺母行星绕恒星周期", body.heliocentricPeriodDays > 0.0)
            assertTrue("${body.id} 太阳日不应发散", body.solarDaySeconds.isFinite())
            assertTrue("${body.id} 本地年日数应为正", body.localDaysPerYear > 0)
        }
    }

    @Test
    fun tritonRetrogradeRotationIsReflectedInSolarDaySign() {
        val triton = Planets.byId("triton")
        assertTrue("海卫一绕海王星逆行", triton.siderealRotationPeriodSeconds < 0.0)
        assertTrue("逆行卫星的太阳日应带负号", triton.solarDaySignedSeconds < 0.0)
        assertEquals(5.8764, triton.solarDaySeconds / 86400.0, 0.02)
    }

    @Test
    fun stationUsesUtcClockAndIsNotClassifiedAsSynchronousPlanet() {
        val station = StarCatalog.stationFor(StarData.byId("sirius"))!!
        assertEquals(SpinState.FREE, station.spinState)
        assertFalse(station.isSynchronous)
        assertTrue(station.solarDaySeconds.isFinite())
        assertEquals(86400.0, station.solarDaySeconds, 0.0)

        val utcSeconds = 1_700_000_000L
        val stationTime = TimeSystem.localStamp(station, utcSeconds)
        val utcTime = TimeSystem.uctStamp(utcSeconds)
        assertFalse(stationTime.phaseOnly)
        assertEquals(utcTime.year, stationTime.year)
        assertEquals(utcTime.month, stationTime.month)
        assertEquals(utcTime.day, stationTime.day)
        assertEquals(utcTime.hour, stationTime.hour)
        assertEquals(DayScheme.SI_SECONDS, stationTime.scheme)
    }

    @Test
    fun localYearDayIndexIsTheOnePassedToSeasonModel() {
        val mars = Planets.byId("mars")
        val uct = TimeSystem.uctStamp(1_700_000_000L)
        val local = TimeSystem.localStamp(mars, 1_700_000_000L)
        assertEquals(mars.localDaysPerYear, local.daysPerYear)
        assertTrue(local.dayOfYearLocal in 1..mars.localDaysPerYear)
        // Regression contract for MainScreen: season input is local.dayOfYearLocal, not uct.dayOfYearLocal.
        assertTrue("测试时刻应能区分公历与火星历", local.dayOfYearLocal != uct.dayOfYearLocal)
    }
}
