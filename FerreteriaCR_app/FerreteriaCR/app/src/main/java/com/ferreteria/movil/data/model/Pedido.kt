package com.ferreteria.movil.data.model

import com.google.gson.annotations.SerializedName

/**
 * Cuerpo de POST /api/pedidos/ (ventas/serializers.py -> PedidoSerializer).
 * El pedido queda en estado PENDIENTE y NO descuenta stock: el cajero lo aprueba o rechaza
 * desde el panel web (bandeja de pedidos). El stock se vuelve a validar al aprobar.
 */
data class PedidoRequest(
    @SerializedName("origen")
    val origen: String = "MOVIL",

    @SerializedName("contacto_nombre")
    val contactoNombre: String,

    @SerializedName("contacto_telefono")
    val contactoTelefono: String = "",

    @SerializedName("observacion")
    val observacion: String = "",

    @SerializedName("detalles")
    val detalles: List<DetallePedidoRequest>
)

data class DetallePedidoRequest(
    @SerializedName("producto")
    val producto: Int,

    @SerializedName("cantidad")
    val cantidad: Int
)

/** Cuerpo de POST /api/pedidos/{id}/aprobar/ (CU17: genera la venta y descuenta stock). */
data class AprobarPedidoRequest(
    @SerializedName("metodo_pago")
    val metodoPago: String = "EFECTIVO",

    @SerializedName("cliente")
    val cliente: Int? = null
)

/** Cuerpo de POST /api/pedidos/{id}/rechazar/ y /cancelar/ */
data class MotivoRequest(
    @SerializedName("motivo")
    val motivo: String
)

/**
 * Pedido tal como lo devuelve el backend. Todos los campos son anulables
 * porque Gson no respeta la nulabilidad de Kotlin.
 */
data class Pedido(
    @SerializedName("id")
    val id: Int? = null,

    @SerializedName("numero")
    val numero: String? = null,

    @SerializedName("fecha")
    val fecha: String? = null,

    @SerializedName("origen")
    val origen: String? = null,

    /** PENDIENTE | APROBADO | RECHAZADO | CANCELADO */
    @SerializedName("estado")
    val estado: String? = null,

    @SerializedName("cliente_nombre")
    val clienteNombre: String? = null,

    @SerializedName("contacto_nombre")
    val contactoNombre: String? = null,

    @SerializedName("contacto_telefono")
    val contactoTelefono: String? = null,

    @SerializedName("observacion")
    val observacion: String? = null,

    @SerializedName("total_estimado")
    val totalEstimado: Double? = null,

    @SerializedName("creado_por")
    val creadoPor: Int? = null,

    @SerializedName("creado_por_nombre")
    val creadoPorNombre: String? = null,

    @SerializedName("revisado_por_nombre")
    val revisadoPorNombre: String? = null,

    @SerializedName("fecha_revision")
    val fechaRevision: String? = null,

    @SerializedName("motivo_rechazo")
    val motivoRechazo: String? = null,

    /** ID de la venta generada al confirmar (null si aún no se confirma). */
    @SerializedName("venta")
    val venta: Int? = null,

    @SerializedName("venta_numero")
    val ventaNumero: String? = null,

    @SerializedName("puede_aprobarse")
    val puedeAprobarse: Boolean? = null,

    @SerializedName("detalles")
    val detalles: List<DetallePedido>? = null
) {
    val esPendiente: Boolean get() = estado == "PENDIENTE"

    /** Nombre que se muestra del cliente del pedido. */
    val nombreCliente: String
        get() = clienteNombre?.takeIf { it.isNotBlank() }
            ?: contactoNombre?.takeIf { it.isNotBlank() }
            ?: "Sin cliente"
}

data class DetallePedido(
    @SerializedName("id")
    val id: Int? = null,

    @SerializedName("producto")
    val producto: Int? = null,

    @SerializedName("producto_codigo")
    val productoCodigo: String? = null,

    @SerializedName("producto_nombre")
    val productoNombre: String? = null,

    @SerializedName("cantidad")
    val cantidad: Int? = null,

    @SerializedName("precio_unitario")
    val precioUnitario: Double? = null,

    @SerializedName("subtotal")
    val subtotal: Double? = null,

    @SerializedName("stock_disponible")
    val stockDisponible: Int? = null,

    @SerializedName("stock_suficiente")
    val stockSuficiente: Boolean? = null
)
