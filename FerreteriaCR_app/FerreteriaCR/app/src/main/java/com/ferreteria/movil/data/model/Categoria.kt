package com.ferreteria.movil.data.model

import com.google.gson.annotations.SerializedName

/**
 * Modelo ligero de Categoria proveniente de GET /api/categorias/ (backend Django).
 * Solo se exponen los campos requeridos por la UI del formulario de productos.
 */
data class Categoria(
    @SerializedName("id")
    val id: Int,

    @SerializedName("nombre")
    val nombre: String,

    @SerializedName("total_productos")
    val totalProductos: Int? = null
)

/**
 * Modelo ligero de Marca proveniente de GET /api/marcas/ (backend Django).
 */
data class Marca(
    @SerializedName("id")
    val id: Int,

    @SerializedName("nombre")
    val nombre: String
)
