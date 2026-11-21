package com.ferreteria.movil.ui.common

/**
 * Envoltorio sellado para comunicar el estado de operaciones asíncronas
 * hacia la capa de presentación (UI).
 */
sealed class Resource<out T> {
    /**
     * Operación exitosa con los datos resultantes.
     */
    data class Success<out T>(val data: T) : Resource<T>()

    /**
     * Operación fallida con mensaje de error descriptivo y código HTTP opcional.
     */
    data class Error(val message: String, val statusCode: Int? = null) : Resource<Nothing>()

    /**
     * Estado de carga en progreso.
     */
    object Loading : Resource<Nothing>()
}
