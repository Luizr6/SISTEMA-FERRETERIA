package com.ferreteria.movil.ui.detalle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ferreteria.movil.data.repository.ProductoRepository

/**
 * Factory para la instanciación de DetalleProductoViewModel.
 */
class DetalleProductoViewModelFactory(
    private val productoRepository: ProductoRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DetalleProductoViewModel::class.java)) {
            return DetalleProductoViewModel(productoRepository) as T
        }
        throw IllegalArgumentException("Clase de ViewModel desconocida: ${modelClass.name}")
    }
}
