package com.ferreteria.movil.ui.detalle

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ferreteria.movil.data.model.Producto
import com.ferreteria.movil.data.repository.ProductoRepository
import com.ferreteria.movil.ui.common.Resource
import kotlinx.coroutines.launch

/**
 * ViewModel para gestionar la creación (POST), edición (PUT) y eliminación (DELETE)
 * de productos en el backend de Django, con validaciones robustas y control de errores.
 */
class DetalleProductoViewModel(
    private val productoRepository: ProductoRepository
) : ViewModel() {

    private val _operationState = MutableLiveData<Resource<String>?>()
    val operationState: LiveData<Resource<String>?> = _operationState

    private val _validationError = MutableLiveData<String?>()
    val validationError: LiveData<String?> = _validationError

    /**
     * Valida los datos del formulario y ejecuta la creación o actualización en la API.
     */
    fun guardarProducto(
        existingId: Int?,
        codigo: String,
        nombre: String,
        descripcion: String?,
        categoriaStr: String,
        marcaStr: String?,
        precioCompraStr: String,
        precioVentaStr: String,
        stockActualStr: String,
        stockMinimoStr: String,
        activo: Boolean
    ) {
        val trimmedCodigo = codigo.trim()
        val trimmedNombre = nombre.trim()

        if (trimmedCodigo.isEmpty()) {
            _validationError.value = "El código del producto es obligatorio."
            return
        }

        if (trimmedNombre.isEmpty()) {
            _validationError.value = "El nombre del producto es obligatorio."
            return
        }

        val categoriaId = categoriaStr.trim().toIntOrNull()
        if (categoriaId == null || categoriaId <= 0) {
            _validationError.value = "La categoría debe ser un ID numérico válido (mayor a 0)."
            return
        }

        val marcaId = marcaStr?.trim()?.takeIf { it.isNotEmpty() }?.toIntOrNull()
        if (marcaStr?.isNotBlank() == true && marcaId == null) {
            _validationError.value = "El ID de la marca debe ser un número entero."
            return
        }

        val precioCompra = precioCompraStr.trim().toDoubleOrNull()
        if (precioCompra == null || precioCompra < 0) {
            _validationError.value = "El precio de compra debe ser un número mayor o igual a 0."
            return
        }

        val precioVenta = precioVentaStr.trim().toDoubleOrNull()
        if (precioVenta == null || precioVenta <= 0) {
            _validationError.value = "El precio de venta debe ser un número mayor a 0."
            return
        }

        val stockActual = stockActualStr.trim().toIntOrNull()
        if (stockActual == null || stockActual < 0) {
            _validationError.value = "El stock actual debe ser un número entero no negativo."
            return
        }

        val stockMinimo = stockMinimoStr.trim().toIntOrNull()
        if (stockMinimo == null || stockMinimo < 0) {
            _validationError.value = "El stock mínimo debe ser un número entero no negativo."
            return
        }

        val producto = Producto(
            id = existingId,
            codigo = trimmedCodigo,
            nombre = trimmedNombre,
            descripcion = descripcion?.trim()?.takeIf { it.isNotEmpty() },
            categoria = categoriaId,
            marca = marcaId,
            precioCompra = precioCompra,
            precioVenta = precioVenta,
            stockActual = stockActual,
            stockMinimo = stockMinimo,
            activo = activo
        )

        _operationState.value = Resource.Loading
        viewModelScope.launch {
            if (existingId == null) {
                // Modo Creación (POST /api/productos/)
                when (val result = productoRepository.createProducto(producto)) {
                    is Resource.Success -> {
                        _operationState.postValue(
                            Resource.Success("Producto '${result.data.nombre}' creado exitosamente en el servidor.")
                        )
                    }
                    is Resource.Error -> {
                        _operationState.postValue(Resource.Error(result.message, result.statusCode))
                    }
                    Resource.Loading -> {}
                }
            } else {
                // Modo Edición (PUT /api/productos/{id}/)
                when (val result = productoRepository.updateProducto(existingId, producto)) {
                    is Resource.Success -> {
                        _operationState.postValue(
                            Resource.Success("Producto '${result.data.nombre}' actualizado correctamente.")
                        )
                    }
                    is Resource.Error -> {
                        _operationState.postValue(Resource.Error(result.message, result.statusCode))
                    }
                    Resource.Loading -> {}
                }
            }
        }
    }

    /**
     * Elimina el producto seleccionado del backend (DELETE /api/productos/{id}/).
     */
    fun eliminarProducto(id: Int) {
        _operationState.value = Resource.Loading
        viewModelScope.launch {
            when (val result = productoRepository.deleteProducto(id)) {
                is Resource.Success -> {
                    _operationState.postValue(
                        Resource.Success("Producto eliminado exitosamente del inventario.")
                    )
                }
                is Resource.Error -> {
                    _operationState.postValue(Resource.Error(result.message, result.statusCode))
                }
                Resource.Loading -> {}
            }
        }
    }

    fun clearOperationState() {
        _operationState.value = null
    }

    fun clearValidationError() {
        _validationError.value = null
    }
}
