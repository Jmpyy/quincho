package com.ombu.tv

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View
import android.view.animation.DecelerateInterpolator

/** Fondo Ombú: base oscura, un toque de rojo arriba y un resplandor teñido por la app enfocada. */
class FondoView(context: Context) : View(context) {

    private val base = Color.parseColor("#161011")
    private val rojo = Color.parseColor("#e23b32")
    private val paint = Paint()

    private var tinte = base
    private var animador: ValueAnimator? = null

    /** Cambia el color del resplandor con una transición suave de 600 ms. */
    fun setTinte(destino: Int) {
        val mezcla = mezclar(destino, base, 0.6f)
        animador?.cancel()
        animador = ValueAnimator.ofObject(ArgbEvaluator(), tinte, mezcla).apply {
            duration = 600
            interpolator = DecelerateInterpolator()
            addUpdateListener { tinte = it.animatedValue as Int; invalidate() }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawColor(base)

        paint.shader = RadialGradient(w * 0.70f, h * 0.85f, w * 0.75f,
            intArrayOf(tinte, tinte and 0x00FFFFFF), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)

        val rojoSuave = (rojo and 0x00FFFFFF) or (0x2A shl 24)
        paint.shader = RadialGradient(w * 0.10f, 0f, w * 0.45f,
            intArrayOf(rojoSuave, rojo and 0x00FFFFFF), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
    }

    private fun mezclar(a: Int, b: Int, t: Float): Int {
        val r = (Color.red(a) * t + Color.red(b) * (1 - t)).toInt()
        val g = (Color.green(a) * t + Color.green(b) * (1 - t)).toInt()
        val bl = (Color.blue(a) * t + Color.blue(b) * (1 - t)).toInt()
        return Color.rgb(r, g, bl)
    }
}
