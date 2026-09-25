package com.ferreteria.movil.data.repository

import com.ferreteria.movil.data.local.TokenManager
import com.ferreteria.movil.data.model.LoginRequest
import com.ferreteria.movil.data.model.LoginResponse
import com.ferreteria.movil.data.remote.ApiService
import com.ferreteria.movil.ui.common.Resource
import kotlinx.coroutines.delay
import java.io.IOException

/**
 * Interfaz del repositorio de autenticación para desacoplar la fuente de datos.
 */
interface AuthRepository {
    suspend fun login(request: LoginRequest): Resource<LoginResponse>
    fun logout()
    fun isLoggedIn(): Boolean
    fun isMockEnabled(): Boolean
    fun setMockEnabled(enabled: Boolean)
    fun getUsername(): String?
    fun getRol(): String?
}

/**
 * Implementación del repositorio de autenticación que soporta tanto consumo
 * del backend real como simulación desacoplada (Mock).
 */
class AuthRepositoryImpl(
    private val apiService: ApiService,
    private val tokenManager: TokenManager
) : AuthRepository {

    override suspend fun login(request: LoginRequest): Resource<LoginResponse> {
        // Si el modo Mock está activo, simulamos la respuesta de login sin invocar la red
        if (tokenManager.isMockModeEnabled()) {
            delay(500) // Simulación de latencia de red

            if (request.username.isBlank() || request.password.isBlank()) {
                return Resource.Error("Usuario y contraseña son requeridos.")
            }

            val rolSimulado = if (request.username.contains("admin", ignoreCase = true)) {
                "ADMIN"
            } else {
                "VENDEDOR"
            }

            val mockToken = "mock_jwt_ferreteria_token_${System.currentTimeMillis()}"
            val response = LoginResponse(
                token = mockToken,
                username = request.username,
                rol = rolSimulado
            )

            // Guardamos la sesión simulada en el TokenManager
            tokenManager.saveAuthData(mockToken, response.username, response.rol)
            return Resource.Success(response)
        }

        // Si el modo Mock está inactivo, consumimos el endpoint real de la API Django
        return try {
            val response = apiService.login(request)
            if (response.isSuccessful && response.body() != null) {
                val loginData = response.body()!!
                tokenManager.saveAuthData(
                    token = loginData.token,
                    username = loginData.username ?: request.username,
                    rol = loginData.rol ?: "VENDEDOR"
                )
                Resource.Success(loginData)
            } else {
                val code = response.code()
                val errorMsg = when (code) {
                    404 -> "Endpoint /api/login/ no encontrado (404). Luis aún no incluyó 'cuentas.urls' en ferreteria/urls.py."
                    400 -> "Credenciales inválidas (400). Verifique usuario y contraseña."
                    500 -> "Error interno del servidor (500). Verifique si simplejwt está instalado en Django."
                    else -> "Error de autenticación (Código: $code): ${response.message()}"
                }
                Resource.Error(errorMsg, code)
            }
        } catch (e: IOException) {
            Resource.Error(
                "Error de red: No se pudo conectar al servidor Django en ${tokenManager.getBaseUrl()}. ¿El servidor está corriendo?",
                -1
            )
        } catch (e: Exception) {
            Resource.Error("Excepción al iniciar sesión: ${e.localizedMessage ?: "Desconocido"}")
        }
    }

    override fun logout() {
        tokenManager.clear()
    }

    override fun isLoggedIn(): Boolean {
        return tokenManager.isLoggedIn()
    }

    override fun isMockEnabled(): Boolean {
        return tokenManager.isMockModeEnabled()
    }

    override fun setMockEnabled(enabled: Boolean) {
        tokenManager.setMockModeEnabled(enabled)
    }

    override fun getUsername(): String? {
        return tokenManager.getUsername()
    }

    override fun getRol(): String? {
        return tokenManager.getRol()
    }
}
