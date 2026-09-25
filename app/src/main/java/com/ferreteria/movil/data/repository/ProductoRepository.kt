package com.ferreteria.movil.data.repository

import com.ferreteria.movil.data.local.TokenManager
import com.ferreteria.movil.data.model.Producto
import com.ferreteria.movil.data.remote.ApiService
import com.ferreteria.movil.ui.common.Resource
import org.json.JSONObject
import java.io.IOException

/**
 * Contrato de operaciones sobre el inventario y catálogo de productos.
 */
interface ProductoRepository {
    suspend fun getProductos(): Resource<List<Producto>>
    suspend fun getProductoById(id: Int): Resource<Producto>
    suspend fun createProducto(producto: Producto): Resource<Producto>
    suspend fun updateProducto(id: Int, producto: Producto): Resource<Producto>
    suspend fun deleteProducto(id: Int): Resource<Unit>
}

/**
 * Implementación de ProductoRepository que interactúa directamente con el endpoint
 * /api/productos/ expuesto por el ModelViewSet de Django REST Framework.
 */
class ProductoRepositoryImpl(
    private val apiService: ApiService,
    private val tokenManager: TokenManager
) : ProductoRepository {

    override suspend fun getProductos(): Resource<List<Producto>> {
        return try {
            val response = apiService.getProductos()
            if (response.isSuccessful && response.body() != null) {
                Resource.Success(response.body()!!)
            } else {
                val errorBody = response.errorBody()?.string()
                val message = parseError(response.code(), errorBody)
                Resource.Error(message, response.code())
            }
        } catch (e: IOException) {
            Resource.Error(
                "Error de red: No se pudo conectar a la API de Django (${tokenManager.getBaseUrl()}). Verifique que el servidor esté encendido.",
                -1
            )
        } catch (e: Exception) {
            Resource.Error("Excepción al listar productos: ${e.localizedMessage ?: "Error desconocido"}")
        }
    }

    override suspend fun getProductoById(id: Int): Resource<Producto> {
        return try {
            val response = apiService.getProductoById(id)
            if (response.isSuccessful && response.body() != null) {
                Resource.Success(response.body()!!)
            } else {
                val errorBody = response.errorBody()?.string()
                Resource.Error(parseError(response.code(), errorBody), response.code())
            }
        } catch (e: IOException) {
            Resource.Error("Error de conexión al obtener el producto con ID $id.", -1)
        } catch (e: Exception) {
            Resource.Error("Excepción: ${e.localizedMessage ?: "Desconocido"}")
        }
    }

    override suspend fun createProducto(producto: Producto): Resource<Producto> {
        return try {
            val response = apiService.createProducto(producto)
            if (response.isSuccessful && response.body() != null) {
                Resource.Success(response.body()!!)
            } else {
                val errorBody = response.errorBody()?.string()
                val parsed = parseDetailedError(response.code(), errorBody)
                Resource.Error(parsed, response.code())
            }
        } catch (e: IOException) {
            Resource.Error("Error de red al guardar el producto. Verifique la conexión con el backend.", -1)
        } catch (e: Exception) {
            Resource.Error("Excepción al crear producto: ${e.localizedMessage ?: "Desconocido"}")
        }
    }

    override suspend fun updateProducto(id: Int, producto: Producto): Resource<Producto> {
        return try {
            val response = apiService.updateProducto(id, producto)
            if (response.isSuccessful && response.body() != null) {
                Resource.Success(response.body()!!)
            } else {
                val errorBody = response.errorBody()?.string()
                val parsed = parseDetailedError(response.code(), errorBody)
                Resource.Error(parsed, response.code())
            }
        } catch (e: IOException) {
            Resource.Error("Error de red al actualizar el producto.", -1)
        } catch (e: Exception) {
            Resource.Error("Excepción al actualizar producto: ${e.localizedMessage ?: "Desconocido"}")
        }
    }

    override suspend fun deleteProducto(id: Int): Resource<Unit> {
        return try {
            val response = apiService.deleteProducto(id)
            if (response.isSuccessful) {
                Resource.Success(Unit)
            } else {
                val errorBody = response.errorBody()?.string()
                Resource.Error(parseError(response.code(), errorBody), response.code())
            }
        } catch (e: IOException) {
            Resource.Error("Error de red al eliminar el producto.", -1)
        } catch (e: Exception) {
            Resource.Error("Excepción al eliminar: ${e.localizedMessage ?: "Desconocido"}")
        }
    }

    /**
     * Parsea respuestas de error HTTP estándar de DRF.
     */
    private fun parseError(code: Int, errorBody: String?): String {
        return when (code) {
            401 -> "Sesión expirada o no autorizada (401). Inicie sesión nuevamente."
            403 -> "Acceso denegado (403): Permisos insuficientes."
            404 -> "Recurso no encontrado (404) en la API."
            500 -> "Error interno del servidor Django (500)."
            else -> errorBody ?: "Error HTTP $code devuelto por el backend."
        }
    }

    /**
     * Parsea errores de validación específicos devueltos por el serializer de DRF
     * (por ejemplo: {"codigo":["producto with this codigo already exists."]})
     */
    private fun parseDetailedError(code: Int, errorBody: String?): String {
        if (errorBody.isNullOrBlank()) return parseError(code, null)
        return try {
            val json = JSONObject(errorBody)
            val builder = StringBuilder()
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = json.opt(key)
                builder.append("• Campo '$key': $value\n")
            }
            if (builder.isNotEmpty()) builder.toString().trim() else errorBody
        } catch (_: Exception) {
            parseError(code, errorBody)
        }
    }
}
