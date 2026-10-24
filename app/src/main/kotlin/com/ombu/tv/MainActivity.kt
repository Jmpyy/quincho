package com.ombu.tv

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.view.KeyEvent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Scroll horizontal que no se mueve solo al enfocar: la posición la decide la actividad. */
private class FilaScroll(ctx: android.content.Context) : HorizontalScrollView(ctx) {
    override fun computeScrollDeltaToGetChildRectOnScreen(rect: Rect?): Int = 0
}

private class Entrada(val paquete: String, val nombre: String, val color: Int)

class MainActivity : Activity() {

    // Orden fijo de la fila. Si una app no está instalada, se omite.
    private val entradas = listOf(
        Entrada("com.netflix.ninja", "Netflix", Color.parseColor("#c0111a")),
        Entrada("com.google.android.youtube.tv", "YouTube", Color.parseColor("#d9142b")),
        Entrada("com.amazon.amazonvideo.livingroom", "Prime Video", Color.parseColor("#0b7fb0")),
        Entrada("com.disney.disneyplus", "Disney+", Color.parseColor("#1b3a9e")),
        Entrada("com.wbd.stream", "Max", Color.parseColor("#4b1fa0")),
        Entrada("com.crunchyroll.crunchyroid", "Crunchyroll", Color.parseColor("#d9641a")),
        Entrada("com.valvesoftware.steamlink", "Steam Link", Color.parseColor("#1d4a66")),
    )

    private val crema = Color.parseColor("#efe8dc")
    private val cian = Color.parseColor("#5fb0ae")

    private var u = 1f
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var raiz: FrameLayout
    private lateinit var fondo: FondoView
    private lateinit var hora: TextView
    private lateinit var fecha: TextView
    private lateinit var clima: TextView
    private lateinit var avisoRed: TextView
    private var reposo: Reposo? = null
    private var hayRed = true
    private val prefs by lazy { getSharedPreferences("ombu", MODE_PRIVATE) }

    private val entrarReposo = Runnable {
        if (reposo == null) {
            reposo = Reposo(this, u).also { raiz.addView(it, FrameLayout.LayoutParams(-1, -1)) }
        }
    }

    private val callbackRed = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(red: Network, caps: NetworkCapabilities) {
            mostrarRed(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
        }
        override fun onLost(red: Network) = mostrarRed(false)
    }
    private lateinit var nombre: TextView
    private lateinit var scroll: FilaScroll
    private lateinit var fila: LinearLayout

    private var temperatura: String? = null
    private var firmaInstaladas = ""
    private val formatoHora = SimpleDateFormat("HH:mm", Locale.getDefault())

    private val formatoFecha = SimpleDateFormat("EEEE d 'de' MMMM", Locale("es", "AR"))

    private val tictac = object : Runnable {
        override fun run() {
            val ahora = Date()
            hora.text = formatoHora.format(ahora)
            fecha.text = formatoFecha.format(ahora).replaceFirstChar { it.uppercase() }
            handler.postDelayed(this, 15_000)
        }
    }
    private val refrescarClima = object : Runnable {
        override fun run() {
            Thread { pedirClima() }.start()
            handler.postDelayed(this, 15 * 60_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        u = resources.displayMetrics.widthPixels / 1280f

        raiz = FrameLayout(this)
        fondo = FondoView(this)
        raiz.addView(fondo, FrameLayout.LayoutParams(-1, -1))

        hora = texto(88f, crema, bold = true)
        fecha = texto(22f, crema).apply { alpha = 0.75f }
        clima = texto(22f, cian).apply { setPadding(0, (4 * u).toInt(), 0, 0) }
        val columna = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(hora)
            addView(fecha)
            addView(clima)
        }
        raiz.addView(columna, FrameLayout.LayoutParams(-2, -2).apply {
            leftMargin = (64 * u).toInt()
            topMargin = (44 * u).toInt()
        })

        scroll = FilaScroll(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = android.view.View.OVER_SCROLL_NEVER
            clipChildren = false
            clipToPadding = false
        }
        fila = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
            val lado = (64 * u).toInt()
            val vert = (24 * u).toInt()
            setPadding(lado, vert, lado, vert)
        }
        scroll.addView(fila, ViewGroup.LayoutParams(-2, -2))
        raiz.addView(scroll, FrameLayout.LayoutParams(-1, (170 * u).toInt(), Gravity.BOTTOM).apply {
            bottomMargin = (72 * u).toInt()
        })

        avisoRed = texto(20f, Color.parseColor("#e23b32")).apply {
            text = "●  Sin internet"
            visibility = android.view.View.GONE
        }
        raiz.addView(avisoRed, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply {
            rightMargin = (64 * u).toInt()
            topMargin = (52 * u).toInt()
        })

        nombre = texto(20f, crema)
        raiz.addView(nombre, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START).apply {
            leftMargin = (64 * u).toInt()
            bottomMargin = (36 * u).toInt()
        })

        setContentView(raiz)
    }

    override fun onStart() {
        super.onStart()
        construirFila()
        tictac.run()
        refrescarClima.run()
        getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(callbackRed)
        programarReposo()
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(tictac)
        handler.removeCallbacks(refrescarClima)
        handler.removeCallbacks(entrarReposo)
        try {
            getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(callbackRed)
        } catch (_: IllegalArgumentException) {
        }
        salirReposo()
    }

    /** Home dentro del launcher: sale del protector y vuelve al primer ícono. */
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        salirReposo()
        programarReposo()
        fila.getChildAt(0)?.requestFocus()
    }

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        if (reposo != null) {
            // La tecla que despierta el protector no llega a la fila.
            if (e.action == KeyEvent.ACTION_DOWN) {
                salirReposo()
                programarReposo()
            }
            return true
        }
        programarReposo()
        return super.dispatchKeyEvent(e)
    }

    private fun programarReposo() {
        handler.removeCallbacks(entrarReposo)
        handler.postDelayed(entrarReposo, 3 * 60_000L)
    }

    private fun salirReposo() {
        reposo?.let { raiz.removeView(it) }
        reposo = null
    }

    private fun mostrarRed(ok: Boolean) {
        runOnUiThread {
            val volvio = ok && !hayRed
            hayRed = ok
            avisoRed.visibility = if (ok) android.view.View.GONE else android.view.View.VISIBLE
            if (volvio) Thread { pedirClima() }.start()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Es el home: Atrás no hace nada.
    }

    private fun construirFila() {
        val instaladas = entradasActivas().filter { packageManager.getLaunchIntentForPackage(it.paquete) != null ||
            packageManager.getLeanbackLaunchIntentForPackage(it.paquete) != null }
        val firma = instaladas.joinToString(",") { it.paquete }
        if (firma == firmaInstaladas && fila.childCount > 0) return
        firmaInstaladas = firma

        fila.removeAllViews()
        val alto = (112 * u).toInt()
        val separacion = (22 * u).toInt()

        for (e in instaladas) {
            val intent = packageManager.getLeanbackLaunchIntentForPackage(e.paquete)
                ?: packageManager.getLaunchIntentForPackage(e.paquete) ?: continue
            val info = intent.resolveActivityInfo(packageManager, 0)
            val banner: Drawable? = info?.loadBanner(packageManager)
                ?: packageManager.getApplicationBanner(e.paquete)
            val imagen = banner ?: info?.loadIcon(packageManager)
            val tile = Tile(this, u, e.nombre, imagen, e.color, banner != null, intent)
            if (imagen != null) tile.tinte = colorDominante(imagen, e.color)
            agregar(tile, (200 * u).toInt(), alto, separacion)
        }

        val ajustes = Tile(this, u, "Ajustes  ·  mantené OK para elegir las apps", null, Color.parseColor("#2a2224"), false,
            Intent(Settings.ACTION_SETTINGS), esAjustes = true).apply {
            tinte = Color.parseColor("#4a3a3d")
            alMantener = { editarApps() }
        }
        agregar(ajustes, alto, alto, 0)

        fila.post { fila.getChildAt(0)?.requestFocus() }
    }

    /**
     * Apps de la fila: las que eligió el usuario (si alguna vez editó la lista) o las de fábrica.
     * Las conocidas van primero en su orden fijo; las agregadas después, por nombre.
     */
    private fun entradasActivas(): List<Entrada> {
        val guardadas = prefs.getStringSet("apps", null) ?: return entradas
        val conocidas = entradas.filter { it.paquete in guardadas }
        val extras = guardadas.filter { p -> entradas.none { it.paquete == p } }
            .map { Entrada(it, etiquetaDe(it), colorDeHash(it)) }
            .sortedBy { it.nombre.lowercase() }
        return conocidas + extras
    }

    private fun etiquetaDe(paquete: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(paquete, 0)).toString()
    } catch (_: Exception) {
        paquete
    }

    private fun colorDeHash(paquete: String): Int =
        Color.HSVToColor(floatArrayOf(Math.abs(paquete.hashCode()) % 360f, 0.55f, 0.55f))

    /** Diálogo para elegir qué apps aparecen en el inicio. Se abre manteniendo OK sobre Ajustes. */
    private fun editarApps() {
        val tv = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val orden = entradas.map { it.paquete }
        val candidatos = packageManager.queryIntentActivities(tv, 0)
            .map { it.activityInfo.packageName }
            .filter { it != packageName }
            .distinct()
            .sortedWith(compareBy({ orden.indexOf(it).let { i -> if (i < 0) Int.MAX_VALUE else i } },
                { etiquetaDe(it).lowercase() }))
        val activos = entradasActivas().map { it.paquete }.toSet()
        val marcadas = BooleanArray(candidatos.size) { candidatos[it] in activos }

        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Apps en el inicio")
            .setMultiChoiceItems(candidatos.map { etiquetaDe(it) }.toTypedArray(), marcadas) { _, i, v ->
                marcadas[i] = v
            }
            .setPositiveButton("Guardar") { _, _ ->
                prefs.edit().putStringSet("apps", candidatos.filterIndexed { i, _ -> marcadas[i] }.toSet()).apply()
                firmaInstaladas = ""
                construirFila()
            }
            .setNeutralButton("Restaurar") { _, _ ->
                prefs.edit().remove("apps").apply()
                firmaInstaladas = ""
                construirFila()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun agregar(tile: Tile, ancho: Int, alto: Int, separacion: Int) {
        tile.alLanzar = { lanzar(it) }
        tile.alEnfocar = { enfocada ->
            fondo.setTinte(enfocada.tinte)
            nombre.text = enfocada.etiqueta
            centrar(enfocada)
        }
        fila.addView(tile, LinearLayout.LayoutParams(ancho, alto).apply { rightMargin = separacion })
    }

    private fun centrar(tile: Tile) {
        val centro = tile.left + tile.width / 2
        val maximo = (fila.width - scroll.width).coerceAtLeast(0)
        scroll.smoothScrollTo((centro - scroll.width / 2).coerceIn(0, maximo), 0)
    }

    private fun lanzar(tile: Tile) {
        try {
            startActivity(tile.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo abrir ${tile.etiqueta}", Toast.LENGTH_SHORT).show()
        }
    }

    /** Promedio de color ponderado por saturación sobre una miniatura de 16x9. */
    private fun colorDominante(d: Drawable, respaldo: Int): Int {
        val bmp = Bitmap.createBitmap(16, 9, Bitmap.Config.ARGB_8888)
        val lienzo = Canvas(bmp)
        d.setBounds(0, 0, 16, 9)
        d.draw(lienzo)
        var r = 0.0; var g = 0.0; var b = 0.0; var peso = 0.0
        val hsv = FloatArray(3)
        for (y in 0 until 9) for (x in 0 until 16) {
            val p = bmp.getPixel(x, y)
            if (Color.alpha(p) < 128) continue
            Color.colorToHSV(p, hsv)
            val w = 0.05 + hsv[1] * hsv[2]
            r += Color.red(p) * w; g += Color.green(p) * w; b += Color.blue(p) * w; peso += w
        }
        bmp.recycle()
        if (peso <= 0.0) return respaldo
        val c = Color.rgb((r / peso).roundToInt(), (g / peso).roundToInt(), (b / peso).roundToInt())
        Color.colorToHSV(c, hsv)
        return if (hsv[1] < 0.35f || hsv[2] < 0.25f) respaldo else c
    }

    private fun texto(tamano: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_PX, tamano * u)
        setTextColor(color)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        includeFontPadding = false
        setShadowLayer(4 * u, 0f, 2 * u, Color.argb(120, 0, 0, 0))
    }

    private fun pedirClima() {
        try {
            val url = URL("https://api.open-meteo.com/v1/forecast?latitude=-34.76&longitude=-58.40&current=temperature_2m")
            val con = url.openConnection() as HttpURLConnection
            con.connectTimeout = 8000
            con.readTimeout = 8000
            val json = con.inputStream.bufferedReader().use { it.readText() }
            con.disconnect()
            val t = JSONObject(json).getJSONObject("current").getDouble("temperature_2m")
            temperatura = "${t.roundToInt()}°"
        } catch (_: Exception) {
            // Sin red: se deja el último valor conocido
        }
        runOnUiThread {
            clima.text = temperatura?.let { "$it · Lomas de Zamora" } ?: "Lomas de Zamora"
        }
    }
}
