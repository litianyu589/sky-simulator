package com.skysim.app.core

/**
 * v0.8.6：统一本地时间与日期体系。
 *
 * 三条硬规则：
 *  1. 统一纪元——任何天体的「本地 1 年 1 月 1 日 00:00:00」都对应同一个物理瞬间
 *     UCT 0001-01-01T00:00:00Z（见 [TimeSystem.EPOCH_EPOCH_DAY]）。天体不再各拍纪元。
 *  2. 单位链由「年长 ÷ 本地日长」的比值决定，写进数据行，不在代码里按 id 分支：
 *     比值 ≥ 20 → 年.月.日 或 年.星.日；1 ≤ 比值 < 20 → 年.日；比值 < 1 → 日.年；
 *     潮汐锁定 → 只有相位（度分秒）。
 *  3. 闰日一律用累积余数法：某年的第一天 = floor((年号 − 1) × 年长)，年长用有理数表示，
 *     跨过整数边界自然补一天。不写闰年表，十万年也不漂。
 *
 * v0.8.8：本地日计数按**真实物理日长**推进——从统一纪元起算的 SI 秒 ÷ 该天体的太阳日，
 * 得到本地日序号与日相位；年月日、太阳时角、本地时分秒、相位全部由它们推出。
 * 渲染层仍沿用 v0.6.9 的规范化（1 个 UCT 日 = 1 个本地日驱动太阳周日运动），
 * 那是控制/渲染的既有简化，与显示层的物理日长互不借用。
 */
enum class CalendarChain(val labelZh: String) {
    /** 年.月.日 时:分:秒（月制，地球） */
    YEAR_MONTH_DAY("年.月.日 时:分:秒"),

    /** 年.星.日 时:分:秒（星制：火星、木星、土星等） */
    YEAR_STAR_DAY("年.星.日 时:分:秒"),

    /** 年.日 时:分:秒（一年只装得下一两天，例如金星 1.925 本地日） */
    YEAR_DAY("年.日 时:分:秒"),

    /** 日.年 时:分:秒（一年短于一个本地日，例如水星 0.5 本地日） */
    DAY_YEAR("日.年 时:分:秒"),

    /** 潮汐锁定天体只有相位：度°分′秒″ */
    PHASE("度°分′秒″")
}

/**
 * 一颗天体的本地历常数（每一行都是一份可核对的数据，代码里没有第二份）。
 *
 * @param yearNumerator 年长分子（单位：本地日）
 * @param yearDenominator 年长分母
 * @param dayLengthSiSeconds 物理太阳日长（SI 秒），只用于算比值与写说明
 * @param middleNameZh 中间单位名：「月」「星」，无中间单位时为空串
 * @param middleCount 中间单位个数（0 = 无）
 * @param middleLengthDays 每个中间单位的固定整数日数（0 = 无）
 */
data class CalendarSpec(
    val calendarNameZh: String,
    val chain: CalendarChain,
    val yearNumerator: Long,
    val yearDenominator: Long,
    val dayLengthSiSeconds: Double,
    val middleNameZh: String,
    val middleCount: Int,
    val middleLengthDays: Int,
    /** true = 月制（有真实卫星且周期能与年排出 8–40 的比例），false = 星制 */
    val monthSystem: Boolean,
    /** true = 用公历的 4/100/400 闰年规则（地球与空间站）；false = 累积余数法 */
    val gregorian: Boolean = false,
    /** 常数的出处或推定方式，直接进说明文档，避免把推定值伪装成观测值 */
    val sourceNote: String,
    /**
     * v0.8.9：纪元瞬间的本地自转相位（本地日小数，0 = 本地午夜）。
     * 加在本地日浮点上，本地 00:00 才落在该天体真实平太阳午夜；缺省 0 表示没有可用自转要素。
     */
    val epochPhaseFraction: Double = 0.0,
    /** v0.8.9：相位来源分级，界面按它标注「实测锚定 / 实测 / 约定 / 未定」 */
    val epochPhaseSource: PhaseSource = PhaseSource.UNDETERMINED
) {
    /** 一年含多少本地日（可带小数，例如火星 668.5991） */
    val yearLocalDays: Double get() = yearNumerator.toDouble() / yearDenominator.toDouble()

    /** 固定中间单位吃掉的天数（火星 20 × 33 = 660），余下的零头由年尾闰日吸收 */
    val fixedDaysPerYear: Int get() = middleCount * middleLengthDays

    /** 格式化模板，UI 与文档共用一份 */
    fun templateZh(): String = when (chain) {
        CalendarChain.YEAR_MONTH_DAY -> "年 月 日 时 分 秒（12 月，28–31 日）"
        CalendarChain.YEAR_STAR_DAY -> "年 星 日 时 分 秒（$middleCount 星 × $middleLengthDays 日，年尾补闰）"
        CalendarChain.YEAR_DAY -> "年 日 时 分 秒（无中间单位）"
        CalendarChain.DAY_YEAR -> "日 年 时 分 秒（一年短于一个本地日）"
        CalendarChain.PHASE -> "度° 分′ 秒″（潮汐锁定，只有相位）"
    }
}

object LocalCalendars {

    /** 年长精度：万分之一本地日（约 8.6 s），十万年累积误差 < 0.1 日 */
    const val DEN: Long = 10_000L

    /**
     * 卫星周期（本地日），用于判定「月制 / 星制」。
     * 来源：NASA Planetary Fact Sheet 与 JPL SSD 常引用的恒星周期，判据只看它与年长的比值是否落在 8–40，
     * 因此用恒星周期近似即可（地球另给朔望周期 29.53，因为判断「月」要看朔望）。
     */
    private val SATELLITES: Map<String, List<Pair<String, Double>>> = mapOf(
        "earth" to listOf("月球" to 29.53),
        "mars" to listOf("火卫一" to 0.31891, "火卫二" to 1.26244),
        "jupiter" to listOf("木卫一" to 1.76914, "木卫二" to 3.55118, "木卫三" to 7.15455, "木卫四" to 16.68902),
        "saturn" to listOf("土卫六" to 15.94542, "土卫五" to 4.51750, "土卫八" to 79.32148),
        "uranus" to listOf("天卫三" to 8.70587),
        "neptune" to listOf("海卫一" to 5.87685),
        "pluto" to listOf("冥卫一" to 6.38723)
    )

    /**
     * 已与天雨逐个确认过的天体，写死星数与每星星长，避免自动择优改掉结论。
     * 火星 20 星 × 33 日 = 660 日，余 8.5991 日由年尾闰日吸收。
     */
    private val STAR_OVERRIDES: Map<String, Pair<Int, Int>> = mapOf(
        "mars" to (20 to 33)
    )

    /** 每个天体的本地历常数。纯函数，只读数据行。 */
    fun of(planet: Planet): CalendarSpec {
        // 空间站：没有行星地表，站内时钟直接是 UCT 公历
        if (planet.isStation) {
            return CalendarSpec(
                calendarNameZh = "公历",
                epochPhaseFraction = planet.epochPhaseFraction,
                epochPhaseSource = planet.epochPhaseSource,
                chain = CalendarChain.YEAR_MONTH_DAY,
                yearNumerator = 3_652_425L,
                yearDenominator = DEN,
                dayLengthSiSeconds = 86_400.0,
                middleNameZh = "月",
                middleCount = 12,
                middleLengthDays = 0,
                monthSystem = true,
                gregorian = true,
                sourceNote = "空间站观察点，直接用 UTC 零时区公历"
            )
        }
        // 地球：公历（4/100/400 闰年规则的平均年恰为 365.2425 日，与累积余数法等价）
        if (planet.gregorianCalendar) {
            return CalendarSpec(
                calendarNameZh = "地球历",
                epochPhaseFraction = planet.epochPhaseFraction,
                epochPhaseSource = planet.epochPhaseSource,
                chain = CalendarChain.YEAR_MONTH_DAY,
                yearNumerator = 3_652_425L,
                yearDenominator = DEN,
                dayLengthSiSeconds = 86_400.0,
                middleNameZh = "月",
                middleCount = 12,
                middleLengthDays = 0,
                monthSystem = true,
                gregorian = true,
                sourceNote = "公历；平太阳日 86400 s，无闰秒、无 ΔT（有意简化）"
            )
        }
        val name = "${planet.nameZh}历"
        // 潮汐锁定：本地日 = 本地年，六单位退化成一维相位
        if (planet.isSynchronous) {
            val yearDays = planet.orbitalPeriodDays.coerceAtLeast(1e-6)
            return CalendarSpec(
                calendarNameZh = name,
                epochPhaseFraction = planet.epochPhaseFraction,
                epochPhaseSource = planet.epochPhaseSource,
                chain = CalendarChain.PHASE,
                // v0.8.8：潮汐锁定天体的「本地日」就是公转周期，所以一年恰好 1 个本地日。
                // 旧写法填的是「公转周期折成多少地球日」（6.0996），那是把本地日当成 UCT 日的历史遗留。
                yearNumerator = DEN,
                yearDenominator = DEN,
                dayLengthSiSeconds = planet.orbitalPeriodDays * 86_400.0,
                middleNameZh = "",
                middleCount = 0,
                middleLengthDays = 0,
                monthSystem = false,
                sourceNote = "同步锁定：公转周期 ${"%.4f".format(yearDays)} d 即本地年，历法只剩相位"
            )
        }
        val daySeconds = planet.solarDaySeconds
        val yearEarthDays = planet.localYearEarthDays
        val yearLocalDays = if (daySeconds.isFinite() && daySeconds > 0.0 && yearEarthDays > 0.0) {
            yearEarthDays * 86_400.0 / daySeconds
        } else 365.2425
        val num = (yearLocalDays * DEN).toLong().coerceAtLeast(1L)
        // 比值 < 1：一年装不下一个本地日，日在前
        if (yearLocalDays < 1.0) {
            return CalendarSpec(
                calendarNameZh = name,
                epochPhaseFraction = planet.epochPhaseFraction,
                epochPhaseSource = planet.epochPhaseSource,
                chain = CalendarChain.DAY_YEAR,
                yearNumerator = num,
                yearDenominator = DEN,
                dayLengthSiSeconds = daySeconds,
                middleNameZh = "",
                middleCount = 0,
                middleLengthDays = 0,
                monthSystem = false,
                sourceNote = "年长 %.5f 本地日（%.3f 地球日 ÷ %.3f 地球日太阳日），一日本地跨 %.2f 年".format(
                    yearLocalDays, yearEarthDays, daySeconds / 86_400.0, 1.0 / yearLocalDays
                )
            )
        }
        // 1 ≤ 比值 < 20：无中间单位（金星 1.925）
        if (yearLocalDays < 20.0) {
            return CalendarSpec(
                calendarNameZh = name,
                epochPhaseFraction = planet.epochPhaseFraction,
                epochPhaseSource = planet.epochPhaseSource,
                chain = CalendarChain.YEAR_DAY,
                yearNumerator = num,
                yearDenominator = DEN,
                dayLengthSiSeconds = daySeconds,
                middleNameZh = "",
                middleCount = 0,
                middleLengthDays = 0,
                monthSystem = false,
                sourceNote = "年长 %.5f 本地日，一年只装得下 %d 个本地日，中间单位没有位置".format(
                    yearLocalDays, yearLocalDays.toInt() + 1
                )
            )
        }
        // 比值 ≥ 20：月制或星制
        val monthPick = pickMonth(planet, yearLocalDays)
        if (monthPick != null) {
            val n = 12
            return CalendarSpec(
                calendarNameZh = name,
                epochPhaseFraction = planet.epochPhaseFraction,
                epochPhaseSource = planet.epochPhaseSource,
                chain = CalendarChain.YEAR_MONTH_DAY,
                yearNumerator = num,
                yearDenominator = DEN,
                dayLengthSiSeconds = daySeconds,
                middleNameZh = "月",
                middleCount = n,
                middleLengthDays = 0,
                monthSystem = true,
                sourceNote = "月制：${monthPick.first} 周期 ${"%.2f".format(monthPick.second)} 本地日，一年排 %.1f 个（落在 8–40）".format(
                    yearLocalDays / monthPick.second
                )
            )
        }
        val override = STAR_OVERRIDES[planet.id]
        val starCount: Int
        val starDays: Int
        if (override != null) {
            starCount = override.first
            starDays = override.second
        } else {
            val picked = pickStarSplit(yearLocalDays)
            starCount = picked.first
            starDays = picked.second
        }
        return CalendarSpec(
            calendarNameZh = name,
            epochPhaseFraction = planet.epochPhaseFraction,
            epochPhaseSource = planet.epochPhaseSource,
            chain = CalendarChain.YEAR_STAR_DAY,
            yearNumerator = num,
            yearDenominator = DEN,
            dayLengthSiSeconds = daySeconds,
            middleNameZh = "星",
            middleCount = starCount,
            middleLengthDays = starDays,
            monthSystem = false,
            sourceNote = "星制：无卫星可用（或周期排不进 8–40），$starCount 星 × $starDays 日，年尾补闰"
        )
    }

    /** 月制判据：有卫星、周期不短于 1 本地日、一年排得出的个数在 8–40。返回该卫星与周期。 */
    fun pickMonth(planet: Planet, yearLocalDays: Double): Pair<String, Double>? {
        val sat = SATELLITES[planet.id] ?: planet.moons.map { it.nameZh to it.periodDays }
        for ((name, period) in sat) {
            if (period < 1.0) continue
            val count = yearLocalDays / period
            if (count >= 8.0 && count <= 40.0) return name to period
        }
        return null
    }

    /**
     * 星数择优：在 8–40 里挑一个能把「整日年长」切得最干净的个数，
     * 平手时取最接近 20 的（20 星最符合「假月」的原有约定）。
     */
    fun pickStarSplit(yearLocalDays: Double): Pair<Int, Int> {
        val base = yearLocalDays.toInt().coerceAtLeast(8)
        var bestN = 20
        var bestRemainder = Int.MAX_VALUE
        var bestDistance = Int.MAX_VALUE
        for (n in 8..40) {
            val days = base / n
            // 星长至少 2 日：1 日一星会退化成「日号的另一种写法」，失去假月的意义
            if (days < 2) continue
            val remainder = base - days * n
            val distance = kotlin.math.abs(n - 20)
            if (remainder < bestRemainder || (remainder == bestRemainder && distance < bestDistance)) {
                bestN = n; bestRemainder = remainder; bestDistance = distance
            }
        }
        return bestN to (base / bestN)
    }

    // ---------- 累积余数法：年号 ↔ 第几个本地日 ----------

    /**
     * 显示年号 [year]（1 起）的第一天是第几个本地日（0 起，纪元当天 = 0）。
     *
     * 地球走公历的 4/100/400 规则（400 年恰 146097 日，等价于平均年 365.2425 日的四百年整循环）；
     * 其余天体一律累积余数法 floor((year − 1) × 年长)，不查表、不漂移。
     */
    fun yearStartDay(year: Long, spec: CalendarSpec): Long =
        if (spec.gregorian) {
            Cal.toEpochDay(year.toInt(), 1, 1) - Cal.toEpochDay(1, 1, 1)
        } else {
            Math.floorDiv((year - 1L) * spec.yearNumerator, spec.yearDenominator)
        }

    /** 第 [dayIndex] 个本地日落在哪个显示年号。 */
    fun yearOfDay(dayIndex: Long, spec: CalendarSpec): Long {
        val guess = Math.floorDiv(dayIndex * spec.yearDenominator, spec.yearNumerator) + 1L
        // 一年短于一个本地日（例如 55 Cancri e 的公转周期不足一日）：同一天内会挤进多个年号，
        // 日粒度的序号无法再把它们区分开，直接返回起始估计，不再做 ±1 修正。
        if (!spec.gregorian && spec.yearNumerator <= spec.yearDenominator) return guess
        var y = guess
        while (yearStartDay(y, spec) > dayIndex) y--
        while (yearStartDay(y + 1L, spec) <= dayIndex) y++
        return y
    }

    /** 该年长多少天（普通年 / 闰年）：公历查规则，其余由累积余数自然产生。 */
    fun daysInYear(year: Long, spec: CalendarSpec): Int =
        (yearStartDay(year + 1L, spec) - yearStartDay(year, spec)).toInt()
}
