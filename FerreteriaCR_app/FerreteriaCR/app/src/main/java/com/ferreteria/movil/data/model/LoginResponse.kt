package com.ferreteria.movil.data.model

import com.google.gson.annotations.SerializedName

/**
 * Respuesta de POST /api/auth/login/ del backend Django (cuentas/serializers.py -> LoginSerializer).
 * {"access": "...", "refresh": "...", "user_id": 2, "username": "vendedor1", "rol": "VENDEDOR"}
 *
 * Los campos son anulables porque Gson no respeta la nulabilidad de Kotlin.
 */
data class LoginResponse(
    @SerializedName("access")
    val access: String? = null,

    @SerializedName("refresh")
    val refresh: String? = null,

    @SerializedName("user_id")
    val userId: Int? = null,

    @SerializedName("username")
    val username: String? = null,

    @SerializedName("rol")
    val rol: String? = null
)
