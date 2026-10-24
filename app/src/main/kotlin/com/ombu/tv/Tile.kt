package com.ombu.tv

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.View

/**
 * Tarjeta apaisada de una app. Dibuja ella misma el borde entintado, la sombra dura
 * (negra en reposo, roja con foco) y el banner recortado con esquinas redondeadas.
 */
class Tile(
    context: Context,
    private val u: Float,
    val etiqueta: String,
    private val imagen: Drawable?,
    private val colorMarca: Int,
    private val esBanner: Boolean,
    val intent: Intent,
    private val esAjustes: Boolean = false,
) : View(context) {

    var alEnfocar: ((Tile) -> Unit)? = null
    var tinte: Int = colorMarca

    private val rojo = Color.parseColor("#e23b32")
    private val crema = Color.parseColor("#efe8dc")
    private val relleno = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trazo = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rect = RectF()
    private val recorte = Path()

    init {
        isFocusable = true
        isClickable = true
        contentDescription = etiqueta
        setOnClickListener { alLanzar?.invoke(this) }
    }

    var alLanzar: ((Tile) -> Unit)? = null

    /** Mantener OK presionado. Si no se asigna, la tarjeta no reacciona al toque largo. */
    var alMantener: ((Tile) -> Unit)? = null
        set(valor) {
            field = valor
            isLongClickable = valor != null
            setOnLongClickListener(if (valor != null) OnLongClickListener { valor(this); true } else null)
        }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: android.graphics.Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        animate().cancel()
        if (gainFocus) {
            animate().scaleX(1.1f).scaleY(1.1f).translationX(-3 * u).translationY(-3 * u)
                .setDuration(180).start()
            translationZ = 10f
            alEnfocar?.invoke(this)
        } else {
            animate().scaleX(1f).scaleY(1f).translationX(0f).translationY(0f)
                .setDuration(180).start()
            translationZ = 0f
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val radio = 12 * u
        val borde = 2 * u
        val enfocada = isFocused

        // sombra dura a 45°
        val d = (if (enfocada) 8 else 4) * u
        relleno.color = if (enfocada) rojo else Color.BLACK
        rect.set(d, d, w + d, h + d)
        canvas.drawRoundRect(rect, radio, radio, relleno)

        // contenido recortado
        rect.set(0f, 0f, w, h)
        recorte.reset()
        recorte.addRoundRect(rect, radio, radio, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(recorte)
        if (esBanner && imagen != null) {
            imagen.setBounds(0, 0, w.toInt(), h.toInt())
            imagen.draw(canvas)
        } else {
            canvas.drawColor(colorMarca)
            if (esAjustes) dibujarTuerca(canvas, w / 2, h / 2) else dibujarIcono(canvas, w, h)
        }
        canvas.restore()

        // borde entintado
        trazo.strokeWidth = borde
        trazo.color = if (enfocada) crema else Color.BLACK
        rect.set(borde / 2, borde / 2, w - borde / 2, h - borde / 2)
        canvas.drawRoundRect(rect, radio, radio, trazo)
    }

    private fun dibujarIcono(canvas: Canvas, w: Float, h: Float) {
        val img = imagen ?: return
        val lado = (h * 0.55f).toInt()
        val x = (w / 2 - lado / 2).toInt()
        val y = (h / 2 - lado / 2).toInt()
        img.setBounds(x, y, x + lado, y + lado)
        img.draw(canvas)
    }

    /** Engranaje simple: anillo con ocho dientes. */
    private fun dibujarTuerca(canvas: Canvas, cx: Float, cy: Float) {
        trazo.color = crema
        trazo.strokeCap = Paint.Cap.ROUND
        trazo.strokeWidth = 6 * u
        canvas.drawCircle(cx, cy, 15 * u, trazo)
        trazo.strokeWidth = 7 * u
        for (i in 0 until 8) {
            val a = Math.toRadians(i * 45.0)
            val c = Math.cos(a).toFloat()
            val s = Math.sin(a).toFloat()
            canvas.drawLine(cx + c * 21 * u, cy + s * 21 * u, cx + c * 27 * u, cy + s * 27 * u, trazo)
        }
        trazo.strokeCap = Paint.Cap.BUTT
    }
}
