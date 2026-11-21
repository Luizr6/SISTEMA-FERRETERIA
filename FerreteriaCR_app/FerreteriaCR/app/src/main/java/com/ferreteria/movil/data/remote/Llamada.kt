package com.ferreteria.movil.data.remote

import com.ferreteria.movil.ui.common.Resource
import retrofit2.Response
import java.io.IOException

/**
 * Ejecuta una llamada Retrofit y la convierte en Resource, con mensajes claros:
 * sin red, sesión vencida, validaciones del backend ({codigo, mensaje, detalle}), etc.
 */
suspend fun <T> llamar(bloque: suspend () -> Response<T>): Resource<T> {
    return try {
        val response = bloque()
        val body = response.body()
        if (response.isSuccessful && body != null) {
            Resource.Success(body)
        } else {
            Resource.Error(ApiErrors.mensaje(response.code(), response.errorBody()?.string()), response.code())
        }
    } catch (e: IOException) {
        Resource.Error(ApiErrors.SIN_RED, -1)
    } catch (e: Exception) {
        Resource.Error("Ocurrió un error inesperado: ${e.localizedMessage ?: "desconocido"}")
    }
}
