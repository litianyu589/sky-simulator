package com.skysim.app.core

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.tan
import kotlin.math.sin
import kotlin.math.PI

/**
 * 由大气成分（瑞利散射 + 气溶胶）与太阳高度角推导天空颜色。
 *
 * 物理模型（单次散射 + 多次散射近似）：
 *   β_c = τ_ray * w_c + τ_aero * tint_c          各通道散射系数，w_c ∝ 1/λ^4
 *   m   = 大气质量（Kasten–Young 近似）
 *   T_c = exp(-m_sun * β_c)                      阳光穿过大气的透射率（低太阳时蓝光被吃掉→红/橙）
 *   L_c = k1 * β_c * T_c * f(m_view) + k2 * (β_c/β_max) * (1 - e^{-τ·m_sun}) * f(m_view)
 * 第一项是薄大气的单次散射（天蓝），第二项是厚大气（金星、土卫六、气态巨行星）的漫射饱和项。
 * 不做逐通道写死的渐变：同一套公式在不同 τ / tint 下自然给出蓝、黄褐、橙、青等颜色。
 */
object SkyModel {

    /** 1/λ^4 的近似权重（650nm / 550nm / 450nm） */
    val RAY_W = floatArrayOf(0.52f, 1.0f, 2.2f)

    private const val K_SINGLE = 1.45f
    private const val K_DIFFUSE = 0.62f

    /** Kasten–Young 大气质量近似，alt 为视线高度角（度） */
    fun airMass(altDeg: Float): Float {
        val h = max(altDeg, -1.5f)
        val s = sin(h * PI / 180.0)
        val corr = 0.50572 * (h + 6.07995).coerceAtLeast(0.1).pow(-1.6364)
        return (1.0 / (s + corr)).toFloat().coerceIn(0.9f, 40f)
    }

    /** 辐照度压缩：外行星太阳很暗，直接线性会让天空接近全黑，因此取 0.3 次方做视觉自适应。 */
    internal fun irradianceScale(planet: Planet): Float =
        planet.sunIrradiance.toDouble().pow(0.30).toFloat()

    private fun beta(planet: Planet, c: Int): Float = when (c) {
        0 -> planet.rayTau.toFloat() * RAY_W[0] + planet.aerosolTau.toFloat() * planet.aerosolTint.r
        1 -> planet.rayTau.toFloat() * RAY_W[1] + planet.aerosolTau.toFloat() * planet.aerosolTint.g
        else -> planet.rayTau.toFloat() * RAY_W[2] + planet.aerosolTau.toFloat() * planet.aerosolTint.b
    }

    /**
     * 天空辐亮度（线性，未归一化）。
     * @param viewAltDeg 视线高度角
     * @param sunAltDeg 太阳高度角
     */
    fun radiance(
        planet: Planet,
        viewAltDeg: Float,
        sunAltDeg: Float,
        viewAzDeg: Float = 0f,
        sunAzDeg: Float = 0f,
        twilight: Twilight.Setting? = null
    ): Rgb {
        // 分层模型开启时走 Twilight；关闭或强度 0 时落回本函数（v0.6.3 的路径）
        if (twilight != null && twilight.active) {
            return Twilight.radiance(planet, viewAltDeg, viewAzDeg, sunAltDeg, sunAzDeg, twilight.clamped())
        }
        val illum = Astro.daylightFactor(sunAltDeg)
        val night = planet.nightTint.scale(1f)
        if (!planet.hasAtmosphere) {
            // 无大气：天空基本全黑，仅有极弱的夜天光
            return Rgb(night.r, night.g, night.b)
        }
        val mSun = airMass(sunAltDeg)
        val mView = airMass(viewAltDeg)
        val irr = irradianceScale(planet)
        val tauTot = (planet.rayTau + planet.aerosolTau).toFloat() * mSun
        val thick = 1f - exp(-tauTot * 0.9f)
        val bMax = max(max(beta(planet, 0), beta(planet, 1)), max(beta(planet, 2), 1e-5f))

        val cloudCoverGlobal = (1f - exp(-planet.aerosolTau.toFloat() * 0.55f)).coerceIn(0f, 1f)
        val thinPath = (1f - 0.85f * cloudCoverGlobal).coerceIn(0.05f, 1f)
        val out = FloatArray(3)
        for (c in 0..2) {
            val b = beta(planet, c)
            val trans = exp(-mSun * b)
            // 厚雾层不能把所有漫射光都按直射光的 Beer-Lambert 透射率压到近黑；
            // 这里把气溶胶的视线路径衰减减弱，保留多次散射后的漫射天空亮度。
            val viewTau = planet.rayTau.toFloat() * 0.32f + planet.aerosolTau.toFloat() * 0.055f
            val viewTrans = exp(-mView * viewTau)
            val single = K_SINGLE * b * trans * illum * viewTrans * thinPath
            val diffuse = K_DIFFUSE * (b / bMax) * thick * illum * (0.45f + 0.55f * viewTrans) * thinPath
            out[c] = (single + diffuse) * irr + when (c) {
                0 -> night.r
                1 -> night.g
                else -> night.b
            }
        }
        // 云顶/厚雾主导项：气溶胶光学厚度很大时（金星、土卫六），视线看到的是被阳光照亮的
        // 高反照率云层本身。它不能按 Beer–Lambert 直射透射率衰减，否则整片天空会掉到 0，
        // 只剩瑞利底光的蓝色——v0.6.4 里金星变蓝紫就是这样来的。
        val cloudCover = (1f - exp(-planet.aerosolTau.toFloat() * 0.55f)).coerceIn(0f, 1f)
        if (cloudCover > 0.02f) {
            val lift = 0.90f * irr * cloudCover * (0.55f + 0.45f * illum)
            out[0] += lift * planet.aerosolTint.r
            out[1] += lift * planet.aerosolTint.g
            out[2] += lift * planet.aerosolTint.b
        }
        // 厚云天空接近各向同性：向云顶亮度收拢，避免出现地球式的天顶/地平线亮度差
        if (cloudCoverGlobal > 0.30f) {
            val w = ((cloudCoverGlobal - 0.30f) / 0.70f).coerceIn(0f, 1f) * 0.72f
            val uniform = 0.95f * irr * cloudCoverGlobal * (0.55f + 0.45f * illum)
            out[0] = out[0] * (1f - w) + uniform * planet.aerosolTint.r * w
            out[1] = out[1] * (1f - w) + uniform * planet.aerosolTint.g * w
            out[2] = out[2] * (1f - w) + uniform * planet.aerosolTint.b * w
        }

        // ---- 气溶胶散射的三个等效项：全部由大气参数导出，不再按天体 id 分支 ----
        // (1) 前向散射：g > 0 的气溶胶（矿物尘埃）把阳光向太阳方向集中，
        //     低太阳高度时太阳附近会出现明显的散射亮区（火星日落的蓝色区就是它）。
        //     相函数用高斯近似，宽度随 g 变窄，只声称角尺度近似。
        val g = planet.aerosolAsymmetry
        if (g > 0.05f) {
            val sep = angularSeparationDeg(viewAltDeg, viewAzDeg, sunAltDeg, sunAzDeg)
            val width = 10f + 12f * g
            val lowSun = ((12f - sunAltDeg) / 18f).coerceIn(0f, 1f)
            val forward = exp(-((sep / width) * (sep / width))) * lowSun * g
            out[2] *= 1f + 2.2f * forward
            out[1] *= 1f + 0.25f * forward
        }
        // (2) 厚气溶胶的多次散射把单次散射的色偏压平：光学厚度越大，色彩越接近灰白。
        val thickMix = (0.28f * ((planet.aerosolTau.toFloat() - 1.0f) / 4.0f)).coerceIn(0f, 0.35f)
        if (thickMix > 0.01f) {
            val mean = (out[0] + out[1] + out[2]) / 3f
            for (c in 0..2) out[c] = out[c] * (1f - thickMix) + mean * thickMix
        }
        // (3) 厚雾 + 暖色气溶胶：低仰角视线穿过更长的雾层，地平线更暖。
        val warmth = (planet.aerosolTint.r - planet.aerosolTint.b).coerceAtLeast(0f)
        val hazeDepth = ((planet.aerosolTau.toFloat() - 1.5f) / 3.0f).coerceIn(0f, 1f)
        if (warmth > 0.02f && hazeDepth > 0.02f) {
            val horizon = (1f - sin(viewAltDeg.coerceIn(0f, 90f) * PI.toFloat() / 180f)).coerceIn(0f, 1f)
            val warm = 0.30f * horizon * hazeDepth * warmth
            out[0] *= 1f + warm
            out[2] *= 1f - warm * 0.75f
        }
        val sc = planet.skyIlluminationColor
        return Rgb(out[0] * sc.r, out[1] * sc.g, out[2] * sc.b)
    }

    /**
     * 分层结果与旧路径按强度混合，并在其上叠加维纳斯带与地影。
     * strength = 0 时严格返回旧路径结果（「对比」按钮依赖这一点）。
     */
    internal fun mixLegacyTuned(
        planet: Planet,
        layered: Rgb,
        viewAltDeg: Float,
        sunAltDeg: Float,
        viewAzDeg: Float,
        sunAzDeg: Float,
        strength: Float
    ): Rgb {
        // v0.8.2：逐天体预计算量（光谱权重、地影色、维纳斯带强度与颜色）走 Twilight 的缓存，
        // 不再每次采样都做 HashMap 取值、Planets.byId 线性扫表与 Rgb 分配。
        val optics = Twilight.opticsFor(planet)
        // 光源光谱：旧路径也要被恒星色温染色，否则 M 型星的天空仍会透出地球式的蓝
        val starW = optics.starW
        val raw = radiance(planet, viewAltDeg, sunAltDeg, viewAzDeg, sunAzDeg)
        val legacy = Rgb(
            (raw.r * starW[0]).toFloat(),
            (raw.g * starW[1]).toFloat(),
            (raw.b * starW[2]).toFloat()
        )
        if (strength <= 0.001f) return legacy
        // 强度不是线性混合：旧路径在低太阳时本身偏蓝灰，线性混合会把分层染成脏灰色。
        // 用 1-exp(-3s) 让默认 0.65 就达到 86% 的分层权重，滑到 0 时严格回到旧路径。
        val t = 1f - exp(-4f * strength.coerceIn(0f, 1f))
        var out = legacy.mix(layered, t)
        val dep = (-sunAltDeg).coerceIn(0f, 14f)
        val anti = Twilight.antiSolarWeight(viewAzDeg, sunAzDeg)
        val shadow = Twilight.shadowFraction(dep, anti, viewAltDeg) * strength
        val belt = Twilight.beltFraction(dep, anti, viewAltDeg) * strength
        if (shadow > 0.01f) {
            out = out.scale(1f - 0.72f * shadow).mix(optics.shadow, 0.55f * shadow)
        }
        // 维纳斯带的紫红来自臭氧 Chappuis 吸收：臭氧柱量越少，这条带越弱。
        // 火星（0）与金星（0）因此不再出现地球式的紫带；地球 1.0 逐位不变。
        val beltAmp = optics.beltAmp
        if (belt > 0.01f && beltAmp > 0.01f) {
            out = out.mix(optics.beltColour, 0.5f * belt * beltAmp)
        }
        return out
    }

    /** 返回经高光软压缩的线性 RGB；屏幕输出前还需调用 toSrgb。 */
    fun display(planet: Planet, viewAltDeg: Float, sunAltDeg: Float): Rgb {
        val l = radiance(planet, viewAltDeg, sunAltDeg)
        return softClip(l)
    }

    /**
     * 角直径（度）→ 屏幕半径（像素）。用 tan 精确换算，避免大角直径天体
     * （例如 KELT-9 b 的 37°）用小角近似带来的几个百分点误差。
     *
     * 注意：入参是直径，返回半径，中间没有第二个 2 倍因子。
     */
    fun discRadiusPx(
        angularDiameterDeg: Float,
        pixelsPerRadian: Float,
        minPx: Float = MIN_DISC_RADIUS_PX,
        maxPx: Float = Float.MAX_VALUE
    ): Float {
        if (angularDiameterDeg <= 0f || pixelsPerRadian <= 0f) return 0f
        val halfRad = angularDiameterDeg * PI.toFloat() / 360f
        return (tan(halfRad) * pixelsPerRadian).coerceIn(minPx, maxPx)
    }

    /**
     * 不做「最小像素」钳位：远日行星看到的太阳本来就小于 1 像素，
     * 把它放大到 1–2 像素会凭空变大 10 倍以上。亚像素圆由抗锯齿按覆盖度淡化，
     * 视觉上就是一个几乎看不见的点——这才是物理上正确的表现。
     */
    const val MIN_DISC_RADIUS_PX = 0f

    /** 线性 RGB -> sRGB 编码。只在最终写入屏幕像素前调用一次。 */
    fun toSrgb(linear: Rgb): Rgb {
        fun encode(v: Float): Float {
            val x = v.coerceAtLeast(0f)
            return if (x <= 0.0031308f) 12.92f * x
            else 1.055f * x.pow(1f / 2.4f) - 0.055f
        }
        return Rgb(encode(linear.r), encode(linear.g), encode(linear.b)).clamp01()
    }

    private fun angularSeparationDeg(altA: Float, azA: Float, altB: Float, azB: Float): Float {
        val a1 = altA * PI.toFloat() / 180f
        val a2 = altB * PI.toFloat() / 180f
        val da = (azA - azB) * PI.toFloat() / 180f
        val cosSep = (sin(a1) * sin(a2) + kotlin.math.cos(a1) * kotlin.math.cos(a2) * kotlin.math.cos(da))
            .coerceIn(-1f, 1f)
        return (kotlin.math.acos(cosSep) * 180f / PI.toFloat())
    }

    /** 天气压暗系数：云量为主，降水再补一点（0 = 晴朗）。 */
    fun weatherDim(cloudCover: Float, precip: Float): Float =
        (0.62f * cloudCover + 0.20f * precip * (0.4f + 0.6f * cloudCover)).coerceIn(0f, 0.86f)

    /**
     * 把晴天天空色按天气压暗并朝阴沉色靠拢：
     * 不是叠一层半透明遮罩，而是真的降低辐亮度、降低饱和度，所以太阳直射感与地平线颜色会一起变化。
     */
    fun applyWeather(base: Rgb, planet: Planet, cloudCover: Float, precip: Float, sunAlt: Float): Rgb {
        if (!planet.hasAtmosphere || (planet.pressureAtm <= 0.0001f && planet.aerosolTau < 0.05)) return base
        if (cloudCover <= 0.001f && precip <= 0.001f) return base
        val dim = weatherDim(cloudCover, precip)
        val day = Astro.daylightFactor(sunAlt)
        // 阴天的灰度：亮度与晴天成比例（所以一定是变暗，而不是把暗天空“提亮成灰”），色相略偏大气气溶胶色
        val lum = 0.2126f * base.r + 0.7152f * base.g + 0.0722f * base.b
        val greyLevel = lum * (1f - dim) * (0.75f + 0.25f * day)
        val overcast = Rgb(0.58f, 0.62f, 0.68f).mix(planet.aerosolTint, 0.35f)
        val grey = overcast.scale(greyLevel / 0.62f)
        return base.scale(1f - dim).mix(grey, (dim * 0.65f).coerceIn(0f, 0.8f))
    }

    fun softClip(c: Rgb): Rgb {
        fun f(v: Float): Float {
            val x = v.coerceAtLeast(0f)
            val y = if (x > 0.86f) 0.86f + (1f - 0.86f) * (1f - exp(-(x - 0.86f) * 5f)) else x
            return y.coerceIn(0f, 1f)
        }
        return Rgb(f(c.r), f(c.g), f(c.b))
    }

    /** 地面 / 云顶颜色 */
    fun surface(planet: Planet, sunAltDeg: Float): Rgb {
        val illum = Astro.daylightFactor(sunAltDeg)
        return when (planet.surfaceKind) {
            SurfaceKind.CLOUD_DECK -> {
                val sky = toSrgb(display(planet, 2f, sunAltDeg))
                sky.mix(planet.groundTint, 0.25f).scale(0.55f + 0.35f * illum).clamp01()
            }
            SurfaceKind.ICE -> planet.groundTint.scale(0.10f + 0.90f * illum).clamp01()
            else -> planet.groundTint.scale(0.03f + 0.92f * illum).clamp01()
        }
    }

    /** 直射太阳光透射率。厚云/厚雾天体会把太阳盘压成漫射光斑。 */
    fun sunTransmission(planet: Planet, sunAltDeg: Float): Float {
        if (!planet.hasAtmosphere) return 1f
        val mSun = airMass(sunAltDeg)
        val effectiveTau = planet.rayTau.toFloat() * 0.75f + planet.aerosolTau.toFloat() * 0.82f
        return exp(-mSun * effectiveTau).coerceIn(0f, 1f)
    }

    /**
     * 厚雾天体（金星、土卫六）上太阳的表现：直射光被厚雾散射成高亮弥散斑。
     *
     * 0.6.1 的改法用 `sunTransmission` 直接压暗太阳盘，金星的有效光学厚度约 4.5、
     * 透射率只剩 1e-4 量级，太阳会在天空中彻底消失；而实际探测器图像里它是明显的亮斑。
     * 所以厚雾天体的太阳亮度改由「散射后的弥散斑」给出，不再乘直射透射率。
     */
    fun hazySunSpot(planet: Planet, sunAltDeg: Float): Rgb {
        val star = planet.starColor
        val bright = Rgb(star.r, star.g, star.b).mix(Rgb(1f, 1f, 1f), 0.35f)
        val day = Astro.daylightFactor(sunAltDeg)
        return bright.scale((0.30f + 0.70f * day).coerceIn(0f, 1f)).clamp01()
    }

    /** sRGB 空间的相对亮度（Rec.709 权重），用来判断「太阳必须比天空亮」。 */
    fun luminance(c: Rgb): Float = 0.2126f * c.r + 0.7152f * c.g + 0.0722f * c.b

    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        if (edge1 <= edge0) return if (x >= edge1) 1f else 0f
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /** 按目标 sRGB 亮度缩放线性颜色（保持色相），用于给太阳盘定亮度。 */
    private fun scaleToSrgbLuminance(c: Rgb, target: Float): Rgb {
        if (luminance(toSrgb(c)) >= target) return c
        var lo = 0f
        var hi = 8f
        repeat(22) {
            val mid = (lo + hi) * 0.5f
            if (luminance(toSrgb(c.scale(mid))) < target) lo = mid else hi = mid
        }
        return c.scale((lo + hi) * 0.5f)
    }

    /**
     * 太阳本体颜色（返回线性 RGB，绘制端再做 sRGB 编码）。
     *
     * v0.6.4 的 bug：直接把直射透射率乘到盘的颜色上。金星有效光学厚度约 4.5，
     * 透射率是 1e-4 量级，盘心于是被算成一个黑点。正确做法是把两件事分开：
     *   · 色相：直射光穿大气后的相对光谱（归一化，只取颜色），再叠加低太阳的暖色；
     *   · 亮度：永远不低于当地天空地平线亮度的一个倍数，且高空接近白。
     * 这样太阳在任何高度角、任何天体上都不会变黑，也不会比背后的天空更暗。
     */
    fun sunDisc(planet: Planet, sunAltDeg: Float): Rgb {
        val star = planet.starColor
        if (!planet.hasAtmosphere) return Rgb(star.r, star.g, star.b)
        val mSun = airMass(sunAltDeg)
        val t = FloatArray(3) { c -> exp(-mSun * beta(planet, c)) }
        val mx = max(max(t[0], t[1]), max(t[2], 1e-5f))
        val chroma = Rgb(
            (t[0] / mx).coerceIn(0f, 1f) * star.r,
            (t[1] / mx).coerceIn(0f, 1f) * star.g,
            (t[2] / mx).coerceIn(0f, 1f) * star.b
        )
        // 暖度：太阳越高越接近白，越低越偏金→橙红，连续过渡，没有黑色区间。
        val warm = 1f - smoothstep(-3f, 20f, sunAltDeg)
        val warmTarget = Rgb(1.0f, 0.55f, 0.22f).mix(Rgb(1.0f, 0.88f, 0.60f), (1f - warm).coerceIn(0f, 1f))
        val tinted = chroma.mix(warmTarget, (0.55f * warm).coerceIn(0f, 0.6f))
        val skyLum = luminance(toSrgb(display(planet, 1.5f, sunAltDeg)))
        // 要求：至少是当地地平线天空亮度的 1.2 倍，且高空时接近亮白。
        val target = max(0.95f - 0.20f * warm, skyLum * 1.20f + 0.12f).coerceIn(0.55f, 0.995f)
        return scaleToSrgbLuminance(tinted, target).clamp01()
    }

    /**
     * 星光可见度 0..1。
     *
     * 有大气：由[Astro.starVisibilityFactor]的夜间权重与气溶胶浑浊度共同决定，白天为 0。
     * 无大气（月球、水星这类）：天空不散射阳光，星点白天仍然存在，只是被地面与太阳压暗，
     * 幅度由数据表 [Planet.dayStarAlpha] 给出（缺省 0 = 不例外，行为与 v0.7.3 一致）。
     */
    fun starAlpha(planet: Planet, sunAltDeg: Float): Float {
        val night = Astro.starVisibilityFactor(sunAltDeg)
        val haze = (1f - exp(-(planet.aerosolTau * 0.6).toFloat())).coerceIn(0f, 0.9f)
        val base = (night * planet.starVisibility * (1f - haze)).coerceIn(0f, 1f)
        if (planet.hasAtmosphere || planet.dayStarAlpha <= 0f) return base
        return max(base, (planet.dayStarAlpha * planet.starVisibility).coerceIn(0f, 1f))
    }

    /** 太阳光晕颜色：接近地平线时按气溶胶染色 */
    fun glowTint(planet: Planet, sunAltDeg: Float): Rgb {
        val t = sunDisc(planet, sunAltDeg)
        return t.mix(planet.aerosolTint, if (planet.hasAtmosphere) 0.35f else 0.05f).scale(1f)
    }

    /** 地平线附近的对流层雾带强度 */
    fun horizonHaze(planet: Planet): Float =
        (1f - exp(-(planet.aerosolTau * 1.5f).toFloat())).coerceIn(0f, 1f)

    fun lerpRgb(a: Rgb, b: Rgb, t: Float): Rgb = a.mix(b, t.coerceIn(0f, 1f))

    fun azimuthDelta(a: Float, b: Float): Float {
        var d = (a - b) % 360f
        if (d > 180f) d -= 360f
        if (d < -180f) d += 360f
        return abs(d)
    }
}
