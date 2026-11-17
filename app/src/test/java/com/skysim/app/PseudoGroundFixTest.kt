package com.skysim.app

import com.skysim.app.core.Ground3D
import com.skysim.app.core.Planets
import com.skysim.app.core.PseudoGroundClip
import com.skysim.app.core.Terrain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.tan

/**
 * v0.8.3 修复验收：气态行星把视线仰到顶（上限 88°）时不得再冒出伪地面。
 *
 * 缺陷成因：伪地面分支在拿不到地平线投影时用屏幕中线 h * 0.5 兜底，而投影在视线与
 * 地平线方向的夹角超过 85.42°（前向余弦 dz <= 0.08）时返回 null，仰角上限 88° 正落在
 * 这段区间，于是 85.4°–88° 每帧都把半屏画成地面。这里断言的是「判据」而不是「画法」：
 * 判据不依赖 Compose，所以能直接对解析投影值逐度核对。
 */
class PseudoGroundFixTest {

    private val fovDeg = 80.0

    private fun focal(w: Float) = (w / 2f) / tan(fovDeg * PI / 360.0).toFloat()

    /** 450x800、16:9 手机屏 */
    private val w = 1080f
    private val h = 2340f

    private fun horizon(lookAlt: Float, ww: Float = w, hh: Float = h, lookAz: Float = 0f) =
        PseudoGroundClip.horizonTopY(lookAz, lookAlt, hh, focal(ww))

    /** 每条断言的统一前置：判据阈值必须和投影的近平面判据是同一条。 */
    @Test
    fun nearPlaneThreshold_matchesProjector() {
        assertEquals(
            "伪地面的近平面阈值必须与 SkyProjector.project() 的 dz <= 0.08 完全一致",
            0.08f,
            PseudoGroundClip.MIN_FORWARD_COS,
            0f
        )
        // 隔离出「前向余弦」这一条规则：把屏幕放到极高，使 y > h 的剔除不会先行生效，
        // 这样返回 null 就只能是因为 dz <= 0.08。
        val tall = 1e6f
        val justProjectable = 85.40f      // cos = 0.08047 > 0.08
        val justBeyond = 85.45f           // cos = 0.07935 < 0.08
        assertTrue(
            "85.40° 时地平线仍在近平面之内（cos 0.08047 > 0.08），必须可投影",
            PseudoGroundClip.canProject(0f, justProjectable, 0f, 0f)
        )
        assertNotNull(
            "85.40° 且屏幕足够高时地平线应给出解析落点",
            PseudoGroundClip.horizonTopY(0f, justProjectable, tall, focal(w))
        )
        assertFalse(
            "85.45° 时地平线方向已越过近平面（cos 0.07935 < 0.08），不可投影",
            PseudoGroundClip.canProject(0f, justBeyond, 0f, 0f)
        )
        assertNull(
            "越过近平面后必须返回 null，不得用任何常量顶替",
            PseudoGroundClip.horizonTopY(0f, justBeyond, tall, focal(w))
        )
    }

    /** 用户报的那一段：85°–88° 扫描，伪地面输出必须为空。 */
    @Test
    fun highAlt_85to88_producesNoGround() {
        var alt = 85f
        while (alt <= 88.001f) {
            assertNull(
                "仰角 $alt° 时地平线在屏幕之外（或不可投影），伪地面必须不绘制，绝不能落到 h * 0.5",
                horizon(alt)
            )
            alt += 0.25f
        }
    }

    /** 防止修成「永远不画」：低仰角必须确实有绘制面积。 */
    @Test
    fun lowAlt_0to15_stillHasDrawableArea() {
        var alt = 0f
        while (alt <= 15.001f) {
            val y = horizon(alt)
            assertNotNull("仰角 $alt° 是正常地面视角，必须有地平线", y)
            val area = h - y!!
            assertTrue(
                "仰角 $alt° 的伪地面绘制面积只有 $area px，低仰角被修没了",
                area > 20f
            )
            alt += 0.5f
        }
        // 正对地平线时约半屏
        val mid = horizon(0f)!!
        assertTrue("仰角 0° 时地面应约占下半屏，实际顶边 $mid", abs(mid - h * 0.5f) < 1f)
    }

    /** 可投影范围内落点必须等于解析投影值，且绝不等同于兜底常量。 */
    @Test
    fun projectableRange_equalsAnalyticProjection() {
        // 用足够高的屏幕，让「y > h 剔除」不参与，只比较几何值
        val tall = 1e6f
        var alt = -12f
        while (alt <= 84.001f) {
            val y = PseudoGroundClip.horizonTopY(0f, alt, tall, focal(w))
            if (PseudoGroundClip.canProject(0f, alt, 0f, 0f)) {
                assertNotNull("仰角 $alt° 可投影，必须给出落点", y)
                val analytic = tall * 0.5f + focal(w) * tan(alt * PI.toFloat() / 180f)
                // 屏幕高 1e6 px 是为了隔离「y > h 剔除」，代价是 tan 的末位舍入被放大到 0.1 px 级，
                // 所以容差取 0.5 px（相对 5e-7），仍然远小于任何可见误差。
                assertEquals("仰角 $alt° 的落点必须等于解析投影值", analytic, y!!, 0.5f)
                val cam = Ground3D.Cam(0f, alt, w, tall, focal(w))
                assertEquals(
                    "仰角 $alt° 的落点必须与三维地面用的地平线公式一致",
                    Ground3D.horizonY(cam),
                    y,
                    0.5f
                )
            } else {
                assertNull("仰角 $alt° 不可投影，必须返回 null 而不是屏幕中线", y)
            }
            alt += 0.25f
        }
        // 兜底常量回归：除仰角 0° 这一条几何上的特例外，任何正仰角都不得等于 h * 0.5
        alt = 0.25f
        while (alt <= 84f) {
            val y = PseudoGroundClip.horizonTopY(0f, alt, tall, focal(w))
            if (y != null) {
                assertTrue(
                    "仰角 $alt° 的落点等于屏幕中线 $y，正是修复前的兜底常量",
                    abs(y - tall * 0.5f) > 1f
                )
            }
            alt += 0.25f
        }
    }

    /** 四颗气态/冰巨星都走同一条规则：伪地面路径成立，且判据一致。 */
    @Test
    fun gasGiants_shareTheSameRule() {
        val ids = listOf("jupiter", "saturn", "uranus", "neptune")
        for (id in ids) {
            val p = Planets.byId(id)
            assertTrue("$id 必须走伪地面分支", Ground3D.isPseudoGround(p))
            assertNull("$id 不应有固体地形剖面（否则会走三维地面）", Terrain.profileOf(p))
            assertNull("$id 在 87° 时不得有伪地面", horizon(87f))
            val y = horizon(6f)
            assertNotNull("$id 在 6° 时必须照常画云顶", y)
            assertTrue("$id 在 6° 时的地面面积应接近半屏", h - y!! > h * 0.4f)
        }
    }

    /** 多种屏幕比例下结论都成立，不能只在一种宽高比上「碰巧对」。 */
    @Test
    fun aspectRatios_allHold() {
        val screens = listOf(
            1080f to 2340f,   // 19.5:9
            1080f to 1920f,   // 16:9
            1440f to 1080f,   // 4:3
            1080f to 1080f,   // 1:1
            720f to 1600f     // 20:9 小屏
        )
        for ((ww, hh) in screens) {
            for (alt in listOf(85f, 86f, 87f, 88f)) {
                assertNull("$ww x $hh 屏上仰角 $alt° 仍在画伪地面", horizon(alt, ww, hh))
            }
            for (alt in listOf(0f, 5f, 10f, 15f)) {
                val y = horizon(alt, ww, hh)
                assertNotNull("$ww x $hh 屏上仰角 $alt° 必须有地面", y)
                assertTrue("$ww x $hh 屏上仰角 $alt° 地面面积过小", hh - y!! > hh * 0.02f)
            }
        }
    }

    /** 方位角不该影响结论：判据只看视线与地平线方向的夹角。 */
    @Test
    fun azimuthDoesNotChangeOutcome() {
        for (az in listOf(0f, 45f, 90f, 180f, 270f, 359f)) {
            assertNull("yaw $az 时俯仰 88° 仍不得画伪地面", horizon(88f, lookAz = az))
            assertNotNull("yaw $az 时俯仰 5° 必须有伪地面", horizon(5f, lookAz = az))
        }
        // 视线方位与地平线方位相同 → 前向余弦就是 cos(仰角)，与方位无关
        assertEquals(cos(88.0 * PI / 180.0).toFloat(), PseudoGroundClip.forwardCos(123f, 88f, 123f, 0f), 1e-5f)
    }
}
