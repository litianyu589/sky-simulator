package com.skysim.app

import com.skysim.app.core.Planets
import com.skysim.app.core.QualityGovernor
import com.skysim.app.core.SkyGrid
import com.skysim.app.core.TerrainQuality
import com.skysim.app.core.Twilight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** v0.8.2：画质三档采样、背日侧覆盖、边缘连续性、维纳斯带锚点、升降回滞。 */
class V082Test {

    // ---------- 三档采样 ----------

    @Test
    fun qualityTiersCarryTheDocumentedSampleCounts() {
        assertEquals("低档 26×15", 390, SkyGrid.samples(TerrainQuality.LOW))
        assertEquals("中档 26×25", 650, SkyGrid.samples(TerrainQuality.MEDIUM))
        assertEquals("高档 26×37（v0.8.1 观感）", 962, SkyGrid.samples(TerrainQuality.HIGH))
        assertEquals(15, SkyGrid.columns(TerrainQuality.LOW))
        assertEquals(25, SkyGrid.columns(TerrainQuality.MEDIUM))
        assertEquals(37, SkyGrid.columns(TerrainQuality.HIGH))
        assertTrue(SkyGrid.samples(TerrainQuality.LOW) < SkyGrid.samples(TerrainQuality.MEDIUM))
        assertTrue(SkyGrid.samples(TerrainQuality.MEDIUM) < SkyGrid.samples(TerrainQuality.HIGH))
    }

    @Test
    fun everyTierKeepsFull360AzimuthCoverage() {
        for (q in TerrainQuality.values()) {
            val rel = SkyGrid.relAzDegrees(SkyGrid.columns(q))
            assertEquals("$q 列数", SkyGrid.columns(q), rel.size)
            assertEquals("$q 必须从太阳 −180° 起", -180f, rel.first(), 1e-4f)
            assertEquals("$q 必须到太阳 +180° 止", 180f, rel.last(), 1e-4f)
            assertTrue("$q 必须覆盖背日侧", rel.any { kotlin.math.abs(it) < 180f / SkyGrid.columns(q) })
            for (i in 1 until rel.size) assertTrue("$q 方位必须单调", rel[i] > rel[i - 1])
        }
        // 低档的列距（25.7°）比 0.8.0 的「15 列 × ±60°」（8.6°）粗，这是保覆盖的代价
        val lowStep = 360f / (SkyGrid.columns(TerrainQuality.LOW) - 1)
        val highStep = 360f / (SkyGrid.columns(TerrainQuality.HIGH) - 1)
        assertTrue("低档列距必须明显粗于高档", lowStep > 2f * highStep)
    }

    // ---------- 边缘连续性（闪烁/花屏） ----------

    private fun visibleColumns(cols: Int, sunAz: Float, lookAz: Float): List<Int> {
        val rel = SkyGrid.relAzDegrees(cols)
        val lookAlt = 10f
        val cosP = kotlin.math.cos(lookAlt * Math.PI.toFloat() / 180f)
        val sinP = kotlin.math.sin(lookAlt * Math.PI.toFloat() / 180f)
        return (0 until cols).filter { j ->
            val dRad = (rel[j] + (sunAz - lookAz)) * Math.PI.toFloat() / 180f
            cosP * kotlin.math.cos(dRad) * cosP + sinP * sinP > 0.02f
        }
    }

    private fun stopsFor(lookAz: Float, sunAz: Float, cols: Int): Triple<Int, FloatArray, IntArray> {
        val rel = SkyGrid.relAzDegrees(cols)
        val colours = IntArray(cols) { it or (0xFF shl 24) }
        val outX = FloatArray(cols + 2)
        val outA = IntArray(cols + 2)
        val n = SkyGrid.buildStops(
            viewAltDeg = 10f,
            relAz = rel,
            sunMinusLook = sunAz - lookAz,
            focal = 900f,
            w = 2000f,
            colours = colours,
            outX = outX,
            outA = outA
        )
        return Triple(n, outX, outA)
    }

    @Test
    fun stopsCoverTheScreenAndAreStrictlyIncreasing() {
        val (n, x, _) = stopsFor(lookAz = 180f, sunAz = 0f, cols = 37)
        assertTrue("必须有停点", n >= 2)
        assertTrue("左端必须覆盖到画布左侧", x[0] <= 0.01f)
        assertTrue("右端必须覆盖到画布右侧", x[n - 1] >= 2000f - 0.01f)
        for (i in 1 until n) assertTrue("停点必须严格递增（去重后不留 0 宽段）", x[i] > x[i - 1])
    }

    @Test
    fun behindCameraColumnsNeverPaintTheScreenEdges() {
        val cols = 37
        val rel = SkyGrid.relAzDegrees(cols)
        val (n, _, a) = stopsFor(lookAz = 180f, sunAz = 0f, cols = cols)
        val visible = visibleColumns(cols, sunAz = 0f, lookAz = 180f)
        assertTrue("可见列不应该为空", visible.isNotEmpty())
        assertTrue("必须有列落在相机背后（否则这条测试没有覆盖边界）", visible.size < cols)
        val used = (0 until n).map { a[it] and 0xFF }.toSet()
        for (j in used) {
            assertTrue(
                "第 $j 列（relAz=${rel[j]}°）在相机背后，它的颜色不允许出现在屏幕上",
                visible.contains(j)
            )
        }
        // 被夹到同一屏幕边缘的相邻列会在去重时合并，允许少量合并，但不得大面积丢失
        assertTrue("停点数量应接近可见列数（只允许两端去重）", used.size >= visible.size / 2)
    }

    @Test
    fun sunAzimuthDriftDoesNotSwapTheEdgeColour() {
        var prevLeft = -1
        var prevRight = -1
        var transitions = 0
        for (step in 0 until 40) {
            val sunAz = step * 0.25f
            val (n, _, a) = stopsFor(lookAz = 180f, sunAz = sunAz, cols = 37)
            val left = a[0] and 0xFF
            val right = a[n - 1] and 0xFF
            if (prevLeft >= 0) {
                if (left != prevLeft) transitions++
                if (right != prevRight) transitions++
                assertTrue("边缘颜色跳变过大：$prevLeft → $left", kotlin.math.abs(left - prevLeft) <= 2)
                assertTrue("边缘颜色跳变过大：$prevRight → $right", kotlin.math.abs(right - prevRight) <= 2)
            }
            prevLeft = left
            prevRight = right
        }
        assertTrue("40 帧里应发生若干次端列切换（否则测试没有真的覆盖边界）", transitions > 0)
    }

    // ---------- 维纳斯带回归 ----------

    @Test
    fun earthBeltColourIsBackOnThePinkAnchor() {
        val c = Twilight.beltColour(Planets.byId("earth"))
        assertEquals("地球维纳斯带 R 必须回到 0.7560", 0.7560f, c.r, 2e-3f)
        assertEquals("地球维纳斯带 G 必须回到 0.4289", 0.4289f, c.g, 2e-3f)
        assertEquals("地球维纳斯带 B 必须回到 0.5809", 0.5809f, c.b, 2e-3f)
        // 粉紫的含义：红 > 蓝 > 绿（绿被臭氧吃掉）。0.823/0.881/0.980 的淡蓝白是 b > g > r，必须判失败。
        assertTrue("必须是红最强", c.r > c.b && c.b > c.g)
        assertEquals("地球带强度仍为 1", 1f, Twilight.beltStrength(Planets.byId("earth")), 1e-6f)
    }

    @Test
    fun beltColourStillFollowsTheLocalStar() {
        val earth = Twilight.beltColour(Planets.byId("earth"))
        val redDwarf = Twilight.beltColour(Planets.byId("trappist1d"))
        val diff = kotlin.math.abs(earth.r - redDwarf.r) +
            kotlin.math.abs(earth.g - redDwarf.g) +
            kotlin.math.abs(earth.b - redDwarf.b)
        assertTrue("不同恒星光谱的带色必须不同（不是套一张调色板）", diff > 0.01f)
        assertNotEquals(earth.r, redDwarf.r, 0f)
    }

    // ---------- 升降回滞 ----------

    @Test
    fun threeSlowFramesDropOneTier() {
        val g = QualityGovernor()
        var q = TerrainQuality.HIGH
        q = g.frame(0.016, 30.0, q)
        q = g.frame(0.016, 30.0, q)
        assertEquals("两帧还不到降档阈值", TerrainQuality.HIGH, q)
        q = g.frame(0.016, 30.0, q)
        assertEquals("连续 3 帧超时必须降一档", TerrainQuality.MEDIUM, q)
        q = g.frame(0.016, 30.0, q)
        q = g.frame(0.016, 30.0, q)
        q = g.frame(0.016, 30.0, q)
        assertEquals("再连续 3 帧降到低档", TerrainQuality.LOW, q)
        q = g.frame(0.016, 30.0, q)
        q = g.frame(0.016, 30.0, q)
        q = g.frame(0.016, 30.0, q)
        assertEquals("低档是地板，不再降", TerrainQuality.LOW, q)
    }

    @Test
    fun twelveCalmSecondsUpgradeOneTier() {
        val g = QualityGovernor()
        var q = TerrainQuality.LOW
        repeat(11) { q = g.frame(1.0, 12.0, q) }
        assertEquals("11 s 无超时帧还不升", TerrainQuality.LOW, q)
        q = g.frame(1.0, 12.0, q)
        assertEquals("12 s 无超时帧升一档", TerrainQuality.MEDIUM, q)
        repeat(12) { q = g.frame(1.0, 12.0, q) }
        assertEquals("再 12 s 升到高档", TerrainQuality.HIGH, q)
    }

    @Test
    fun aSingleSlowFrameRestartsTheCalmWindow() {
        val g = QualityGovernor()
        var q = TerrainQuality.LOW
        repeat(11) { q = g.frame(1.0, 12.0, q) }
        q = g.frame(1.0, 40.0, q)
        repeat(5) { q = g.frame(1.0, 12.0, q) }
        assertEquals("超时帧必须重置升档计时", TerrainQuality.LOW, q)
    }

    @Test
    fun droppingSoonAfterUpgradeLocksTheAutoUpgrade() {
        val g = QualityGovernor()
        var q = TerrainQuality.LOW
        repeat(12) { q = g.frame(1.0, 12.0, q) }
        assertEquals(TerrainQuality.MEDIUM, q)
        q = g.frame(1.0, 40.0, q)
        q = g.frame(1.0, 40.0, q)
        q = g.frame(1.0, 40.0, q)
        assertEquals("重新降档", TerrainQuality.LOW, q)
        assertTrue("升后立刻掉帧必须上锁", g.locked)
        repeat(60) { q = g.frame(1.0, 12.0, q) }
        assertEquals("上锁后不得自动升档", TerrainQuality.LOW, q)
    }

    @Test
    fun manualChoiceOptsOutOfAutomation() {
        val g = QualityGovernor()
        g.manual()
        assertFalse(g.auto)
        var q = TerrainQuality.HIGH
        repeat(30) { q = g.frame(1.0, 60.0, q) }
        assertEquals("手动选档后自动逻辑必须完全退出", TerrainQuality.HIGH, q)
        g.enableAuto()
        assertTrue(g.auto)
    }
}
