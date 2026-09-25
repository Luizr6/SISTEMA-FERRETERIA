package com.ferreteria.movil.ui.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ferreteria.movil.data.model.LoginRequest
import com.ferreteria.movil.data.model.LoginResponse
import com.ferreteria.movil.data.repository.AuthRepository
import com.ferreteria.movil.ui.common.Resource
import kotlinx.coroutines.launch

/**
 * ViewModel encargado de la lógica de autenticación en la pantalla de Login.
 */
class LoginViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _loginState = MutableLiveData<Resource<LoginResponse>?>()
    val loginState: LiveData<Resource<LoginResponse>?> = _loginState

    private val _isMockMode = MutableLiveData<Boolean>()
    val isMockMode: LiveData<Boolean> = _isMockMode

    init {
        _isMockMode.value = authRepository.isMockEnabled()
    }

    /**
     * Intenta autenticar al usuario validando campos antes de invocar el repositorio.
     */
    fun login(username: String, password: String) {
        val trimmedUsername = username.trim()
        val trimmedPassword = password.trim()

        if (trimmedUsername.isEmpty()) {
            _loginState.value = Resource.Error("Por favor ingrese su nombre de usuario.")
            return
        }

        if (trimmedPassword.isEmpty()) {
            _loginState.value = Resource.Error("Por favor ingrese su contraseña.")
            return
        }

        _loginState.value = Resource.Loading
        viewModelScope.launch {
            val result = authRepository.login(LoginRequest(trimmedUsername, trimmedPassword))
            _loginState.postValue(result)
        }
    }

    /**
     * Cambia la configuración entre el modo Mock (simulado) y el backend real.
     */
    fun setMockMode(enabled: Boolean) {
        authRepository.setMockEnabled(enabled)
        _isMockMode.value = enabled
    }

    /**
     * Comprueba si existe una sesión previa guardada.
     */
    fun isUserLoggedIn(): Boolean {
        return authRepository.isLoggedIn()
    }

    /**
     * Limpia el estado de inicio de sesión para no repetir notificaciones de error.
     */
    fun clearState() {
        _loginState.value = null
    }
}
