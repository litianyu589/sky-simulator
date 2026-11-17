package com.skysim.app

import com.skysim.app.core.Planets
import com.skysim.app.core.SurfaceKind
import com.skysim.app.core.Terrain
import com.skysim.app.core.TerrainQuality
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** v0.6.4：地形高度场的确定性、阴影行进、水面高光与画质档。 */
class TerrainTest {

    private val earth = Planets.byId("earth")

    /** 同一行星球体、同一列数 → 完全相同的高度场（固定种子可复现）。 */
    @Test
    fun heightFieldIsDeterministic() {
        val a = Terrain.heightField(earth, 64)
        val b = Terrain.heightField(earth, 64)
        assertArrayEquals(a, b, 0f)
        assertEquals(64, a.size)
    }

    /** 不同天体必须得到不同地形。 */
    @Test
    fun differentBodiesGetDifferentRelief() {
        val e = Terrain.heightField(earth, 64)
        val m = Terrain.heightField(Planets.byId("mars"), 64)
        var diff = 0f
        for (i in 0 until 64) diff += abs(e[i] - m[i])
        assertTrue("地球与火星的高度场不应相同", diff / 64f > 0.05f)
    }

    /** 气态巨行星与空间站没有地形。 */
    @Test
    fun gasGiantsHaveNoTerrain() {
        for (id in listOf("jupiter", "saturn", "uranus", "neptune")) {
            val p = Planets.byId(id)
            assertEquals(SurfaceKind.CLOUD_DECK, p.surfaceKind)
            assertNull("$id 不应有地形", Terrain.profileOf(p))
            assertTrue(Terrain.heightField(p, 32).isEmpty())
        }
    }

    /** 地球既有海平面以下的水面，也有海平面以上的陆地。 */
    @Test
    fun earthHasBothWaterAndLand() {
        val p = Terrain.profileOf(earth)!!
        assertTrue(p.water)
        assertTrue(p.trees)
        val hf = Terrain.heightField(earth, 200)
        assertTrue("应有水面", hf.any { it < p.seaLevel })
        assertTrue("应有陆地", hf.any { it > p.seaLevel + 0.05f })
    }

    /** 太阳在地平线以下时没有直射光。 */
    @Test
    fun noDirectLightWhenSunBelowHorizon() {
        val hf = Terrain.heightField(earth, 48)
        val trees = FloatArray(48)
        for (i in 0 until 48) {
            assertEquals(0f, Terrain.sunVisibility(earth, hf, trees, i, -1f, 60f), 1e-6f)
            assertEquals(0f, Terrain.sunVisibility(earth, hf, trees, i, 0f, 60f), 1e-6f)
        }
    }

    /** 完全平坦的地面不会被遮挡。 */
    @Test
    fun flatGroundIsFullyLit() {
        val flat = FloatArray(48) { 0.5f }
        val trees = FloatArray(48)
        for (i in 0 until 48) {
            assertEquals(1f, Terrain.sunVisibility(earth, flat, trees, i, 20f, 75f), 1e-4f)
        }
    }

    /** 太阳方向上的高地会挡住低角度的太阳。 */
    @Test
    fun ridgeBlocksLowSun() {
        val hf = FloatArray(48) { 0.2f }
        // 右侧堆一条高山脊
        for (i in 30 until 48) hf[i] = 1f
        val trees = FloatArray(48)
        val blocked = Terrain.sunVisibility(earth, hf, trees, 24, 5f, 90f)
        val clear = Terrain.sunVisibility(earth, hf, trees, 24, 60f, 90f)
        assertTrue("低太阳应被山脊遮挡", blocked < 0.85f)
        assertTrue("高太阳应几乎不受影响", clear > blocked)
        // 太阳换到另一侧，同一列不应再被这条山脊遮挡
        val other = Terrain.sunVisibility(earth, hf, trees, 24, 5f, -90f)
        assertTrue("背向山脊时应更亮", other > blocked)
    }

    /** 遮挡率随太阳方位连续变化，不出现突变。 */
    @Test
    fun shadowVariesContinuouslyWithAzimuth() {
        val hf = Terrain.heightField(Planets.byId("mars"), 96)
        val trees = FloatArray(96)
        var prev = Terrain.sunVisibility(Planets.byId("mars"), hf, trees, 48, 8f, -180f)
        var az = -180f
        while (az <= 180f) {
            val v = Terrain.sunVisibility(Planets.byId("mars"), hf, trees, 48, 8f, az)
            assertTrue("方位 $az 处跳变", abs(v - prev) < 0.55f)
            prev = v
            az += 2f
        }
    }

    /** 树剪影只出现在植树天体上，并随植被密度线性变高。 */
    @Test
    fun treeSilhouettesOnlyWhereVegetationGrows() {
        val low = Terrain.treeField(earth, 96, 0.1f)
        val high = Terrain.treeField(earth, 96, 0.9f)
        assertTrue("低密度也要有树", low.any { it > 0f })
        assertTrue("高密度应更高", high.sum() > low.sum() * 1.5f)
        val marsTrees = Terrain.treeField(Planets.byId("mars"), 96, 0.9f)
        assertTrue("火星没有植被", marsTrees.all { it == 0f })
        val none = Terrain.treeField(earth, 96, 0f)
        assertTrue(none.all { it == 0f })
    }

    /** 画质档：列数与步数逐档增加，用于性能控制。 */
    @Test
    fun qualityPresetsScaleWork() {
        assertTrue(TerrainQuality.LOW.columns < TerrainQuality.MEDIUM.columns)
        assertTrue(TerrainQuality.MEDIUM.columns < TerrainQuality.HIGH.columns)
        assertTrue(TerrainQuality.LOW.steps < TerrainQuality.MEDIUM.steps)
        assertTrue(TerrainQuality.MEDIUM.steps < TerrainQuality.HIGH.steps)
    }

    /** 水面高光指向太阳：方位差最小处最强，太阳落下后消失。 */
    @Test
    fun waterGlintFollowsTheSun() {
        val toward = Terrain.waterGlint(6f, 0f)
        val side = Terrain.waterGlint(6f, 90f)
        val behind = Terrain.waterGlint(6f, 180f)
        assertTrue(toward > side)
        assertTrue(side > behind)
        assertEquals(0f, Terrain.waterGlint(-1f, 0f), 1e-6f)
        // 太阳越低，同方位的光路越长越亮
        assertTrue(Terrain.waterGlint(3f, 0f) > Terrain.waterGlint(30f, 0f))
    }

    /** 关闭光影后只剩环境光；太阳落下时直射项消失。 */
    @Test
    fun lightMixFallsBackToAmbient() {
        assertEquals(1f, Terrain.lightMix(1f, 45f, 0f), 1e-5f)
        val shaded = Terrain.lightMix(0f, 30f, 0.65f)
        assertEquals(0.35f, shaded, 1e-5f)
        val night = Terrain.lightMix(1f, -5f, 0.65f)
        assertEquals(0.35f, night, 1e-5f)
        assertFalse(shaded > 0.5f)
    }
}
