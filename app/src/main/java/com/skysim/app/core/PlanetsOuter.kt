package com.skysim.app.core

/**
 * v0.4 新增的 10 个外太阳系天体。
 * 数值取自公开天文常数与任务结果（近似值）；没有大气的天体一律 pressureAtm≈0、光学厚度为 0，
 * 因此它们在渲染时不会出现云、雨、虹、晕，天空接近纯黑、太阳（母星）是一个刺眼的小点。
 */
object OuterBodies {

    val all: List<Planet> = listOf(
        Planet(
            id = "enceladus", nameZh = "土卫二", nameEn = "Enceladus", kindZh = "土星卫星",
            radiusKm = 252.1, siderealPeriodOverrideSeconds = 118368.0000,
            orbitalPeriodDays = 1.370, heliocentricPeriodDays = 10759.22, axialTiltDeg = 0.0, gravity = 0.113,
            pressureAtm = 1e-11, scaleHeightKm = 10.0, compositionZh = "水汽羽流（近乎真空）",
            sunDistanceAu = 9.583, rayTau = 0.0, aerosolTau = 0.04,
            aerosolTint = Rgb(0.92f, 0.96f, 1f), groundTint = Rgb(0.92f, 0.94f, 0.96f),
            nightTint = Rgb(0.002f, 0.003f, 0.006f), surfaceKind = SurfaceKind.ICE,
            seasonPhase = 0.2f, skyBasisZh = "无实质大气：天空近黑，只有冰面反照"
        ),
        Planet(
            id = "iapetus", nameZh = "土卫八", nameEn = "Iapetus", kindZh = "土星卫星",
            radiusKm = 734.5, siderealPeriodOverrideSeconds = 6854400.0000,
            orbitalPeriodDays = 79.33, heliocentricPeriodDays = 10759.22, axialTiltDeg = 0.0, gravity = 0.223,
            pressureAtm = 0.0, scaleHeightKm = 1.0, compositionZh = "无大气",
            sunDistanceAu = 9.583, rayTau = 0.0, aerosolTau = 0.0,
            aerosolTint = Rgb(0.8f, 0.8f, 0.8f), groundTint = Rgb(0.34f, 0.31f, 0.28f),
            nightTint = Rgb(0.002f, 0.002f, 0.003f), surfaceKind = SurfaceKind.ICE,
            seasonPhase = 0.25f, skyBasisZh = "无大气：天空全黑，昼夜两面明暗反差极大"
        ),
        Planet(
            id = "titania", nameZh = "天卫三", nameEn = "Titania", kindZh = "天王星卫星",
            radiusKm = 788.4, siderealPeriodOverrideSeconds = 752184.0000,
            orbitalPeriodDays = 8.706, heliocentricPeriodDays = 30688.5, axialTiltDeg = 0.0, gravity = 0.367,
            pressureAtm = 0.0, scaleHeightKm = 1.0, compositionZh = "无大气",
            sunDistanceAu = 19.19, rayTau = 0.0, aerosolTau = 0.0,
            aerosolTint = Rgb(0.8f, 0.8f, 0.82f), groundTint = Rgb(0.70f, 0.70f, 0.72f),
            nightTint = Rgb(0.001f, 0.001f, 0.002f), surfaceKind = SurfaceKind.ICE,
            seasonPhase = 0.4f, skyBasisZh = "无大气：天空全黑"
        ),
        Planet(
            id = "miranda", nameZh = "天卫五", nameEn = "Miranda", kindZh = "天王星卫星",
            radiusKm = 235.8, siderealPeriodOverrideSeconds = 122076.0000,
            orbitalPeriodDays = 1.413, heliocentricPeriodDays = 30688.5, axialTiltDeg = 0.0, gravity = 0.079,
            pressureAtm = 0.0, scaleHeightKm = 1.0, compositionZh = "无大气",
            sunDistanceAu = 19.19, rayTau = 0.0, aerosolTau = 0.0,
            aerosolTint = Rgb(0.8f, 0.8f, 0.82f), groundTint = Rgb(0.66f, 0.66f, 0.68f),
            nightTint = Rgb(0.001f, 0.001f, 0.002f), surfaceKind = SurfaceKind.ICE,
            seasonPhase = 0.42f, skyBasisZh = "无大气：天空全黑"
        ),
        Planet(
            id = "charon", nameZh = "冥卫一", nameEn = "Charon", kindZh = "冥王星卫星",
            radiusKm = 606.0, siderealPeriodOverrideSeconds = 551844.0000,
            orbitalPeriodDays = 6.387, heliocentricPeriodDays = 90560.0, axialTiltDeg = 0.0, gravity = 0.288,
            pressureAtm = 0.0, scaleHeightKm = 1.0, compositionZh = "无大气",
            sunDistanceAu = 39.48, rayTau = 0.0, aerosolTau = 0.0,
            aerosolTint = Rgb(0.8f, 0.8f, 0.8f), groundTint = Rgb(0.56f, 0.54f, 0.53f),
            nightTint = Rgb(0.001f, 0.001f, 0.001f), surfaceKind = SurfaceKind.ICE,
            seasonPhase = 0.55f, skyBasisZh = "无大气：天空全黑，与冥王星互为潮汐锁定"
        ),
        Planet(
            id = "ceres", nameZh = "谷神星", nameEn = "Ceres", kindZh = "矮行星",
            radiusKm = 469.7, siderealPeriodOverrideSeconds = 32666.4000,
            orbitalPeriodDays = 1681.6, axialTiltDeg = 4.0, gravity = 0.28,
            pressureAtm = 0.0, scaleHeightKm = 1.0, compositionZh = "水汽（瞬变，极稀薄）",
            sunDistanceAu = 2.767, rayTau = 0.0, aerosolTau = 0.0,
            aerosolTint = Rgb(0.85f, 0.85f, 0.85f), groundTint = Rgb(0.44f, 0.42f, 0.40f),
            nightTint = Rgb(0.002f, 0.002f, 0.003f), surfaceKind = SurfaceKind.ROCK,
            seasonPhase = 0.1f, skyBasisZh = "几乎没有大气：天空全黑"
        ),
        Planet(
            id = "vesta", nameZh = "灶神星", nameEn = "Vesta", kindZh = "小行星",
            radiusKm = 262.7, siderealPeriodOverrideSeconds = 19231.2000,
            orbitalPeriodDays = 1325.9, axialTiltDeg = 29.6, gravity = 0.22,
            pressureAtm = 0.0, scaleHeightKm = 1.0, compositionZh = "无大气",
            sunDistanceAu = 2.362, rayTau = 0.0, aerosolTau = 0.0,
            aerosolTint = Rgb(0.82f, 0.8f, 0.78f), groundTint = Rgb(0.48f, 0.45f, 0.42f),
            nightTint = Rgb(0.002f, 0.002f, 0.003f), surfaceKind = SurfaceKind.ROCK,
            seasonPhase = 0.05f, skyBasisZh = "无大气：天空全黑"
        ),
        Planet(
            id = "eris", nameZh = "阋神星", nameEn = "Eris", kindZh = "矮行星",
            radiusKm = 1163.0, siderealPeriodOverrideSeconds = 1364400.0000,
            orbitalPeriodDays = 203_830.0, axialTiltDeg = 78.0, gravity = 0.82,
            pressureAtm = 1e-9, scaleHeightKm = 30.0, compositionZh = "氮 · 甲烷（季节性稀薄）",
            sunDistanceAu = 67.8, rayTau = 0.0, aerosolTau = 0.02,
            aerosolTint = Rgb(0.86f, 0.86f, 0.9f), groundTint = Rgb(0.86f, 0.86f, 0.88f),
            nightTint = Rgb(0.001f, 0.001f, 0.002f), surfaceKind = SurfaceKind.ICE,
            seasonPhase = 0.7f, skyBasisZh = "极稀薄季节性大气：天空近黑，仅地平线有极弱辉光"
        ),
        Planet(
            id = "makemake", nameZh = "鸟神星", nameEn = "Makemake", kindZh = "矮行星",
            radiusKm = 715.0, siderealPeriodOverrideSeconds = 82188.0000,
            orbitalPeriodDays = 112_897.0, axialTiltDeg = 0.0, gravity = 0.5,
            pressureAtm = 0.0, scaleHeightKm = 1.0, compositionZh = "无显著大气（甲烷霜）",
            sunDistanceAu = 45.4, rayTau = 0.0, aerosolTau = 0.0,
            aerosolTint = Rgb(0.85f, 0.8f, 0.78f), groundTint = Rgb(0.62f, 0.52f, 0.46f),
            nightTint = Rgb(0.001f, 0.001f, 0.001f), surfaceKind = SurfaceKind.ICE,
            seasonPhase = 0.6f, skyBasisZh = "无大气：天空全黑"
        ),
        Planet(
            id = "haumea", nameZh = "妊神星", nameEn = "Haumea", kindZh = "矮行星",
            radiusKm = 715.0, siderealPeriodOverrideSeconds = 14094.0000,
            orbitalPeriodDays = 103_410.0, axialTiltDeg = 0.0, gravity = 0.44,
            pressureAtm = 0.0, scaleHeightKm = 1.0, compositionZh = "无大气（水冰表面）",
            sunDistanceAu = 43.1, rayTau = 0.0, aerosolTau = 0.0,
            aerosolTint = Rgb(0.88f, 0.9f, 0.94f), groundTint = Rgb(0.72f, 0.72f, 0.75f),
            nightTint = Rgb(0.001f, 0.001f, 0.001f), surfaceKind = SurfaceKind.ICE,
            seasonPhase = 0.58f, skyBasisZh = "无大气：天空全黑（快速自转，3.9 小时一转）"
        )
    )
}
