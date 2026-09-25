package com.ferreteria.movil.data.model

import com.google.gson.annotations.SerializedName
import java.io.Serializable

/**
 * Modelo de datos correspondiente a la entidad Producto del backend Django (productos/models.py).
 * Mapea exactamente los campos definidos en el ProductoSerializer.
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
    val fechaCreacion: String? = null
) : Serializable {

    /**
     * Retorna verdadero si el stock actual es igual o menor al stock mínimo,
     * indicando una condición de inventario crítico.
     */
    fun isStockCritico(): Boolean {
        return stockActual <= stockMinimo
    }
}
