package com.ombu.tv

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

/**
 * Protector de pantalla: reloj y fecha sobre fondo casi negro que derivan muy despacio
 * por la pantalla, para no marcar el panel.
 */
class Reposo(context: Context, private val u: Float) : View(context) {

    private val locale = Locale("es", "AR")
    private val formatoHora = SimpleDateFormat("HH:mm", locale)
    private val formatoFecha = SimpleDateFormat("EEEE d 'de' MMMM", locale)

    private val pHora = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#efe8dc")
        alpha = 190
        textSize = 210 * u
        typeface = context.resources.getFont(R.font.bebas_neue)
        letterSpacing = 0.02f
        textAlign = Paint.Align.CENTER
    }
    private val pFecha = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#5fb0ae")
        alpha = 170
        textSize = 28 * u
        textAlign = Paint.Align.CENTER
    }

    private val handler = Handler(Looper.getMainLooper())
    private val inicio = SystemClock.elapsedRealtime()
    private val paso = object : Runnable {
        override fun run() {
            invalidate()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        handler.post(paso)
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(paso)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.parseColor("#0b0809"))
        val t = (SystemClock.elapsedRealtime() - inicio) / 1000f
        val cx = width / 2f + width * 0.18f * sin(t * 2 * PI.toFloat() / 240f)
        val cy = height / 2f + height * 0.18f * sin(t * 2 * PI.toFloat() / 170f)
        val ahora = Date()
        canvas.drawText(formatoHora.format(ahora), cx, cy, pHora)
        val fecha = formatoFecha.format(ahora).replaceFirstChar { it.uppercase() }
        canvas.drawText(fecha, cx, cy + 52 * u, pFecha)
    }
}
