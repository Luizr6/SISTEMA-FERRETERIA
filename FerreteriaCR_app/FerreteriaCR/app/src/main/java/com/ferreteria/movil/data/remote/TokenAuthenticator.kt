package com.ferreteria.movil.data.remote

import com.ferreteria.movil.data.local.TokenManager
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Renovación automática del access token (FCR-703: "Retrofit + token refresh").
 *
 * Cuando Django responde 401 a una petición autenticada, OkHttp llama a este Authenticator:
 *  1. Pide un access nuevo a POST /api/auth/refresh/ con el refresh guardado.
 *  2. Reintenta la petición original con el token nuevo (una sola vez).
 *  3. Si el refresh también venció o fue rechazado, borra la sesión y avisa a la UI
 *     (SesionEvents) para volver al login. Si el fallo es de red, NO cierra la sesión.
 */
class TokenAuthenticator(private val tokenManager: TokenManager) : Authenticator {

    // Cliente aparte, sin interceptores, para no entrar en bucle con este mismo Authenticator
    private val clienteRefresh = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val lock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        val path = response.request.url.encodedPath
        if (path.contains("/auth/login") || path.contains("/auth/refresh")) return null
        if (intentosPrevios(response) >= 1) return null // ya se reintentó con un token nuevo

        val tokenEnviado = response.request.header("Authorization")?.removePrefix("Bearer ")?.trim()

        synchronized(lock) {
            // Otro hilo pudo haber renovado mientras esperábamos el lock
            val actual = tokenManager.getToken()
            if (!actual.isNullOrBlank() && actual != tokenEnviado) {
                return conToken(response.request, actual)
            }

            val refresh = tokenManager.getRefreshToken()
            if (refresh.isNullOrBlank()) {
                cerrarSesion()
                return null
            }

            return when (val r = refrescar(refresh)) {
                is Resultado.Ok -> conToken(response.request, r.access)
                Resultado.Rechazado -> {
                    cerrarSesion()
                    null
                }
                Resultado.SinRed -> null // se conserva la sesión; la UI muestra el error de red
            }
        }
    }

    private fun refrescar(refresh: String): Resultado {
        val url = tokenManager.getBaseUrl() + "api/auth/refresh/"
        val cuerpo = JSONObject().put("refresh", refresh).toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        val peticion = Request.Builder().url(url).post(cuerpo).build()
        return try {
            clienteRefresh.newCall(peticion).execute().use { r ->
                if (!r.isSuccessful) return Resultado.Rechazado
                val json = JSONObject(r.body?.string().orEmpty())
                val access = json.optString("access")
                if (access.isBlank()) return Resultado.Rechazado
                // Si el backend activa ROTATE_REFRESH_TOKENS también devuelve un refresh nuevo
                tokenManager.updateTokens(access, json.optString("refresh").takeIf { it.isNotBlank() })
                Resultado.Ok(access)
            }
        } catch (e: IOException) {
            Resultado.SinRed
        } catch (e: Exception) {
            Resultado.Rechazado
        }
    }

    private fun cerrarSesion() {
        tokenManager.clear()
        SesionEvents.notificarExpirada()
    }

    private fun conToken(request: Request, token: String): Request =
        request.newBuilder().header("Authorization", "Bearer $token").build()

    private fun intentosPrevios(response: Response): Int {
        var r = response.priorResponse
        var n = 0
        while (r != null) {
            n++
            r = r.priorResponse
        }
        return n
    }

    private sealed class Resultado {
        data class Ok(val access: String) : Resultado()
        object Rechazado : Resultado()
        object SinRed : Resultado()
    }
}
