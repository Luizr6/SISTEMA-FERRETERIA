package com.ferreteria.movil.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.ferreteria.movil.data.model.Producto
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Línea del pedido en armado. Se guarda una copia del precio y del stock que había
 * al agregarla solo para mostrarla; el backend recalcula todo con los datos vigentes.
 */
data class ItemCarrito(
    val productoId: Int,
    val codigo: String,
    val nombre: String,
    val unidad: String?,
    val precioUnitario: Double,
    val stockDisponible: Int,
    val cantidad: Int
) {
    val subtotal: Double get() = precioUnitario * cantidad
    val excedeStock: Boolean get() = cantidad > stockDisponible
}

/**
 * Datos de la venta en armado: cliente elegido (o "Cliente varios"), método de pago y nota.
 */
data class DatosVenta(
    val clienteId: Int? = null,
    val clienteNombre: String = "",
    val clienteDocumento: String = "",
    val metodoPago: String = "EFECTIVO",
    val observacion: String = ""
)

/**
 * Venta en armado guardada en el celular: se guarda en cada cambio, así no se pierde
 * si se cierra la app o se corta la señal.
 * Se guarda en SharedPreferences en cada cambio, así sobrevive a cierres de la app,
 * a la falta de señal y al cierre de sesión. Solo se borra cuando el backend
 * confirma la venta.
 */
class CarritoStore private constructor(context: Context) {

    companion object {
        private const val PREFS = "ferreteria_carrito"
        private const val KEY_ITEMS = "items_json"
        private const val KEY_CLIENTE = "datos_venta_json"

        /** IGV usado solo para mostrar el total estimado (el backend usa IGV_TASA = 0.18). */
        const val IGV = 0.18

        @Volatile
        private var instance: CarritoStore? = null

        fun getInstance(context: Context): CarritoStore {
            return instance ?: synchronized(this) {
                instance ?: CarritoStore(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _items = MutableLiveData<List<ItemCarrito>>(cargarItems())
    val items: LiveData<List<ItemCarrito>> = _items

    fun actuales(): List<ItemCarrito> = _items.value.orEmpty()

    fun totalUnidades(): Int = actuales().sumOf { it.cantidad }

    fun subtotal(): Double = actuales().sumOf { it.subtotal }

    fun igv(): Double = Math.round(subtotal() * IGV * 100.0) / 100.0

    fun total(): Double = subtotal() + igv()

    fun cantidadDe(productoId: Int): Int = actuales().firstOrNull { it.productoId == productoId }?.cantidad ?: 0

    /** Suma [cantidad] al producto (o lo agrega si no estaba). */
    fun agregar(producto: Producto, cantidad: Int) {
        val id = producto.id ?: return
        if (cantidad <= 0) return
        val lista = actuales().toMutableList()
        val idx = lista.indexOfFirst { it.productoId == id }
        val nuevo = ItemCarrito(
            productoId = id,
            codigo = producto.codigo,
            nombre = producto.nombre,
            unidad = producto.unidadMedida,
            precioUnitario = producto.precioVenta,
            stockDisponible = producto.stockActual,
            cantidad = (lista.getOrNull(idx)?.cantidad ?: 0) + cantidad
        )
        if (idx >= 0) lista[idx] = nuevo else lista.add(nuevo)
        guardar(lista)
    }

    fun cambiarCantidad(productoId: Int, cantidad: Int) {
        if (cantidad <= 0) {
            quitar(productoId)
            return
        }
        guardar(actuales().map { if (it.productoId == productoId) it.copy(cantidad = cantidad) else it })
    }

    fun quitar(productoId: Int) {
        guardar(actuales().filterNot { it.productoId == productoId })
    }

    /** Actualiza precio/stock mostrados con el catálogo recién descargado. */
    fun refrescarDesdeCatalogo(productos: List<Producto>) {
        val porId = productos.filter { it.id != null }.associateBy { it.id!! }
        val actual = actuales()
        if (actual.isEmpty()) return
        val nueva = actual.map { item ->
            val p = porId[item.productoId] ?: return@map item
            item.copy(precioUnitario = p.precioVenta, stockDisponible = p.stockActual, nombre = p.nombre)
        }
        if (nueva != actual) guardar(nueva)
    }

    fun vaciar() {
        guardar(emptyList())
        guardarDatos(DatosVenta())
    }

    fun datos(): DatosVenta {
        val json = prefs.getString(KEY_CLIENTE, null) ?: return DatosVenta()
        return try {
            gson.fromJson(json, DatosVenta::class.java) ?: DatosVenta()
        } catch (_: Exception) {
            DatosVenta()
        }
    }

    fun guardarDatos(datos: DatosVenta) {
        prefs.edit().putString(KEY_CLIENTE, gson.toJson(datos)).apply()
    }

    private fun guardar(lista: List<ItemCarrito>) {
        prefs.edit().putString(KEY_ITEMS, gson.toJson(lista)).apply()
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            _items.value = lista
        } else {
            _items.postValue(lista)
        }
    }

    private fun cargarItems(): List<ItemCarrito> {
        val json = prefs.getString(KEY_ITEMS, null) ?: return emptyList()
        return try {
            val tipo = object : TypeToken<List<ItemCarrito>>() {}.type
            gson.fromJson<List<ItemCarrito>>(json, tipo).orEmpty()
                .filter { it.cantidad > 0 }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
