package com.ferreteria.movil.data.remote

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

/**
 * Aviso global de "sesión vencida": lo emite TokenAuthenticator cuando el refresh token
 * ya no sirve. Las pantallas lo observan para volver al login.
 */
object SesionEvents {
    private val _expirada = MutableLiveData(false)
    val expirada: LiveData<Boolean> = _expirada

    fun notificarExpirada() {
        _expirada.postValue(true)
    }

    /** Se llama tras un login exitoso (desde el hilo principal, para que el cambio sea inmediato). */
    fun reiniciar() {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            _expirada.value = false
        } else {
            _expirada.postValue(false)
        }
    }
}
