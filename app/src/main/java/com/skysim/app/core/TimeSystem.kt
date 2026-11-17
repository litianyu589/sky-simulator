package com.skysim.app.core

import kotlin.math.roundToLong

/** 公历工具（用于 UCT 与地球本地时间）。 */
object Cal {
    fun isLeap(y: Int): Boolean = (y % 4 == 0 && y % 100 != 0) || y % 400 == 0

    fun daysInMonth(y: Int, m: Int): Int = when (m) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        2 -> if (isLeap(y)) 29 else 28
        else -> 30
    }

    /** 公历日期 → 距 1970-01-01 的天数 */
    fun toEpochDay(y: Int, m: Int, d: Int): Long {
        var yy = y.toLong()
        val mm = m.toLong()
        yy -= if (mm <= 2) 1 else 0
        val era = (if (yy >= 0) yy else yy - 399) / 400
        val yoe = yy - era * 400
        val doy = (153 * (if (mm > 2) mm - 3 else mm + 9) + 2) / 5 + d - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097 + doe - 719468
    }

    /** 距 1970-01-01 的天数 → 公历日期 [年, 月, 日] */
    fun fromEpochDay(epochDay: Long): IntArray {
        var z = epochDay + 719468
        val era = (if (z >= 0) z else z - 146096) / 146097
        val doe = z - era * 146097
        val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
        val y = yoe + era * 400
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = doy - (153 * mp + 2) / 5 + 1
        val m = if (mp < 10) mp + 3 else mp - 9
        val year = if (m <= 2) y + 1 else y
        return intArrayOf(year.toInt(), m.toInt(), d.toInt())
    }

    fun dayOfYear(y: Int, m: Int, d: Int): Int =
        (toEpochDay(y, m, d) - toEpochDay(y, 1, 1)).toInt() + 1

    fun format(year: Int, month: Int, day: Int, h: Int, mi: Int, s: Int): String =
        String.format("%04d:%02d:%02d:%02d:%02d:%02d", year, month, day, h, mi, s)
}

/**
 * 某颗星球在某一 UCT 瞬间的本地时间。
 *
 * v0.8.6：字段随单位链变化——[chain] 决定显示形态，[month] 在星制里是「星」的序号，
 * 两者含义由 [chain] 与 [middleNameZh] 说明，不再靠位置猜。
 */
data class LocalStamp(
    val year: Int,
    val month: Int,
    val day: Int,
    val hour: Int,
    val minute: Int,
    val second: Int,
    /** 该星球自己历法的"一年多少本地日"（取整，用于说明与旧接口） */
    val daysPerYear: Int,
    val monthsPerYear: Int,
    val dayOfYearLocal: Int,
    /** 本地日相位 0..1（v0.8.9 起已含纪元自转相位，0 = 真实本地午夜） */
    val dayFraction: Double,
    /** v0.8.9：该天体纪元瞬间的自转相位（本地日小数） */
    val epochPhaseFraction: Double = 0.0,
    /** v0.8.9：相位来源分级（实测锚定 / 实测 / 约定 / 未定） */
    val epochPhaseSource: PhaseSource = PhaseSource.UNDETERMINED,
    /** v0.8.9：这条读数是否含模型假设（除地球公历与空间站外都为 true） */
    val modelBased: Boolean = false,
    /** 本版新增：本地时间进制与派生量 */
    val scheme: DayScheme = DayScheme.EQUAL_24,
    val localSecondRatio: Double = 1.0,
    val solarHourAngleDeg: Double = 0.0,
    val siSecondsIntoDay: Double = 0.0,
    /** 潮汐锁定天体的轨道相位（0–360°），此时没有本地时分秒 */
    val orbitPhaseDeg: Double = 0.0,
    val phaseOnly: Boolean = false,
    /** v0.8.6：单位链与历法名 */
    val chain: CalendarChain = CalendarChain.YEAR_MONTH_DAY,
    val calendarNameZh: String = "公历",
    /** 中间单位名：「月」「星」，无中间单位为空串 */
    val middleNameZh: String = "月",
    /** 中间单位个数（0 = 无） */
    val middleCount: Int = 12,
    /** 当前这一年的实际天数（普通年 / 闰年），由累积余数法产生 */
    val daysInYear: Int = 365,
    /** 当前中间单位的实际天数（年尾闰日会把它撑长） */
    val daysInMiddleUnit: Int = 31,
    /** 仅「日.年」链使用：当前本地日里的第几个年（0 / 1 / 2） */
    val yearInDay: Int = 0,
    /**
     * v0.8.8：从统一纪元起算的**本地日序号**（按该天体的真实太阳日计数）。
     * 它才是有物理意义的「第几个本地日」；显示层不直接印它，但测试与说明用它核对日长。
     */
    val localDayNumber: Long = 0
) {
    /** 年号文本：公元前没有 0 年，年号 ≤ 0 一律写「公元前（1 − 年号）年」。 */
    fun yearText(): String = if (year <= 0) "公元前${1 - year}" else year.toString()

    /** 时:分:秒，各自带单位标签，不靠位置暗示。 */
    fun clockText(): String = String.format("%02d时%02d分%02d秒", hour, minute, second)

    /**
     * v0.8.8：主行的时间部分按**该天体自己的进制**渲染，标签与渲染必须一一对应：
     *  - 24 等分 / SI 秒：时 分 秒（SI 秒档的小时不封顶，日长 10 h 的天体就到 10 时）；
     *  - 十进制粗分：**不出现时分秒**，直接给 0–1 的十进制日相位（0.4172 日），时角另在说明行。
     */
    fun timePartText(): String = when {
        phaseOnly -> "—"
        scheme == DayScheme.COARSE_DECIMAL -> String.format("%.4f 日", dayFraction)
        else -> clockText()
    }

    /** 相位文本：整数度 + 60 分 + 60 秒。 */
    fun phaseText(): String {
        var total = Math.floorMod(Math.round(orbitPhaseDeg * 3600.0), 1_296_000L)
        val d = (total / 3600L).toInt()
        total %= 3600L
        val m = (total / 60L).toInt()
        val s = (total % 60L).toInt()
        return "$d°%02d′%02d″".format(m, s)
    }

    /**
     * v0.8.9：读数前缀。地球公历与空间站读的是现实通行的民用历，不加；
     * 其余天体的历法结构、进制、自转周期都含自定约定或假设，统一加「（模型推测）」。
     */
    fun prefixText(): String = if (modelBased) "（模型推测）" else ""

    /** v0.9.0：左上角本地时间行——只有数字与单位，前缀与限定语改由下面那一行小字承担。 */
    fun hudTimeText(): String = calendarText()

    /**
     * v0.9.0：HUD 小字行（限定语）。地球是基准真值，没有可提示的假设，返回空串；
     * 其余天体至少标「模型推测」，气巨再加「约定相位」，无自转要素的标「相位未定」。
     */
    fun hudQualifierText(): String {
        val parts = ArrayList<String>(2)
        if (modelBased) parts.add("模型推测")
        when (epochPhaseSource) {
            PhaseSource.CONVENTION -> parts.add("约定相位（设定值，非实测）")
            PhaseSource.UNDETERMINED -> parts.add("相位未定")
            PhaseSource.MEASURED -> parts.add("相位实测")
            // 地球与空间站：相位就是定义本身，没有需要提示的假设
            PhaseSource.MEASURED_ANCHOR -> if (modelBased) parts.add("相位实测锚定")
        }
        return parts.joinToString(" · ")
    }

    /** v0.9.0：信息栏的「一年结构」一行。 */
    fun yearStructureText(): String = when (chain) {
        CalendarChain.YEAR_MONTH_DAY -> "年 → 12 月 → 日（%d 日/年）".format(daysInYear)
        CalendarChain.YEAR_STAR_DAY -> "年 → %d 星 → 日（%d 日/年，年尾补闰）".format(middleCount, daysInYear)
        CalendarChain.YEAR_DAY -> "年 → 日（%d 日/年，无中间单位）".format(daysInYear)
        CalendarChain.DAY_YEAR -> "日 → 年（1 年 = 1 本地日，年内年序 0/1）"
        CalendarChain.PHASE -> "只用相位（度分秒），无年月日"
    }

    /** v0.9.0：信息栏的「进制」一行：进制族 + 1 本地秒换算。 */
    fun schemeShortText(): String = when (scheme) {
        DayScheme.EQUAL_24 -> "%s · 1 本地秒 = %.5f SI 秒".format(scheme.labelZh, localSecondRatio)
        DayScheme.SI_SECONDS -> "%s · 1 本地秒 = 1 SI 秒（小时不封顶）".format(scheme.labelZh)
        DayScheme.COARSE_DECIMAL -> "%s · 主行印 0–1 小数日，1 本地秒 = 1 SI 秒".format(scheme.labelZh)
    }

    /** v0.8.9：相位来源短标注，气巨的约定相位要在读数后面立刻可见。 */
    fun phaseSourceText(): String = epochPhaseSource.shortZh

    /** v0.8.9：主显示行 = 前缀 + 历法读数，约定相位的天体在读数后紧接标注。 */
    fun localLineText(): String {
        val tail = if (epochPhaseSource == PhaseSource.CONVENTION) "（约定相位）" else ""
        return prefixText() + calendarText() + tail
    }

    /** 主显示行：每个字段都带单位标签，历法名不在这里（放在天体标题行）。 */
    fun calendarText(): String = when {
        phaseOnly -> phaseText()
        chain == CalendarChain.DAY_YEAR -> "${day}日${yearInDay}年 ${timePartText()}"
        chain == CalendarChain.YEAR_DAY -> "${yearText()}年${day}日 ${timePartText()}"
        chain == CalendarChain.YEAR_STAR_DAY -> "${yearText()}年${month}星${day}日 ${timePartText()}"
        else -> String.format("%s年%02d月%02d日 %s", yearText(), month, day, timePartText())
    }

    /** 兼容旧接口：潮汐锁定仍报「轨道相位」，其余走统一的六字段带标签格式。 */
    fun format(): String = if (phaseOnly) "轨道相位 ${orbitPhaseDeg.toInt()}°（潮汐锁定）" else calendarText()

    fun timeText(): String =
        if (phaseOnly) "—" else String.format("%02d:%02d:%02d", hour, minute, second)

    /** 进制说明：UI 用它标注「这是本地秒，不是 SI 秒」。 */
    fun schemeText(): String {
        val base = when {
            phaseOnly -> "潮汐锁定：太阳固定在天空，无本地昼夜、无日出日落；只显示轨道相位"
            else -> schemeTextPlain()
        }
        return base + " · 相位：" + phaseDetailText()
    }

    /** v0.8.9：相位来源的一句话说明，直接印在说明行，避免把约定值读成实测值。 */
    fun phaseDetailText(): String = when (epochPhaseSource) {
        PhaseSource.MEASURED_ANCHOR ->
            if (epochPhaseFraction == 0.0 && !modelBased) "实测锚定（UT1 定义，相位 0）"
            else "实测锚定（纪元瞬间本地时 %.5f 日）".format(epochPhaseFraction)
        PhaseSource.MEASURED -> "实测（2015 自转要素，纪元瞬间本地时 %.5f 日）".format(epochPhaseFraction)
        PhaseSource.CONVENTION -> "约定值：按 IAU 2015 平均自转速率外推，非实测，相位实质不可定"
        PhaseSource.UNDETERMINED -> "未定：没有可用的自转要素，读数按相位 0 起算"
    }

    private fun schemeTextPlain(): String = when (scheme) {
        DayScheme.EQUAL_24 ->
            "%s · 1 本地秒 = %.5f SI 秒（本地日 %.3f s）".format(scheme.labelZh, localSecondRatio, siSecondsIntoDay)
        DayScheme.SI_SECONDS ->
            "%s · 本地日 %.0f SI 秒（%.2f h，时分秒按 SI 秒进位）"
                .format(scheme.labelZh, siSecondsIntoDay, siSecondsIntoDay / 3600.0)
        DayScheme.COARSE_DECIMAL ->
            "%s · 日相位 %.4f（0–1 小数日，1000 等分）· 太阳时角 %.1f° · 本地日 %.0f SI 秒"
                .format(scheme.labelZh, dayFraction, solarHourAngleDeg, siSecondsIntoDay)
    }
}

/** UCT 与各星球本地时间的换算。 */
object TimeSystem {

    /**
     * v0.8.6：统一纪元。所有天体「本地 1 年 1 月 1 日 00:00:00」= UCT 0001-01-01T00:00:00Z。
     * 该瞬间用公历（前推格里高利历，天文纪年）表示就是公元 1 年 1 月 1 日。
     */
    val EPOCH_EPOCH_DAY: Long = Cal.toEpochDay(1, 1, 1)

    fun nowUctSeconds(): Long = System.currentTimeMillis() / 1000L

    /** 毫秒精度的 UCT 时刻（自动播放需要亚秒级推进） */
    fun nowUctMillis(): Long = System.currentTimeMillis()

    fun uctEpochDay(uctSeconds: Long): Long = Math.floorDiv(uctSeconds, 86400L)

    fun uctFractionOfDay(uctSeconds: Long): Double {
        val secOfDay = Math.floorMod(uctSeconds, 86400L)
        return secOfDay / 86400.0
    }

    /** UCT 读数：与 UTC 零时区公历同一套六字段形态（年 月 日 时 分 秒，各带单位）。 */
    fun uctStamp(uctSeconds: Long): LocalStamp {
        val days = uctEpochDay(uctSeconds)
        val (y, m, d) = Cal.fromEpochDay(days).let { Triple(it[0], it[1], it[2]) }
        val sec = Math.floorMod(uctSeconds, 86400L)
        return LocalStamp(
            year = y, month = m, day = d,
            hour = (sec / 3600).toInt(), minute = (sec % 3600 / 60).toInt(), second = (sec % 60).toInt(),
            daysPerYear = if (Cal.isLeap(y)) 366 else 365,
            monthsPerYear = 12,
            localDayNumber = days - EPOCH_EPOCH_DAY,
            dayOfYearLocal = Cal.dayOfYear(y, m, d),
            dayFraction = uctFractionOfDay(uctSeconds),
            chain = CalendarChain.YEAR_MONTH_DAY,
            calendarNameZh = "公历",
            middleNameZh = "月",
            middleCount = 12,
            daysInYear = if (Cal.isLeap(y)) 366 else 365,
            daysInMiddleUnit = Cal.daysInMonth(y, m)
        )
    }

    /** 公历（UCT）整行文本：历法名 + 六字段。 */
    fun uctText(uctSeconds: Long): String = "公历（UCT）${uctStamp(uctSeconds).calendarText()}"

    /**
     * v0.8.8：把「本地日相位」按该天体的进制切成时 / 分 / 秒三个字段。
     *  - [DayScheme.EQUAL_24]：24 时 × 60 分 × 60 秒，秒是本地秒（= 太阳日/86400 SI 秒）；
     *  - [DayScheme.SI_SECONDS]：小时不封顶（日长可能超过 24 h 就照实显示 0..dayLen/3600），分秒 60 进位；
     *  - [DayScheme.COARSE_DECIMAL]：10 × 10 × 10，即十进制粗分，三个字段都在 0..9。
     */
    fun localClockFields(scheme: DayScheme, dayFraction: Double, dayLengthSi: Double): IntArray {
        val f = dayFraction - kotlin.math.floor(dayFraction)
        return when (scheme) {
            DayScheme.COARSE_DECIMAL -> {
                val h = (f * 10.0).toInt()
                val m = ((f * 100.0) % 10.0).toInt()
                val s = ((f * 1000.0) % 10.0).toInt()
                intArrayOf(h, m, s)
            }
            DayScheme.SI_SECONDS -> {
                val sec = f * dayLengthSi
                intArrayOf((sec / 3600.0).toInt(), ((sec % 3600.0) / 60.0).toInt(), (sec % 60.0).toInt())
            }
            DayScheme.EQUAL_24 -> {
                val sec = f * 86400.0
                intArrayOf((sec / 3600.0).toInt(), ((sec % 3600.0) / 60.0).toInt(), (sec % 60.0).toInt())
            }
        }
    }

    /**
     * 星球本地时间。
     *
     * 刻度沿用 v0.6.9 的规范化：1 个 UCT 日 = 1 个本地日（渲染层按这个约定驱动太阳位置，
     * 本版没有改动渲染）。日期部分在本版重写：从**统一纪元**起算本地日序号，
     * 再按该天体的单位链换算成年 / 中间单位 / 日，闰日由累积余数法产生。
     */
    /**
     * v0.8.9：该天体一个本地日的 SI 秒数。localStamp、日出日落按钮、时间轴滑条共用这一份口径，
     * 避免「显示用一个日长、按钮用另一个」的分裂。
     */
    fun localDayLengthSeconds(planet: Planet): Double {
        // 地球的显示层走公历（本地日恒 86400 s），相位换算必须用同一个口径，
        // 否则滑条与按钮会在 86400 与 86399.991 之间累积出小时级偏差。
        if (planet.gregorianCalendar) return 86_400.0
        val rawDay = planet.solarDaySeconds
        return when {
            rawDay.isFinite() && rawDay >= 1.0 -> rawDay
            planet.isSynchronous && planet.orbitalPeriodDays > 0.0 -> planet.orbitalPeriodDays * 86400.0
            else -> 86400.0
        }
    }

    /** v0.8.9：本地日相位（0 = 真实本地午夜，0.5 = 本地正午），已含纪元自转相位。 */
    fun localPhaseOf(planet: Planet, uctSeconds: Long): Double {
        val d = localDayLengthSeconds(planet)
        val v = (uctSeconds - EPOCH_EPOCH_DAY * 86400L).toDouble() / d + planet.epochPhaseFraction
        return v - kotlin.math.floor(v)
    }

    /**
     * v0.8.9：把「当前本地日」的指定日相位换算回 UCT 秒。
     * 主变量仍然只有一个（UCT 秒），这里只是同一个量的另一种参数化：
     * 本地相位 φ 与 UCT 的关系是 φ = frac((UCT − 纪元)/日长 + f)，可逆，不新增状态。
     */
    fun uctSecondsForLocalPhase(planet: Planet, uctSeconds: Long, phase: Double): Long {
        val d = localDayLengthSeconds(planet)
        val f = planet.epochPhaseFraction
        val v = (uctSeconds - EPOCH_EPOCH_DAY * 86400L).toDouble() / d + f
        val k = kotlin.math.floor(v)
        // 向上取整：整秒分辨率下宁可晚 1 s，也不要让「午夜」落到 23:59:59（滑条与按钮的边界体验）
        return kotlin.math.ceil(EPOCH_EPOCH_DAY * 86400L + (k - f + phase) * d).toLong()
    }

    fun localStamp(planet: Planet, uctSeconds: Long, tidalNightSide: Boolean = false): LocalStamp {
        val spec = LocalCalendars.of(planet)
        // v0.8.8：本地日按**真实物理日长**推进，不再把「1 个 UCT 日」当成「1 个本地日」。
        // 从统一纪元（UCT 0001-01-01T00:00:00Z）起算经过的 SI 秒，除以该天体的太阳日长度，
        // 得到本地日计数与日相位；年月日、太阳时角、相位、本地时分秒全部由这两个量推出。
        // 渲染层仍按 UCT 当天的日进度驱动太阳（控制/渲染的既有简化），两件事互不借用。
        // 潮汐锁定天体的太阳日数学上发散（太阳不动），它们的「本地日」就是公转周期；
        // 空间站与其它异常值退回 86400 s。口径与 localDayLengthSeconds 同一份。
        val dayLengthSi = localDayLengthSeconds(planet)
        val elapsedSeconds = (uctSeconds - EPOCH_EPOCH_DAY * 86400L).toDouble()
        // v0.8.9：加上纪元自转相位——纪元瞬间不再是各天体共同的「本地午夜」，
        // 而是各自真实平太阳午夜之后的 f 个本地日（地球 f = 0，火星 f = 0.09281）。
        val localDayFloat = elapsedSeconds / dayLengthSi + planet.epochPhaseFraction
        val localDayIndex = kotlin.math.floor(localDayFloat).toLong()
        // 0 = 本地午夜，0.5 = 本地正午；太阳时角由它推出（正午 0°，午夜 180°）
        val dayFraction = localDayFloat - localDayIndex
        val scheme = LocalTimeBase.scheme(planet)
        val clockFields = localClockFields(scheme, dayFraction, dayLengthSi)
        val hh = clockFields[0]
        val mi = clockFields[1]
        val ss = clockFields[2]

        // 空间站：站内时钟直接使用 UCT 公历与 SI 秒
        if (planet.isStation) {
            val base = uctStamp(uctSeconds)
            return base.copy(
                localDayNumber = uctEpochDay(uctSeconds) - EPOCH_EPOCH_DAY,
                scheme = DayScheme.SI_SECONDS,
                localSecondRatio = 1.0,
                solarHourAngleDeg = LocalTimeBase.solarHourAngleDeg(base.dayFraction),
                siSecondsIntoDay = 86400.0,
                orbitPhaseDeg = 0.0,
                phaseOnly = false,
                calendarNameZh = "公历",
                epochPhaseFraction = planet.epochPhaseFraction,
                epochPhaseSource = planet.epochPhaseSource,
                modelBased = false
            )
        }
        // 地球：公历。本地读数与 UCT 完全同构（同一套六字段、同一套进位）
        if (planet.gregorianCalendar) {
            val base = uctStamp(uctSeconds)
            return base.copy(
                localDayNumber = uctEpochDay(uctSeconds) - EPOCH_EPOCH_DAY,
                scheme = LocalTimeBase.scheme(planet),
                localSecondRatio = LocalTimeBase.localSecondRatio(planet),
                solarHourAngleDeg = LocalTimeBase.solarHourAngleDeg(base.dayFraction),
                siSecondsIntoDay = planet.solarDaySeconds,
                calendarNameZh = spec.calendarNameZh,
                epochPhaseFraction = planet.epochPhaseFraction,
                epochPhaseSource = planet.epochPhaseSource,
                modelBased = false
            )
        }

        val common = LocalStamp(
            year = 1, month = 1, day = 1,
            localDayNumber = localDayIndex,
            hour = hh, minute = mi, second = ss,
            daysPerYear = planet.localDaysPerYear,
            monthsPerYear = spec.middleCount.coerceAtLeast(1),
            dayOfYearLocal = 1,
            dayFraction = dayFraction,
            scheme = scheme,
            localSecondRatio = LocalTimeBase.localSecondRatio(planet),
            solarHourAngleDeg = LocalTimeBase.solarHourAngleDeg(dayFraction),
            siSecondsIntoDay = planet.solarDaySeconds,
            orbitPhaseDeg = dayFraction * 360.0,
            phaseOnly = planet.isSynchronous,
            chain = spec.chain,
            calendarNameZh = spec.calendarNameZh,
            middleNameZh = spec.middleNameZh,
            middleCount = spec.middleCount,
            daysInYear = 1,
            daysInMiddleUnit = 1,
            epochPhaseFraction = planet.epochPhaseFraction,
            epochPhaseSource = planet.epochPhaseSource,
            modelBased = true
        )

        // 潮汐锁定：本地时刻冻结（永昼面 12:00 / 永夜面 00:00），日期与相位继续推进
        if (spec.chain == CalendarChain.PHASE) {
            val year = LocalCalendars.yearOfDay(localDayIndex, spec)
            // 一年短于一个本地日时（公转周期不足 1 日的锁定天体），日粒度已无法区分年内的第几天，
            // 只保留年号与相位，日固定为 1，避免出现「第 0 天」或越界日号。
            val subDayYear = spec.yearNumerator <= spec.yearDenominator
            val yearLength = if (subDayYear) 1 else LocalCalendars.daysInYear(year, spec)
            val doy = if (subDayYear) 1 else (localDayIndex - LocalCalendars.yearStartDay(year, spec)).toInt() + 1
            return common.copy(
                year = year.toInt(),
                month = 1,
                day = doy,
                hour = if (tidalNightSide) 0 else 12,
                minute = 0,
                second = 0,
                dayOfYearLocal = doy,
                daysInYear = yearLength,
                daysInMiddleUnit = yearLength
            )
        }

        // 日.年：一年短于一个本地日（水星 0.5 本地日），日在前，年里只有 0 / 1
        if (spec.chain == CalendarChain.DAY_YEAR) {
            val yearNow = Math.floorDiv(
                localDayIndex * spec.yearDenominator + (dayFraction * spec.yearDenominator).toLong(),
                spec.yearNumerator
            )
            val yearAtDayStart = Math.floorDiv(localDayIndex * spec.yearDenominator, spec.yearNumerator)
            val yearInDay = (yearNow - yearAtDayStart).toInt()
            return common.copy(
                year = (yearNow + 1L).toInt(),
                month = 1,
                day = (localDayIndex + 1L).toInt(),
                yearInDay = yearInDay,
                dayOfYearLocal = yearInDay + 1,
                daysInYear = 1,
                daysInMiddleUnit = 1
            )
        }

        val year = LocalCalendars.yearOfDay(localDayIndex, spec)
        val start = LocalCalendars.yearStartDay(year, spec)
        val doy = (localDayIndex - start).toInt()
        val yearLength = LocalCalendars.daysInYear(year, spec)

        // 年.日：一年只装得下一两天（金星 1.925）
        if (spec.chain == CalendarChain.YEAR_DAY) {
            return common.copy(
                year = year.toInt(),
                month = 1,
                day = doy + 1,
                dayOfYearLocal = doy + 1,
                daysInYear = yearLength,
                daysInMiddleUnit = yearLength
            )
        }

        // 年.星.日：固定整数星长，年尾零头把最后一星撑长（闰日全部落在年尾）
        if (spec.chain == CalendarChain.YEAR_STAR_DAY) {
            val n = spec.middleCount
            val f = spec.middleLengthDays
            val idx = if (f <= 0) 0 else kotlin.math.min(doy / f, n - 1)
            val dayInStar = doy - idx * f + 1
            val starLength = if (idx == n - 1) yearLength - idx * f else f
            return common.copy(
                year = year.toInt(),
                month = idx + 1,
                day = dayInStar,
                dayOfYearLocal = doy + 1,
                daysInYear = yearLength,
                daysInMiddleUnit = starLength
            )
        }

        // 年.月.日（月制、非地球）：按 12 个月均分，余数塞给前面的月
        val months = 12
        val baseMonth = yearLength / months
        val extra = yearLength - baseMonth * months
        var remain = doy
        var month = 1
        while (month <= months && remain >= baseMonth + if (month <= extra) 1 else 0) {
            remain -= baseMonth + if (month <= extra) 1 else 0
            month++
        }
        if (month > months) month = months
        val monthLength = baseMonth + if (month <= extra) 1 else 0
        return common.copy(
            year = year.toInt(),
            month = month,
            day = remain + 1,
            dayOfYearLocal = doy + 1,
            daysInYear = yearLength,
            daysInMiddleUnit = monthLength
        )
    }

    /** 该天体所在轨道系统的一年相当于多少地球日；卫星使用母行星绕恒星周期。 */
    fun localYearEarthDays(planet: Planet): Double = planet.localYearEarthDays

    fun formatDuration(seconds: Double): String {
        val total = Math.round(seconds).toLong()
        val d = total / 86400
        val h = (total % 86400) / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return when {
            d > 0 -> "${d}天 ${h}时${m}分"
            h > 0 -> "${h}时${m}分${s}秒"
            else -> "${m}分${s}秒"
        }
    }
}
