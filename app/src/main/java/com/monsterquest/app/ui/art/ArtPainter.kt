package com.monsterquest.app.ui.art

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.core.graphics.PathParser
import kotlin.math.min

/**
 * Low-level vector drawing on an Android [Canvas] from SVG path data, so the
 * same artwork renders in Compose (via nativeCanvas) and as map-marker bitmaps.
 */
/** Performance setting: when [reduced], glows, engraving, rune circles and auras are skipped. */
object ArtQuality {
    @Volatile
    var reduced: Boolean = false

    /**
     * Running under the screenshot tests: endless motion (idle monsters, the
     * buddy's trot) stays still there, so each screen settles and can be captured.
     */
    val stillForTests: Boolean = android.os.Build.FINGERPRINT == "robolectric"
}

internal object ArtPainter {
    private val paths = HashMap<String, Path>()
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    fun path(d: String): Path = paths.getOrPut(d) { PathParser.createPathFromPathData(d) }

    /** Scales and centers a [vbW]x[vbH] viewBox into a [w]x[h] area. */
    fun fit(c: Canvas, w: Float, h: Float, vbW: Float, vbH: Float) {
        val s = min(w / vbW, h / vbH)
        c.translate((w - vbW * s) / 2f, (h - vbH * s) / 2f)
        c.scale(s, s)
    }

    /** Like [fit], but for the sub-rectangle ([x], [y], [vbW], [vbH]) of the artwork. */
    fun fitRegion(c: Canvas, w: Float, h: Float, x: Float, y: Float, vbW: Float, vbH: Float) {
        fit(c, w, h, vbW, vbH)
        c.translate(-x, -y)
    }

    fun fill(c: Canvas, d: String, color: Int, alpha: Float = 1f) {
        fillPaint.shader = null
        fillPaint.color = color
        fillPaint.alpha = (Argb.alphaOf(color) * alpha).toInt()
        c.drawPath(path(d), fillPaint)
    }

    fun fill(c: Canvas, d: String, shader: Shader) {
        fillPaint.color = 0xFFFFFFFF.toInt()
        fillPaint.shader = shader
        c.drawPath(path(d), fillPaint)
        fillPaint.shader = null
    }

    fun fill(c: Canvas, p: Path, color: Int, alpha: Float = 1f) {
        fillPaint.shader = null
        fillPaint.color = color
        fillPaint.alpha = (Argb.alphaOf(color) * alpha).toInt()
        c.drawPath(p, fillPaint)
    }

    fun fill(c: Canvas, p: Path, shader: Shader) {
        fillPaint.color = 0xFFFFFFFF.toInt()
        fillPaint.shader = shader
        c.drawPath(p, fillPaint)
        fillPaint.shader = null
    }

    fun stroke(c: Canvas, p: Path, color: Int, width: Float, alpha: Float = 1f) {
        strokePaint.color = color
        strokePaint.alpha = (Argb.alphaOf(color) * alpha).toInt()
        strokePaint.strokeWidth = width
        c.drawPath(p, strokePaint)
    }

    /** [hatch] for an already-built path. */
    fun hatch(c: Canvas, p: Path, color: Int, alpha: Float, spacing: Float = 6f) {
        if (ArtQuality.reduced) return
        val b = android.graphics.RectF().also { p.computeBounds(it, true) }
        c.save()
        c.clipPath(p)
        strokePaint.color = color
        strokePaint.alpha = (Argb.alphaOf(color) * alpha).toInt()
        strokePaint.strokeWidth = 1f
        var x = b.left - b.height()
        while (x < b.right) {
            c.drawLine(x, b.bottom, x + b.height(), b.top, strokePaint)
            x += spacing
        }
        c.restore()
    }

    fun stroke(c: Canvas, d: String, color: Int, width: Float, alpha: Float = 1f) {
        strokePaint.color = color
        strokePaint.alpha = (Argb.alphaOf(color) * alpha).toInt()
        strokePaint.strokeWidth = width
        c.drawPath(path(d), strokePaint)
    }

    fun circle(c: Canvas, cx: Float, cy: Float, r: Float, color: Int, alpha: Float = 1f) {
        fillPaint.shader = null
        fillPaint.color = color
        fillPaint.alpha = (Argb.alphaOf(color) * alpha).toInt()
        c.drawCircle(cx, cy, r, fillPaint)
    }

    fun ringStroke(c: Canvas, cx: Float, cy: Float, r: Float, color: Int, width: Float, alpha: Float = 1f) {
        strokePaint.color = color
        strokePaint.alpha = (Argb.alphaOf(color) * alpha).toInt()
        strokePaint.strokeWidth = width
        c.drawCircle(cx, cy, r, strokePaint)
    }

    fun glow(c: Canvas, cx: Float, cy: Float, r: Float, color: Int, alpha: Float) {
        if (ArtQuality.reduced) return
        val a = (alpha * 255).toInt().coerceIn(0, 255)
        val inner = (color and 0x00FFFFFF) or (a shl 24)
        val shader = RadialGradient(cx, cy, r, intArrayOf(inner, color and 0x00FFFFFF), null, Shader.TileMode.CLAMP)
        fillPaint.color = 0xFFFFFFFF.toInt()
        fillPaint.shader = shader
        c.drawCircle(cx, cy, r, fillPaint)
        fillPaint.shader = null
    }

    private val boundsCache = HashMap<String, android.graphics.RectF>()

    fun bounds(d: String): android.graphics.RectF =
        boundsCache.getOrPut(d) { android.graphics.RectF().also { path(d).computeBounds(it, true) } }

    /** Engraving-style diagonal hatching inside the shape [d], like an old storybook illustration. */
    fun hatch(c: Canvas, d: String, color: Int, alpha: Float, spacing: Float = 6f) {
        if (ArtQuality.reduced) return
        val b = bounds(d)
        c.save()
        c.clipPath(path(d))
        strokePaint.color = color
        strokePaint.alpha = (Argb.alphaOf(color) * alpha).toInt()
        strokePaint.strokeWidth = 1f
        var x = b.left - b.height()
        while (x < b.right) {
            c.drawLine(x, b.bottom, x + b.height(), b.top, strokePaint)
            x += spacing
        }
        c.restore()
    }

    /** A carved circle of runes on the ground, as an ellipse ([rx], [ry]) around ([cx], [cy]). */
    fun runeCircle(c: Canvas, cx: Float, cy: Float, rx: Float, ry: Float, color: Int, alpha: Float, glyphs: Int = 12) {
        if (ArtQuality.reduced) return
        strokePaint.color = color
        strokePaint.alpha = (Argb.alphaOf(color) * alpha).toInt()
        strokePaint.strokeWidth = 1.4f
        c.drawOval(cx - rx, cy - ry, cx + rx, cy + ry, strokePaint)
        strokePaint.strokeWidth = 0.9f
        c.drawOval(cx - rx * 0.86f, cy - ry * 0.86f, cx + rx * 0.86f, cy + ry * 0.86f, strokePaint)
        // Small angular glyphs between the two rings.
        val mid = 0.93f
        for (i in 0 until glyphs) {
            val a = Math.PI * 2 * i / glyphs
            val gx = cx + (rx * mid * Math.cos(a)).toFloat()
            val gy = cy + (ry * mid * Math.sin(a)).toFloat()
            val s = 2.2f
            when (i % 3) {
                0 -> { c.drawLine(gx, gy - s, gx, gy + s, strokePaint); c.drawLine(gx, gy - s, gx + s, gy, strokePaint) }
                1 -> { c.drawLine(gx - s, gy + s, gx, gy - s, strokePaint); c.drawLine(gx, gy - s, gx + s, gy + s, strokePaint) }
                else -> { c.drawLine(gx - s, gy, gx + s, gy, strokePaint); c.drawLine(gx, gy - s, gx, gy + s, strokePaint) }
            }
        }
    }

    fun linear(x0: Float, y0: Float, x1: Float, y1: Float, from: Int, to: Int): Shader =
        LinearGradient(x0, y0, x1, y1, from, to, Shader.TileMode.CLAMP)
}

/** Color helpers on packed ARGB ints. */
internal object Argb {
    fun alphaOf(c: Int) = (c ushr 24) and 0xFF

    fun hex(s: String): Int = (0xFF000000.toInt()) or s.removePrefix("#").toInt(16)

    /** Mix [c] toward black by [amount] (0..1). */
    fun darken(c: Int, amount: Float): Int = mix(c, 0xFF000000.toInt(), amount)

    fun mix(a: Int, b: Int, t: Float): Int {
        fun ch(x: Int, shift: Int) = (x shr shift) and 0xFF
        fun lerp(x: Int, y: Int) = (x + (y - x) * t).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or
            (lerp(ch(a, 16), ch(b, 16)) shl 16) or
            (lerp(ch(a, 8), ch(b, 8)) shl 8) or
            lerp(ch(a, 0), ch(b, 0))
    }
}
