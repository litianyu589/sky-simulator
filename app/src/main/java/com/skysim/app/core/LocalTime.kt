package com.skysim.app.core

import kotlin.math.abs

/**
 * 本地时间进制（v0.6.9）。
 *
 * 唯一真相是 UCT：连续 SI 秒计数，没有时区、没有历法、没有「一天 = 86400 秒」的隐含假设。
 * 天体本地时间是**纯函数** (UCT 秒, 该天体的一行数据) → 本地日期 + 本地时分秒 + 太阳时角，
 * 函数内部不做任何按 id / 名字的分支。
 */
enum class DayScheme(val labelZh: String) {
    /** 把该天体的一个太阳日等分成 24 份，再各分 60/60。本地「秒」不是 SI 秒。 */
    EQUAL_24("24 等分"),

    /** 直接用 SI 秒计时（本地秒 = 1 SI 秒），适合被地球观测、日长又与地球差得远的天体。 */
    SI_SECONDS("SI 秒"),

    /** 日长超过 3 个地球日：时分秒已经没有生活意义，改用十进制粗分 + 太阳时角。 */
    COARSE_DECIMAL("十进制粗分")
}

object LocalTimeBase {

    const val SOLAR_DAY_SI = 86400.0

    /**
     * 自动判据（纯函数，可覆盖）：
     *  1. 卫星（[Planet.heliocentricPeriodDays] > 0）：沿用地球的 SI 秒，观测口径统一；
     *  2. 太阳日与 86400 s 的相对差 < 10 %：24 等分（地球、火星）；
     *  3. 太阳日 > 3 个地球日：十进制粗分（金星、水星）；
     *  4. 其余：SI 秒。
     * 判据关心的是「屏幕上的几点能不能对上太阳位置和作息」，不是物理必然。
     */
    fun autoScheme(planet: Planet): DayScheme {
        if (planet.heliocentricPeriodDays > 0.0) return DayScheme.SI_SECONDS
        val day = planet.solarDaySeconds
        val rel = abs(day - SOLAR_DAY_SI) / SOLAR_DAY_SI
        return when {
            rel < 0.10 -> DayScheme.EQUAL_24
            day > 3.0 * SOLAR_DAY_SI -> DayScheme.COARSE_DECIMAL
            else -> DayScheme.SI_SECONDS
        }
    }

    /** 数据表可以覆盖自动判据。 */
    fun scheme(planet: Planet): DayScheme = planet.localDaySchemeOverride ?: autoScheme(planet)

    /** 本地时间各进位的基数，如 [24, 60, 60]。 */
    fun split(planet: Planet): IntArray = when (scheme(planet)) {
        DayScheme.EQUAL_24, DayScheme.SI_SECONDS -> intArrayOf(24, 60, 60)
        DayScheme.COARSE_DECIMAL -> intArrayOf(10, 10, 10)
    }

    /** 本地秒 / SI 秒。24 等分时 = 太阳日/86400（火星 1.02749），其余为 1 或十进制基准。 */
    fun localSecondRatio(planet: Planet): Double = when (scheme(planet)) {
        DayScheme.EQUAL_24 -> planet.solarDaySeconds / SOLAR_DAY_SI
        DayScheme.SI_SECONDS -> 1.0
        DayScheme.COARSE_DECIMAL -> planet.solarDaySeconds / 1000.0
    }

    /** 太阳时角：本地正午为 0°，向东/向西各到 ±180°（这里归一化到 0..360）。 */
    fun solarHourAngleDeg(dayFraction: Double): Double {
        val a = ((dayFraction - 0.5) * 360.0) % 360.0
        return if (a < 0) a + 360.0 else a
    }

    /** 本地秒换成 SI 秒的对照文本，例如火星「88775.244 s」。 */
    fun siDayText(planet: Planet): String = "%.3f s SI".format(planet.solarDaySeconds)

    fun splitText(planet: Planet): String = split(planet).joinToString(":")
}
