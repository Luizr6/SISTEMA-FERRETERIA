package com.ferreteria.movil.data

import android.content.Context
import com.ferreteria.movil.data.local.TokenManager
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
import com.ferreteria.movil.data.remote.ApiClient
import com.ferreteria.movil.data.remote.ApiErrors
import com.ferreteria.movil.data.remote.ApiService
import com.ferreteria.movil.data.remote.SesionEvents
import com.ferreteria.movil.data.remote.llamar
import com.ferreteria.movil.ui.common.Resource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Único punto de acceso a los datos: todas las pantallas piden aquí y nunca llaman a Retrofit
 * directo. Cada función devuelve Resource.Success o Resource.Error con un mensaje claro.
 */
class Repositorio(context: Context) {

    private val appContext = context.applicationContext
    private val sesion = TokenManager.getInstance(appContext)
    private val api: ApiService get() = ApiClient.get(appContext)

    // ------------------------------------------------------------------ sesión
    suspend fun login(usuario: String, clave: String): Resource<Unit> = withContext(Dispatchers.IO) {
        try {
            val r = api.login(LoginRequest(usuario.trim(), clave))
            val body = r.body()
            if (r.isSuccessful && body != null && !body.access.isNullOrBlank()) {
                sesion.saveSession(body.access, body.refresh, body.userId, body.username ?: usuario.trim(), body.rol ?: "VENDEDOR")
                SesionEvents.reiniciar()
                Resource.Success(Unit)
            } else {
                val msg = when (r.code()) {
                    400, 401 -> "Usuario o contraseña incorrectos."
                    404 -> "El servidor no respondió en /api/auth/login/. Revisa la dirección del servidor."
                    else -> ApiErrors.mensaje(r.code(), r.errorBody()?.string())
                }
                Resource.Error(msg, r.code())
            }
        } catch (e: IOException) {
            Resource.Error("No hay conexión con el servidor (${sesion.getBaseUrl()}). Revisa que Django esté encendido.", -1)
        } catch (e: Exception) {
            Resource.Error("No se pudo iniciar sesión: ${e.localizedMessage ?: "error desconocido"}")
        }
    }

    // ------------------------------------------------------------------ inicio
    suspend fun dashboard(): Resource<Dashboard> = io { llamar { api.dashboard() } }

    suspend fun preguntar(pregunta: String): Resource<RespuestaAsistente> =
        io { llamar { api.preguntarAsistente(PreguntaRequest(pregunta)) } }

    suspend fun alertas(): Resource<RespuestaAlertas> = io { llamar { api.alertas() } }

    // ------------------------------------------------------------------ catálogo
    suspend fun productos(busqueda: String? = null, categoria: Int? = null): Resource<List<Producto>> =
        io { llamar { api.productos(busqueda?.takeIf { it.isNotBlank() }, categoria) } }

    suspend fun categorias(): Resource<List<Categoria>> = io { llamar { api.categorias() } }

    // ------------------------------------------------------------------ clientes
    suspend fun clientes(busqueda: String? = null): Resource<List<Cliente>> =
        io { llamar { api.clientes(busqueda?.takeIf { it.isNotBlank() }) } }

    suspend fun crearCliente(tipo: String, numero: String, nombre: String, telefono: String): Resource<Cliente> =
        io { llamar { api.crearCliente(ClienteRequest(tipo, numero.trim(), nombre.trim(), telefono.trim())) } }

    // ------------------------------------------------------------------ ventas
    suspend fun crearVenta(request: VentaRequest): Resource<Venta> = io { llamar { api.crearVenta(request) } }

    suspend fun ventas(soloMias: Boolean, desde: String?, hasta: String?, busqueda: String? = null): Resource<List<Venta>> =
        io { llamar { api.ventas(if (soloMias) true else null, desde, hasta, busqueda?.takeIf { it.isNotBlank() }) } }

    suspend fun resumenVentas(soloMias: Boolean, desde: String?, hasta: String?): Resource<ResumenVentas> =
        io { llamar { api.resumenVentas(if (soloMias) true else null, desde, hasta) } }

    suspend fun venta(id: Int): Resource<Venta> = io { llamar { api.venta(id) } }

    suspend fun anularVenta(id: Int, motivo: String): Resource<Venta> =
        io { llamar { api.anularVenta(id, AnularRequest(motivo.trim())) } }

    /** Descarga la boleta en PDF a la caché para abrirla o compartirla por WhatsApp. */
    suspend fun descargarComprobante(id: Int, numero: String): Resource<File> = withContext(Dispatchers.IO) {
        try {
            val r = api.comprobante(id)
            val body = r.body()
            if (!r.isSuccessful || body == null) {
                return@withContext Resource.Error(ApiErrors.mensaje(r.code(), r.errorBody()?.string()), r.code())
            }
            val carpeta = File(appContext.cacheDir, "comprobantes").apply { mkdirs() }
            val archivo = File(carpeta, "boleta_${numero.ifBlank { id.toString() }}.pdf")
            body.byteStream().use { entrada -> archivo.outputStream().use { salida -> entrada.copyTo(salida) } }
            Resource.Success(archivo)
        } catch (e: IOException) {
            Resource.Error(ApiErrors.SIN_RED, -1)
        } catch (e: Exception) {
            Resource.Error("No se pudo descargar la boleta: ${e.localizedMessage ?: ""}")
        }
    }

    // ------------------------------------------------------------------ pedidos
    suspend fun pedidos(estado: String?, origen: String?): Resource<List<Pedido>> =
        io { llamar { api.pedidos(estado, origen) } }

    suspend fun resumenPedidos(): Resource<ResumenPedidos> = io { llamar { api.resumenPedidos() } }

    suspend fun pedido(id: Int): Resource<Pedido> = io { llamar { api.pedido(id) } }

    suspend fun aprobarPedido(id: Int, metodoPago: String): Resource<Pedido> =
        io { llamar { api.aprobarPedido(id, AprobarPedidoRequest(metodoPago = metodoPago)) } }

    suspend fun rechazarPedido(id: Int, motivo: String): Resource<Pedido> =
        io { llamar { api.rechazarPedido(id, MotivoRequest(motivo.trim())) } }

    // ------------------------------------------------------------------ caja
    suspend fun cajaActual(): Resource<CajaActual> = io { llamar { api.cajaActual() } }

    suspend fun abrirCaja(montoInicial: Double): Resource<Caja> =
        io { llamar { api.abrirCaja(AbrirCajaRequest(montoInicial)) } }

    suspend fun movimientoCaja(id: Int, tipo: String, monto: Double, concepto: String): Resource<MovimientoCaja> =
        io { llamar { api.movimientoCaja(id, MovimientoCajaRequest(tipo, monto, concepto.trim())) } }

    suspend fun cerrarCaja(id: Int, contado: Double, observacion: String): Resource<Caja> =
        io { llamar { api.cerrarCaja(id, CerrarCajaRequest(contado, observacion.trim())) } }

    suspend fun cajas(desde: String?, hasta: String?): Resource<List<Caja>> = io { llamar { api.cajas(desde, hasta) } }

    suspend fun caja(id: Int): Resource<Caja> = io { llamar { api.caja(id) } }

    // ------------------------------------------------------------------ WhatsApp
    suspend fun conversaciones(busqueda: String? = null): Resource<List<Conversacion>> =
        io { llamar { api.conversaciones(busqueda?.takeIf { it.isNotBlank() }) } }

    suspend fun conversacion(id: Int): Resource<Conversacion> = io { llamar { api.conversacion(id) } }

    suspend fun mensajes(id: Int): Resource<List<Mensaje>> = io { llamar { api.mensajes(id) } }

    suspend fun responder(id: Int, texto: String): Resource<Mensaje> =
        io { llamar { api.responder(id, TextoRequest(texto.trim())) } }

    suspend fun cambiarBot(id: Int, activo: Boolean): Resource<Conversacion> =
        io { llamar { api.cambiarBot(id, BotRequest(activo)) } }

    private suspend fun <T> io(bloque: suspend () -> Resource<T>): Resource<T> =
        withContext(Dispatchers.IO) { bloque() }
}
