package com.skysim.app.core

import java.util.concurrent.ConcurrentHashMap

/**
 * 天体 id 的唯一解析入口。
 *
 * v0.5 的 bug 就出在这里：界面把选中的 id 交给 `Planets.byId()`，而空间站是「按恒星临时生成」的，
 * 不在 [Planets.all] 里，于是 byId 静默回退到地球——用户点空间站看起来「点不动」。
 *
 * 现在所有「当前天体」都走 [resolve]：先在真实天体库里找，再按 `station:<恒星id>` 生成（并缓存），
 * 两条路径之外才回退，且回退目标写死为地球并可用 [resolveOrNull] 判定。
 */
object BodyCatalog {

    const val STATION_PREFIX = "station:"

    private val stations = ConcurrentHashMap<String, Planet>()

    fun isStationId(id: String): Boolean = id.startsWith(STATION_PREFIX)

    /** 某颗恒星的空间站（只有恒星、没有行星时才有）；同一 id 永远返回同一个对象 */
    fun stationFor(starId: String): Planet? {
        stations[starId]?.let { return it }
        val star = StarData.byId(starId)
        val station = StarCatalog.stationFor(star) ?: return null
        stations[starId] = station
        return station
    }

    /** 能解析就返回，解析不到返回 null（不静默替换） */
    fun resolveOrNull(id: String): Planet? {
        Planets.all.firstOrNull { it.id == id }?.let { return it }
        if (isStationId(id)) return stationFor(id.removePrefix(STATION_PREFIX))
        return null
    }

    /** 界面用：解析不到时退回地球（唯一兜底，且不会把空间站误判成地球） */
    fun resolve(id: String): Planet = resolveOrNull(id) ?: Planets.byId("earth")

    /** 清空缓存（测试用） */
    fun clearStationCache() = stations.clear()
}
