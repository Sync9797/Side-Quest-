package com.monsterquest.app.ui.art

import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import com.monsterquest.core.Mythic
import com.monsterquest.core.Species

/**
 * The hidden legendary's four forms (Murklet, Cinderwrithe, Fumewarden and
 * Turumarth), drawn from [MythicArtData]: the approved mockups, shape for
 * shape, in a 200x200 box.
 */
internal object MythicArt {
    private class Op(
        val paint: Paint,
        val matrix: Matrix?,
        val d: String,
    )

    private val parsed = HashMap<Int, List<Op>>()

    private fun color(hex: String, alpha: Float): Int =
        ((alpha.coerceIn(0f, 1f) * 255).toInt() shl 24) or hex.removePrefix("#").toInt(16)

    private fun shader(spec: String): Shader {
        val (geo, stopText) = spec.substring(1).split(';')
        val g = geo.split(',').map { it.toFloat() }
        val stops = stopText.split(',').map { it.split(':') }
        val positions = stops.map { it[0].toFloat() }.toFloatArray()
        val colors = stops.map { color(it[1], it[2].toFloat()) }.toIntArray()
        return if (spec[0] == 'L') {
            LinearGradient(g[0], g[1], g[2], g[3], colors, positions, Shader.TileMode.CLAMP)
        } else {
            RadialGradient(g[0], g[1], g[2].coerceAtLeast(0.01f), colors, positions, Shader.TileMode.CLAMP)
        }
    }

    private fun parse(text: String): List<Op> = text.split('\n').filter { it.isNotBlank() }.map { line ->
        val f = line.split('|', limit = 8)
        val alpha = f[1].toFloat()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = if (f[0] == "s") Paint.Style.STROKE else Paint.Style.FILL
            strokeJoin = Paint.Join.ROUND
            if (f[2].startsWith("#")) {
                color = color(f[2], alpha)
            } else {
                color = color("#ffffff", alpha)
                shader = shader(f[2])
            }
            if (f[0] == "s") {
                strokeWidth = f[3].toFloat()
                strokeCap = when (f[4]) { "r" -> Paint.Cap.ROUND; "s" -> Paint.Cap.SQUARE; else -> Paint.Cap.BUTT }
                if (f[5].isNotEmpty()) {
                    val dash = f[5].split(',').map { it.toFloat() }
                    pathEffect = DashPathEffect((if (dash.size % 2 == 1) dash + dash else dash).toFloatArray(), 0f)
                }
            }
        }
        val matrix = f[6].takeIf { it.isNotEmpty() }?.split(',')?.map { it.toFloat() }?.let { m ->
            Matrix().apply { setValues(floatArrayOf(m[0], m[2], m[4], m[1], m[3], m[5], 0f, 0f, 1f)) }
        }
        Op(paint, matrix, f[7])
    }

    private fun ops(stage: Int): List<Op> = synchronized(parsed) {
        parsed.getOrPut(stage) { parse(MythicArtData.stages[stage]) }
    }

    /** Fainted: grey. */
    private val dimPaint = Paint().apply { colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0.1f) }) }

    /** Shiny: the colours turned a third of the way round the wheel. */
    private val shinyPaint = Paint().apply { colorFilter = ColorMatrixColorFilter(hueRotation(120f)) }

    /** The standard hue-rotation colour matrix. */
    private fun hueRotation(deg: Float): ColorMatrix {
        val r = Math.toRadians(deg.toDouble())
        val cos = kotlin.math.cos(r).toFloat()
        val sin = kotlin.math.sin(r).toFloat()
        val lr = 0.213f; val lg = 0.715f; val lb = 0.072f
        return ColorMatrix(floatArrayOf(
            lr + cos * (1 - lr) + sin * -lr, lg + cos * -lg + sin * -lg, lb + cos * -lb + sin * (1 - lb), 0f, 0f,
            lr + cos * -lr + sin * 0.143f, lg + cos * (1 - lg) + sin * 0.140f, lb + cos * -lb + sin * -0.283f, 0f, 0f,
            lr + cos * -lr + sin * -(1 - lr), lg + cos * -lg + sin * lg, lb + cos * (1 - lb) + sin * lb, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ))
    }

    fun draw(c: Canvas, w: Float, h: Float, sp: Species, dim: Boolean, shiny: Boolean) {
        val stage = Mythic.LINE.indexOf(sp.id).coerceAtLeast(0)
        c.save()
        ArtPainter.fit(c, w, h, 200f, 200f)
        val layer = when {
            dim -> c.saveLayer(0f, 0f, 200f, 200f, dimPaint)
            shiny -> c.saveLayer(0f, 0f, 200f, 200f, shinyPaint)
            else -> -1
        }
        for (op in ops(stage)) {
            if (op.matrix != null) {
                c.save()
                c.concat(op.matrix)
            }
            c.drawPath(ArtPainter.path(op.d), op.paint)
            if (op.matrix != null) c.restore()
        }
        if (layer >= 0) c.restoreToCount(layer)
        c.restore()
    }
}
