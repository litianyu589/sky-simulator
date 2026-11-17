package com.skysim.app.core

import kotlin.math.atan
import kotlin.math.roundToInt

/** 线性 RGB，分量范围 0..1（不做 gamma 处理）。 */
data class Rgb(val r: Float, val g: Float, val b: Float) {
    fun scale(k: Float) = Rgb(r * k, g * k, b * k)
    fun mix(o: Rgb, t: Float) = Rgb(r + (o.r - r) * t, g + (o.g - g) * t, b + (o.b - b) * t)
    fun clamp01() = Rgb(r.coerceIn(0f, 1f), g.coerceIn(0f, 1f), b.coerceIn(0f, 1f))
}

/** 天空表面的性质，决定地平线以下画什么。 */
/** 自转-公转的三态（v0.6.10）。 */
enum class SpinState(val labelZh: String) {
    /** 同步锁定：自转周期 = 公转周期，没有本地昼夜 */
    SYNCHRONOUS("同步锁定"),
    /** 3:2 共振：水星式，一个太阳日 = 两个轨道年，太阳会倒退一次 */
    RESONANT_3_2("3:2 共振"),
    /** 自由自转：有正常的昼夜循环 */
    FREE("自由自转")
}

enum class SurfaceKind { ROCK, ICE, DUST, CLOUD_DECK }

/** 天空中可见的天然卫星（角直径取平均视直径）。 */
data class Moon(
    val nameZh: String,
    val angularDiameterDeg: Float,
    val periodDays: Double,
    val tint: Rgb,
    val phaseOffsetDeg: Float = 0f,
    val declinationDeg: Float = 0f
)

/** 行星环：半径以行星半径为单位给出真实公里数。 */
data class Ring(
    val innerRadiusKm: Double,
    val outerRadiusKm: Double,
    val tint: Rgb,
    val opacity: Float
)

/** 天体分组，用于选择界面的分类。由 kindZh 自动推导（见 [Planet.group]）。 */
enum class PlanetGroup(val labelZh: String, val shortZh: String) {
    PLANET("太阳系行星", "行星"),
    MOON("外太阳系与卫星", "卫星"),
    DWARF("矮行星与小行星", "矮行星"),
    EXOPLANET("系外行星", "系外")
}

/**
 * 大气数据的置信度。系外行星的视觉效果本质上不可验证，必须把结论的可靠性标出来：
 * - OBSERVED：有光谱 / 凌星等观测支持的大气特征
 * - MODELED：由质量、半径、平衡温度等推断出的模型结果
 * - UNKNOWN：连有没有大气都不确定，一律按「无大气」保守处理
 */
enum class DataConfidence(val labelZh: String) {
    OBSERVED("大气：有观测证据"),
    MODELED("大气：模型推测"),
    UNKNOWN("大气：未知（按无大气处理）")
}

/**
 * v0.8.9：纪元自转相位的来源分级。相位本身是「纪元瞬间该天体本初子午线上的本地平太阳时」，
 * 以本地日为单位的小数；来源决定这条读数能承诺到什么程度，界面必须如实标注。
 */
enum class PhaseSource(val labelZh: String, val shortZh: String) {
    /** 实测锚定（地球 UT1 定义、火星着陆器/MOLA 测地与 MTC 交叉验证） */
    MEASURED_ANCHOR("实测锚定", "实测锚定"),

    /** 2015 自转要素可用的测量值（水星、金星、月球、伽利略卫星、土卫六、海卫一、冥王星） */
    MEASURED("实测", "实测"),

    /** 约定值：按 IAU 2015 平均自转速率从纪元线性外推，是设定值不是测量值（四颗气态巨行星） */
    CONVENTION("约定相位（设定值，非实测）", "约定相位"),

    /** 没有可用的自转要素，相位不可定（系外行星、小行星、未列入核对表的卫星） */
    UNDETERMINED("相位未定（无自转要素）", "未定")
}

/**
 * 星球数据。所有物理量均为公开天文常数（近似值），用于本应用的视觉模拟。
 * rayTau / aerosolTau 是 550nm 处的垂直光学厚度，用来推导天空颜色。
 */
data class Planet(
    val id: String,
    val nameZh: String,
    val nameEn: String,
    val kindZh: String,
    val radiusKm: Double,
    /**
     * 恒星自转周期（秒，负值表示逆行）。**天球的周日旋转一律用这个量**：
     * 地球 86164.0905 s（23h56m04.09s），不是 86400。
     */
    val siderealPeriodOverrideSeconds: Double = 0.0,
    /**
     * 自转/公转周期比。**三态判定的唯一依据**：
     * 1 = 同步锁定，2/3（水星，自转 3 次 / 公转 2 周）= 3:2 共振，其余 = 自由自转。
     * 0 = 未指定，此时由恒星日 / 公转周期 推算（老数据兼容）。
     */
    val spinOrbitRatio: Double = 0.0,
    /** 轨道偏心率（v0.7.0）：方程中心修正真近点角，水星 0.2056 */
    val eccentricity: Double = 0.0,
    /** 近日点幅角（度），决定倒退环落在轨道上的哪一段 */
    val perihelionAngleDeg: Double = 0.0,
    /** 地表反照率（0–1），影响地平线亮度与地气耦合 */
    val surfaceAlbedo: Double = 0.30,
    /** 稀薄外逸层的钠辉光强度（0–1），水星专用字段但由数据驱动 */
    val sodiumGlow: Double = 0.0,
    /** 恒星视直径覆盖值（度），0 = 由恒星半径与日距推出 */
    val starAngularDiameterOverrideDeg: Double = 0.0,
    /** 公转周期（地球日） */
    val orbitalPeriodDays: Double,
    val axialTiltDeg: Double,
    val gravity: Double,
    val pressureAtm: Double,
    val scaleHeightKm: Double,
    val compositionZh: String,
    val sunDistanceAu: Double,
    val rayTau: Double,
    val aerosolTau: Double,
    val aerosolTint: Rgb,
    /**
     * 气溶胶散射相函数的一阶不对称因子 g（0 = 各向同性，0.7–0.9 = 强前向散射的矿物尘埃）。
     * 天空模型用它决定「太阳附近的前向散射亮区」，不再按天体 id 写死。
     */
    val aerosolAsymmetry: Float = 0f,
    /**
     * 臭氧柱量（地球 = 1，约 0.32 atm·cm）。只影响晨昏分层里的 Chappuis 吸收带：
     * 地球 1.0；当前目录中火星及其它未确认有显著臭氧柱的天体设为 0。
     */
    val ozoneColumn: Float = 0f,
    /** 甲烷柱量（相对地球整层 = 1），吸收暮光 → 晨昏变短（天王星/海王星/土卫六） */
    val methaneColumn: Double = 0.0,
    /** 二氧化碳柱量（相对地球整层 = 1），红外吸收 */
    val co2Column: Double = 0.0,
    val groundTint: Rgb,
    val nightTint: Rgb,
    val surfaceKind: SurfaceKind,
    val moons: List<Moon> = emptyList(),
    val ring: Ring? = null,
    val starVisibility: Float = 1f,
    /**
     * 白天能看到星星的强度（0 = 完全看不到）。
     *
     * 有大气时白天满天散射光会把暗星淹没，这个字段没有意义；没有大气的天体
     * （月球、水星）天空不散射阳光，星点白天照样在，只是被地面反光与太阳压住一部分。
     * v0.8.0 新增，缺省 0 表示「不做例外」，因此不影响任何旧数据行。
     */
    val dayStarAlpha: Float = 0f,
    val seasonPhase: Float = 0f,
    /** 母星名称（太阳系天体为「太阳」） */
    val starNameZh: String = "太阳",
    /** 母星有效温度（K） */
    val starTempK: Double = 5772.0,
    /** 母星半径（太阳半径 = 1） */
    val starRadiusSolar: Double = 1.0,
    /** 母星光度（太阳 = 1） */
    val starLuminositySolar: Double = 1.0,
    /** 距地球（光年，0 表示太阳系内） */
    val distanceLy: Double = 0.0,
    /** 平衡温度（K，0 表示未填） */
    val eqTempK: Double = 0.0,
    /** 是否潮汐锁定（一面永昼、一面永夜） */
    val tidallyLocked: Boolean = false,
    /**
     * v0.8.9：纪元瞬间的本地自转相位（单位：本地日的小数，0 = 本地午夜、0.5 = 本地正午）。
     * 加入本地日浮点后，本地 00:00 才落在该天体真实的平太阳午夜上。
     * 数值来源与可信度见 [epochPhaseSource]，出处见《自转相位核对表》。
     */
    val epochPhaseFraction: Double = 0.0,
    /** v0.8.9：上面那个相位的来源分级；缺省 = 没有可用自转要素 */
    val epochPhaseSource: PhaseSource = PhaseSource.UNDETERMINED,
    /** 大气数据的置信度 */
    val confidence: DataConfidence = DataConfidence.OBSERVED,
    /** 半径（地球半径 = 1，0 表示未填） */
    val radiusEarths: Double = 0.0,
    /** 质量（地球质量 = 1，0 表示未填） */
    val massEarths: Double = 0.0,
    /** 天空颜色推导依据的一句话说明 */
    val skyBasisZh: String = "",
    /** 所属恒星 id（对应 StarData） */
    val hostStarId: String = "sun",
    /** 是否为「空间站」观察点（无大气、无地面） */
    val isStation: Boolean = false,
    /**
     * 本地历法是否直接采用公历（只有地球为 true）。
     * 有了这个字段，TimeSystem 里就不用再写 `id == "earth"` 的分支。
     */
    val gregorianCalendar: Boolean = false,
    /**
     * 本地时钟把**自己的太阳日**分成多少格（地球 24，对应 24 小时制）。
     * 这里的 86400 秒是地球的太阳日长度，不是「一天的通用长度」；其它天体按自己的太阳日切分。
     */
    val dayClockHours: Int = 24,
    /**
     * 气溶胶相函数不对称因子 g 的表内缺省值（物理量）。
     * 0 = 各向同性散射，0.6–0.9 = 强前向散射的雾霾/尘埃。
     */
    val mieAsymmetry: Float = 0.6f,
    /**
     * 以下是**视觉标定值，不是物理量**，集中放在同一张表里，方便按天体微调而不用改代码。
     * 全部为 1（或 0）时等于纯物理结果。
     */
    /** 低太阳时的暖色偏移（0 = 不加，0.2 = 明显偏暖） */
    val visualWarmBias: Float = 0f,
    /** 地平线辉光幅度倍率 */
    val horizonGlowScale: Float = 1f,
    /** 暮光整体压暗倍率 */
    val twilightDimScale: Float = 1f,
    /** 维纳斯带颜色覆盖（null = 由臭氧柱量与气溶胶色推导） */
    val beltTintOverride: Rgb? = null,
    /**
     * 推导太阳日时该用哪个公转周期（地球日）。
     *
     * 卫星的 `orbitalPeriodDays` 是**绕母星**的周期（月球 27.32 天），而太阳的视运动由
     * **绕日**周期决定；不区分的话月球的太阳日会算成「27.32 天与 27.32 天相消 → 无限大」。
     * 0 = 直接用 orbitalPeriodDays（绝大多数天体）。
     */
    val heliocentricPeriodDays: Double = 0.0,
    /**
     * 本地时间进制的覆盖值（null = 按 [LocalTimeBase.autoScheme] 的判据自动选取）。
     * 这是数据表字段，不是代码分支。
     */
    val localDaySchemeOverride: DayScheme? = null
) {
    /** 所属分组 */
    val group: PlanetGroup
        get() = when {
            kindZh.contains("系外") -> PlanetGroup.EXOPLANET
            kindZh.contains("卫星") -> PlanetGroup.MOON
            kindZh.contains("矮行星") || kindZh.contains("小行星") -> PlanetGroup.DWARF
            else -> PlanetGroup.PLANET
        }

    /** 母星相对太阳的偏色（太阳为 (1,1,1)） */
    val starColor: Rgb
        get() = Stars.relativeColor(starTempK)

    /** 多恒星系统的综合色：按各恒星在行星处的辐照度加权。 */
    val skyIlluminationColor: Rgb
        get() {
            val sources = StarCatalog.visualSources(hostStarId, sunDistanceAu)
            val total = sources.sumOf { it.irradianceSolar }.coerceAtLeast(1e-12)
            return Rgb(
                (sources.sumOf { it.color.r * it.irradianceSolar } / total).toFloat(),
                (sources.sumOf { it.color.g * it.irradianceSolar } / total).toFloat(),
                (sources.sumOf { it.color.b * it.irradianceSolar } / total).toFloat()
            )
        }

    val spectralTypeZh: String
        get() = Stars.spectralTypeOf(starTempK).labelZh

    /** 母星视直径（度） */
    val sunAngularDiameterDeg: Float
        get() = if (starAngularDiameterOverrideDeg > 0.0) starAngularDiameterOverrideDeg.toFloat()
        else (2.0 * atan(starRadiusSolar * SUN_RADIUS_KM / (sunDistanceAu * AU_KM)) * 180.0 / Math.PI).toFloat()

    /** 相对地球的日照强度（L/d^2） */
    val sunIrradiance: Float
        get() = StarCatalog.visualSources(hostStarId, sunDistanceAu)
            .sumOf { it.irradianceSolar }.toFloat()

    /** 一个本地年包含多少个本地太阳日（卫星以母行星绕恒星周期为年长，取整） */
    val localDaysPerYear: Int
        get() {
            // 空间站没有行星历法；UI 时钟直接使用 UCT。
            if (isStation) return 365
            // 同步锁定行星没有有限太阳日，沿用「轨道周期折算日」的相位历法。
            if (isSynchronous) return orbitalPeriodDays.roundToInt().coerceIn(1, 2_000_000)
            val yearDays = solarDayReferenceDays
            val daySec = solarDaySeconds.coerceAtLeast(1.0)
            // 卫星的一年是母行星绕恒星一周，不是卫星绕母行星一周。
            val raw = yearDays * 86400.0 / daySec
            if (!raw.isFinite() || raw.isNaN()) return 1
            return raw.roundToInt().coerceIn(1, 2_000_000)
        }

    /** 本地历法的月数 */
    val monthsPerYear: Int
        get() = when {
            localDaysPerYear >= 336 -> 12
            localDaysPerYear >= 60 -> 6
            localDaysPerYear >= 12 -> 4
            else -> localDaysPerYear
        }

    /** 每月天数（把 localDaysPerYear 尽量平均分配，余数塞给前面的月） */
    val monthLengths: IntArray
        get() {
            val n = monthsPerYear
            val base = localDaysPerYear / n
            var rest = localDaysPerYear - base * n
            return IntArray(n) { _ ->
                val extra = if (rest > 0) { rest--; 1 } else 0
                base + extra
            }
        }

    /** 1 本地秒等于多少地球秒 */
    val localSecondLengthSeconds: Double
        get() = solarDaySeconds / 86400.0

    /** 本地日长（小时） */
    val localDayHours: Double
        get() = solarDaySeconds / 3600.0

    /** 自转/公转比值：表里填了就用表里的，否则由恒星日与公转周期推出。 */
    /** 自转共振所对应的轨道周期：卫星绕母行星，其它天体绕恒星。 */
    val spinOrbitReferenceDays: Double
        get() = when {
            isStation -> 0.0
            isSatellite -> orbitalPeriodDays
            else -> solarDayReferenceDays
        }

    val spinOrbitRatioEffective: Double
        get() = when {
            isStation -> 0.0 // 空间站不是行星自转体，不参与同步锁定分类
            spinOrbitRatio > 0.0 -> spinOrbitRatio
            siderealPeriodOverrideSeconds != 0.0 && spinOrbitReferenceDays > 0.0 ->
                kotlin.math.abs(siderealPeriodOverrideSeconds) / (spinOrbitReferenceDays * 86400.0)
            else -> 0.0
        }

    /** 三态：同步锁定 / 3:2 共振 / 自由自转。 */
    val spinState: SpinState
        get() {
            val r = spinOrbitRatioEffective
            return when {
                isStation -> SpinState.FREE
                r <= 0.0 -> SpinState.FREE
                kotlin.math.abs(r - 1.0) < 0.02 -> SpinState.SYNCHRONOUS
                // 3:2 共振按「周期比」记是 2/3（自转 3 次 / 公转 2 周，水星 58.646/87.969）；
                // 反过来 3 圈公转 2 次自转的写法 1.5 也认，避免两种记法互相打架
                kotlin.math.abs(r - 2.0 / 3.0) < 0.02 || kotlin.math.abs(r - 1.5) < 0.02 -> SpinState.RESONANT_3_2
                else -> SpinState.FREE
            }
        }

    /** 恒星视半径（度）：优先用显式视直径，否则由 恒星半径/日距 推出。 */
    val starAngularRadiusDeg: Double
        get() {
            if (starAngularDiameterOverrideDeg > 0.0) return starAngularDiameterOverrideDeg / 2.0
            if (starRadiusSolar <= 0.0 || sunDistanceAu <= 0.0) return 0.2665
            val rKm = starRadiusSolar * 696000.0
            val dKm = sunDistanceAu * 1.495979e8
            return Math.toDegrees(kotlin.math.atan(rKm / dKm))
        }

    /** 恒星视直径（度）。 */
    val starAngularDiameterDegResolved: Double
        get() = starAngularRadiusDeg * 2.0

    /** 是否有可站立的固体/液体表面；气巨星的「地平线」是 1 bar 云顶。 */
    val hasSolidSurface: Boolean
        get() = !isStation && surfaceKind != SurfaceKind.CLOUD_DECK

    /** 是否为卫星（数据字段决定，不看名字猜）。卫星锁定的是母星，不是太阳。 */
    val isSatellite: Boolean
        get() = heliocentricPeriodDays > 0.0 || kindZh.endsWith("卫星")

    /**
     * 同步锁定：没有本地昼夜，太阳日数学上发散。
     * 卫星被母星锁定（月球、土卫六）不算 —— 太阳相对它仍在动，只是慢。
     */
    val isSynchronous: Boolean
        get() = !isStation && spinState == SpinState.SYNCHRONOUS && !isSatellite

    /** 恒星日（秒，逆行为负）：优先用表里的绝对值，否则由 公转周期 × 比值 推出。 */
    val siderealRotationPeriodSeconds: Double
        get() {
            // 显式负恒星周期本身就表示逆行；大于 90° 的轴倾角也按逆行折叠。
            val sign = if (siderealPeriodOverrideSeconds < 0.0 || axialTiltDeg > 90.0) -1.0 else 1.0
            val raw = when {
                siderealPeriodOverrideSeconds != 0.0 ->
                    kotlin.math.abs(siderealPeriodOverrideSeconds)
                spinOrbitRatio > 0.0 && spinOrbitReferenceDays > 0.0 ->
                    spinOrbitReferenceDays * 86400.0 * spinOrbitRatio
                else -> 0.0
            }
            return sign * raw
        }

    /** 恒星日（小时，负值表示逆行）：由 [siderealRotationPeriodSeconds] 推出，不再单独存一份。 */
    val siderealDayHours: Double
        get() = siderealRotationPeriodSeconds / 3600.0

    /**
     * 由恒星日与公转周期推出的**太阳日**（秒，逆行为负）：
     *   1/T_solar = 1/T_sidereal − 1/T_orbit
     * 地球：1/86164.0905 − 1/(365.256×86400) = 1/86400.0 → 差值 235.9095 s（太阳日更长）。
     * 潮汐锁定或周期几乎相等时退化为恒星日（太阳不动）。
     */
    val solarDaySignedSeconds: Double
        get() {
            // 空间站没有地表太阳日；对其时间显示采用 86400 SI 秒的 UCT 日。
            if (isStation) return 86400.0
            if (siderealRotationPeriodSeconds == 0.0) return 0.0
            // 同步锁定：太阳在天空中不动，太阳日发散（不是「等于公转周期」）
            if (isSynchronous) return Double.POSITIVE_INFINITY
            val ref = solarDayReferenceDays
            if (ref <= 0.0) return siderealRotationPeriodSeconds
            val inv = 1.0 / siderealRotationPeriodSeconds - 1.0 / (ref * 86400.0)
            if (kotlin.math.abs(inv) < 1e-12) return siderealRotationPeriodSeconds
            val v = 1.0 / inv
            return if (v.isFinite() && v != 0.0) v else siderealRotationPeriodSeconds
        }

    /** 太阳日长度（秒，取正）。本地时间、日出日落、本地历法都用它。 */
    val solarDaySeconds: Double
        get() {
            val v = kotlin.math.abs(solarDaySignedSeconds)
            // 同步锁定：太阳日发散，返回 +∞ 而不是伪装成一个数字
            if (v.isInfinite()) return Double.POSITIVE_INFINITY
            return if (v.isFinite() && v >= 1.0) v else 86400.0
        }

    /** 本地年长（地球日） */
    val localYearEarthDays: Double
        get() = solarDayReferenceDays

    /**
     * 自转方向：恒星日为负、或赤道相对轨道面倾角大于 90°，都表示逆行自转。
     * 金星（−5832.5 h）与天王星（97.8°）因此都走这一条，不需要按 id 判断。
     */
    val retrogradeSpin: Boolean
        get() = siderealDayHours < 0.0 || axialTiltDeg > 90.0

    /** 有效轴倾角（0..90）：大于 90° 的倾角等价于「逆行 + 180° − 倾角」。 */
    val obliquityDeg: Double
        get() = if (axialTiltDeg > 90.0) 180.0 - axialTiltDeg else axialTiltDeg

    /**
     * 由**恒星日**和公转周期推出的太阳日（小时，逆行为负）：
     *   1/T_solar = 1/T_sidereal − 1/T_orbit
     * 金星：1/(−5832.5) − 1/(224.7×24) = −1/2802 h → 116.75 地球日，一年只看到约 1.93 次日出。
     */
    val derivedSolarDayHours: Double
        get() = solarDaySignedSeconds / 3600.0

    /** 推导太阳日时使用的公转周期（卫星取绕日周期）。 */
    val solarDayReferenceDays: Double
        get() = when {
            heliocentricPeriodDays > 0.0 -> heliocentricPeriodDays
            isSatellite -> 0.0 // 缺少母行星绕恒星周期时不允许误用卫星绕母行星周期
            else -> orbitalPeriodDays
        }

    /** 一个本地年里有几次日出（= 一个本地年包含多少个太阳日）。 */
    val sunrisesPerYear: Double
        get() {
            if (isStation) return 0.0
            val dayDays = solarDaySeconds / 86400.0
            if (dayDays <= 0.0 || !dayDays.isFinite() || solarDayReferenceDays <= 0.0) return 0.0
            return solarDayReferenceDays / dayDays
        }

    /**
     * 是否存在足以形成全球天光的连续大气柱。
     * 仅有局部羽流/外逸层（如木卫一的 SO₂、土卫二的水汽羽流）不能当成全球天空散射层。
     * 先要求有非零的全球表面气压，再检查可见光光学厚度；这也避免把季节性极稀薄外逸层
     * （例如当前目录中的阋神星示意值）渲染成完整暮光色带。
     */
    val hasAtmosphere: Boolean
        get() = pressureAtm >= 1e-8 && (rayTau > 0.0005 || aerosolTau > 0.005)

    /** 气态/冰巨行星以约 1 bar 云顶参考层观察，不存在可站立的固体地面。 */
    val isGasGiantCloudTop: Boolean
        get() = id in setOf("jupiter", "saturn", "uranus", "neptune")

    /** 显示给用户的观察基准。 */
    val observationLevelZh: String
        get() = when {
            isStation -> "空间站观察点（虚构）"
            isGasGiantCloudTop -> "云顶参考层（约 1 bar 示意，不是固体地面）"
            else -> "固体表面 / 近表面（视天体而定）"
        }

    /**
     * 界面上「大气是测出来的还是推出来的」的唯一措辞（v0.8.3）。
     *
     * 只有 [DataConfidence.OBSERVED] 才能说「有观测证据」；其余一律显式写明是推断，
     * 并且没有大气实测。这一段不能省：系外行星的天空是模型算出来的，
     * 不给标识就会让一个假设出来的大气看起来像测出来的。
     */
    val atmosphereEvidenceZh: String
        get() = when (confidence) {
            DataConfidence.OBSERVED -> "大气：有观测证据"
            DataConfidence.MODELED -> "推断，无大气实测（模型假设）"
            DataConfidence.UNKNOWN -> "推断，无大气实测（按无大气处理）"
        }

    /** 是否有任何一项大气实测；false 时界面必须标为推断值。 */
    val hasMeasuredAtmosphere: Boolean
        get() = confidence == DataConfidence.OBSERVED

    /** 说明天空颜色模型的证据边界，避免把视觉近似误读为实测天空照片。 */
    val skyBasisDisplayZh: String
        get() = skyBasisZh.ifBlank {
            when (id) {
                "mercury" -> "几乎无大气：不形成蓝天；天空近黑，太阳盘按真实视直径缩放。"
                "io" -> "局部、瞬变 SO₂ 外逸层：不形成稳定全球天光，天空近黑。"
                "venus" -> "高压 CO₂ 与硫酸云：黄橙色厚雾的多次散射近似；太阳表现为低对比漫射光斑。"
                "earth" -> "氮氧大气：瑞利散射为主，叠加气溶胶与太阳高度角变化。"
                "mars" -> "稀薄 CO₂ 与矿物尘埃：黄褐色天空；日落附近加入蓝色前向散射示意。"
                "jupiter" -> "氢氦大气与云顶气溶胶：乳白至浅黄云顶色调近似；无固体地面。"
                "saturn" -> "氢氦大气与高层雾霾：淡金色云顶近似；无固体地面。"
                "uranus" -> "氢氦与甲烷大气：浅青色雾霾近似，未完整求解甲烷光谱吸收。"
                "neptune" -> "氢氦与甲烷大气：较深蓝色云顶近似，未完整求解甲烷光谱吸收。"
                "titan" -> "氮甲烷厚雾：橙棕色多次散射与低空增暖近似。"
                "moon" -> "极稀薄外逸层：不形成普通天空散射，背景近黑。"
                else -> if (confidence == DataConfidence.UNKNOWN) "大气性质未知：按保守参数进行示意，不代表已确认大气。"
                    else "根据当前大气参数进行简化散射计算；不是完整辐射传输模型。"
            }
        }

    companion object {
        const val SUN_RADIUS_KM = 696000.0
        const val AU_KM = 1.495979e8
    }
}

/** 七个典型观测纬度。 */
data class Latitude(val labelZh: String, val degrees: Float)

object Latitudes {
    val all = listOf(
        Latitude("北极点 90°N", 90f),
        Latitude("北回归线 23.5°N", 23.5f),
        Latitude("北中纬 50°N", 50f),
        Latitude("赤道 0°", 0f),
        Latitude("南中纬 50°S", -50f),
        Latitude("南回归线 23.5°S", -23.5f),
        Latitude("南极点 90°S", -90f)
    )
    fun shortLabel(deg: Float): String = when {
        deg >= 89f -> "北极点"
        deg <= -89f -> "南极点"
        deg == 0f -> "赤道"
        deg > 0f -> "北纬 ${trim(deg)}°"
        else -> "南纬 ${trim(-deg)}°"
    }
    private fun trim(v: Float): String =
        if (v == v.toInt().toFloat()) v.toInt().toString() else String.format("%.1f", v)
}

object Planets {

    private val base: List<Planet> = listOf(
        Planet(
            id = "earth", nameZh = "地球", nameEn = "Earth", kindZh = "类地行星",
            epochPhaseFraction = 0.00000, epochPhaseSource = PhaseSource.MEASURED_ANCHOR,
            radiusKm = 6371.0, siderealPeriodOverrideSeconds = 86164.0905,
            orbitalPeriodDays = 365.256, eccentricity = 0.0167, perihelionAngleDeg = 102.9, axialTiltDeg = 23.44, gravity = 9.807,
            pressureAtm = 1.0, scaleHeightKm = 8.5, compositionZh = "氮 78% · 氧 21%",
            sunDistanceAu = 1.0, rayTau = 0.10, aerosolTau = 0.03, ozoneColumn = 1.0f,
            aerosolTint = Rgb(0.92f, 0.94f, 0.98f), groundTint = Rgb(0.18f, 0.24f, 0.14f),
            nightTint = Rgb(0.015f, 0.035f, 0.075f), surfaceKind = SurfaceKind.ROCK,
            moons = listOf(Moon("月球", 0.52f, 27.32, Rgb(0.86f, 0.86f, 0.83f), 40f, 15f)),
            seasonPhase = 0.0f, gregorianCalendar = true
        ),
        Planet(
            id = "moon", nameZh = "月球", nameEn = "Moon", kindZh = "地球卫星",
            epochPhaseFraction = 0.65472, epochPhaseSource = PhaseSource.MEASURED,
            radiusKm = 1737.4, siderealPeriodOverrideSeconds = 2360592.0000,
            orbitalPeriodDays = 27.32, eccentricity = 0.0549, perihelionAngleDeg = 318.0, heliocentricPeriodDays = 365.256, axialTiltDeg = 1.54, gravity = 1.62,
            pressureAtm = 0.0, scaleHeightKm = 0.0, compositionZh = "几乎真空（外逸层）",
            sunDistanceAu = 1.0, rayTau = 0.0, aerosolTau = 0.0,
            aerosolTint = Rgb(0.7f, 0.7f, 0.7f), groundTint = Rgb(0.40f, 0.39f, 0.37f),
            nightTint = Rgb(0.0008f, 0.0009f, 0.0012f), surfaceKind = SurfaceKind.ROCK,
            dayStarAlpha = 0.55f, seasonPhase = 0.05f
        ),
        Planet(
            id = "mercury", nameZh = "水星", nameEn = "Mercury", kindZh = "类地行星",
            epochPhaseFraction = 0.38342, epochPhaseSource = PhaseSource.MEASURED,
            radiusKm = 2439.7, siderealPeriodOverrideSeconds = 5067360.0000,
            orbitalPeriodDays = 87.97, eccentricity = 0.2056, perihelionAngleDeg = 77.5, sodiumGlow = 0.15, axialTiltDeg = 0.034, gravity = 3.70,
            pressureAtm = 0.0, scaleHeightKm = 0.0, compositionZh = "几乎真空（外逸层）",
            sunDistanceAu = 0.387, rayTau = 0.0, aerosolTau = 0.0,
            aerosolTint = Rgb(0.7f, 0.68f, 0.66f), groundTint = Rgb(0.34f, 0.32f, 0.30f),
            nightTint = Rgb(0.0008f, 0.0008f, 0.0011f), surfaceKind = SurfaceKind.ROCK,
            dayStarAlpha = 0.45f, seasonPhase = 0.3f
        ),
        Planet(
            id = "venus", nameZh = "金星", nameEn = "Venus", kindZh = "类地行星",
            epochPhaseFraction = 0.62033, epochPhaseSource = PhaseSource.MEASURED,
            radiusKm = 6051.8, siderealPeriodOverrideSeconds = -20997000.0000,
            orbitalPeriodDays = 224.701, eccentricity = 0.0068, perihelionAngleDeg = 131.5, axialTiltDeg = 177.36, gravity = 8.87,
            pressureAtm = 92.0, scaleHeightKm = 15.9, compositionZh = "二氧化碳 96.5% · 硫酸云",
            sunDistanceAu = 0.723, rayTau = 0.02, aerosolTau = 5.5,
            aerosolTint = Rgb(1.0f, 0.88f, 0.58f), groundTint = Rgb(0.52f, 0.40f, 0.20f),
            nightTint = Rgb(0.06f, 0.045f, 0.02f), surfaceKind = SurfaceKind.CLOUD_DECK,
            starVisibility = 0.015f, seasonPhase = 0.6f
        ),
        Planet(
            id = "mars", nameZh = "火星", nameEn = "Mars", kindZh = "类地行星",
            epochPhaseFraction = 0.09281, epochPhaseSource = PhaseSource.MEASURED_ANCHOR,
            radiusKm = 3389.5, siderealPeriodOverrideSeconds = 88642.6630,
            orbitalPeriodDays = 686.98, eccentricity = 0.0934, perihelionAngleDeg = 336.0, axialTiltDeg = 25.19, gravity = 3.721,
            pressureAtm = 0.0064, scaleHeightKm = 11.1, compositionZh = "二氧化碳 95% · 尘",
            sunDistanceAu = 1.524, rayTau = 0.008, aerosolTau = 0.42, ozoneColumn = 0.0f,
            aerosolTint = Rgb(0.95f, 0.63f, 0.40f), aerosolAsymmetry = 0.75f, groundTint = Rgb(0.60f, 0.31f, 0.18f),
            nightTint = Rgb(0.035f, 0.020f, 0.014f), surfaceKind = SurfaceKind.DUST,
            moons = listOf(
                Moon("火卫一", 0.10f, 0.319, Rgb(0.45f, 0.42f, 0.38f), 20f, 8f),
                Moon("火卫二", 0.05f, 1.263, Rgb(0.42f, 0.40f, 0.36f), 220f, -12f)
            ),
            seasonPhase = 0.15f
        ),
        Planet(
            id = "jupiter", nameZh = "木星", nameEn = "Jupiter", kindZh = "气态巨行星",
            epochPhaseFraction = 0.98039, epochPhaseSource = PhaseSource.CONVENTION,
            radiusKm = 69911.0, siderealPeriodOverrideSeconds = 35730.0000,
            orbitalPeriodDays = 4332.59, eccentricity = 0.0489, perihelionAngleDeg = 14.8, axialTiltDeg = 3.13, gravity = 24.79,
            pressureAtm = 1.0, scaleHeightKm = 27.0, compositionZh = "氢 90% · 氦 10%",
            sunDistanceAu = 5.204, rayTau = 0.03, aerosolTau = 0.55,
            aerosolTint = Rgb(0.93f, 0.88f, 0.80f), groundTint = Rgb(0.72f, 0.63f, 0.50f),
            nightTint = Rgb(0.02f, 0.025f, 0.04f), methaneColumn = 0.05, surfaceKind = SurfaceKind.CLOUD_DECK,
            moons = listOf(
                Moon("木卫一", 0.10f, 1.769, Rgb(0.92f, 0.82f, 0.42f), 10f, 6f),
                Moon("木卫二", 0.09f, 3.551, Rgb(0.88f, 0.86f, 0.80f), 95f, -4f),
                Moon("木卫三", 0.11f, 7.155, Rgb(0.72f, 0.70f, 0.66f), 175f, 9f),
                Moon("木卫四", 0.10f, 16.689, Rgb(0.55f, 0.52f, 0.48f), 260f, -9f)
            ),
            seasonPhase = 0.42f
        ),
        Planet(
            id = "saturn", nameZh = "土星", nameEn = "Saturn", kindZh = "气态巨行星",
            epochPhaseFraction = 0.46374, epochPhaseSource = PhaseSource.CONVENTION,
            radiusKm = 58232.0, siderealPeriodOverrideSeconds = 38361.6000,
            orbitalPeriodDays = 10759.22, eccentricity = 0.0565, perihelionAngleDeg = 92.4, axialTiltDeg = 26.73, gravity = 10.44,
            pressureAtm = 1.0, scaleHeightKm = 59.5, compositionZh = "氢 96% · 氦 3%",
            sunDistanceAu = 9.583, rayTau = 0.025, aerosolTau = 0.5,
            aerosolTint = Rgb(0.95f, 0.88f, 0.72f), groundTint = Rgb(0.80f, 0.70f, 0.52f),
            nightTint = Rgb(0.02f, 0.02f, 0.03f), methaneColumn = 0.05, surfaceKind = SurfaceKind.CLOUD_DECK,
            moons = listOf(
                Moon("土卫六", 0.11f, 15.945, Rgb(0.85f, 0.60f, 0.28f), 30f, 12f),
                Moon("土卫五", 0.05f, 4.518, Rgb(0.82f, 0.80f, 0.76f), 200f, -10f)
            ),
            ring = Ring(66900.0, 140220.0, Rgb(0.86f, 0.79f, 0.64f), 0.85f),
            seasonPhase = 0.75f
        ),
        Planet(
            id = "uranus", nameZh = "天王星", nameEn = "Uranus", kindZh = "冰巨星",
            epochPhaseFraction = 0.04988, epochPhaseSource = PhaseSource.CONVENTION,
            radiusKm = 25559.0, siderealPeriodOverrideSeconds = -62064.0000,
            orbitalPeriodDays = 30688.5, eccentricity = 0.0457, perihelionAngleDeg = 170.9, axialTiltDeg = 97.77, gravity = 8.69,
            pressureAtm = 1.0, scaleHeightKm = 27.7, compositionZh = "氢 · 氦 · 甲烷",
            sunDistanceAu = 19.19, rayTau = 0.045, aerosolTau = 0.09,
            aerosolTint = Rgb(0.58f, 0.94f, 0.97f), groundTint = Rgb(0.44f, 0.79f, 0.87f),
            nightTint = Rgb(0.01f, 0.03f, 0.04f), methaneColumn = 0.60, surfaceKind = SurfaceKind.CLOUD_DECK,
            moons = listOf(Moon("天卫三", 0.06f, 8.706, Rgb(0.72f, 0.74f, 0.76f), 60f, 20f)),
            ring = Ring(41837.0, 51149.0, Rgb(0.62f, 0.72f, 0.78f), 0.18f),
            seasonPhase = 0.9f
        ),
        Planet(
            id = "neptune", nameZh = "海王星", nameEn = "Neptune", kindZh = "冰巨星",
            epochPhaseFraction = 0.40201, epochPhaseSource = PhaseSource.CONVENTION,
            radiusKm = 24764.0, siderealPeriodOverrideSeconds = 57996.0000,
            orbitalPeriodDays = 60195.0, eccentricity = 0.0113, perihelionAngleDeg = 44.9, axialTiltDeg = 28.32, gravity = 11.15,
            pressureAtm = 1.0, scaleHeightKm = 19.7, compositionZh = "氢 · 氦 · 甲烷",
            sunDistanceAu = 30.07, rayTau = 0.05, aerosolTau = 0.10,
            aerosolTint = Rgb(0.42f, 0.70f, 0.98f), groundTint = Rgb(0.28f, 0.50f, 0.85f),
            nightTint = Rgb(0.01f, 0.02f, 0.05f), methaneColumn = 0.80, surfaceKind = SurfaceKind.CLOUD_DECK,
            moons = listOf(Moon("海卫一", 0.07f, 5.877, Rgb(0.80f, 0.82f, 0.84f), 120f, -18f)),
            seasonPhase = 0.35f
        ),
        Planet(
            id = "titan", nameZh = "土卫六", nameEn = "Titan", kindZh = "土星卫星",
            epochPhaseFraction = 0.01069, epochPhaseSource = PhaseSource.MEASURED,
            radiusKm = 2574.7, siderealPeriodOverrideSeconds = 1377648.0000,
            orbitalPeriodDays = 15.945, heliocentricPeriodDays = 10759.22, axialTiltDeg = 0.3, gravity = 1.352,
            pressureAtm = 1.45, scaleHeightKm = 40.0, compositionZh = "氮 95% · 甲烷 5%（橙色雾霾）",
            sunDistanceAu = 9.583, rayTau = 0.02, aerosolTau = 3.2,
            aerosolTint = Rgb(0.95f, 0.60f, 0.28f), groundTint = Rgb(0.34f, 0.27f, 0.17f),
            nightTint = Rgb(0.05f, 0.03f, 0.015f), methaneColumn = 0.5, surfaceKind = SurfaceKind.CLOUD_DECK,
            starVisibility = 0.15f, seasonPhase = 0.2f
        ),
        Planet(
            id = "io", nameZh = "木卫一", nameEn = "Io", kindZh = "木星卫星",
            epochPhaseFraction = 0.11844, epochPhaseSource = PhaseSource.MEASURED,
            radiusKm = 1821.6, siderealPeriodOverrideSeconds = 152856.0000,
            orbitalPeriodDays = 1.769, heliocentricPeriodDays = 4332.59, axialTiltDeg = 0.1, gravity = 1.796,
            pressureAtm = 0.0, scaleHeightKm = 0.0, compositionZh = "稀薄二氧化硫",
            sunDistanceAu = 5.204, rayTau = 0.001, aerosolTau = 0.03,
            aerosolTint = Rgb(0.95f, 0.82f, 0.40f), groundTint = Rgb(0.86f, 0.79f, 0.36f),
            nightTint = Rgb(0.02f, 0.02f, 0.01f), surfaceKind = SurfaceKind.ROCK,
            starVisibility = 0.6f, seasonPhase = 0.5f,
            skyBasisZh = "局部、瞬变 SO₂ 外逸层：不形成稳定全球天光，天空近黑"
        ),
        Planet(
            id = "europa", nameZh = "木卫二", nameEn = "Europa", kindZh = "木星卫星",
            epochPhaseFraction = 0.49048, epochPhaseSource = PhaseSource.MEASURED,
            radiusKm = 1560.8, siderealPeriodOverrideSeconds = 306828.0000,
            orbitalPeriodDays = 3.551, heliocentricPeriodDays = 4332.59, axialTiltDeg = 0.1, gravity = 1.315,
            pressureAtm = 0.0, scaleHeightKm = 0.0, compositionZh = "极稀薄氧气",
            sunDistanceAu = 5.204, rayTau = 0.0, aerosolTau = 0.0,
            aerosolTint = Rgb(0.9f, 0.9f, 0.92f), groundTint = Rgb(0.80f, 0.78f, 0.72f),
            nightTint = Rgb(0.006f, 0.007f, 0.010f), surfaceKind = SurfaceKind.ICE,
            seasonPhase = 0.1f
        ),
        Planet(
            id = "ganymede", nameZh = "木卫三", nameEn = "Ganymede", kindZh = "木星卫星",
            epochPhaseFraction = 0.92789, epochPhaseSource = PhaseSource.MEASURED,
            radiusKm = 2634.1, siderealPeriodOverrideSeconds = 618192.0000,
            orbitalPeriodDays = 7.155, heliocentricPeriodDays = 4332.59, axialTiltDeg = 0.33, gravity = 1.428,
            pressureAtm = 0.0, scaleHeightKm = 0.0, compositionZh = "稀薄氧气",
            sunDistanceAu = 5.204, rayTau = 0.0, aerosolTau = 0.0,
            aerosolTint = Rgb(0.85f, 0.85f, 0.85f), groundTint = Rgb(0.54f, 0.51f, 0.47f),
            nightTint = Rgb(0.006f, 0.006f, 0.009f), surfaceKind = SurfaceKind.ICE,
            seasonPhase = 0.25f
        ),
        Planet(
            id = "callisto", nameZh = "木卫四", nameEn = "Callisto", kindZh = "木星卫星",
            epochPhaseFraction = 0.64823, epochPhaseSource = PhaseSource.MEASURED,
            radiusKm = 2410.3, siderealPeriodOverrideSeconds = 1441944.0000,
            orbitalPeriodDays = 16.689, heliocentricPeriodDays = 4332.59, axialTiltDeg = 0.19, gravity = 1.235,
            pressureAtm = 0.0, scaleHeightKm = 0.0, compositionZh = "极稀薄大气",
            sunDistanceAu = 5.204, rayTau = 0.0, aerosolTau = 0.0,
            aerosolTint = Rgb(0.8f, 0.78f, 0.75f), groundTint = Rgb(0.34f, 0.31f, 0.28f),
            nightTint = Rgb(0.005f, 0.005f, 0.008f), surfaceKind = SurfaceKind.ROCK,
            seasonPhase = 0.45f
        ),
        Planet(
            id = "pluto", nameZh = "冥王星", nameEn = "Pluto", kindZh = "矮行星",
            epochPhaseFraction = 0.46859, epochPhaseSource = PhaseSource.MEASURED,
            radiusKm = 1188.3, siderealPeriodOverrideSeconds = 551844.0000,
            orbitalPeriodDays = 90560.0, axialTiltDeg = 122.53, gravity = 0.62,
            pressureAtm = 0.00001, scaleHeightKm = 50.0, compositionZh = "氮 · 甲烷（极稀薄）",
            sunDistanceAu = 39.48, rayTau = 0.001, aerosolTau = 0.02,
            aerosolTint = Rgb(0.85f, 0.82f, 0.78f), groundTint = Rgb(0.62f, 0.58f, 0.54f),
            nightTint = Rgb(0.004f, 0.004f, 0.006f), surfaceKind = SurfaceKind.ICE,
            moons = listOf(Moon("冥卫一", 0.75f, 6.387, Rgb(0.62f, 0.60f, 0.58f), 0f, 5f)),
            seasonPhase = 0.55f
        ),
        Planet(
            id = "triton", nameZh = "海卫一", nameEn = "Triton", kindZh = "海王星卫星",
            epochPhaseFraction = 0.61991, epochPhaseSource = PhaseSource.MEASURED,
            radiusKm = 1353.4, siderealPeriodOverrideSeconds = -507780.0000,
            orbitalPeriodDays = 5.877, heliocentricPeriodDays = 60195.0, axialTiltDeg = 0.0, gravity = 0.779,
            pressureAtm = 0.00001, scaleHeightKm = 20.0, compositionZh = "稀薄氮气",
            sunDistanceAu = 30.07, rayTau = 0.001, aerosolTau = 0.01,
            aerosolTint = Rgb(0.86f, 0.88f, 0.92f), groundTint = Rgb(0.78f, 0.80f, 0.84f),
            nightTint = Rgb(0.004f, 0.005f, 0.008f), surfaceKind = SurfaceKind.ICE,
            seasonPhase = 0.65f
        )
    )

    /**
     * 完整天体库：原有 16 个 + 外太阳系 10 个 + 系外行星 16 + 25 + 8（v0.8.3）。
     *
     * 末尾过一次 [CatalogCompletion]：把太阳系天体「未填」的半径／质量／平衡温度按目录内
     * 物理量补出来。补全只碰这三个显示字段，已有数值逐位不变（v0.8.2 的 230 项断言全部保留）。
     */
    val all: List<Planet> = CatalogCompletion.completeAll(
        base + OuterBodies.all + ExoPlanets.all + ExoPlanets2.all + ExoPlanets3.all
    )

    fun byId(id: String): Planet = all.firstOrNull { it.id == id } ?: all[0]

    fun byGroup(g: PlanetGroup): List<Planet> = all.filter { it.group == g }

    fun byHost(starId: String): List<Planet> = all.filter { it.hostStarId == starId }

    val groups: List<PlanetGroup> = PlanetGroup.values().toList()
}
