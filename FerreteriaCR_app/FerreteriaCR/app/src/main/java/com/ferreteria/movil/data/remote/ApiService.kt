package com.ferreteria.movil.data.remote

import com.ferreteria.movil.data.model.AbrirCajaRequest
import com.ferreteria.movil.data.model.AnularRequest
import com.ferreteria.movil.data.model.AprobarPedidoRequest
import com.ferreteria.movil.data.model.BotRequest
import com.ferreteria.movil.data.model.Caja
import com.ferreteria.movil.data.model.CajaActual
import com.ferreteria.movil.data.model.CerrarCajaRequest
import com.ferreteria.movil.data.model.Categoria
import com.ferreteria.movil.data.model.Cliente
import com.ferreteria.movil.data.model.ClienteRequest
import com.ferreteria.movil.data.model.Conversacion
import com.ferreteria.movil.data.model.Dashboard
import com.ferreteria.movil.data.model.LoginRequest
import com.ferreteria.movil.data.model.LoginResponse
import com.ferreteria.movil.data.model.Mensaje
import com.ferreteria.movil.data.model.MotivoRequest
import com.ferreteria.movil.data.model.MovimientoCaja
import com.ferreteria.movil.data.model.MovimientoCajaRequest
import com.ferreteria.movil.data.model.Pedido
import com.ferreteria.movil.data.model.PreguntaRequest
import com.ferreteria.movil.data.model.Producto
import com.ferreteria.movil.data.model.RespuestaAlertas
import com.ferreteria.movil.data.model.RespuestaAsistente
import com.ferreteria.movil.data.model.ResumenPedidos
import com.ferreteria.movil.data.model.ResumenVentas
import com.ferreteria.movil.data.model.TextoRequest
import com.ferreteria.movil.data.model.Venta
import com.ferreteria.movil.data.model.VentaRequest
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

/**
 * API REST del backend Django (única fuente de datos). Todas las rutas cuelgan de /api/.
 * La app nunca habla con Groq ni con Meta: todo pasa por Django.
 */
interface ApiService {

    // ------------------------------------------------------------------ Sesión
    @POST("api/auth/login/")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    // ------------------------------------------------------------------ Inicio
    @GET("api/dashboard/")
    suspend fun dashboard(): Response<Dashboard>

    /** Solo Administrador: resumen redactado por la IA con los datos reales. */
    @POST("api/asistente/")
    suspend fun preguntarAsistente(@Body request: PreguntaRequest): Response<RespuestaAsistente>

    /** Solo Administrador: avisos para las notificaciones. */
    @GET("api/asistente/alertas/")
    suspend fun alertas(): Response<RespuestaAlertas>

    // ------------------------------------------------------------------ Productos
    @GET("api/productos/")
    suspend fun productos(
        @Query("search") search: String? = null,
        @Query("categoria") categoria: Int? = null,
        @Query("activo") activo: Boolean? = true
    ): Response<List<Producto>>

    @GET("api/categorias/")
    suspend fun categorias(): Response<List<Categoria>>

    // ------------------------------------------------------------------ Clientes
    @GET("api/clientes/")
    suspend fun clientes(
        @Query("search") search: String? = null,
        @Query("activo") activo: Boolean? = true
    ): Response<List<Cliente>>

    @POST("api/clientes/")
    suspend fun crearCliente(@Body request: ClienteRequest): Response<Cliente>

    // ------------------------------------------------------------------ Ventas
    @POST("api/ventas/")
    suspend fun crearVenta(@Body request: VentaRequest): Response<Venta>

    @GET("api/ventas/")
    suspend fun ventas(
        @Query("mias") mias: Boolean? = null,
        @Query("fecha_desde") fechaDesde: String? = null,
        @Query("fecha_hasta") fechaHasta: String? = null,
        @Query("search") search: String? = null
    ): Response<List<Venta>>

    @GET("api/ventas/resumen/")
    suspend fun resumenVentas(
        @Query("mias") mias: Boolean? = null,
        @Query("fecha_desde") fechaDesde: String? = null,
        @Query("fecha_hasta") fechaHasta: String? = null
    ): Response<ResumenVentas>

    @GET("api/ventas/{id}/")
    suspend fun venta(@Path("id") id: Int): Response<Venta>

    @Streaming
    @GET("api/ventas/{id}/comprobante/")
    suspend fun comprobante(@Path("id") id: Int): Response<ResponseBody>

    /** Solo Administrador. Reingresa el stock. */
    @POST("api/ventas/{id}/anular/")
    suspend fun anularVenta(@Path("id") id: Int, @Body request: AnularRequest): Response<Venta>

    // ------------------------------------------------------------------ Pedidos
    /** estado: PENDIENTE | APROBADO | "RECHAZADO,CANCELADO" ; origen: WHATSAPP | MOSTRADOR | WEB | MOVIL */
    @GET("api/pedidos/")
    suspend fun pedidos(
        @Query("estado") estado: String? = null,
        @Query("origen") origen: String? = null
    ): Response<List<Pedido>>

    @GET("api/pedidos/resumen/")
    suspend fun resumenPedidos(): Response<ResumenPedidos>

    @GET("api/pedidos/{id}/")
    suspend fun pedido(@Path("id") id: Int): Response<Pedido>

    /** Genera la venta y descuenta stock. Si falta stock responde 400. */
    @POST("api/pedidos/{id}/aprobar/")
    suspend fun aprobarPedido(@Path("id") id: Int, @Body request: AprobarPedidoRequest): Response<Pedido>

    @POST("api/pedidos/{id}/rechazar/")
    suspend fun rechazarPedido(@Path("id") id: Int, @Body request: MotivoRequest): Response<Pedido>

    // ------------------------------------------------------------------ Caja
    @GET("api/cajas/actual/")
    suspend fun cajaActual(): Response<CajaActual>

    @POST("api/cajas/abrir/")
    suspend fun abrirCaja(@Body request: AbrirCajaRequest): Response<Caja>

    @POST("api/cajas/{id}/movimientos/")
    suspend fun movimientoCaja(@Path("id") id: Int, @Body request: MovimientoCajaRequest): Response<MovimientoCaja>

    @POST("api/cajas/{id}/cerrar/")
    suspend fun cerrarCaja(@Path("id") id: Int, @Body request: CerrarCajaRequest): Response<Caja>

    /** Admin: todas las cajas; Vendedor: las suyas. */
    @GET("api/cajas/")
    suspend fun cajas(
        @Query("fecha_desde") fechaDesde: String? = null,
        @Query("fecha_hasta") fechaHasta: String? = null
    ): Response<List<Caja>>

    @GET("api/cajas/{id}/")
    suspend fun caja(@Path("id") id: Int): Response<Caja>

    // ------------------------------------------------------------------ WhatsApp
    @GET("api/whatsapp/conversaciones/")
    suspend fun conversaciones(@Query("search") search: String? = null): Response<List<Conversacion>>

    @GET("api/whatsapp/conversaciones/{id}/")
    suspend fun conversacion(@Path("id") id: Int): Response<Conversacion>

    /** Devuelve el historial y marca el chat como leído. */
    @GET("api/whatsapp/conversaciones/{id}/mensajes/")
    suspend fun mensajes(@Path("id") id: Int): Response<List<Mensaje>>

    /** Respuesta escrita por el vendedor; Django la envía por WhatsApp. */
    @POST("api/whatsapp/conversaciones/{id}/responder/")
    suspend fun responder(@Path("id") id: Int, @Body request: TextoRequest): Response<Mensaje>

    /** Pausa o reactiva el bot en ese chat. */
    @PATCH("api/whatsapp/conversaciones/{id}/")
    suspend fun cambiarBot(@Path("id") id: Int, @Body request: BotRequest): Response<Conversacion>
}
