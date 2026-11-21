package com.ferreteria.movil.data.model

import com.google.gson.annotations.SerializedName
import java.io.Serializable

/**
 * Modelo de la entidad Producto del backend Django (productos/models.py).
 * Mapea los campos del ProductoSerializer. Los campos *_nombre y estado_stock
 * son de solo lectura: el backend los ignora al crear o editar.
 */
data class Producto(
    @SerializedName("id")
    val id: Int? = null,

    @SerializedName("codigo")
    val codigo: String,

    @SerializedName("nombre")
    val nombre: String,

    @SerializedName("descripcion")
    val descripcion: String? = null,

    @SerializedName("categoria")
    val categoria: Int = 1, // ID de la clave foránea Categoria

    @SerializedName("marca")
    val marca: Int? = null, // ID opcional de la clave foránea Marca

    @SerializedName("precio_compra")
    val precioCompra: Double = 0.0,

    @SerializedName("precio_venta")
    val precioVenta: Double = 0.0,

    @SerializedName("stock_actual")
    val stockActual: Int = 0,

    @SerializedName("stock_minimo")
    val stockMinimo: Int = 5,

    @SerializedName("activo")
    val activo: Boolean = true,

    @SerializedName("fecha_creacion")
    val fechaCreacion: String? = null,

    // ---- Solo lectura (vienen del backend) ----
    @SerializedName("categoria_nombre")
    val categoriaNombre: String? = null,

    @SerializedName("marca_nombre")
    val marcaNombre: String? = null,

    @SerializedName("proveedor")
    val proveedor: Int? = null,

    @SerializedName("proveedor_nombre")
    val proveedorNombre: String? = null,

    @SerializedName("unidad_medida")
    val unidadMedida: String? = null,

    /** AGOTADO | BAJO | NORMAL (calculado por el backend) */
    @SerializedName("estado_stock")
    val estadoStock: String? = null
) : Serializable {

    /** Estado de stock (RF08 / RF09): mismos valores que estado_stock del backend. */
    enum class NivelStock(val etiqueta: String) { AGOTADO("Agotado"), BAJO("Bajo"), NORMAL("Normal") }

    /** AGOTADO = 0 · BAJO = stock <= mínimo (alerta) · NORMAL = por encima del mínimo. */
    fun nivelStock(): NivelStock = when {
        stockActual <= 0 -> NivelStock.AGOTADO
        stockActual <= stockMinimo -> NivelStock.BAJO
        else -> NivelStock.NORMAL
    }

    /** RF08: alerta visual cuando el stock es igual o inferior al mínimo (incluye agotado). */
    fun isStockCritico(): Boolean = nivelStock() != NivelStock.NORMAL

    fun isAgotado(): Boolean = stockActual <= 0
}
