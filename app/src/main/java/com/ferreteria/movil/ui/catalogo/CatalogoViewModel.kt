package com.ferreteria.movil.ui.catalogo

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ferreteria.movil.data.model.Producto
import com.ferreteria.movil.data.repository.AuthRepository
import com.ferreteria.movil.data.repository.ProductoRepository
import com.ferreteria.movil.ui.common.Resource
import kotlinx.coroutines.launch

/**
 * ViewModel que gestiona la carga, búsqueda y filtrado reactivo del catálogo de productos.
 */
class CatalogoViewModel(
    private val productoRepository: ProductoRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _productosState = MutableLiveData<Resource<List<Producto>>>()
    val productosState: LiveData<Resource<List<Producto>>> = _productosState

    private val _filteredList = MutableLiveData<List<Producto>>()
    val filteredList: LiveData<List<Producto>> = _filteredList

    private val _stockCriticoCount = MutableLiveData<Int>()
    val stockCriticoCount: LiveData<Int> = _stockCriticoCount

    private var rawList: List<Producto> = emptyList()
    private var currentFilterQuery: String = ""

    init {
        loadProductos()
    }

    /**
     * Consulta el inventario en tiempo real desde el endpoint GET /api/productos/
     */
    fun loadProductos() {
        _productosState.value = Resource.Loading
        viewModelScope.launch {
            val result = productoRepository.getProductos()
            if (result is Resource.Success) {
                rawList = result.data
                applyFilter(currentFilterQuery)
                val criticos = rawList.count { it.isStockCritico() }
                _stockCriticoCount.postValue(criticos)
            }
            _productosState.postValue(result)
        }
    }

    /**
     * Aplica filtro local por nombre o código del producto.
     */
    fun filter(query: String) {
        currentFilterQuery = query.trim()
        applyFilter(currentFilterQuery)
    }

    private fun applyFilter(query: String) {
        val filtered = if (query.isEmpty()) {
            rawList
        } else {
            rawList.filter { producto ->
                producto.nombre.contains(query, ignoreCase = true) ||
                        producto.codigo.contains(query, ignoreCase = true)
            }
        }
        _filteredList.value = filtered
    }

    fun getCurrentUsername(): String {
        return authRepository.getUsername() ?: "Usuario"
    }

    fun getCurrentRol(): String {
        return authRepository.getRol() ?: "VENDEDOR"
    }

    fun logout() {
        authRepository.logout()
    }
}
