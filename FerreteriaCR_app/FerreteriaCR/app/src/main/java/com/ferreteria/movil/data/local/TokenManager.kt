package com.ferreteria.movil.data.local

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Sesión guardada en el celular:
 *  - tokens JWT de Django (access + refresh); TokenAuthenticator renueva el access solo.
 *  - usuario y rol (ADMIN / VENDEDOR).
 *  - PIN de 4 dígitos (se guarda solo su hash con sal, nunca el PIN).
 *  - dirección del servidor (emulador, IP de la PC o la URL de ngrok).
 */
class TokenManager private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "ferreteria_sesion"
        private const val KEY_TOKEN = "access"
        private const val KEY_REFRESH = "refresh"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USERNAME = "username"
        private const val KEY_ROL = "rol"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_SAL = "pin_sal"
        private const val KEY_PIN_INTENTOS = "pin_intentos"
        private const val KEY_ULTIMA_ACTIVIDAD = "ultima_actividad"

        /** Emulador de Android Studio -> localhost de la PC. */
        const val DEFAULT_BASE_URL = "http://10.0.2.2:8000/"

        /** Tiempo fuera de la app después del cual se pide el PIN. */
        const val MINUTOS_BLOQUEO = 30L

        const val MAX_INTENTOS_PIN = 5

        @Volatile
        private var instance: TokenManager? = null

        fun getInstance(context: Context): TokenManager =
            instance ?: synchronized(this) {
                instance ?: TokenManager(context.applicationContext).also { instance = it }
            }
    }

    // ------------------------------------------------------------------ sesión
    fun saveSession(access: String, refresh: String?, userId: Int?, username: String?, rol: String?) {
        prefs.edit()
            .putString(KEY_TOKEN, access)
            .putString(KEY_REFRESH, refresh)
            .putInt(KEY_USER_ID, userId ?: -1)
            .putString(KEY_USERNAME, username)
            .putString(KEY_ROL, rol)
            .putInt(KEY_PIN_INTENTOS, 0)
            .commit()
        marcarActividad()
    }

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH, null)

    /** Lo llama TokenAuthenticator al renovar (síncrono: la siguiente petición debe ver el token nuevo). */
    fun updateTokens(access: String, refresh: String?) {
        val editor = prefs.edit().putString(KEY_TOKEN, access)
        if (!refresh.isNullOrBlank()) editor.putString(KEY_REFRESH, refresh)
        editor.commit()
    }

    fun getUserId(): Int = prefs.getInt(KEY_USER_ID, -1)

    fun getUsername(): String = prefs.getString(KEY_USERNAME, null).orEmpty()

    fun getRol(): String = prefs.getString(KEY_ROL, null).orEmpty()

    fun isAdmin(): Boolean = getRol().equals("ADMIN", ignoreCase = true)

    fun isLoggedIn(): Boolean = !getToken().isNullOrBlank() && !getRefreshToken().isNullOrBlank()

    /** Cierra la sesión: borra tokens y PIN (el siguiente usuario crea el suyo). */
    fun clear() {
        prefs.edit()
            .remove(KEY_TOKEN).remove(KEY_REFRESH).remove(KEY_USER_ID)
            .remove(KEY_USERNAME).remove(KEY_ROL)
            .remove(KEY_PIN_HASH).remove(KEY_PIN_SAL).remove(KEY_PIN_INTENTOS)
            .remove(KEY_ULTIMA_ACTIVIDAD)
            .commit()
    }

    // ------------------------------------------------------------------ servidor
    fun getBaseUrl(): String = prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL

    fun setBaseUrl(url: String) {
        var limpia = url.trim()
        if (!limpia.startsWith("http://") && !limpia.startsWith("https://")) limpia = "http://$limpia"
        if (!limpia.endsWith("/")) limpia += "/"
        prefs.edit().putString(KEY_BASE_URL, limpia).apply()
    }

    // ------------------------------------------------------------------ PIN
    fun tienePin(): Boolean = !prefs.getString(KEY_PIN_HASH, null).isNullOrBlank()

    fun guardarPin(pin: String) {
        val sal = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        prefs.edit()
            .putString(KEY_PIN_SAL, sal)
            .putString(KEY_PIN_HASH, hash(sal, pin))
            .putInt(KEY_PIN_INTENTOS, 0)
            .apply()
    }

    /** true si el PIN es correcto. Cuenta los intentos fallidos. */
    fun verificarPin(pin: String): Boolean {
        val sal = prefs.getString(KEY_PIN_SAL, null) ?: return false
        val ok = hash(sal, pin) == prefs.getString(KEY_PIN_HASH, null)
        prefs.edit().putInt(KEY_PIN_INTENTOS, if (ok) 0 else intentosFallidos() + 1).apply()
        if (ok) marcarActividad()
        return ok
    }

    fun intentosFallidos(): Int = prefs.getInt(KEY_PIN_INTENTOS, 0)

    private fun hash(sal: String, pin: String): String =
        MessageDigest.getInstance("SHA-256").digest("$sal:$pin".toByteArray())
            .joinToString("") { "%02x".format(it) }

    // ------------------------------------------------------------------ bloqueo por inactividad
    fun marcarActividad() {
        prefs.edit().putLong(KEY_ULTIMA_ACTIVIDAD, System.currentTimeMillis()).apply()
    }

    /** true si pasó más de [MINUTOS_BLOQUEO] desde que el usuario dejó la app. */
    fun debeBloquear(): Boolean {
        val ultima = prefs.getLong(KEY_ULTIMA_ACTIVIDAD, 0L)
        return System.currentTimeMillis() - ultima > MINUTOS_BLOQUEO * 60_000L
    }
}
