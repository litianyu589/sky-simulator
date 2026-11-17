package com.skysim.app.core

/**
 * v0.8.5：HUD 上「调试读数」与「用户可见状态」的分离。
 *
 * 帧间隔、P95、各分段耗时（天空 / 地面 / 霾带 / 树木 / 其它 / 合成）是开发读数，正式版不需要，
 * 收进菜单里的 Debug 开关（默认关）。关闭时这些行**根本不进组合树**，因此既不绘制也不占布局；
 * 打开时按原排版恢复。
 *
 * 画质档位与「已自动降档」是用户能看懂的状态（说明当前为什么变卡/变模糊），不属于调试读数，
 * 始终显示，不随 Debug 开关隐藏。
 */
object DebugReadout {

    /** 始终显示的一行：画质档位（自动降档时附原因提示）。 */
    fun qualityLine(quality: TerrainQuality, autoDowngraded: Boolean): String {
        val q = when (quality) {
            TerrainQuality.LOW -> "低"
            TerrainQuality.MEDIUM -> "中"
            else -> "高"
        }
        return "画质 $q" + if (autoDowngraded) "（已自动降档）" else ""
    }

    /** Debug 关闭时返回空列表 —— 调用方连 Text 都不该发射，这样才真的不占位。 */
    fun debugLines(
        enabled: Boolean,
        frameMs: Double,
        p95Ms: Double,
        stageLine: String,
        residualMs: Double
    ): List<String> {
        if (!enabled) return emptyList()
        return listOf(
            String.format("帧间隔 %.1f ms · P95 %.1f ms", frameMs, p95Ms),
            String.format("%s  合成%.1f  (ms)", stageLine, residualMs)
        )
    }

    /** 调试读数是否占用布局（与 [debugLines] 同源，测试用它断言「关闭时不留空白条」）。 */
    fun occupiesSpace(enabled: Boolean): Boolean = enabled
}
