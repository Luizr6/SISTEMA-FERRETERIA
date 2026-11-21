package com.ferreteria.movil.data.model

import com.google.gson.annotations.SerializedName

/** GET /api/whatsapp/conversaciones/ */
data class Conversacion(
    @SerializedName("id") val id: Int? = null,
    @SerializedName("telefono") val telefono: String? = null,
    @SerializedName("nombre") val nombre: String? = null,
    @SerializedName("cliente_nombre") val clienteNombre: String? = null,
    @SerializedName("ultimo_mensaje") val ultimoMensaje: String? = null,
    @SerializedName("ultimo_texto") val ultimoTexto: String? = null,
    @SerializedName("no_leidos") val noLeidos: Int? = null,
    @SerializedName("total_mensajes") val totalMensajes: Int? = null,
    @SerializedName("bot_activo") val botActivo: Boolean? = null
) {
    val nombreVisible: String
        get() = clienteNombre?.takeIf { it.isNotBlank() }
            ?: nombre?.takeIf { it.isNotBlank() }
            ?: telefonoVisible

    /** "51987654321" -> "+51 987 654 321" */
    val telefonoVisible: String
        get() {
            val t = telefono.orEmpty()
            return if (t.length == 11 && t.startsWith("51")) {
                "+51 ${t.substring(2, 5)} ${t.substring(5, 8)} ${t.substring(8)}"
            } else {
                "+$t"
            }
        }

    /** "María Torres" -> "MT"; sin nombre -> "+51" */
    val iniciales: String
        get() {
            val base = clienteNombre?.takeIf { it.isNotBlank() } ?: nombre?.takeIf { it.isNotBlank() }
                ?: return "+" + telefono.orEmpty().take(2)
            return base.trim().split(" ").filter { it.isNotBlank() }.take(2)
                .joinToString("") { it.first().uppercase() }
        }
}

/** GET /api/whatsapp/conversaciones/{id}/mensajes/ */
data class Mensaje(
    @SerializedName("id") val id: Int? = null,
    /** ENTRANTE (cliente) | SALIENTE (bot o vendedor) */
    @SerializedName("direccion") val direccion: String? = null,
    @SerializedName("texto") val texto: String? = null,
    /** MANUAL cuando lo escribió una persona desde el panel o la app */
    @SerializedName("intencion") val intencion: String? = null,
    @SerializedName("estado_envio") val estadoEnvio: String? = null,
    @SerializedName("error") val error: String? = null,
    @SerializedName("pedido") val pedido: Int? = null,
    @SerializedName("pedido_numero") val pedidoNumero: String? = null,
    @SerializedName("enviado_por_nombre") val enviadoPorNombre: String? = null,
    @SerializedName("fecha") val fecha: String? = null
) {
    val esCliente: Boolean get() = direccion == "ENTRANTE"
    val esPersona: Boolean get() = !esCliente && (intencion == "MANUAL" || !enviadoPorNombre.isNullOrBlank())
}

data class TextoRequest(@SerializedName("texto") val texto: String)

data class BotRequest(@SerializedName("bot_activo") val botActivo: Boolean)
