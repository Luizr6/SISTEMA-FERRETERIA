package com.ferreteria.movil.data.model

import com.google.gson.annotations.SerializedName

/** POST /api/asistente/ {"pregunta": "..."} (solo Administrador) */
data class PreguntaRequest(@SerializedName("pregunta") val pregunta: String)

/** Respuesta del asistente: la IA redacta con los datos reales; si no hay IA, modo BASICO. */
data class RespuestaAsistente(
    @SerializedName("respuesta") val respuesta: String? = null,
    /** IA | BASICO */
    @SerializedName("modo") val modo: String? = null,
    @SerializedName("modelo") val modelo: String? = null,
    @SerializedName("aviso") val aviso: String? = null,
    @SerializedName("alertas") val alertas: List<Alerta>? = null
)

/** Aviso calculado por el backend (stock, pedidos, compras, ventas bajas). */
data class Alerta(
    /** Estable mientras la situación no cambie: sirve para no repetir la notificación. */
    @SerializedName("id") val id: String? = null,
    @SerializedName("tipo") val tipo: String? = null,
    /** CRITICO | ALERTA | INFO */
    @SerializedName("nivel") val nivel: String? = null,
    @SerializedName("titulo") val titulo: String? = null,
    @SerializedName("mensaje") val mensaje: String? = null
)

/** GET /api/asistente/alertas/ */
data class RespuestaAlertas(
    @SerializedName("alertas") val alertas: List<Alerta>? = null,
    @SerializedName("total") val total: Int? = null,
    @SerializedName("ia_activa") val iaActiva: Boolean? = null
)

/** POST /api/ventas/{id}/anular/ */
data class AnularRequest(@SerializedName("motivo") val motivo: String)
