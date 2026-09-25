package com.ferreteria.movil.data.remote

import com.ferreteria.movil.data.local.TokenManager
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Interceptor de OkHttp encargado de inyectar la cabecera 'Authorization'
 * con el token almacenado en cada petición saliente hacia la API de Django.
 */
class AuthInterceptor(private val tokenManager: TokenManager) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val token = tokenManager.getToken()

        // Si no hay token guardado, es un token de prueba mock, o es login, procedemos sin header de auth
        if (token.isNullOrBlank() || token.startsWith("mock_") || originalRequest.url.encodedPath.contains("login")) {
            return chain.proceed(originalRequest)
        }

        // Si el token es de tipo JWT (comienza por 'eyJ'), usamos 'Bearer',
        // de lo contrario usamos 'Token' (formato estándar de DRF TokenAuthentication).
        val authHeaderValue = if (token.startsWith("eyJ")) {
            "Bearer $token"
        } else {
            "Token $token"
        }

        val authenticatedRequest = originalRequest.newBuilder()
            .header("Authorization", authHeaderValue)
            .build()

        return chain.proceed(authenticatedRequest)
    }
}
