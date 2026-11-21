package com.ferreteria.movil.data.model

import com.google.gson.annotations.SerializedName

/** RF22: GET /api/dashboard/ */
data class Dashboard(
    @SerializedName("ventas_hoy") val ventasHoy: ResumenPeriodo? = null,
    @SerializedName("ventas_mes") val ventasMes: ResumenPeriodo? = null,
    @SerializedName("ventas_ultimos_7_dias") val ultimos7Dias: List<VentaDia>? = null,
    @SerializedName("productos_mas_vendidos") val masVendidos: List<ProductoTop>? = null,
    @SerializedName("stock_critico") val stockCritico: List<ProductoCritico>? = null,
    @SerializedName("total_productos_activos") val totalProductosActivos: Int? = null,
    @SerializedName("valor_inventario") val valorInventario: Double? = null,
    @SerializedName("pedidos_pendientes") val pedidosPendientes: Int? = null,
    @SerializedName("compras_por_recibir") val comprasPorRecibir: Int? = null
)

data class ResumenPeriodo(
    @SerializedName("cantidad") val cantidad: Int? = null,
    @SerializedName("total") val total: Double? = null
)

data class VentaDia(
    @SerializedName("dia") val dia: String? = null,
    @SerializedName("cantidad") val cantidad: Int? = null,
    @SerializedName("total") val total: Double? = null
)

data class ProductoTop(
    @SerializedName("producto_id") val productoId: Int? = null,
    @SerializedName("nombre") val nombre: String? = null,
    @SerializedName("unidades") val unidades: Int? = null,
    @SerializedName("monto") val monto: Double? = null
)

data class ProductoCritico(
    @SerializedName("id") val id: Int? = null,
    @SerializedName("codigo") val codigo: String? = null,
    @SerializedName("nombre") val nombre: String? = null,
    @SerializedName("stock_actual") val stockActual: Int? = null,
    @SerializedName("stock_minimo") val stockMinimo: Int? = null
)

/** GET /api/pedidos/resumen/ (badge de pedidos por confirmar, RF32). */
data class ResumenPedidos(
    @SerializedName("pendientes") val pendientes: Int? = null,
    @SerializedName("aprobados") val aprobados: Int? = null,
    @SerializedName("rechazados") val rechazados: Int? = null,
    @SerializedName("pendientes_por_origen") val pendientesPorOrigen: Map<String, Int>? = null
)
