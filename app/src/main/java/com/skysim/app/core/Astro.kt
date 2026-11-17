package com.skysim.app.core

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.math.PI

/** 太阳在天空中的几何状态。角度单位均为度。 */
data class SkyGeo(
    /** 太阳高度角（地平线以上为正） */
    val sunAltDeg: Float,
    /** 太阳方位角，0=正北，90=正东，180=正南，270=正西 */
    val sunAzDeg: Float,
    /** 太阳赤纬 */
    val declinationDeg: Float,
    /** 太阳时角（正午为 0，向西为正） */
    val hourAngleDeg: Float,
    /** 日出时角（极昼/极夜时无意义） */
    val sunriseHourAngleDeg: Float,
    val polarDay: Boolean,
    val polarNight: Boolean,
    /** 白昼长度（小时） */
    val dayLengthHours: Float,
    /** 日出、日落对应的 UCT 小时（0..24），极昼为 0/24，极夜为 NaN */
    val sunriseHourUct: Float,
    val sunsetHourUct: Float,
    /** 正午太阳高度角（可能为负，表示正午太阳仍在地平线下） */
    val noonAltDeg: Float,
    /** 午夜太阳高度角 */
    val midnightAltDeg: Float
)

object Astro {

    /**
     * 季节原点：公历第 80 天（3 月 21 日前后）是地球的春分，占一年的 79/365。
     * 其它天体沿用同一个「年内相位原点」，配合各自的 [Planet.seasonPhase] 微调。
     */
    const val SEASON_ORIGIN = 79.0 / 365.0

    /** 偏心率阈值：低于它的轨道按正圆处理，保证与 v0.6.10 逐位一致。 */
    const val ECC_MIN = 0.01

    private fun rad(d: Double) = d * PI / 180.0
    private fun deg(r: Double) = r * 180.0 / PI

    /**
     * 计算某星球、某纬度、某个时刻的太阳位置。
     *
     * @param uctFractionOfDay 当天已经过的时间比例 0..1（0.5 = 12:00 UCT）
     * @param dayOfYear 用于计算太阳赤纬的年内天数（1..366）
     * @param anchored true 表示使用"日出日落锚定"模式：把太阳的周日运动压缩/拉伸，
     *                 使 UCT 06:00 / 18:00 恰好是日出 / 日落。
     *                 false 表示物理模式：太阳时角严格按 UCT 时刻均匀变化。
     */
    /**
     * v0.8.9：解出「太阳高度角 = targetAltDeg」的本地日相位。
     *
     * 日出/日落按钮与画面上的太阳必须共用同一套几何，所以这里不写死 0.25 / 0.75，
     * 而是在同一个 [sunGeo] 模型上做二分：上升侧取 [0, 0.5]，下降侧取 [0.5, 1]。
     * 极昼（整日都在地平线上）或极夜（整日都在地平线下）时无解，返回 NaN，按钮据此置灰。
     */
    fun phaseOfSunAltitude(
        planet: Planet,
        latitudeDeg: Float,
        dayOfYear: Int,
        targetAltDeg: Double,
        rising: Boolean,
        tidalNightSide: Boolean = false,
        anchored: Boolean = false
    ): Double {
        fun alt(x: Double): Double =
            sunGeo(planet, latitudeDeg, dayOfYear, x, anchored, tidalNightSide).sunAltDeg.toDouble()
        val noon = alt(0.5)
        val mid = alt(0.0)
        if (targetAltDeg > maxOf(noon, mid) + 1e-9 || targetAltDeg < minOf(noon, mid) - 1e-9) return Double.NaN
        var a = if (rising) 0.0 else 0.5
        var b = if (rising) 0.5 else 1.0
        var ga = alt(a) - targetAltDeg
        val gb = alt(b) - targetAltDeg
        if (ga == 0.0) return a
        if (gb == 0.0) return b
        if (ga * gb > 0.0) return Double.NaN
        repeat(40) {
            val m = 0.5 * (a + b)
            val gm = alt(m) - targetAltDeg
            if (ga * gm <= 0.0) b = m else {
                a = m
                ga = gm
            }
        }
        return 0.5 * (a + b)
    }

    fun sunGeo(
        planet: Planet,
        latitudeDeg: Float,
        dayOfYear: Int,
        uctFractionOfDay: Double,
        anchored: Boolean,
        /** 潮汐锁定天体：false = 永昼面（次星点正对母星），true = 永夜面 */
        tidalNightSide: Boolean = false
    ): SkyGeo {
        // 有效轴倾角：>90° 的倾角（天王星 97.8°）等价于「逆行 + 180°−倾角」，见 Planet.obliquityDeg
        val tilt = rad(planet.obliquityDeg)
        // 自转方向：金星、天王星为 −1，太阳因此西升东落
        val spin = if (planet.retrogradeSpin) -1.0 else 1.0
        // 季节用**天体自己的**一年：dayOfYear 是本地历法的第几天，除以本地年天数得到年内相位。
        // v0.6.8 之前这里写死了地球的 365.2422 天，火星/金星/天王星的季节长度全错。
        val yearDays = planet.localDaysPerYear.coerceAtLeast(1).toDouble()
        val yearFrac = (dayOfYear - 1).toDouble() / yearDays
        val f = ((uctFractionOfDay % 1.0) + 1.0) % 1.0
        // v0.7.0：偏心率。方程中心把平近点角换成真近点角（到 e² 项），
        // 水星 e=0.2056 时近日点附近的轨道角速度会超过自转角速度，太阳因此倒退一次。
        val e = planet.eccentricity.coerceIn(0.0, 0.9)
        val peri = rad(planet.perihelionAngleDeg)
        // 一个 UCT 日 = 一个本地太阳日，期间轨道相位也在前进（水星：720°/日）
        val solarDayDays = (planet.solarDaySeconds / 86400.0).let { if (it.isFinite()) it else 0.0 }
        // 卫星的季节轨道相位跟随母行星绕恒星运动；不能拿卫星绕母行星的月长代替一年。
        val yearPeriodDays = planet.solarDayReferenceDays
        val orbitAdvanceRad = if (yearPeriodDays > 0.0) 2.0 * PI * solarDayDays / yearPeriodDays else 0.0
        val meanAnomaly = 2.0 * PI * (yearFrac - SEASON_ORIGIN + planet.seasonPhase) + peri + orbitAdvanceRad * f
        val eqCenter = if (e < ECC_MIN) 0.0 else 2.0 * e * sin(meanAnomaly) + 1.25 * e * e * sin(2.0 * meanAnomaly)
        val trueAnomaly = meanAnomaly + eqCenter
        val seasonAngle = trueAnomaly - peri
        val decl = asin(sin(tilt) * sin(seasonAngle))
        val phi = rad(latitudeDeg.toDouble())

        val cosH0 = -tan(phi) * tan(decl)
        // 潮汐锁定：自转周期 = 公转周期，太阳时角恒定时时刻刻是「永昼面正午」或「永夜面午夜」
        val locked = planet.tidallyLocked
        val polarDay = if (locked) !tidalNightSide else cosH0 <= -1.0
        val polarNight = if (locked) tidalNightSide else cosH0 >= 1.0
        val h0 = if (polarDay || polarNight) 0.0 else acos(cosH0.coerceIn(-1.0, 1.0))
        val h0Deg = deg(h0)

        val hourAngleDeg = when {
            locked -> if (tidalNightSide) 180.0 else 0.0
            anchored -> spin * anchoredHourAngle(f, h0Deg, polarDay || polarNight)
            // 视太阳时角 = 自转角 − 太阳方向角；偏心轨道让第二项不是匀速，于是出现倒退
            else -> spin * (360.0 * f - 180.0) - if (e < ECC_MIN) 0.0 else deg(eqCenter)
        }
        val h = rad(hourAngleDeg)

        val sinAlt = sin(phi) * sin(decl) + cos(phi) * cos(decl) * cos(h)
        val alt = asin(sinAlt.coerceIn(-1.0, 1.0))
        val az = if (abs(cos(phi)) < 1e-6) {
            // 极点：方位角退化为时角方向
            normalize360(hourAngleDeg + 180.0)
        } else {
            val sinAz = -cos(decl) * sin(h)
            val cosAz = (sin(decl) - sin(alt) * sin(phi)) / (cos(alt) * cos(phi))
            normalize360(deg(atan2(sinAz, cosAz)))
        }

        // 白昼长度换算成**地球小时**：先把太阳时角的比例换算成太阳日的比例，再乘该天体的太阳日。
        // 地球春分 = 0.5 × 24 h = 12 h；金星 = 0.5 × 2802 h；天王星 = 0.5 × 17.24 h。
        val solarDayHours = abs(planet.solarDaySeconds) / 3600.0
        val dayLengthFraction = if (polarDay) 1.0 else if (polarNight) 0.0 else 2.0 * h0Deg / 360.0
        // 潮汐锁定天体：本地时钟被冻结在正午/午夜，白昼按 24 小时报（与潮汐锁定一节的口径一致）
        val dayLength = if (locked) {
            if (tidalNightSide) 0f else 24f
        } else (dayLengthFraction * solarDayHours).toFloat()

        val (sunriseUct, sunsetUct) = when {
            polarDay -> 0f to 24f
            polarNight -> Float.NaN to Float.NaN
            anchored -> 6f to 18f
            else -> (12.0 - h0Deg / 15.0).toFloat() to (12.0 + h0Deg / 15.0).toFloat()
        }

        val noonAlt = deg(asin((sin(phi) * sin(decl) + cos(phi) * cos(decl)).coerceIn(-1.0, 1.0))).toFloat()
        val midnightAlt = deg(asin((sin(phi) * sin(decl) - cos(phi) * cos(decl)).coerceIn(-1.0, 1.0))).toFloat()

        // 报告 sunriseHourAngleDeg 时，锚定模式下"日出时角"就是映射后的等价时角
        val reportedH0 = if (polarDay || polarNight) 0f else h0Deg.toFloat()

        return SkyGeo(
            sunAltDeg = deg(alt).toFloat(),
            sunAzDeg = az.toFloat(),
            declinationDeg = deg(decl).toFloat(),
            hourAngleDeg = hourAngleDeg.toFloat(),
            sunriseHourAngleDeg = reportedH0,
            polarDay = polarDay,
            polarNight = polarNight,
            dayLengthHours = dayLength,
            sunriseHourUct = sunriseUct,
            sunsetHourUct = sunsetUct,
            noonAltDeg = noonAlt,
            midnightAltDeg = midnightAlt
        )
    }

    /**
     * 锚定模式下的时角映射：
     * 0.25（06:00）→ -H0（日出），0.5（12:00）→ 0（正午），
     * 0.75（18:00）→ +H0（日落），0（00:00）→ 180（午夜）。
     * 极昼/极夜时 H0 无意义，退化为均匀 360° 旋转。
     */
    private fun anchoredHourAngle(f: Double, h0Deg: Double, polar: Boolean): Double {
        if (polar) return 360.0 * f - 180.0
        return when {
            f >= 0.25 && f <= 0.75 -> h0Deg * (4.0 * f - 2.0)
            else -> {
                val ff = if (f < 0.25) f + 1.0 - 0.75 else f - 0.75 // 0..0.5
                h0Deg + (360.0 - 2.0 * h0Deg) * (2.0 * ff)
            }
        }
    }

    /**
     * 空间站观察点的几何：没有地面、没有自转，恒星固定在天空某个方向，
     * 时间仍然按 UCT 前进（站内时钟直接用 UCT）。
     */
    fun stationGeo(starAltDeg: Float = 24f, azDeg: Float = 180f): SkyGeo = SkyGeo(
        sunAltDeg = starAltDeg,
        sunAzDeg = azDeg,
        declinationDeg = 0f,
        hourAngleDeg = 0f,
        sunriseHourAngleDeg = 0f,
        polarDay = true,
        polarNight = false,
        dayLengthHours = 24f,
        sunriseHourUct = Float.NaN,
        sunsetHourUct = Float.NaN,
        noonAltDeg = starAltDeg,
        midnightAltDeg = starAltDeg
    )

    fun normalize360(v: Double): Double {
        var x = v % 360.0
        if (x < 0) x += 360.0
        return x
    }

    /** 风向式方位角文本 */
    fun azimuthLabel(azDeg: Float): String {
        val dirs = arrayOf("北", "东北", "东", "东南", "南", "西南", "西", "西北")
        val idx = (((azDeg + 22.5f) % 360f) / 45f).toInt().coerceIn(0, 7)
        return dirs[idx]
    }

    /** 天空亮度因子：太阳高度角 -18° 以下为夜，+6° 以上为全白天。 */
    fun daylightFactor(sunAltDeg: Float): Float {
        val t = ((sunAltDeg + 18f) / 24f).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    fun starVisibilityFactor(sunAltDeg: Float): Float {
        val t = ((sunAltDeg + 8f) / 16f).coerceIn(0f, 1f)
        return 1f - (t * t * (3f - 2f * t))
    }

    /** 地球小时 + 分钟 → 小数小时 */
    fun hmToHours(h: Int, m: Int): Double = h + m / 60.0

    fun formatUctHour(hours: Float): String {
        if (hours.isNaN()) return "—"
        val h = hours.toInt()
        val m = ((hours - h) * 60f).toInt()
        return String.format("%02d:%02d", (h % 24 + 24) % 24, (m % 60 + 60) % 60)
    }
}
