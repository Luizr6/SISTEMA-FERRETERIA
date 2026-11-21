package com.ferreteria.movil.data.remote

import org.json.JSONArray
import org.json.JSONObject

/**
 * Traduce los errores del backend a mensajes para el vendedor.
 * Django responde siempre {"codigo": 400, "mensaje": "...", "detalle": {...}}
 * (ferreteria/exceptions.py).
 */
object ApiErrors {

    fun mensaje(code: Int, errorBody: String?): String {
        val delServidor = extraer(errorBody)
        return when (code) {
            401 -> "Tu sesión venció. Vuelve a iniciar sesión."
            403 -> delServidor ?: "Tu usuario no tiene permiso para esta acción."
            404 -> delServidor ?: "No se encontró el recurso en el servidor (404)."
            409 -> delServidor ?: "La operación no es válida en el estado actual (409)."
            in 500..599 -> "El servidor tuvo un problema ($code). Intenta de nuevo en un momento."
            else -> delServidor ?: "Error $code del servidor."
        }
    }

    const val SIN_RED = "Sin conexión con el servidor. Revisa la señal o el Wi-Fi e inténtalo de nuevo."

    /** Saca el texto útil de {codigo, mensaje, detalle} o de un error DRF plano. */
    fun extraer(errorBody: String?): String? {
        if (errorBody.isNullOrBlank()) return null
        return try {
            val json = JSONObject(errorBody)
            val detalle = json.opt("detalle") ?: json
            val partes = mutableListOf<String>()
            aplanar(detalle, partes)
            val mensaje = json.optString("mensaje").takeIf { it.isNotBlank() }
            when {
                partes.isNotEmpty() -> partes.distinct().joinToString("\n")
                mensaje != null -> mensaje
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Claves cuyo mensaje ya se entiende solo (no se antepone el nombre del campo). */
    private val SIN_PREFIJO = setOf("detail", "non_field_errors", "detalles", "estado", "carrito", "items", "caja", "monto", "concepto", "monto_inicial", "monto_contado")

    /** Nombres amigables de campos del backend. */
    private val NOMBRES = mapOf(
        "numero_documento" to "Documento",
        "nombre" to "Nombre",
        "cliente" to "Cliente",
        "motivo" to "Motivo",
        "metodo_pago" to "Método de pago",
        "codigo" to "Código",
        "precio_venta" to "Precio de venta",
        "precio_compra" to "Precio de compra"
    )

    private fun aplanar(valor: Any?, salida: MutableList<String>, campo: String? = null) {
        when (valor) {
            is JSONObject -> valor.keys().forEach { k ->
                aplanar(valor.opt(k), salida, if (k in SIN_PREFIJO) campo else NOMBRES[k] ?: k)
            }
            is JSONArray -> for (i in 0 until valor.length()) aplanar(valor.opt(i), salida, campo)
            is String -> if (valor.isNotBlank()) salida.add(if (campo != null) "$campo: $valor" else valor)
            null, JSONObject.NULL -> {}
            else -> salida.add(valor.toString())
        }
    }
}
