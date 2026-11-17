package com.skysim.app.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * v0.6.5：以观察者为中心的真 3D 地面网格。
 *
 * v0.6.4 的「地面」是屏幕坐标上的矩形色块，不随视角变化，近处/远处也没有透视，
 * 看起来像一张贴图。这里改成极坐标网格：多圈半径 × 多方位，每个格点带高度，
 * 按相机的 yaw / pitch 做透视投影，逐格画成梯形条带。相机左右转、上下俯仰时，
 * 地面与天空一样会平移、起伏，遮挡关系由真实投影决定。
 *
 * 纯函数、无 Android 依赖，可以直接单元测试。
 */
object Ground3D {

    /** 网格规模：圈数 / 方位数 / 可种树的近端圈数。 */
    data class Preset(val rings: Int, val sectors: Int, val treeRings: Int)

    // v0.6.8：圈数整体加密。远处一圈都不能跨好几个数量级（18 m 与 5 km 相邻时，
    // 任何「逐格一个颜色」的做法都会在格边留下一条硬线），所以多铺几圈让它渐变。
    val LOW = Preset(12, 48, 3)
    val MEDIUM = Preset(18, 72, 4)
    val HIGH = Preset(24, 96, 5)

    fun preset(q: TerrainQuality): Preset = when (q) {
        TerrainQuality.LOW -> LOW
        TerrainQuality.MEDIUM -> MEDIUM
        TerrainQuality.HIGH -> HIGH
    }

    /** 有固体表面的天体：类地行星、卫星、矮行星、小行星。空间站与气态/冰巨星云顶不算。 */
    fun isSolidSurface(planet: Planet): Boolean =
        !planet.isStation && planet.surfaceKind != SurfaceKind.CLOUD_DECK

    /** 伪地面：气态巨行星与冰巨星，用半透明云带模仿云顶，不画固体地面。 */
    fun isPseudoGround(planet: Planet): Boolean =
        !planet.isStation && planet.surfaceKind == SurfaceKind.CLOUD_DECK

    /** 观察者眼高（km）：1.7 m。 */
    const val EYE_HEIGHT_KM = 0.0017f

    /**
     * 可见地面距离（km）。地面按**平面**处理，不引入球面曲率。
     *
     * 原因（也是为什么不做「穹顶/大锅盖」）：1.7 m 眼高下球面下沉量 d²/(2R) 在
     * 100 m 处只有 0.8 mm、1 km 处 7.8 cm、5 km 处约 2 m —— 前两者远小于一个像素，
     * 最后一项也小于地形本身的起伏。人眼对「大地是平的」的直觉正是来自曲率相对
     * 观察尺度可以忽略；真实曲率要几十公里外（看远处船帆先于船身）才略有体现，
     * 那已经超出本模拟器的地面范围。所以底面取 z = 0 的平面，远端交给空气透视淡出。
     */
    const val VIEW_RANGE_KM = 5f

    /**
     * v0.6.8：几何一直铺到几百公里。
     *
     * 5 km 只是「颜色真正收敛到天空」的距离，不能当作可见边界——盘外直接露天空正是
     * v0.6.7 里那道「环形围墙」的墙顶。平面方案下 300 km 与 5 km 的投影差不到 0.01 px，
     * 所以多铺两圈几乎不花代价，却保证视线方向上地面永远消失在地平线里、没有盘缘。
     */
    const val VIEW_FAR_KM = 300f

    /** 眼高基准：可见距离按 √(眼高/基准) 缩放（与真实地平线同样的 √h 规律），但底面仍是平面。 */
    fun viewRangeKm(planet: Planet, eyeHeightKm: Float = EYE_HEIGHT_KM): Float {
        val heightFactor = sqrt(eyeHeightKm / EYE_HEIGHT_KM).coerceIn(0.5f, 2f)
        // 大气消光越强，远处越先被霾吃掉：可见地面距离相应缩短（金星、土卫六明显短于地球）
        val hazeFactor = (1.0f - 0.06f * planet.aerosolTau.toFloat()).coerceIn(0.45f, 1.0f)
        return VIEW_RANGE_KM * heightFactor * hazeFactor
    }

    /** 平面方案下的可见距离：由观察高度与大气消光决定，与天体半径（曲率）无关。 */
    fun horizonDistanceKm(planet: Planet): Float = viewRangeKm(planet)

    /** 近处额外补的几圈：只在俯视看脚下时出现在画面里，避免画面正中漏洞。 */
    const val CLOSE_RINGS = 3

    /**
     * 第 r 圈的世界距离（km）。
     *
     * v0.6.7 关键改动：按 **1/d 等距** 布置半径。平面地面上某格点的屏幕纵坐标
     * `y = f · 眼高 / d`，所以 1/d 均匀 ⇒ 相邻两圈在地平线附近的投影间距近似相等，
     * 不会再出现「一个色块占掉屏幕 1/5」（原来前几圈是从 5 cm 线性铺开的）。
     *
     * 近端从「画面下沿对应的距离」开始：`near = f·眼高 / 画布高`，由相机画布决定，
     * 这样平视时最后一圈正好落在下沿，既不浪费圈数也不会出现一整块糊住下屏。
     * 前面 CLOSE_RINGS 圈（0.03 m 起）专门负责俯视时的脚下。
     */
    fun ringDistance(planet: Planet, r: Int, rings: Int, nearKm: Float = DEFAULT_NEAR_KM): Float {
        val far = horizonDistanceKm(planet)
        val close = min(CLOSE_RINGS, max(0, rings - 3))
        if (r < close) {
            // 0.03 m / 0.25·near / 0.6·near：都在平视画面之外，只为俯视准备
            return when (r) {
                0 -> 0.00003f
                1 -> nearKm * 0.25f
                else -> nearKm * 0.60f
            }
        }
        // 最后一圈一直铺到几百公里：地面几何没有尽头，只在地平线里淡出
        if (r == rings - 1) return VIEW_FAR_KM
        val k = rings - 2 - close
        if (k <= 0) return nearKm
        val t = (r - close).toFloat() / k.toFloat()
        val invNear = 1f / nearKm
        val invFar = 1f / far
        return 1f / (invNear + (invFar - invNear) * t)
    }

    /** 16:9 横屏、1.7 m 眼高时画面下沿对应的地面距离（km），约 3.6 m。 */
    const val DEFAULT_NEAR_KM = 0.0036f

    fun ringDistances(planet: Planet, rings: Int, nearKm: Float = DEFAULT_NEAR_KM): FloatArray =
        FloatArray(rings) { ringDistance(planet, it, rings, nearKm) }

    /** 相机决定近端：画面下沿恰好落在第一圈上。 */
    fun nearDistanceKm(cam: Cam): Float =
        (cam.focal * EYE_HEIGHT_KM / max(1f, cam.h)).coerceIn(0.0008f, 0.02f)

    /** 观察者高度（km）：站在该点地面之上一个眼高。地面高时相机跟着抬高，绝不埋进土里。 */
    fun viewerHeightKm(localGroundKm: Float = 0f): Float = localGroundKm + EYE_HEIGHT_KM

    /** 方位方向 + 径向 3 抽头平滑，供坡度/法线使用：相邻格点明暗不再跳变。 */
    fun smoothedMesh(mesh: Array<FloatArray>, rings: Int, sectors: Int): Array<FloatArray> {
        val out = Array(rings) { FloatArray(sectors) }
        for (r in 0 until rings) {
            for (s in 0 until sectors) {
                val a = mesh[r][(s - 1 + sectors) % sectors]
                val b = mesh[r][s]
                val c = mesh[r][(s + 1) % sectors]
                val n = if (r > 0) mesh[r - 1][s] else b
                val f = if (r < rings - 1) mesh[r + 1][s] else b
                out[r][s] = (2f * b + a + c + n + f) / 6f
            }
        }
        return out
    }

    /**
     * 二维高度场（归一化 0..1）。方位方向用 cos/sin 采样一维 fBm，保证绕一圈连续无接缝；
     * 径向再叠加一个低频起伏，形成「近处山谷、远处山脊」的层次。
     */
    // 高度场只与（天体, 网格规模）有关，逐帧重算是纯浪费；缓存一份即可。
    private val meshCache = java.util.HashMap<String, Array<FloatArray>>()

    fun heightField(planet: Planet, preset: Preset): Array<FloatArray> {
        val key = planet.id + "/" + preset.rings + "x" + preset.sectors
        synchronized(meshCache) {
            meshCache[key]?.let { return it }
        }
        val built = buildHeightField(planet, preset)
        synchronized(meshCache) { meshCache[key] = built }
        return built
    }

    private fun buildHeightField(planet: Planet, preset: Preset): Array<FloatArray> {
        val profile = Terrain.profileOf(planet)
        val seed = Terrain.seedOf(planet)
        val out = Array(preset.rings) { FloatArray(preset.sectors) }
        val dist = ringDistances(planet, preset.rings)
        val logSpan = ln(1f + VIEW_FAR_KM / 0.05f)
        for (r in 0 until preset.rings) {
            // v0.6.8 的根本修复：噪声坐标用**世界距离的对数**，而不是「第几圈 / 总圈数」。
            // 用圈号当坐标时，相邻两圈各自落在噪声的不同位置，圈与圈之间天然就有一道接缝，
            // 只能靠平滑去抹（v0.6.7 抹不干净，真机上就是那些「硬边色块」）。
            // 换成世界坐标后，高度场是连续的：同一个地点永远同一个高度，
            // 换画质档、换圈数都不会让它变形。
            val t = ln(1f + dist[r] / 0.05f) / logSpan
            for (s in 0 until preset.sectors) {
                val th = s.toFloat() / preset.sectors * 2f * PI.toFloat()
                val ax = cos(th) * (0.6f + t * 5.5f)
                val ay = sin(th) * (0.6f + t * 5.5f)
                val base = Terrain.fbm(ax * 1.7f + ay * 0.9f + t * 4.3f, seed, profile?.octaves ?: 3)
                val ridge = Terrain.fbm(ax * 0.6f - ay * 1.3f + t * 1.7f, seed xor 0x5bd1e995, 2)
                val h = (0.62f * base + 0.38f * ridge)
                out[r][s] = (0.12f + 0.88f * h).coerceIn(0f, 1f)
            }
        }
        return out
    }


    /**
     * 世界坐标 → 屏幕。返回 [x, y, 相机前方距离]；在相机后方返回 null。
     * east/north 是相对观察者的水平位移（km），up 是格点高度相对眼高的差（km）。
     */
    fun project(
        east: Float,
        north: Float,
        up: Float,
        lookAzDeg: Float,
        lookAltDeg: Float,
        w: Float,
        h: Float,
        focal: Float
    ): FloatArray? {
        val az = lookAzDeg * PI.toFloat() / 180f
        val pitch = lookAltDeg * PI.toFloat() / 180f
        val rx = cos(az)
        val ry = -sin(az)
        val fx = sin(az)
        val fy = cos(az)
        val cx = east * rx + north * ry
        val horiz = east * fx + north * fy
        val upC = up * cos(pitch) - horiz * sin(pitch)
        val fwdC = horiz * cos(pitch) + up * sin(pitch)
        if (fwdC <= 1e-5f) return null
        return floatArrayOf(
            w * 0.5f + focal * cx / fwdC,
            h * 0.5f - focal * upC / fwdC,
            fwdC
        )
    }

    /** 单位化的地表法线（用于朗伯光照）。gx/gy 是高度对东/北方向的梯度。 */
    fun normalZ(gx: Float, gy: Float): Float {
        val n = sqrt(gx * gx + gy * gy + 1f)
        return (1f / n).coerceIn(0f, 1f)
    }

    /** 朗伯光照：n·L，L 为太阳方向单位向量。 */
    fun lambert(gx: Float, gy: Float, sunAltDeg: Float, sunAzDeg: Float): Float {
        val sa = sunAltDeg * PI.toFloat() / 180f
        val aa = sunAzDeg * PI.toFloat() / 180f
        val lx = sin(aa) * cos(sa)
        val ly = cos(aa) * cos(sa)
        val lz = sin(sa)
        val inv = 1f / sqrt(gx * gx + gy * gy + 1f)
        return ((-gx * lx - gy * ly + lz) * inv).coerceIn(0f, 1f)
    }

    /** 伪地面云带：颜色 + 相对高度（0..1，0 = 地平线）+ 不透明度。 */
    data class Band(val colour: Rgb, val at: Float, val alpha: Float)

    /**
     * 气态/冰巨星的云带调色板：由天体自身的 groundTint / aerosolTint 派生，
     * 不针对具体行星写死颜色（木星褐白、土星淡金、天王星青绿、海王星深蓝都由各自的 tint 给出）。
     */
    fun bands(planet: Planet): List<Band> {
        val base = planet.groundTint
        val haze = planet.aerosolTint
        val n = 7
        return (0 until n).map { i ->
            val t = i.toFloat() / (n - 1).toFloat()
            val wave = sin((i * 1.7f) + 0.6f) * 0.5f + 0.5f
            val colour = base.mix(haze, 0.25f + 0.45f * wave).scale(0.75f + 0.45f * (1f - t))
            Band(colour, 0.06f + 0.94f * t.pow(1.35f), 0.20f + 0.22f * (1f - t))
        }
    }

    /** 树的位置：确定性伪随机，密度随植被参数变化，避免整齐排列。 */
    fun treeAt(planet: Planet, ring: Int, sector: Int, vegetation: Float): Float {
        if (vegetation <= 0.02f) return 0f
        val s = Terrain.seedOf(planet) xor (ring * 73856093) xor (sector * 19349663)
        val h = Terrain.valueNoise(sector * 0.37f + ring * 1.71f, s)
        val gate = h * h * 1.35f
        return if (gate < 1f - vegetation.coerceIn(0f, 1f) * 0.75f) 0f else (0.35f + 0.65f * h) * vegetation
    }


    // ------------------------------------------------------------------
    // v0.6.6：相机变换 + 裁剪 + 随距离收敛的高程
    // ------------------------------------------------------------------

    /** 近平面（km）：0.8 m。任何 z < NEAR 的顶点都必须被裁掉，否则 1/z 会把格子炸成满屏楔形。 */
    const val NEAR_KM = 0.0008f

    /** 地形仰角上限：山体相对眼睛的仰角永远不超过这个角度，保证地面不可能铺到天顶。 */
    const val MAX_RELIEF_ELEV_DEG = 4f

    /** 高度场在 6 km 处达到满幅；更近处被压平——人站在地面上，脚边不该是几千米的悬崖。 */
    const val RELIEF_FULL_KM = 6f

    /** 地形绝对高度上限（km）：真实行星上极少有超过 3 km 的单点起伏。 */
    const val MAX_RELIEF_KM = 3f

    /**
     * 格点相对眼高的高度（km）。
     *
     * v0.6.5 直接用 `(高度场 - 海平面) × 起伏幅度`，于是一个 4.9 km 的山头可以出现在离眼睛 20 m 的地方，
     * 它的四边形从脚下一路铺到天顶——这正是真机截图里那些「贯穿画面的竖直长条 / 倒挂锥体」。
     * 现在高度随距离平滑上升（脚边为 0），并且按仰角上限再压一次，任何格点都在 22° 以下。
     */
    fun reliefHeightKm(profile: ReliefProfile, meshValue: Float, distanceKm: Float): Float {
        val raw = (meshValue - profile.seaLevel).coerceAtLeast(0f) * profile.amplitudeKm
        val t = ((distanceKm - 0.02f) / (RELIEF_FULL_KM - 0.02f)).coerceIn(0f, 1f)
        val ramp = t * t * (3f - 2f * t)
        // 只保留仰角上限（避免出现直插天顶的尖刺），不再把地形压到眼平面以下：
        // v0.6.7 起允许山体合法高出地平线，可见性交给近平面与相机前向裁剪。
        val cap = distanceKm * tan(MAX_RELIEF_ELEV_DEG * PI.toFloat() / 180f) * 0.85f
        // 绝对上限：几何铺到 300 km 时，4° 的锥形上限会允许 20 km 高的「山」，必须再压一道
        return min(min(raw * ramp, cap), MAX_RELIEF_KM)
    }

    /**
     * 与天空共用同一套基向量的相机：前向 (cos e·cos a, cos e·sin a, sin e)、右向 (−sin a, cos a, 0)、
     * 上向 (−sin e·cos a, −sin e·sin a, cos e)。世界向量按 (北, 东, 高) 排列。
     * 天空那边（SkyProjector）用的是同一组公式，所以两套的地平线严格落在同一条扫描线上。
     */
    class Cam(
        val lookAzDeg: Float,
        val lookAltDeg: Float,
        val w: Float,
        val h: Float,
        val focal: Float
    ) {
        private val a = lookAzDeg * PI.toFloat() / 180f
        private val e = lookAltDeg * PI.toFloat() / 180f
        private val ca = cos(a)
        private val sa = sin(a)
        private val ce = cos(e)
        private val se = sin(e)

        /** 世界（北, 东, 高）→ 相机空间（右, 上, 前）。 */
        fun toCamera(north: Float, east: Float, up: Float): FloatArray = floatArrayOf(
            -north * sa + east * ca,
            -north * se * ca - east * se * sa + up * ce,
            north * ce * ca + east * ce * sa + up * se
        )

        fun toScreen(c: FloatArray): FloatArray = floatArrayOf(
            w * 0.5f + focal * c[0] / c[2],
            h * 0.5f - focal * c[1] / c[2]
        )
    }

    /** 地平线（仰角 0）在屏幕上的 y——地面与天空必须共用它。 */
    fun horizonY(cam: Cam): Float =
        cam.h * 0.5f + cam.focal * tan(cam.lookAltDeg * PI.toFloat() / 180f)

    /** 保留「地平线以下」的部分（仰角 < 0）。 */
    fun clipBelowEye(pts: List<FloatArray>): List<FloatArray> = clip(pts) { -it[2] - 1e-7f }

    /** 保留近平面之前的部分（相机空间 z ≥ NEAR）。 */
    fun clipNearCam(pts: List<FloatArray>): List<FloatArray> = clip(pts) { it[2] - NEAR_KM }

    /** Sutherland–Hodgman：signed 距离 ≥ 0 的一侧保留。 */
    private inline fun clip(pts: List<FloatArray>, dist: (FloatArray) -> Float): List<FloatArray> {
        if (pts.size < 3) return emptyList()
        val out = ArrayList<FloatArray>(pts.size + 4)
        for (i in pts.indices) {
            val cur = pts[i]
            val nxt = pts[(i + 1) % pts.size]
            val dc = dist(cur)
            val dn = dist(nxt)
            if (dc >= 0f) out.add(cur)
            if ((dc >= 0f) != (dn >= 0f)) {
                val t = dc / (dc - dn)
                out.add(FloatArray(3) { k -> cur[k] + (nxt[k] - cur[k]) * t })
            }
        }
        return if (out.size >= 3) out else emptyList()
    }

    /** 完整的格点流程：眼平面裁剪 → 相机变换 → 近平面裁剪 → 投影 → 视口粗筛。 */
    fun screenPolygon(cam: Cam, world: List<FloatArray>, margin: Float = 24f): List<FloatArray>? {
        // v0.6.7：仰角不再是可见性判据（山体可以合法高出地平线），只做近平面裁剪。
        val camPts = world.map { cam.toCamera(it[0], it[1], it[2]) }
        val near = clipNearCam(camPts)
        if (near.size < 3) return null
        val screen = near.map { cam.toScreen(it) }
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (p in screen) {
            minX = min(minX, p[0]); maxX = max(maxX, p[0])
            minY = min(minY, p[1]); maxY = max(maxY, p[1])
        }
        if (maxX < -margin || minX > cam.w + margin || maxY < -margin || minY > cam.h + margin) return null
        return screen
    }

    /** 树的投影：底点与顶点走同一套相机变换，超过 maxPx 直接按上限截断。 */
    fun treeScreen(cam: Cam, base: FloatArray, treeKm: Float, maxPx: Float): FloatArray? {
        if (base[2] >= -1e-6f) return null
        val bc = cam.toCamera(base[0], base[1], base[2])
        if (bc[2] < 0.02f) return null
        val tc = cam.toCamera(base[0], base[1], base[2] + treeKm)
        val bs = cam.toScreen(bc)
        val tz = max(tc[2], NEAR_KM)
        val tsy = cam.h * 0.5f - cam.focal * tc[1] / tz
        val hPx = (bs[1] - tsy).coerceIn(0f, maxPx)
        if (hPx < 1.5f) return null
        if (bs[0] < -40f || bs[0] > cam.w + 40f) return null
        // 只在画面内、且在地平线以下才画：仰视时树随地面一起消失
        if (bs[1] < horizonY(cam) - 1f || bs[1] > cam.h + 20f) return null
        return floatArrayOf(bs[0], bs[1], hPx)
    }


    /** 地面上一个格点的世界坐标（北, 东, 高）。 */
    fun surfaceWorld(planet: Planet, meshValue: Float, distanceKm: Float, bearingDeg: Float): FloatArray {
        val profile = Terrain.profileOf(planet)
        val br = bearingDeg * PI.toFloat() / 180f
        val h = if (profile == null) 0f
        else if (profile.water && meshValue < profile.seaLevel) 0f
        else reliefHeightKm(profile, meshValue, distanceKm)
        return floatArrayOf(distanceKm * cos(br), distanceKm * sin(br), h - EYE_HEIGHT_KM)
    }

    /** 一个已经过裁剪的地面格子（渲染与测试共用同一条路径）。 */
    data class Cell(
        val ring: Int,
        val sector: Int,
        val distanceKm: Float,
        val meshValue: Float,
        val poly: List<FloatArray>,
        val world: List<FloatArray>,
        val clipped: Boolean = false
    )

    /** 全部可见格子，按「远 → 近」排列。仰视时列表为空。 */
    fun groundCells(cam: Cam, planet: Planet, preset: Preset): List<Cell> {
        if (Terrain.profileOf(planet) == null) return emptyList()
        val mesh = heightField(planet, preset)
        val dist = ringDistances(planet, preset.rings, nearDistanceKm(cam))
        fun bearing(s: Int) = (s.toFloat() / preset.sectors) * 360f
        val out = ArrayList<Cell>(preset.rings * preset.sectors)
        for (r in preset.rings - 1 downTo 1) {
            for (s in 0 until preset.sectors) {
                val s2 = (s + 1) % preset.sectors
                val world = listOf(
                    surfaceWorld(planet, mesh[r][s], dist[r], bearing(s)),
                    surfaceWorld(planet, mesh[r][s2], dist[r], bearing(s2)),
                    surfaceWorld(planet, mesh[r - 1][s2], dist[r - 1], bearing(s2)),
                    surfaceWorld(planet, mesh[r - 1][s], dist[r - 1], bearing(s))
                )
                val poly = screenPolygon(cam, world) ?: continue
                val clipped = world.any { cam.toCamera(it[0], it[1], it[2])[2] < NEAR_KM }
                out.add(Cell(r, s, dist[r], mesh[r][s], poly, world, clipped))
            }
        }
        return out
    }

    fun clamp01(v: Float): Float = min(1f, max(0f, v))

    /** 树只在近处画：20–200 m。更近会撑满屏幕，更远缩成一个点。 */
    fun treeVisible(distanceKm: Float): Boolean = distanceKm in 0.02f..0.2f

    fun smoothstep(a: Float, b: Float, x: Float): Float {
        if (a == b) return if (x < a) 0f else 1f
        val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    fun luminance(c: Rgb): Float = 0.2126f * c.r + 0.7152f * c.g + 0.0722f * c.b

    /**
     * 地平线霾（叠加层）：以**屏幕距离**为准，把地平线附近的地面染成该方位的天空色。
     *
     * 平面 + 1.7 m 眼高时，整个可见地面带（屏高 1/4）其实是从十几米一直到无穷远，
     * 越远压缩越狠。真实大气把最后几公里的地面压成一层薄雾，屏幕上就是地平线附近
     * 一小条——这一层就是它，用一层不透明度渐变的天空色盖在整幅画面上，
     * 所以任何方位、任何 pitch 都不会出现「地面从哪儿开始」的硬边。
     */
    const val HAZE_END_PX = 128f

    /** 地平线下 px 处的霾不透明度：0 px 处为 1（完全是天空色），HAZE_END_PX 处为 0。 */
    fun hazeAlpha(pxBelowHorizon: Float): Float {
        if (pxBelowHorizon < 0f) return 0f
        return (1f - smoothstep(0f, HAZE_END_PX, pxBelowHorizon)).coerceIn(0f, 1f)
    }

    /**
     * 单格地表颜色：地貌 + 高度 + 水/植被 + 光照 + 空气透视。
     *
     * 与渲染器共用同一条代码路径（测试也调它），所以颜色断言检验的就是真正画出来的颜色。
     */
    fun cellColor(
        planet: Planet,
        profile: ReliefProfile,
        heightNorm: Float,
        isWater: Boolean,
        tree: Float,
        shade: Float,
        sky: Rgb,
        aerial: Float
    ): Rgb {
        // 环境光下限：任何坡面都不会压成纯黑（最低 0.34×基色）
        val lit = (0.34f + 0.86f * shade).coerceIn(0.32f, 1.2f)
        val base = if (isWater) {
            val depth = ((profile.seaLevel - heightNorm) / max(0.01f, profile.seaLevel)).coerceIn(0f, 1f)
            // v0.6.8：水面必须有水的深色底，只留 12 % 天空反射——
            // 原来 18–40 % 的天空混色让它和天空几乎同色，看起来像「墙上漏了个洞」。
            Rgb(0.020f, 0.055f, 0.110f).mix(sky, 0.12f)
                .scale((0.90f - 0.18f * depth).coerceIn(0.55f, 0.95f) * lit)
        } else {
            val raw = when (profile.kind) {
                ReliefKind.EARTH -> {
                    val rocky = ((heightNorm - 0.68f) / 0.32f).coerceIn(0f, 1f)
                    Rgb(0.11f, 0.25f, 0.12f).mix(Rgb(0.36f, 0.34f, 0.31f), rocky)
                }
                ReliefKind.MARS -> {
                    val dust = ((heightNorm - 0.55f) / 0.45f).coerceIn(0f, 1f)
                    Rgb(0.42f, 0.19f, 0.10f).mix(Rgb(0.58f, 0.34f, 0.20f), dust)
                }
                ReliefKind.CRATERED -> {
                    val v = 0.24f + 0.12f * heightNorm
                    Rgb(v, v * 0.98f, v * 0.93f)
                }
                ReliefKind.ICY -> Rgb(0.60f, 0.70f, 0.80f).mix(Rgb(0.86f, 0.90f, 0.95f), heightNorm)
                ReliefKind.SMOOTH -> planet.groundTint.mix(Rgb(0.72f, 0.66f, 0.55f), 0.35f)
                ReliefKind.NONE -> planet.groundTint
            }
            val withTrees = if (tree > 0.02f) {
                raw.mix(Rgb(0.06f, 0.17f, 0.07f), (tree * 0.85f).coerceIn(0f, 0.75f))
            } else raw
            withTrees.scale(lit)
        }
        // 空气透视：外圈强制收敛到地平线色，做到与天空无缝
        return base.mix(sky, aerial.coerceIn(0f, 1f))
    }

    /** 渲染用的已着色格子（远→近）。 */
    data class Quad(
        val poly: List<FloatArray>,
        val colour: Rgb,
        val ring: Int,
        val sector: Int,
        val distanceKm: Float,
        val water: Boolean
    )

    fun key(ring: Int, sector: Int): Long = (ring.toLong() shl 32) or (sector.toLong() and 0xffffffffL)

    /** 单格朗伯光照：坡度用「实际高程」（高度场 × 随距离收敛的幅度）。 */
    fun rawLight(
        planet: Planet,
        profile: ReliefProfile,
        shadeMesh: Array<FloatArray>,
        dist: FloatArray,
        r: Int,
        s: Int,
        preset: Preset,
        sunAltDeg: Float,
        sunAzDeg: Float
    ): Float {
        val sectors = preset.sectors
        val rings = preset.rings
        val s2 = (s + 1) % sectors
        val sPrev = (s - 1 + sectors) % sectors
        val cellEast = max(0.02f, dist[r] * 2f * PI.toFloat() / sectors)
        val cellNorth = max(0.02f, dist[r] - dist[max(0, r - 1)])
        fun hKm(rr: Int, ss: Int) = reliefHeightKm(profile, shadeMesh[rr][ss], dist[rr])
        val dhE = (hKm(r, s2) - hKm(r, sPrev)) * 0.5f
        val dhN = (hKm(min(r + 1, rings - 1), s) - hKm(max(r - 1, 0), s)) * 0.5f
        return lambert(
            (dhE / cellEast).coerceIn(-1.2f, 1.2f),
            (dhN / cellNorth).coerceIn(-1.2f, 1.2f),
            sunAltDeg,
            sunAzDeg
        )
    }

    /**
     * 渲染与测试共用的完整着色管线：几何 → 光照 → 平滑 → 颜色 → 霾。
     * 返回远→近的 Quad 列表。
     */
    fun groundQuads(
        cam: Cam,
        planet: Planet,
        preset: Preset,
        sunAzDeg: Float,
        sunAltDeg: Float,
        sky: Rgb,
        vegetation: Float,
        shadowStrength: Float
    ): List<Quad> {
        val profile = Terrain.profileOf(planet) ?: return emptyList()
        val mesh = heightField(planet, preset)
        val shadeMesh = smoothedMesh(mesh, preset.rings, preset.sectors)
        val dist = ringDistances(planet, preset.rings, nearDistanceKm(cam))
        val cells = groundCells(cam, planet, preset)
        if (cells.isEmpty()) return emptyList()
        val sectors = preset.sectors
        val shadow = shadowStrength.coerceIn(0f, 1f)

        val lightMap = HashMap<Long, Float>(cells.size * 2)
        for (c in cells) {
            lightMap[key(c.ring, c.sector)] =
                rawLight(planet, profile, shadeMesh, dist, c.ring, c.sector, preset, sunAltDeg.coerceIn(-20f, 90f), sunAzDeg)
        }
        repeat(4) {
            val next = HashMap<Long, Float>(lightMap.size)
            for (c in cells) {
                val l = lightMap[key(c.ring, c.sector)] ?: 0f
                val lp = lightMap[key(c.ring, (c.sector - 1 + sectors) % sectors)] ?: l
                val ln = lightMap[key(c.ring, (c.sector + 1) % sectors)] ?: l
                next[key(c.ring, c.sector)] = (lp + 2f * l + ln) / 4f
            }
            lightMap.putAll(next)
        }

        val range = horizonDistanceKm(planet)
        val out = ArrayList<Quad>(cells.size)
        for (c in cells) {
            val light = (lightMap[key(c.ring, c.sector)] ?: 0f).coerceIn(0f, 1f)
            val shade = ((1f - shadow) + shadow * light).coerceIn(0f, 1f)
            val water = profile.water && c.meshValue < profile.seaLevel
            val tree = if (profile.trees && !water && c.ring <= preset.treeRings) {
                treeAt(planet, c.ring, c.sector, vegetation.coerceIn(0f, 1f))
            } else 0f
            // v0.6.8：格子颜色**不再**按距离变淡。远近格子的距离差着几个数量级
            // （18 m 与 5 km 相邻），逐格做空气透视会在格边留下一条能看出来的线；
            // 淡化交给屏幕空间的地平线霾叠加层（hazeAlpha），它对每个像素都是连续的。
            val aerial = 0f
            val colour = cellColor(planet, profile, c.meshValue, water, tree, shade, sky, aerial)
            out.add(Quad(c.poly, colour, c.ring, c.sector, c.distanceKm, water))
        }

        // 颜色在网格上再做两遍 3×3 平滑：相邻圈的格子尺度可能差一倍，
        // 逐格一个常色会在圈与圈的接缝上留下一条能看出来的线；平滑之后同一片坡面是连续的。
        val smoothed = ArrayList<Quad>(out.size)
        val map = HashMap<Long, Rgb>(out.size * 2)
        for (q in out) map[key(q.ring, q.sector)] = q.colour
        repeat(3) {
            val next = HashMap<Long, Rgb>(map.size)
            for (q in out) {
                if (q.water) { next[key(q.ring, q.sector)] = map[key(q.ring, q.sector)]!!; continue }
                var r = 0f
                var g = 0f
                var b = 0f
                var n = 0
                for (dr in -1..1) for (ds in -1..1) {
                    val rr = q.ring + dr
                    val ss = (q.sector + ds + sectors) % sectors
                    val c = map[key(rr, ss)] ?: continue
                    r += c.r; g += c.g; b += c.b; n++
                }
                if (n == 0) { next[key(q.ring, q.sector)] = map[key(q.ring, q.sector)]!!; continue }
                next[key(q.ring, q.sector)] = Rgb(r / n, g / n, b / n)
            }
            map.putAll(next)
        }
        // 最后把整体色调收 15 %：地面是一片连续的地表，不该出现「这块比那块亮一档」的碎块感
        var mr = 0f; var mg = 0f; var mb = 0f; var mn = 0
        for (q in out) {
            if (q.water) continue
            val c = map[key(q.ring, q.sector)]!!
            mr += c.r; mg += c.g; mb += c.b; mn++
        }
        val mean = if (mn > 0) Rgb(mr / mn, mg / mn, mb / mn) else null
        for (q in out) {
            val c = map[key(q.ring, q.sector)]!!
            smoothed.add(if (q.water || mean == null) q.copy(colour = c) else q.copy(colour = c.mix(mean, 0.15f)))
        }
        return smoothed
    }
}
