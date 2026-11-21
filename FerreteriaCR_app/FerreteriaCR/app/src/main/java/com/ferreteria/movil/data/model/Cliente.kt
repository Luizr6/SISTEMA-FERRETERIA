package com.ferreteria.movil.data.model

import com.google.gson.annotations.SerializedName

/** RF16: clientes registrados (GET/POST /api/clientes/). */
data class Cliente(
    @SerializedName("id") val id: Int? = null,
    /** DNI | RUC | CE | OTRO */
    @SerializedName("tipo_documento") val tipoDocumento: String? = null,
    @SerializedName("numero_documento") val numeroDocumento: String? = null,
    @SerializedName("nombre") val nombre: String? = null,
    @SerializedName("telefono") val telefono: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("direccion") val direccion: String? = null,
    @SerializedName("activo") val activo: Boolean? = null
) {
    val documento: String
        get() = listOfNotNull(tipoDocumento, numeroDocumento?.takeIf { it.isNotBlank() }).joinToString(" ")
}

data class ClienteRequest(
    @SerializedName("tipo_documento") val tipoDocumento: String,
    @SerializedName("numero_documento") val numeroDocumento: String,
    @SerializedName("nombre") val nombre: String,
    @SerializedName("telefono") val telefono: String = ""
)

/** GET /api/proveedores/ (filtro de inventario, RF09). */
data class Proveedor(
    @SerializedName("id") val id: Int? = null,
    @SerializedName("razon_social") val razonSocial: String? = null
)

/** GET /api/usuarios/?rol=VENDEDOR (solo Admin, para filtrar ventas por vendedor). */
data class Usuario(
    @SerializedName("id") val id: Int? = null,
    @SerializedName("username") val username: String? = null,
    @SerializedName("first_name") val firstName: String? = null,
    @SerializedName("last_name") val lastName: String? = null
) {
    val nombreVisible: String
        get() = listOfNotNull(firstName, lastName).joinToString(" ").trim().ifBlank { username.orEmpty() }
}
