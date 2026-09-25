package com.ferreteria.movil.data.local

import android.content.Context
import android.content.SharedPreferences

/**
 * Gestor de persistencia local para tokens de autenticación, información de sesión
 * y configuración de entorno (Modo Mock vs API Real).
 */
class TokenManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "ferreteria_auth_prefs"
        private const val KEY_TOKEN = "auth_token"
        private const val KEY_USERNAME = "auth_username"
        private const val KEY_ROL = "auth_rol"
        private const val KEY_MOCK_MODE = "mock_mode_enabled"
        private const val KEY_BASE_URL = "base_url"

        // IP por defecto para Android Emulator accediendo a localhost de la PC host
        const val DEFAULT_BASE_URL = "http://10.0.2.2:8000/"

        @Volatile
        private var instance: TokenManager? = null

        fun getInstance(context: Context): TokenManager {
            return instance ?: synchronized(this) {
                instance ?: TokenManager(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * Guarda los datos de autenticación obtenidos en el login.
     */
    fun saveAuthData(token: String, username: String?, rol: String?) {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_USERNAME, username)
            .putString(KEY_ROL, rol)
            .apply()
    }

    /**
     * Retorna el token almacenado o null si no existe sesión.
     */
    fun getToken(): String? {
        return prefs.getString(KEY_TOKEN, null)
    }

    /**
     * Retorna el nombre de usuario de la sesión actual.
     */
    fun getUsername(): String? {
        return prefs.getString(KEY_USERNAME, null)
    }

    /**
     * Retorna el rol del usuario actual (ADMIN, VENDEDOR).
     */
    fun getRol(): String? {
        return prefs.getString(KEY_ROL, null)
    }

    /**
     * Comprueba si el usuario tiene una sesión activa válida.
     */
    fun isLoggedIn(): Boolean {
        val token = getToken()
        if (token.isNullOrBlank()) return false
        // Si el token es de prueba (mock) pero el modo mock ya no está activo, requiere login real
        if (token.startsWith("mock_") && !isMockModeEnabled()) return false
        return true
    }

    /**
     * Cierra la sesión activa eliminando las credenciales locales.
     */
    fun clear() {
        prefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_USERNAME)
            .remove(KEY_ROL)
            .apply()
    }

    /**
     * Indica si el modo de autenticación simulada (Mock) está activado.
     * Ahora por defecto es FALSE porque el backend de Django ya está corriendo.
     */
    fun isMockModeEnabled(): Boolean {
        return prefs.getBoolean(KEY_MOCK_MODE, false)
    }

    /**
     * Activa o desactiva el modo de autenticación simulada.
     */
    fun setMockModeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MOCK_MODE, enabled).apply()
    }

    /**
     * Obtiene la URL base de la API Django.
     */
    fun getBaseUrl(): String {
        return prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
    }

    /**
     * Permite configurar una URL base personalizada (ej. IP física en red LAN).
     */
    fun setBaseUrl(url: String) {
        val sanitized = if (url.endsWith("/")) url else "$url/"
        prefs.edit().putString(KEY_BASE_URL, sanitized).apply()
    }
}
