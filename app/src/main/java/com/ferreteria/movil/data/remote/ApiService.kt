package com.ferreteria.movil.data.remote

import com.ferreteria.movil.data.model.LoginRequest
import com.ferreteria.movil.data.model.LoginResponse
import com.ferreteria.movil.data.model.Producto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Definición de endpoints de Retrofit para interactuar con la API REST de Django.
 */
interface ApiService {

    /**
     * Endpoint de autenticación.
     * Mapeado a cuentas/views.py (LoginView).
     * Nota: Pendiente de inclusión en ferreteria/urls.py por parte del backend.
     */
    @POST("api/login/")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<LoginResponse>

    /**
     * Obtener listado de productos del inventario.
     * Mapeado a productos/views.py (ProductoViewSet - ModelViewSet).
     */
    @GET("api/productos/")
    suspend fun getProductos(
        @Query("search") search: String? = null
    ): Response<List<Producto>>

    /**
     * Obtener un producto específico por su ID primario.
     */
    @GET("api/productos/{id}/")
    suspend fun getProductoById(
        @Path("id") id: Int
    ): Response<Producto>

    /**
     * Crear un nuevo producto en el catálogo.
     */
    @POST("api/productos/")
    suspend fun createProducto(
        @Body producto: Producto
    ): Response<Producto>

    /**
     * Actualizar completamente un producto existente.
     */
    @PUT("api/productos/{id}/")
    suspend fun updateProducto(
        @Path("id") id: Int,
        @Body producto: Producto
    ): Response<Producto>

    /**
     * Eliminar un producto del catálogo.
     */
    @DELETE("api/productos/{id}/")
    suspend fun deleteProducto(
        @Path("id") id: Int
    ): Response<Unit>
}
