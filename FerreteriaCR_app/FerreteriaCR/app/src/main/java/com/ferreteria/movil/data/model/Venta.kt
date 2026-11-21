package com.ferreteria.movil.data.model

import com.google.gson.annotations.SerializedName
import java.io.Serializable

/**
 * CU08 / RF17: POST /api/ventas/
 * El precio lo pone el backend desde el catálogo; la app solo envía producto y cantidad.
 */
data class VentaRequest(
    @SerializedName("cliente") val cliente: Int? = null,
    @SerializedName("metodo_pago") val metodoPago: String = "EFECTIVO",
    @SerializedName("origen") val origen: String = "MOVIL",
    @SerializedName("observacion") val observacion: String = "",
    @SerializedName("detalles") val detalles: List<DetalleVentaRequest>
)

data class DetalleVentaRequest(
    @SerializedName("producto") val producto: Int,
    @SerializedName("cantidad") val cantidad: Int
)

/** Venta tal como la devuelve el backend (campos anulables: Gson no respeta la nulabilidad de Kotlin). */
data class Venta(
    @SerializedName("id") val id: Int? = null,
    @SerializedName("numero") val numero: String? = null,
    @SerializedName("fecha") val fecha: String? = null,
    @SerializedName("cliente") val cliente: Int? = null,
    @SerializedName("cliente_nombre") val clienteNombre: String? = null,
    @SerializedName("cliente_documento") val clienteDocumento: String? = null,
    @SerializedName("vendedor_nombre") val vendedorNombre: String? = null,
    @SerializedName("metodo_pago") val metodoPago: String? = null,
    @SerializedName("metodo_pago_display") val metodoPagoDisplay: String? = null,
    @SerializedName("origen") val origen: String? = null,
    @SerializedName("subtotal") val subtotal: Double? = null,
    @SerializedName("igv") val igv: Double? = null,
    @SerializedName("total") val total: Double? = null,
    /** COMPLETADA | ANULADA */
    @SerializedName("estado") val estado: String? = null,
    @SerializedName("observacion") val observacion: String? = null,
    @SerializedName("pedido_numero") val pedidoNumero: String? = null,
    @SerializedName("anulada_por_nombre") val anuladaPorNombre: String? = null,
    @SerializedName("motivo_anulacion") val motivoAnulacion: String? = null,
    @SerializedName("detalles") val detalles: List<DetalleVenta>? = null
) : Serializable {
    val anulada: Boolean get() = estado == "ANULADA"
    val nombreCliente: String get() = clienteNombre?.takeIf { it.isNotBlank() } ?: "Cliente varios"
}

data class DetalleVenta(
    @SerializedName("producto") val producto: Int? = null,
    @SerializedName("producto_codigo") val productoCodigo: String? = null,
    @SerializedName("producto_nombre") val productoNombre: String? = null,
    @SerializedName("unidad_medida") val unidadMedida: String? = null,
    @SerializedName("cantidad") val cantidad: Int? = null,
    @SerializedName("precio_unitario") val precioUnitario: Double? = null,
    @SerializedName("subtotal") val subtotal: Double? = null
) : Serializable

/** GET /api/ventas/resumen/ (mismos filtros que el listado). */
data class ResumenVentas(
    @SerializedName("ventas_completadas") val ventasCompletadas: Int? = null,
    @SerializedName("ventas_anuladas") val ventasAnuladas: Int? = null,
    @SerializedName("total_vendido") val totalVendido: Double? = null,
    @SerializedName("ticket_promedio") val ticketPromedio: Double? = null,
    @SerializedName("por_vendedor") val porVendedor: List<VentasPorVendedor>? = null
)

data class VentasPorVendedor(
    @SerializedName("vendedor_id") val vendedorId: Int? = null,
    @SerializedName("vendedor") val vendedor: String? = null,
    @SerializedName("cantidad") val cantidad: Int? = null,
    @SerializedName("total") val total: Double? = null
)

/** Métodos de pago que acepta el backend (ventas/models.py -> METODOS_PAGO). */
object MetodosPago {
    val TODOS = listOf(
        "EFECTIVO" to "Efectivo",
        "YAPE" to "Yape",
        "PLIN" to "Plin",
        "TARJETA" to "Tarjeta",
        "TRANSFERENCIA" to "Transferencia"
    )

    fun etiqueta(codigo: String?): String = TODOS.firstOrNull { it.first == codigo }?.second ?: codigo.orEmpty()
}
