package com.ferreteria.movil.data.model

import com.google.gson.annotations.SerializedName

/**
 * Petición de autenticación enviada al endpoint de login del backend Django.
 */
data class LoginRequest(
    @SerializedName("username")
    val username: String,

    @SerializedName("password")
    val password: String
)
