package com.ferreteria.movil.data.model

import com.google.gson.annotations.SerializedName

/** Caja (turno) de un vendedor: GET /api/cajas/actual/ y /api/cajas/ */
data class Caja(
    @SerializedName("id") val id: Int? = null,
    @SerializedName("usuario") val usuario: Int? = null,
    @SerializedName("usuario_nombre") val usuarioNombre: String? = null,
    /** ABIERTA | CERRADA */
    @SerializedName("estado") val estado: String? = null,
    @SerializedName("fecha_apertura") val fechaApertura: String? = null,
    @SerializedName("monto_inicial") val montoInicial: Double? = null,
    @SerializedName("fecha_cierre") val fechaCierre: String? = null,
    @SerializedName("cerrada_por_nombre") val cerradaPorNombre: String? = null,
    @SerializedName("monto_esperado") val montoEsperado: Double? = null,
    @SerializedName("monto_contado") val montoContado: Double? = null,
    @SerializedName("diferencia") val diferencia: Double? = null,
    @SerializedName("observacion") val observacion: String? = null,
    @SerializedName("resumen") val resumen: ResumenCaja? = null,
    @SerializedName("movimientos") val movimientos: List<MovimientoCaja>? = null
) {
    val abierta: Boolean get() = estado == "ABIERTA"
}

data class ResumenCaja(
    @SerializedName("monto_inicial") val montoInicial: Double? = null,
    @SerializedName("ventas_efectivo") val ventasEfectivo: Double? = null,
    @SerializedName("ventas_otros_medios") val ventasOtrosMedios: Double? = null,
    @SerializedName("total_vendido") val totalVendido: Double? = null,
    @SerializedName("cantidad_ventas") val cantidadVentas: Int? = null,
    @SerializedName("ventas_anuladas") val ventasAnuladas: Int? = null,
    @SerializedName("por_metodo") val porMetodo: List<MontoMetodo>? = null,
    @SerializedName("ingresos") val ingresos: Double? = null,
    @SerializedName("egresos") val egresos: Double? = null,
    /** Lo que debería haber en efectivo en el cajón. */
    @SerializedName("efectivo_esperado") val efectivoEsperado: Double? = null
)

data class MontoMetodo(
    @SerializedName("metodo_pago") val metodoPago: String? = null,
    @SerializedName("total") val total: Double? = null,
    @SerializedName("cantidad") val cantidad: Int? = null
)

data class MovimientoCaja(
    @SerializedName("id") val id: Int? = null,
    /** INGRESO | EGRESO */
    @SerializedName("tipo") val tipo: String? = null,
    @SerializedName("monto") val monto: Double? = null,
    @SerializedName("concepto") val concepto: String? = null,
    @SerializedName("usuario_nombre") val usuarioNombre: String? = null,
    @SerializedName("fecha") val fecha: String? = null
)

/** {"caja": {...}} o {"caja": null} */
data class CajaActual(@SerializedName("caja") val caja: Caja? = null)

data class AbrirCajaRequest(@SerializedName("monto_inicial") val montoInicial: Double)

data class MovimientoCajaRequest(
    @SerializedName("tipo") val tipo: String,
    @SerializedName("monto") val monto: Double,
    @SerializedName("concepto") val concepto: String
)

data class CerrarCajaRequest(
    @SerializedName("monto_contado") val montoContado: Double,
    @SerializedName("observacion") val observacion: String
)
