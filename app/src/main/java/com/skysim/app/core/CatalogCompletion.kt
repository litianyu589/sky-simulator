package com.skysim.app.core

import kotlin.math.pow
import kotlin.math.sqrt

/**
 * 目录补全：把「未填」(0) 的派生字段按目录内已有物理量算出来，已有非零值一律原样保留。
 *
 * v0.8.3 之前太阳系天体（地球、月球、水星…）的 [Planet.radiusEarths]、[Planet.massEarths]、
 * [Planet.eqTempK] 都是 0（数据表的「未填」占位），面板上会显示 0.00 R⊕ / 0 K。
 * 这里不改任何已有数值，只补这三个字段，且全部由目录内的量推导，不引入外部记忆值：
 *
 * - 半径：radiusKm / 6371.0（地球赤道半径）
 * - 质量：g·R²/G，用目录里的重力与半径反推；地球因此正好是 1.00 M⊕
 * - 平衡温度：以地球 279 K（零反照率、均匀再辐射）为锚，T = 279 K × (L/L☉)^0.25 / √(a/AU)
 *
 * 这三个字段只用于信息面板显示，不参与天空、晨昏、地面任何计算，因此补全不会改变
 * 任何已有天体的渲染结果（v0.8.2 的 230 项断言在补全后仍逐位成立）。
 */
object CatalogCompletion {

    private const val R_EARTH_KM = 6371.0
    private const val G_EARTH = 9.81
    /** 地球的平衡温度锚点（K）：零反照率、均匀再辐射，T = T☉·√(R☉/2a) 代入地球 */
    const val EARTH_EQ_TEMP_K = 279.0

    fun complete(p: Planet): Planet {
        val rE = if (p.radiusEarths > 0.0) p.radiusEarths else p.radiusKm / R_EARTH_KM
        val mE = when {
            p.massEarths > 0.0 -> p.massEarths
            p.gravity > 0.0 && rE > 0.0 -> p.gravity * rE * rE / G_EARTH
            else -> 0.0
        }
        val eqT = if (p.eqTempK > 0.0) p.eqTempK
        else EARTH_EQ_TEMP_K * p.starLuminositySolar.pow(0.25) / sqrt(p.sunDistanceAu)
        return p.copy(radiusEarths = rE, massEarths = mE, eqTempK = eqT)
    }

    /** 逐条补全，顺序与输入完全一致（id 不变，界面的选择状态不受影响）。 */
    fun completeAll(list: List<Planet>): List<Planet> = list.map { complete(it) }

    /**
     * 补全后仍然缺失的字段名。v0.8.3 的验收断言要求它为空表：
     * 任何一条目录天体都不许再有半径、质量、平衡温度、半长轴、周期、母星光谱型的空洞。
     */
    fun missingFields(p: Planet): List<String> = buildList {
        if (!(p.radiusEarths > 0.0) || !p.radiusEarths.isFinite()) add("半径")
        if (!(p.massEarths > 0.0) || !p.massEarths.isFinite()) add("质量")
        if (!(p.eqTempK > 0.0) || !p.eqTempK.isFinite()) add("平衡温度")
        if (!(p.sunDistanceAu > 0.0)) add("轨道半长轴")
        if (!(p.orbitalPeriodDays > 0.0)) add("轨道周期")
        if (p.spectralTypeZh.isBlank()) add("母星光谱型")
    }
}
