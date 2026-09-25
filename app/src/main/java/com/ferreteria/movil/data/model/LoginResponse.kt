package com.ferreteria.movil.data.model

import com.google.gson.annotations.SerializedName

/**
 * Respuesta del endpoint de login según la implementación en cuentas/views.py del backend.
 */
data class LoginResponse(
    @SerializedName("token")
    val token: String,

    @SerializedName("username")
    val username: String? = null,

    @SerializedName("rol")
    val rol: String? = null
)
