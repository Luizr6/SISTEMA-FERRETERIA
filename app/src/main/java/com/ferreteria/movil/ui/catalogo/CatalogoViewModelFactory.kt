package com.ferreteria.movil.ui.catalogo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ferreteria.movil.data.repository.AuthRepository
import com.ferreteria.movil.data.repository.ProductoRepository

/**
 * Factory para instanciar CatalogoViewModel con sus repositorios.
 */
class CatalogoViewModelFactory(
    private val productoRepository: ProductoRepository,
    private val authRepository: AuthRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CatalogoViewModel::class.java)) {
            return CatalogoViewModel(productoRepository, authRepository) as T
        }
        throw IllegalArgumentException("Clase de ViewModel desconocida: ${modelClass.name}")
    }
}
