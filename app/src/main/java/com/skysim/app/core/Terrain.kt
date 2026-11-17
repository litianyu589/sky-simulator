package com.skysim.app.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/** 地形类型：决定高度场的统计特征，不需要每个天体各写一套。 */
enum class ReliefKind(val labelZh: String) {
    EARTH("山脉 + 海岸线"),
    CRATERED("坑洼粗糙"),
    MARS("高原 + 峡谷"),
    SMOOTH("平缓"),
    ICY("冰原起伏"),
    NONE("不适用（无固体地面）")
}

/** 画质档位：同时决定高度场列数与光线行进步数。 */
enum class TerrainQuality(val labelZh: String, val columns: Int, val steps: Int, val skyColumns: Int) {
    // v0.8.2：skyColumns = 天空网格的方位列数（行数固定 26）。
    // 三档采样点数 26×15=390 / 26×25=650 / 26×37=962，全部覆盖 ±180° 完整方位。
    LOW("低", 96, 8, 15),
    MEDIUM("中", 180, 16, 25),
    HIGH("高", 300, 28, 37)
}

/** 单个天体的地形参数（全部为常量，不依赖屏幕尺寸，可单元测试）。 */
data class ReliefProfile(
    val kind: ReliefKind,
    val amplitudeKm: Float,
    val seaLevel: Float,
    val octaves: Int,
    val frequency: Float,
    val cratered: Boolean = false,
    val water: Boolean = false,
    val trees: Boolean = false
)

/**
 * 程序化地形与光影。
 *
 * 高度场是「多频值噪声」的和（fBm），种子由天体 id 决定，所以同一个天体每次打开都一样，
 * 换天体得到完全不同的轮廓。光影用逐列 ray-march：
 * 从当前列沿太阳方位在高度场里前进，若某点的仰角超过太阳高度角，就算被这一列挡住，
 * 按 1/k 加权累加遮挡率——越近的山脊遮挡权重越大，阴影边界随太阳移动连续滑动。
 */
object Terrain {

    private const val EARTH_SEA = 0.42f

    /** 气态巨行星与空间站没有可站立的地面，返回 null。 */
    fun profileOf(planet: Planet): ReliefProfile? {
        if (planet.isStation) return null
        if (planet.surfaceKind == SurfaceKind.CLOUD_DECK) return null
        return when (planet.id) {
            "earth" -> ReliefProfile(ReliefKind.EARTH, 8.5f, EARTH_SEA, 4, 3.2f, water = true, trees = true)
            "mars" -> ReliefProfile(ReliefKind.MARS, 11.0f, 0.05f, 3, 1.7f)
            "venus" -> ReliefProfile(ReliefKind.SMOOTH, 2.5f, 0f, 2, 1.2f)
            "titan" -> ReliefProfile(ReliefKind.SMOOTH, 2.0f, 0.34f, 2, 1.6f, water = true)
            "moon", "mercury", "callisto", "ganymede", "iapetus" ->
                ReliefProfile(ReliefKind.CRATERED, 4.5f, 0f, 3, 5.4f, cratered = true)
            "io" -> ReliefProfile(ReliefKind.CRATERED, 5.0f, 0f, 3, 4.0f, cratered = true)
            "europa", "enceladus", "triton", "pluto", "eris", "makemake", "haumea", "vesta", "ceres" ->
                ReliefProfile(ReliefKind.ICY, 3.0f, 0f, 2, 2.2f)
            else -> when (planet.surfaceKind) {
                SurfaceKind.ROCK -> ReliefProfile(ReliefKind.CRATERED, 4.0f, 0f, 3, 4.5f, cratered = true)
                SurfaceKind.ICE -> ReliefProfile(ReliefKind.ICY, 2.5f, 0f, 2, 2.0f)
                SurfaceKind.DUST -> ReliefProfile(ReliefKind.MARS, 7.0f, 0f, 3, 1.8f)
                SurfaceKind.CLOUD_DECK -> null
            }
        }
    }

    fun seaLevel(planet: Planet): Float = profileOf(planet)?.seaLevel ?: 0f

    fun seedOf(planet: Planet): Int {
        var h = 216613626
        for (ch in planet.id) {
            h = (h xor ch.code) * 16777619
        }
        return h
    }

    private fun hash1(i: Int, seed: Int): Float {
        var h = i * 374761393 + seed * 668265263
        h = (h xor (h shr 13)) * 1274126177
        h = h xor (h shr 16)
        return (h and 0x7fffffff).toFloat() / 2147483647f
    }

    /** 一维平滑值噪声（三次插值），确定性、无分配。 */
    fun valueNoise(x: Float, seed: Int): Float {
        val i = floor(x).toInt()
        val f = x - i
        val u = f * f * (3f - 2f * f)
        return hash1(i, seed) * (1f - u) + hash1(i + 1, seed) * u
    }

    /** 多频叠加：octaves 层，每层频率 ×2.07、幅度 ×0.5。 */
    fun fbm(x: Float, seed: Int, octaves: Int): Float {
        var sum = 0f
        var amp = 1f
        var norm = 0f
        var freq = 1f
        for (o in 0 until octaves) {
            sum += amp * valueNoise(x * freq, seed + o * 131)
            norm += amp
            amp *= 0.5f
            freq *= 2.07f
        }
        return if (norm > 0f) sum / norm else 0f
    }

    /** 归一化高度 0..1（不含海平面，海平面在 [ReliefProfile.seaLevel]）。 */
    fun height(planet: Planet, profile: ReliefProfile, xNorm: Float): Float {
        val seed = seedOf(planet)
        var n = fbm(xNorm * profile.frequency, seed, profile.octaves)
        if (profile.cratered) {
            // 环形山：把噪声折成「碗形」，再叠一层小尺度粗糙度
            n = 1f - abs(2f * n - 1f)
            n = 0.65f * n + 0.35f * fbm(xNorm * profile.frequency * 6.5f, seed + 977, 2)
        }
        if (profile.kind == ReliefKind.MARS) {
            // 峡谷：在高度场上开一条低频窄槽
            val canyon = 1f - kotlin.math.exp(-((xNorm - 0.62f) / 0.05f) * ((xNorm - 0.62f) / 0.05f))
            n *= (0.35f + 0.65f * canyon)
        }
        return n.coerceIn(0f, 1f)
    }

    /** 整屏高度场（列数由画质档决定）。 */
    fun heightField(planet: Planet, columns: Int): FloatArray {
        val p = profileOf(planet) ?: return FloatArray(0)
        val n = columns.coerceIn(8, 2048)
        val out = FloatArray(n)
        for (i in 0 until n) out[i] = height(planet, p, i.toFloat() / (n - 1).coerceAtLeast(1))
        return out
    }

    /** 树木/植被剪影高度（0..1，叠加在高度场上，参与投影阴影）。 */
    fun treeField(planet: Planet, columns: Int, vegetation: Float): FloatArray {
        val p = profileOf(planet) ?: return FloatArray(0)
        if (!p.trees || vegetation <= 0.01f) return FloatArray(columns.coerceIn(8, 2048))
        val n = columns.coerceIn(8, 2048)
        val seed = seedOf(planet) + 4231
        val out = FloatArray(n)
        val hf = heightField(planet, n)
        for (i in 0 until n) {
            if (hf[i] <= p.seaLevel + 0.02f) continue
            val land = (hf[i] - p.seaLevel) / (1f - p.seaLevel).coerceAtLeast(0.01f)
            val clump = fbm(i.toFloat() / (n - 1).coerceAtLeast(1) * 22f, seed, 2)
            val cover = vegetation.coerceIn(0f, 1f) * (0.35f + 0.65f * clump) * land
            out[i] = (cover * 0.55f).coerceIn(0f, 0.6f)
        }
        return out
    }

    /**
     * 逐列阴影：返回该列可见太阳光的比例 0..1。
     * @param deltaAzDeg 太阳方位相对视线方向的差（-180..180，正 = 右手侧）
     */
    fun sunVisibility(
        planet: Planet,
        heights: FloatArray,
        trees: FloatArray,
        index: Int,
        sunAltDeg: Float,
        deltaAzDeg: Float,
        stepKm: Float = 2.0f,
        steps: Int = 16
    ): Float {
        if (heights.isEmpty()) return 1f
        if (sunAltDeg <= 0.15f) return 0f
        val p = profileOf(planet) ?: return 1f
        val dir = sin(deltaAzDeg * PI.toFloat() / 180f)
        if (abs(dir) < 0.02f) return 1f
        var blocked = 0f
        var wsum = 0f
        var k = 1
        while (k <= steps) {
            val dx = k * dir * 0.7f
            val xi = index + dx.roundToInt()
            if (xi < 0 || xi >= heights.size) break
            val dist = abs(dx) * stepKm
            if (dist < 0.5f) { k++; continue }
            val hi = (heights[index] + trees.getOrElse(index) { 0f }) * p.amplitudeKm
            val hj = (heights[xi] + trees.getOrElse(xi) { 0f }) * p.amplitudeKm
            val elev = atan((hj - hi) / dist) * 180f / PI.toFloat()
            val w = 1f / k
            wsum += w
            if (elev > sunAltDeg) blocked += w
            k++
        }
        if (wsum <= 0f) return 1f
        return (1f - blocked / wsum).coerceIn(0f, 1f)
    }

    /** 水面镜面高光强度：太阳越低、越接近视线方位，光路越长越亮。 */
    fun waterGlint(sunAltDeg: Float, deltaAzDeg: Float): Float {
        if (sunAltDeg <= 0f) return 0f
        val d = abs(deltaAzDeg).coerceAtMost(180f)
        val azWeight = kotlin.math.exp(-((d / 38f) * (d / 38f)))
        val lowSun = kotlin.math.exp(-((sunAltDeg / 22f) * (sunAltDeg / 22f)))
        return (azWeight * (0.35f + 0.65f * lowSun)).coerceIn(0f, 1f)
    }

    /** 直射光与天空环境光的权重（朗伯余弦 + 天空光底噪）。 */
    fun lightMix(visibility: Float, sunAltDeg: Float, shadowStrength: Float): Float {
        val direct = (visibility * sin(((sunAltDeg.coerceIn(0f, 90f)) * PI.toFloat() / 180f))).coerceIn(0f, 1f)
        val s = shadowStrength.coerceIn(0f, 1f)
        return (1f - s) + s * direct
    }
}
