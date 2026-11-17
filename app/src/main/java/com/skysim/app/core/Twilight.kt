package com.skysim.app.core

import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/**
 * 晨昏分层天空（v0.6.4 实验性）。
 *
 * 和 v0.6.3 的差别：不再用一个「整体变暖」的粗模型，而是沿视线把三条光路分别算清楚：
 *
 * 1. 瑞利散射：β_R(λ) ∝ 1/λ⁴，短波被散射得多，所以天顶蓝、日落红。
 * 2. 气溶胶（Mie）：β_M(λ) ∝ λ^-1.2，相函数用 Henyey–Greenstein，g 越大越前向。
 *    火星那种尘埃大气（g≈0.75、τ_aero 远大于 τ_ray）日落在太阳一侧偏蓝白，就是这个相函数算出来的。
 * 3. 臭氧 Chappuis 吸收带：500–700 nm 的宽带弱吸收，峰值约 600 nm。
 *    它把日落时本已偏红的光里的黄绿再吃掉一层，剩下的就是粉紫——维纳斯带的颜色来源。
 *
 * 每一层的辐亮度：
 *   σ(λ) = β_R + β_M + β_O3
 *   L(λ) = K·E·exp(-m_sun·σ)·(1-exp(-m_view·σ))·(β_R·P_R + β_M·P_M)/σ
 * 其中 m 用 Kasten–Young 大气质量。视线越高，散射点越可能仍在阳光里，
 * 因此把太阳大气质量按 exp(-h/16) 衰减（近似地球阴影高度），天顶才不会被一起染红。
 *
 * 叠加光（多次散射）单独一项，用来保留蓝调时刻与天顶的蓝：
 *   L_amb = K_amb · (β_R/β_max) · exp(-0.35·m_sun·σ)
 *
 * 维纳斯带与地影不是贴图，而是反日方向上的两个几何光学量：
 *   地影顶端高度 ≈ 0.9·d + 0.25·d^1.4（度，d = 太阳俯角），
 *   维纳斯带位于地影之上，宽度随俯角增大，强度在 d≈1.5°–9° 之间最明显。
 */
object Twilight {

    /** 界面设置：默认开启，强度 0.65。强度 0 时完全回落到 v0.6.3 的渲染路径。 */
    data class Setting(val enabled: Boolean = true, val strength: Float = 0.65f) {
        fun clamped(): Float = strength.coerceIn(0f, 1f)
        val active: Boolean get() = enabled && clamped() > 0.001f
    }

    /** 地球散射层有效高度（度）：v0.7.3 的标定值，作为其它天体的换算基准 */
    const val EARTH_SCATTER_ALT_DEG = 16.0

    const val LAMBDA_R = 610.0
    const val LAMBDA_G = 550.0
    const val LAMBDA_B = 460.0

    /** 单位臭氧柱在 600 nm 处的峰值光学厚度（地球柱量 = 1） */
    private const val OZONE_TAU_UNIT = 0.014
    private const val K_SINGLE = 1.7f
    private const val K_DIFFUSE = 1.15f
    private const val K_AMBIENT = 0.45f
    private const val SUN_EXT_DIFFUSE = 0.50f
    private const val GLOW_AMP = 1.05

    /** 臭氧 Chappuis 带相对截面（0..1）：500–700 nm 宽带，峰值 600 nm。 */
    fun ozoneRelative(nm: Double): Double {
        if (nm < 430.0 || nm > 790.0) return 0.0
        val main = exp(-((nm - 600.0) / 55.0).pow(2))
        val shoulder = 0.35 * exp(-((nm - 575.0) / 26.0).pow(2))
        return (main + shoulder).coerceIn(0.0, 1.0)
    }

    /** 瑞利相函数（归一化到 ∫P dΩ/4π = 1）。 */
    fun phaseRay(cosTheta: Double): Double = 0.75 * (1.0 + cosTheta * cosTheta)

    /** Henyey–Greenstein 相函数，g = 0 各向同性，g → 1 强前向。 */
    fun phaseMie(cosTheta: Double, g: Double): Double {
        val gg = g.coerceIn(0.0, 0.95)
        val denom = 1.0 + gg * gg - 2.0 * gg * cosTheta
        return (1.0 - gg * gg) / (4.0 * PI * denom.pow(1.5))
    }

    /**
     * 气溶胶相函数的不对称因子 g。
     *
     * 完全由数据表决定：`aerosolAsymmetry`（按天体填写的物理量）优先；
     * 没填（0）时用 [Planet.mieAsymmetry] 的表内缺省值（典型雾霾 0.6）。
     * v0.6.8 之前这里写的是 `max(0.6, ...)`，等于把 0.6 这个魔法数藏进了代码。
     */
    fun mieG(planet: Planet): Float =
        (if (planet.aerosolAsymmetry > 0f) planet.aerosolAsymmetry else planet.mieAsymmetry)
            .coerceIn(0f, 0.95f)

    /**
     * 散射层有效高度（度）：决定「太阳已经落到地平线以下时，多高的视线还在阳光里」。
     *
     * v0.7.3 里这个量是写死的 16°（地球的标定值）。v0.8.0 起按各天体自己的大气标高
     * 等比换算：地球 8.5 km → 16.0°（逐位不变），火星 11.1 km → 20.9°（尘埃扬得更高，
     * 晨昏线爬得更高、暮光更长），金星 15.9 km → 29.9°（硫酸云顶远高于云下地表）。
     * 这不是视觉调参：标高是数据表里的物理量，换算比例由地球的 16° 定标得出。
     */
    fun scatteringAltitudeDeg(planet: Planet): Double {
        val earthH = Planets.byId("earth").scaleHeightKm
        if (planet.scaleHeightKm <= 0.0 || earthH <= 0.0) return EARTH_SCATTER_ALT_DEG
        return EARTH_SCATTER_ALT_DEG * (planet.scaleHeightKm / earthH)
    }

    /**
     * 维纳斯带的地球型臭氧色偏权重。
     *
     * 不能把“有大气”或“有气溶胶”直接等同于有维纳斯带：此处要求可观的臭氧
     * Chappuis 吸收，同时按瑞利散射在总散射中的占比抑制厚尘埃/厚云大气的
     * 地球式粉紫覆盖。以地球数据归一化，地球仍为 1；无臭氧天体为 0。
     * 这只是可视化近似权重，不代表已经求解了该天体的完整反日光谱。
     */
    fun beltStrength(planet: Planet): Float {
        if (!planet.hasAtmosphere || planet.ozoneColumn <= 0.01f) return 0f
        val earth = Planets.byId("earth")
        val earthShare = earth.rayTau / (earth.rayTau + earth.aerosolTau).coerceAtLeast(1e-6)
        val rayleighShare = planet.rayTau / (planet.rayTau + planet.aerosolTau).coerceAtLeast(1e-6)
        val ozone = (planet.ozoneColumn / earth.ozoneColumn.coerceAtLeast(0.01f)).coerceIn(0f, 1f)
        return (ozone * (rayleighShare / earthShare).toFloat()).coerceIn(0f, 1f)
    }

    private val LAMBDAS = doubleArrayOf(LAMBDA_R, LAMBDA_G, LAMBDA_B)

    /** 地球（归一化基准），延迟解析避免对象初始化顺序问题。 */
    private val EARTH: Planet by lazy { Planets.byId("earth") }

    /**
     * v0.8.2：逐天体预计算量。
     *
     * 0.8.1 里 radiance 每被调用一次都要重算：普朗克光谱权重（HashMap 取值 + 装箱）、
     * 维纳斯带强度（Planets.byId 线性扫 68 条）、地影色（新建 Rgb + 又一次光谱权重），
     * 而一次天空网格重建要调用 962 次。真机 211.3 ms 里「单个采样变贵」是主项，
     * 不是「采样变多」，这些就是单个采样变贵的主因。与视线无关的量在这里算一次，
     * radiance 只留几何项。
     */
    class PlanetOptics internal constructor(
        val irr: Float,
        val scatterAlt: Double,
        val tint: FloatArray,
        val g: Double,
        val bRay: DoubleArray,
        val sigmaArr: DoubleArray,
        val scatTotalMax: Double,
        val cloudCoverEarly: Double,
        val thin: Double,
        val warmGain: DoubleArray,
        val starW: DoubleArray,
        val beltAmp: Float,
        val beltColour: Rgb,
        val shadow: Rgb
    )

    private var optCache: PlanetOptics? = null
    private var optPlanet: Planet? = null

    /** 取（并在天体不变时复用）逐天体预计算量。UI 单线程调用。 */
    fun opticsFor(planet: Planet): PlanetOptics {
        val cached = optCache
        if (cached != null && optPlanet === planet) return cached
        val tint = floatArrayOf(planet.aerosolTint.r, planet.aerosolTint.g, planet.aerosolTint.b)
        val bRay = DoubleArray(3)
        for (c in 0..2) bRay[c] = planet.rayTau * betaRay(LAMBDAS[c])
        val sigmaArr = DoubleArray(3)
        var scatTotalMax = 1e-9
        for (c in 0..2) {
            val lam = LAMBDAS[c]
            val spectralExt = 1.0 - smoothstep(1.2f, 3.0f, planet.aerosolTau.toFloat())
            val baExt = planet.aerosolTau * betaAer(lam) * (1.0 + (tint[c] - 1.0) * spectralExt)
            val bo = planet.ozoneColumn * OZONE_TAU_UNIT * ozoneRelative(lam)
            sigmaArr[c] = (bRay[c] + baExt + bo).coerceAtLeast(1e-6)
            scatTotalMax = max(scatTotalMax, bRay[c] + planet.aerosolTau * betaAer(lam) * tint[c])
        }
        val cloudCoverEarly = (1.0 - exp(-planet.aerosolTau * 0.55)).coerceIn(0.0, 1.0)
        val thinV = (1.0 - 0.85 * cloudCoverEarly).coerceIn(0.05, 1.0)
        val warm = planet.visualWarmBias.coerceIn(-0.5f, 0.5f)
        val warmGain = if (warm == 0f) doubleArrayOf(1.0, 1.0, 1.0)
            else doubleArrayOf(1.0 + warm, 1.0 + warm * 0.35, 1.0 - warm * 0.6)
        return PlanetOptics(
            irr = SkyModel.irradianceScale(planet),
            scatterAlt = scatteringAltitudeDeg(planet),
            tint = tint,
            g = mieG(planet).toDouble(),
            bRay = bRay,
            sigmaArr = sigmaArr,
            scatTotalMax = scatTotalMax,
            cloudCoverEarly = cloudCoverEarly,
            thin = thinV,
            warmGain = warmGain,
            starW = StarSpectrum.rgbWeight(planet.starTempK.toDouble()),
            beltAmp = beltStrength(planet),
            beltColour = beltColour(planet),
            shadow = shadowColour(planet)
        ).also { optCache = it; optPlanet = planet }
    }

    /** 复用的输出缓冲（UI 单线程 + 非重入，避免每次采样新建 5 个数组）。 */
    private val OUT = FloatArray(3)
    private val BEAM = DoubleArray(3)

    fun betaRay(nm: Double): Double = (550.0 / nm).pow(4.0)
    fun betaAer(nm: Double): Double = (550.0 / nm).pow(1.2)

    /** 地影顶端高度（度）。太阳俯角 d 越大，地影升得越高。 */
    fun shadowTopDeg(depressionDeg: Float): Float {
        val d = depressionDeg.coerceIn(0f, 14f)
        return 0.9f * d + 0.25f * d.pow(1.4f)
    }

    /** 维纳斯带上边界（度）。 */
    fun beltTopDeg(depressionDeg: Float): Float {
        val d = depressionDeg.coerceIn(0f, 14f)
        return shadowTopDeg(d) + 9f + 0.3f * d
    }

    private fun smoothstep(a: Float, b: Float, x: Float): Float {
        val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /** 反日方向权重：正对反日点（太阳方位 + 180°）= 1，朝太阳一侧 = 0。 */
    fun antiSolarWeight(viewAzDeg: Float, sunAzDeg: Float): Float {
        val d = ((viewAzDeg - sunAzDeg - 180f) % 360f + 540f) % 360f - 180f
        return cos(d * PI.toFloat() / 180f).coerceIn(0f, 1f)
    }

    /** 地影遮挡率（0..1）：只出现在反日侧、且低于地影顶端时。 */
    fun shadowFraction(depressionDeg: Float, antiSolar: Float, viewAltDeg: Float): Float {
        if (depressionDeg <= 0.2f) return 0f
        val top = shadowTopDeg(depressionDeg)
        val edge = smoothstep(top + 0.8f, top - 0.8f, viewAltDeg)
        return edge * antiSolar
    }

    /** 维纳斯带强度（0..1）：出现在地影之上，随俯角连续变化。 */
    fun beltFraction(depressionDeg: Float, antiSolar: Float, viewAltDeg: Float): Float {
        val d = depressionDeg
        if (d <= 0.2f) return 0f
        val bottom = shadowTopDeg(d)
        val top = beltTopDeg(d)
        if (viewAltDeg < bottom || viewAltDeg > top) return 0f
        val band = 1f - kotlin.math.abs((viewAltDeg - (bottom + top) / 2f) / ((top - bottom) / 2f))
        val appearance = smoothstep(0.2f, 1.6f, d) * (1f - smoothstep(9f, 14f, d))
        return band.coerceIn(0f, 1f) * antiSolar * appearance
    }

    /**
     * 维纳斯带的显示色。由 Chappuis 吸收的三通道相对差异构造粉紫偏色，
     * 再乘入母星光谱与本地气溶胶色；不再给所有天体套用同一张紫色调色板。
     * 这是 RGB 近似，不能替代高光谱辐射传输。
     */
    fun beltColour(planet: Planet): Rgb {
        planet.beltTintOverride?.let { return it }
        val ozone = planet.ozoneColumn.coerceIn(0f, 3f)
        // v0.8.2：回到 v0.8.0 的地球标定粉紫锚点（0.7560 / 0.4289 / 0.5809）。
        // v0.8.1 用 Chappuis 三通道消光直接算颜色，但按地球标定的有效光学厚度只有
        // OZONE_TAU_UNIT × 1 × 8 ≈ 0.112，绿光只被吃掉 6%，三个通道几乎等量通过，
        // 反日带被洗成淡蓝白（0.8227 / 0.8814 / 0.9800）。
        // 维纳斯带的品红来自两个方向相反的波段效应：黄绿被臭氧吃掉（绿降），
        // 蓝光被长光路瑞利散射掉（蓝降）——只算臭氧项永远出不了粉紫。
        // 这里保留 v0.8.0 的经验形状，再按母星光谱调制；以地球归一化，地球逐位不变。
        val base = Rgb(0.52f, 0.40f, 0.46f)
            .mix(Rgb(0.76f, 0.30f, 0.50f), (ozone / 1.2f).coerceIn(0f, 1f))
            .mix(planet.aerosolTint, 0.18f)
        val star = StarSpectrum.rgbWeight(planet.starTempK)
        val earthStar = StarSpectrum.rgbWeight(EARTH.starTempK)
        return Rgb(
            (base.r * (star[0] / earthStar[0]).toFloat()).coerceIn(0f, 1f),
            (base.g * (star[1] / earthStar[1]).toFloat()).coerceIn(0f, 1f),
            (base.b * (star[2] / earthStar[2]).toFloat()).coerceIn(0f, 1f)
        )
    }

    /**
     * 地影颜色随本地恒星光谱与气溶胶色变化，不把地球的蓝灰色硬套到所有世界。
     * 基准仍是低亮度阴影；恒星越红、尘埃越暖，地影也相应偏暖。
     */
    fun shadowColour(planet: Planet): Rgb {
        val star = StarSpectrum.rgbWeight(planet.starTempK)
        return Rgb(
            (0.045f * star[0].toFloat() * planet.aerosolTint.r).coerceIn(0f, 0.12f),
            (0.065f * star[1].toFloat() * planet.aerosolTint.g).coerceIn(0f, 0.12f),
            (0.105f * star[2].toFloat() * planet.aerosolTint.b).coerceIn(0f, 0.12f)
        )
    }

    /**
     * 分层天空辐亮度（线性 RGB，未做 sRGB 编码）。
     * 与 v0.6.3 的 [SkyModel.radiance] 物理同源，但把三个波段的消光/散射分开算，并加入臭氧与地影/维纳斯带。
     */
    fun radiance(
        planet: Planet,
        viewAltDeg: Float,
        viewAzDeg: Float,
        sunAltDeg: Float,
        sunAzDeg: Float,
        strength: Float
    ): Rgb {
        if (!planet.hasAtmosphere) {
            val base = SkyModel.radiance(planet, viewAltDeg, sunAltDeg, viewAzDeg, sunAzDeg)
            // 稀薄外逸层的钠辉光（水星）：钠 D 线在 589 nm，日落后短时间内仍有一层极弱的黄光。
            // 强度来自数据表字段，不是按天体名写死。
            if (planet.sodiumGlow <= 0.0) return base
            val dist = abs(sunAltDeg).toDouble()
            val a = (planet.sodiumGlow * 0.020 * exp(-dist / 6.0)).toFloat().coerceIn(0f, 0.05f)
            return Rgb(base.r + a, base.g + a * 0.55f, base.b + a * 0.15f)
        }
        val s = strength.coerceIn(0f, 1f)
        // v0.8.2：与视线无关的量按天体摊掉（见 PlanetOptics），这里只留几何。
        val o = opticsFor(planet)
        val illum = Astro.daylightFactor(sunAltDeg)
        val irr = o.irr
        val mView = SkyModel.airMass(viewAltDeg).toDouble()
        val altFactor = exp(-max(viewAltDeg, 0f) / o.scatterAlt)
        val mSun = SkyModel.airMass(sunAltDeg).toDouble() * (0.22 + 0.78 * altFactor)

        val cosSep = cosSeparation(viewAltDeg, viewAzDeg, sunAltDeg, sunAzDeg).toDouble()
        val pRay = phaseRay(cosSep)
        val pMie = phaseMie(cosSep, o.g)
        val tint = o.tint
        val bRay = o.bRay
        val sigmaArr = o.sigmaArr
        val scatTotalMax = o.scatTotalMax
        val cloudCoverEarly = o.cloudCoverEarly
        val thin = o.thin
        // 复用的输出缓冲，不再每次采样新建 5 个数组
        val out = OUT
        for (c in 0..2) {
            val lam = LAMBDAS[c]
            val br = bRay[c]
            val ba = planet.aerosolTau * betaAer(lam) * tint[c].toDouble()
            val sigma = sigmaArr[c]
            val transSun = exp(-mSun * sigma)
            val inView = 1.0 - exp(-mView * sigma)
            val scat = br * pRay + ba * pMie
            val single = K_SINGLE * irr * transSun * inView * scat

            // 多次散射：太阳消光用更短的有效路径（光子在被散射出去之前只走了不到一整条太阳光路），
            // 这是厚大气在日落时仍然明亮的原因。
            val transDiffuse = exp(-SUN_EXT_DIFFUSE * mSun * sigma)
            val diffuse = K_DIFFUSE * irr * transDiffuse * inView * (scat / sigma) * 0.6

            // 环境光：保证蓝调时刻与天顶不会变成纯黑。权重取「该通道的总散射占比」而不是纯瑞利项，
            // 这样气溶胶主导的天体（金星、土卫六）不会在环境光里被强行刷成蓝色。
            val baForAmbient = planet.aerosolTau * betaAer(lam) * tint[c].toDouble()
            // 环境光随视线高度增强：蓝调时刻低层大气已经进入地影，天顶比地平线更亮。
            val altBoost = 0.5 + 1.3 * sin(max(viewAltDeg, 0f) * PI.toFloat() / 180f)
            // 只在低太阳（晨昏/夜间）时起作用，白天不额外抬亮天空以免压掉太阳与天空的对比。
            val twilightGate = 1.0 - smoothstep(0f, 10f, sunAltDeg).toDouble()
            val ambient = K_AMBIENT * irr * ((bRay[c] + baForAmbient) / scatTotalMax) *
                exp(-SUN_EXT_DIFFUSE * mSun * 0.6) * (0.25 + 0.75 * illum) * altBoost * twilightGate

            out[c] = ((single + diffuse) * thin + ambient).toFloat()
        }

        // 云顶/厚雾主导项（与 SkyModel.radiance 同源）：厚云天体的天空是「被照亮的云层」，
        // 不按直射 Beer–Lambert 衰减，否则金星、土卫六会掉成只剩瑞利蓝的冷色。
        val cloudCover = cloudCoverEarly
        if (cloudCover > 0.02) {
            val lift = 0.90 * irr * cloudCover * (0.55 + 0.45 * illum)
            for (c in 0..2) out[c] += (lift * tint[c]).toFloat()
        }
        // 厚云天空各方向亮度接近一致：把结果向一个各向同性的云顶亮度收拢，
        // 这样金星/土卫六不会出现「天顶亮、地平线暗」的地球式梯度。
        if (cloudCover > 0.30) {
            val w = ((cloudCover - 0.30) / 0.70).coerceIn(0.0, 1.0) * 0.72
            val uniform = 0.95 * irr * cloudCover * (0.55 + 0.45 * illum)
            for (c in 0..2) out[c] = (out[c] * (1.0 - w) + uniform * tint[c] * w).toFloat()
        }

        // 低太阳时的地平线暖光（日出日落橘红分层的直接来源）：
        // 直射光穿过整条大气之后的颜色 beam(λ) = exp(-m_sun·σ_λ) 本身就是算出来的，
        // 不是调色板。它在地平线附近最强（视线穿过最厚的低层大气），朝太阳一侧最亮。
        var beamMax = 1e-9
        val beam = BEAM
        for (c in 0..2) {
            beam[c] = exp(-mSun * sigmaArr[c])
            beamMax = max(beamMax, beam[c])
        }
        // 散射光同样是被这条已经染红的直射光照亮的：把低空部分按 beam 的颜色再乘一次，
        // 否则瑞利的蓝色底光会把地平线的橘红冲淡成灰棕。
        // 权重随视线高度衰减：天顶的散射点远在阴影线之上，看到的太阳还没那么红，
        // 所以蓝调时刻的天顶仍然是蓝的，不会被一起压成黑色。
        val lowAirWeight = exp(-max(viewAltDeg, 0f) / 12f)
        // 视觉暖色偏移（数据表字段，缺省 0）：只在低空光路上起作用，红加、蓝减
        val warmGain = o.warmGain
        for (c in 0..2) {
            val beamTint = 0.15 + 0.85 * (beam[c] / beamMax)
            val factor = 1.0 + (beamTint - 1.0) * lowAirWeight
            val visual = 1.0 + (warmGain[c] - 1.0) * lowAirWeight
            out[c] = (out[c] * factor * visual).toFloat()
        }
        val horizonWeight = exp(-max(viewAltDeg, 0f) / 8f)
        val lowSun = 1.0 - smoothstep(2f, 15f, sunAltDeg).toDouble()
        val towardSun = (0.18 + 0.82 * cosSep).coerceIn(0.0, 1.0)
        // 视觉标定倍率（数据表字段，缺省 1 = 纯物理结果）
        val glowAmp = GLOW_AMP * irr * horizonWeight * lowSun * towardSun * planet.horizonGlowScale
        for (c in 0..2) {
            out[c] += (glowAmp * (beam[c] / beamMax)).toFloat()
        }

        // 暮光时刻低层大气进入地影：地平线方向的天空比天顶暗（蓝调时刻天顶最亮）。
        val depression = (-sunAltDeg).coerceAtLeast(0f)
        val duskDim = 1.0 - 0.68 * smoothstep(0f, 8f, depression).toDouble() *
            exp(-max(viewAltDeg, 0f) / 7f)
        for (c in 0..2) out[c] = (out[c] * duskDim).toFloat()

        // 晨昏整体压暗：日落时的天空亮度远低于正午，不做这一步的话 sRGB 编码会把
        // 饱和度压平，分层看起来像一片发白的灰。压暗曲线只依赖太阳高度角，因此日出日落仍然对称。
        // twilightDimScale = 1 时与纯物理曲线完全一致；0 = 不压暗
        val rawDim = 0.46f + 0.54f * smoothstep(-1f, 14f, sunAltDeg)
        val twDim = 1f - (1f - rawDim) * planet.twilightDimScale.coerceIn(0f, 2f)
        // 光源光谱：同样的 1/λ⁴ 散射，被 M 型星的红光照射时天空就是橙红色而不是蓝色
        val starW = o.starW
        for (c in 0..2) out[c] = (out[c] * starW[c]).toFloat()
        val sky = Rgb(out[0], out[1], out[2]).scale(illum.coerceAtLeast(0.02f) * twDim)
        return SkyModel.mixLegacyTuned(planet, sky, viewAltDeg, sunAltDeg, viewAzDeg, sunAzDeg, s)
    }

    private fun cosSeparation(a1Deg: Float, az1: Float, a2Deg: Float, az2: Float): Float {
        val a1 = a1Deg * PI.toFloat() / 180f
        val a2 = a2Deg * PI.toFloat() / 180f
        val da = (az1 - az2) * PI.toFloat() / 180f
        return (sin(a1) * sin(a2) + cos(a1) * cos(a2) * cos(da)).coerceIn(-1f, 1f)
    }
}
