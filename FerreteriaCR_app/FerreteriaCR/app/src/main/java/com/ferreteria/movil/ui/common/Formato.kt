package com.ferreteria.movil.ui.common

import android.content.Context
import android.content.res.ColorStateList
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.ferreteria.movil.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToLong

/** Formatos compartidos por las pantallas. */
object Formato {

    private val peru = Locale("es", "PE")

    /** IGV que suma el backend al vender (el precio del catálogo se guarda SIN IGV). */
    const val IGV = 0.18

    fun soles(valor: Double?): String = String.format(Locale.US, "S/ %,.2f", valor ?: 0.0)

    /** Precio que paga el cliente: precio_venta + 18 %, redondeado a céntimos. */
    fun conIgv(precioSinIgv: Double): Double = redondear(precioSinIgv * (1 + IGV))

    fun redondear(valor: Double): Double = (valor * 100.0).roundToLong() / 100.0

    /** Total igual al del backend: subtotal + IGV(redondeado). */
    fun totalConIgv(subtotalSinIgv: Double): Double = redondear(subtotalSinIgv + redondear(subtotalSinIgv * IGV))

    /** "2026-11-05T10:05:54-05:00" -> "05/11/2026 10:05" (la hora ya viene en hora de Lima). */
    fun fechaHora(iso: String?): String = convertir(iso, "dd/MM/yyyy HH:mm")

    fun hora(iso: String?): String = convertir(iso, "HH:mm")

    /** Hoy -> "10:05"; otro día -> "05/11" */
    fun horaOFecha(iso: String?): String {
        if (iso.isNullOrBlank() || iso.length < 10) return ""
        return if (iso.substring(0, 10) == Fechas.hoy()) hora(iso) else convertir(iso, "dd/MM")
    }

    /** Minutos desde la fecha ISO hasta ahora (para "Espera 35 min"). */
    fun minutosDesde(iso: String?): Long {
        if (iso.isNullOrBlank() || iso.length < 16) return 0
        return try {
            val f = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).parse(iso.substring(0, 16)) ?: return 0
            ((System.currentTimeMillis() - f.time) / 60_000L).coerceAtLeast(0)
        } catch (_: Exception) {
            0
        }
    }

    fun haceTiempo(iso: String?): String {
        val m = minutosDesde(iso)
        return when {
            m < 1 -> "Ahora"
            m < 60 -> "Hace $m min"
            m < 24 * 60 -> "Hace ${m / 60} h"
            else -> fechaHora(iso)
        }
    }

    /** "Jueves 12 de noviembre" */
    fun hoyLargo(): String = SimpleDateFormat("EEEE d 'de' MMMM", peru).format(Calendar.getInstance().time)
        .replaceFirstChar { it.uppercase() }

    private fun convertir(iso: String?, patron: String): String {
        if (iso.isNullOrBlank() || iso.length < 16) return iso.orEmpty()
        return try {
            val entrada = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US)
            SimpleDateFormat(patron, Locale.US).format(entrada.parse(iso.substring(0, 16))!!)
        } catch (_: Exception) {
            iso
        }
    }

    fun estadoPedido(estado: String?): String = when (estado) {
        "PENDIENTE" -> "Por confirmar"
        "APROBADO" -> "Aprobado"
        "RECHAZADO" -> "Rechazado"
        "CANCELADO" -> "Cancelado"
        else -> estado.orEmpty()
    }

    fun origen(origen: String?): String = when (origen) {
        "WHATSAPP" -> "WhatsApp"
        "WEB" -> "Web"
        "MOVIL" -> "App"
        "MOSTRADOR" -> "Mostrador"
        else -> origen.orEmpty()
    }

    fun rol(rol: String?): String = when (rol?.uppercase()) {
        "ADMIN" -> "Administrador"
        "VENDEDOR" -> "Vendedor"
        else -> rol.orEmpty()
    }

    fun unidad(codigo: String?): String = when (codigo?.uppercase()) {
        "UND" -> "und"
        "KG" -> "kg"
        "M" -> "m"
        "L" -> "L"
        "GLN" -> "gl"
        "CAJA" -> "cajas"
        "BOLSA" -> "bolsas"
        null, "" -> "und"
        else -> codigo.lowercase()
    }

    /** "/ kg" para precios que no son por unidad. */
    fun sufijoPrecio(codigo: String?): String = when (codigo?.uppercase()) {
        "KG" -> " / kg"
        "M" -> " / m"
        "L" -> " / L"
        else -> ""
    }

    /** Pinta una etiqueta redondeada con texto del color dado y fondo suave del mismo tono. */
    fun pintarEtiqueta(tv: TextView, colorRes: Int, fondoRes: Int, texto: String) {
        val ctx = tv.context
        tv.text = texto
        tv.setTextColor(ContextCompat.getColor(ctx, colorRes))
        tv.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(ctx, fondoRes))
    }

    fun colorEstado(ctx: Context, estado: String?): Int = ContextCompat.getColor(
        ctx, when (estado) {
            "PENDIENTE" -> R.color.ambar_texto
            "APROBADO", "COMPLETADA" -> R.color.verde
            "RECHAZADO", "ANULADA" -> R.color.rojo
            else -> R.color.texto_2
        }
    )
}

/** Fechas en el formato que acepta el backend (YYYY-MM-DD). */
object Fechas {
    private fun fmt(cal: Calendar) = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)

    fun hoy(): String = fmt(Calendar.getInstance())

    fun haceDias(n: Int): String = fmt(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -n) })
}
