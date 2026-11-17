package com.skysim.app.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * v0.7.2：整片天空的栅格缓冲。
 *
 * 真机实测（v0.7.1，地球低纬 600× 推进，画质已降到低）帧间隔 176.6 ms、P95 242.4 ms。
 * 天空原来每帧要建 200 多个 Compose 渐变 Brush 并 drawRect 200 多次，每个 Brush 都会生成一个
 * 线性渐变着色器 —— 这是量级上的浪费，不是调参能救的。
 *
 * 改成：把每一行插值成 64 个像素写进一张小位图（原生 Bitmap，可复用，零分配），
 * 再由一次 drawImage 双线性放大到全屏。视觉上与原渐变等价（渐变本身就是线性插值），
 * 但每帧的着色器数量从 200+ 降到 1。
 */
class SkyRaster(var width: Int, var height: Int) {
    var pixels = IntArray(width * height)
        private set
    var bitmap: Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        private set
    var image: ImageBitmap = bitmap.asImageBitmap()
        private set

    fun ensure(w: Int, h: Int) {
        if (w == width && h == height) return
        width = w
        height = h
        pixels = IntArray(w * h)
        bitmap.recycle()
        bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        image = bitmap.asImageBitmap()
    }

    /** 把一行里 n 个已排序的 stop 线性插值成 width 个像素。 */
    fun fillRow(row: Int, n: Int, xs: FloatArray, argb: IntArray, screenW: Float) {
        if (n <= 0) return
        val base = row * width
        if (n == 1) {
            val c = argb[0]
            for (k in 0 until width) pixels[base + k] = c
            return
        }
        var seg = 0
        for (k in 0 until width) {
            val x = (k + 0.5f) / width * screenW
            while (seg < n - 2 && x > xs[seg + 1]) seg++
            val x0 = xs[seg]
            val x1 = xs[seg + 1]
            val t = if (x1 > x0) ((x - x0) / (x1 - x0)).coerceIn(0f, 1f) else 0f
            val c0 = argb[seg]
            val c1 = argb[seg + 1]
            // v0.7.3：逐像素 4×4 Bayer 抖动（±0.5 色阶）。行与行的均值不再跳变，
            // 横条纹消失；抖动量本身低于 1 个色阶，肉眼不可见。
            val d = com.skysim.app.core.Dither.offset(row, k) * 255f
            val r = (((c0 shr 16) and 0xFF) * (1 - t) + ((c1 shr 16) and 0xFF) * t + d).toInt().coerceIn(0, 255)
            val g = (((c0 shr 8) and 0xFF) * (1 - t) + ((c1 shr 8) and 0xFF) * t + d).toInt().coerceIn(0, 255)
            val b = ((c0 and 0xFF) * (1 - t) + (c1 and 0xFF) * t + d).toInt().coerceIn(0, 255)
            pixels[base + k] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
    }

    fun upload() { bitmap.setPixels(pixels, 0, width, 0, 0, width, height) }
}
