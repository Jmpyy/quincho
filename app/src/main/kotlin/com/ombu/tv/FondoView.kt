package com.ombu.tv

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.hypot

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
        paint.shader = null

        trama?.let {
            pTrama.color = mezclar(tinte, crema, 0.45f)
            pTrama.alpha = 34
            canvas.drawBitmap(it, 0f, 0f, pTrama)
        }

        val rojoSuave = (rojo and 0x00FFFFFF) or (0x2A shl 24)
        paint.shader = RadialGradient(w * 0.10f, 0f, w * 0.45f,
            intArrayOf(rojoSuave, rojo and 0x00FFFFFF), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
    }

    private val crema = Color.parseColor("#efe8dc")
    private val pTrama = Paint(Paint.FILTER_BITMAP_FLAG)
    private var trama: Bitmap? = null

    /**
     * Trama de semitonos (puntos de imprenta) que crece hacia la esquina inferior derecha.
     * Se dibuja una sola vez en un bitmap ALPHA_8 (1 byte por píxel) y en cada cuadro solo se
     * pinta con el color del momento.
     */
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        trama?.recycle()
        trama = null
        if (w == 0 || h == 0) return

        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8)
        val lienzo = Canvas(bmp)
        val punto = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
        val paso = w / 1280f * 11f
        val alcance = w * 0.5f
        var fila = 0
        var y = 0f
        while (y < h + paso) {
            var x = if (fila % 2 == 0) 0f else paso / 2
            while (x < w + paso) {
                val d = hypot(w - x, h - y)
                val f = 1f - d / alcance
                if (f > 0f) {
                    val r = paso * 0.46f * f
                    if (r > 0.5f) lienzo.drawCircle(x, y, r, punto)
                }
                x += paso
            }
            y += paso * 0.866f
            fila++
        }
        trama = bmp
    }

    private fun mezclar(a: Int, b: Int, t: Float): Int {
        val r = (Color.red(a) * t + Color.red(b) * (1 - t)).toInt()
        val g = (Color.green(a) * t + Color.green(b) * (1 - t)).toInt()
        val bl = (Color.blue(a) * t + Color.blue(b) * (1 - t)).toInt()
        return Color.rgb(r, g, bl)
    }
}
