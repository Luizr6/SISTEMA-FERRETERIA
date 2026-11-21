package com.ferreteria.movil.ui.vender

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Spinner
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.ferreteria.movil.R
import com.ferreteria.movil.data.Repositorio
import com.ferreteria.movil.data.local.CarritoStore
import com.ferreteria.movil.data.local.DatosVenta
import com.ferreteria.movil.data.local.ItemCarrito
import com.ferreteria.movil.data.model.Cliente
import com.ferreteria.movil.data.model.DetalleVentaRequest
import com.ferreteria.movil.data.model.MetodosPago
import com.ferreteria.movil.data.model.VentaRequest
import com.ferreteria.movil.databinding.ActivityCobroBinding
import com.ferreteria.movil.databinding.ItemCarritoBinding
import com.ferreteria.movil.ui.caja.ofrecerAbrirCaja
import com.ferreteria.movil.ui.common.BaseActivity
import com.ferreteria.movil.ui.common.Formato
import com.ferreteria.movil.ui.common.Resource
import com.ferreteria.movil.ui.common.aviso
import com.ferreteria.movil.ui.common.visible
import com.ferreteria.movil.ui.ventas.VentaDetalleActivity
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Cobro: revisar productos, elegir cliente (o "Clientes varios"), forma de pago y confirmar.
 * Django calcula precios, IGV y descuenta el stock; aquí solo se muestra el total estimado.
 */
class CobroActivity : BaseActivity() {

    private lateinit var binding: ActivityCobroBinding
    private lateinit var carrito: CarritoStore
    private lateinit var repo: Repositorio
    private var enviando = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCobroBinding.inflate(layoutInflater)
        setContentView(binding.root)
        carrito = CarritoStore.getInstance(this)
        repo = Repositorio(this)

        binding.btnVolver.setOnClickListener { finish() }
        binding.btnVaciar.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("¿Vaciar la venta?")
                .setMessage("Se quitarán todos los productos.")
                .setPositiveButton("Vaciar") { _, _ ->
                    carrito.vaciar()
                    finish()
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
        binding.filaCliente.setOnClickListener { elegirCliente() }
        binding.etRecibido.doAfterTextChanged { pintarVuelto() }
        binding.btnConfirmar.setOnClickListener { confirmar() }

        crearMetodosPago()
        carrito.items.observe(this) { items ->
            if (items.isEmpty() && !enviando) {
                finish()
                return@observe
            }
            pintarItems(items)
        }
        pintarCliente()
    }

    private fun crearMetodosPago() {
        val elegido = carrito.datos().metodoPago
        MetodosPago.TODOS.forEach { (codigo, nombre) ->
            val chip = layoutInflater.inflate(R.layout.chip_filtro, binding.grupoPago, false) as Chip
            chip.id = View.generateViewId()
            chip.text = nombre
            chip.tag = codigo
            chip.isChecked = codigo == elegido
            binding.grupoPago.addView(chip)
        }
        binding.grupoPago.setOnCheckedStateChangeListener { g, ids ->
            val codigo = ids.firstOrNull()?.let { g.findViewById<Chip>(it)?.tag as? String } ?: return@setOnCheckedStateChangeListener
            carrito.guardarDatos(carrito.datos().copy(metodoPago = codigo))
            binding.bloqueEfectivo.visible(codigo == "EFECTIVO")
        }
        binding.bloqueEfectivo.visible(elegido == "EFECTIVO")
    }

    private fun pintarItems(items: List<ItemCarrito>) {
        binding.listaItems.removeAllViews()
        items.forEach { item ->
            val b = ItemCarritoBinding.inflate(layoutInflater, binding.listaItems, false)
            val precio = Formato.conIgv(item.precioUnitario)
            b.txtNombre.text = item.nombre
            b.txtPrecio.text = "${Formato.soles(precio)} c/u"
            b.txtCantidad.text = item.cantidad.toString()
            b.txtSubtotal.text = Formato.soles(Formato.redondear(precio * item.cantidad))
            b.txtAviso.visible(item.excedeStock)
            b.txtAviso.text = "Solo hay ${item.stockDisponible} en stock"
            b.btnMenos.setOnClickListener { carrito.cambiarCantidad(item.productoId, item.cantidad - 1) }
            b.btnMas.setOnClickListener {
                if (item.cantidad >= item.stockDisponible) aviso("No hay más stock de ${item.nombre}.")
                else carrito.cambiarCantidad(item.productoId, item.cantidad + 1)
            }
            binding.listaItems.addView(b.root)
        }
        binding.txtSubtotal.text = Formato.soles(carrito.subtotal())
        binding.txtIgv.text = Formato.soles(carrito.igv())
        binding.txtTotal.text = Formato.soles(carrito.total())
        binding.btnConfirmar.text = "Confirmar venta · ${Formato.soles(carrito.total())}"
        pintarVuelto()
    }

    private fun pintarVuelto() {
        val recibido = binding.etRecibido.text?.toString()?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
        val vuelto = recibido - carrito.total()
        binding.txtVuelto.text = if (recibido <= 0) "S/ 0.00" else Formato.soles(vuelto.coerceAtLeast(0.0))
        binding.txtVuelto.setTextColor(getColor(if (recibido > 0 && vuelto < 0) R.color.rojo else R.color.verde))
    }

    private fun pintarCliente() {
        val d = carrito.datos()
        binding.txtCliente.text = d.clienteNombre.ifBlank { "Clientes varios" }
        binding.txtDocumento.text = d.clienteDocumento.ifBlank { "Boleta sin datos del cliente" }
    }

    // ------------------------------------------------------------------ cliente
    private fun elegirCliente() {
        val campo = EditText(this).apply {
            hint = "Buscar por nombre, DNI o RUC"
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val lista = ListView(this)
        val contenedor = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 16, 48, 0)
            addView(campo)
            addView(lista, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (resources.displayMetrics.density * 300).toInt()))
        }
        var clientes: List<Cliente> = emptyList()
        val adaptador = ArrayAdapter<String>(this, android.R.layout.simple_list_item_2, android.R.id.text1)
        lista.adapter = adaptador

        val dialogo = MaterialAlertDialogBuilder(this)
            .setTitle("Elegir cliente")
            .setView(contenedor)
            .setPositiveButton("Nuevo cliente") { _, _ -> nuevoCliente() }
            .setNeutralButton("Clientes varios") { _, _ ->
                carrito.guardarDatos(carrito.datos().copy(clienteId = null, clienteNombre = "", clienteDocumento = ""))
                pintarCliente()
            }
            .setNegativeButton("Cancelar", null)
            .show()

        var busqueda: Job? = null
        fun buscar(texto: String) {
            busqueda?.cancel()
            busqueda = lifecycleScope.launch {
                delay(300)
                val r = repo.clientes(texto)
                if (r is Resource.Success) {
                    clientes = r.data
                    adaptador.clear()
                    adaptador.addAll(r.data.map { "${it.nombre.orEmpty()}  ·  ${it.documento}" })
                }
            }
        }
        campo.doAfterTextChanged { buscar(it?.toString().orEmpty()) }
        lista.setOnItemClickListener { _, _, pos, _ ->
            val c = clientes.getOrNull(pos) ?: return@setOnItemClickListener
            carrito.guardarDatos(carrito.datos().copy(clienteId = c.id, clienteNombre = c.nombre.orEmpty(), clienteDocumento = c.documento))
            pintarCliente()
            dialogo.dismiss()
        }
        buscar("")
    }

    private fun nuevoCliente() {
        val tipos = listOf("DNI", "RUC", "CE")
        val spTipo = Spinner(this).apply {
            adapter = ArrayAdapter(this@CobroActivity, android.R.layout.simple_spinner_dropdown_item, tipos)
        }
        val etNumero = EditText(this).apply { hint = "Número de documento"; inputType = InputType.TYPE_CLASS_NUMBER }
        val etNombre = EditText(this).apply { hint = "Nombre o razón social"; inputType = InputType.TYPE_TEXT_FLAG_CAP_WORDS or InputType.TYPE_CLASS_TEXT }
        val etTelefono = EditText(this).apply { hint = "Teléfono (opcional)"; inputType = InputType.TYPE_CLASS_PHONE }
        val contenedor = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 16, 48, 0)
            listOf(spTipo, etNumero, etNombre, etTelefono).forEach { addView(it) }
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Nuevo cliente")
            .setView(contenedor)
            .setPositiveButton("Guardar") { _, _ ->
                val tipo = tipos[spTipo.selectedItemPosition]
                val numero = etNumero.text.toString().trim()
                val nombre = etNombre.text.toString().trim()
                if (numero.isBlank() || nombre.isBlank()) {
                    aviso("Escribe el documento y el nombre del cliente.")
                    return@setPositiveButton
                }
                lifecycleScope.launch {
                    when (val r = repo.crearCliente(tipo, numero, nombre, etTelefono.text.toString())) {
                        is Resource.Success -> {
                            val c = r.data
                            carrito.guardarDatos(carrito.datos().copy(clienteId = c.id, clienteNombre = c.nombre.orEmpty(), clienteDocumento = c.documento))
                            pintarCliente()
                            aviso("Cliente registrado.")
                        }
                        is Resource.Error -> aviso(r.message)
                        Resource.Loading -> Unit
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ------------------------------------------------------------------ confirmar
    private fun confirmar() {
        val items = carrito.actuales()
        if (items.isEmpty() || enviando) return
        val sinStock = items.filter { it.excedeStock }
        if (sinStock.isNotEmpty()) {
            aviso("Revisa las cantidades: ${sinStock.joinToString { it.nombre }} supera el stock.")
            return
        }
        val datos: DatosVenta = carrito.datos()
        val request = VentaRequest(
            cliente = datos.clienteId,
            metodoPago = datos.metodoPago,
            origen = "MOVIL",
            observacion = binding.etObservacion.text?.toString().orEmpty().trim(),
            detalles = items.map { DetalleVentaRequest(it.productoId, it.cantidad) }
        )
        enviando = true
        binding.btnConfirmar.isEnabled = false
        binding.btnConfirmar.text = "Registrando…"
        lifecycleScope.launch {
            when (val r = repo.crearVenta(request)) {
                is Resource.Success -> {
                    carrito.vaciar()
                    val venta = r.data
                    startActivity(
                        Intent(this@CobroActivity, VentaDetalleActivity::class.java)
                            .putExtra(VentaDetalleActivity.EXTRA_ID, venta.id ?: 0)
                            .putExtra(VentaDetalleActivity.EXTRA_NUEVA, true)
                    )
                    finish()
                }
                is Resource.Error -> {
                    enviando = false
                    binding.btnConfirmar.isEnabled = true
                    binding.btnConfirmar.text = "Confirmar venta · ${Formato.soles(carrito.total())}"
                    if (ofrecerAbrirCaja(r.message)) return@launch
                    MaterialAlertDialogBuilder(this@CobroActivity)
                        .setTitle("No se registró la venta")
                        .setMessage(r.message)
                        .setPositiveButton("Entendido", null)
                        .show()
                }
                Resource.Loading -> Unit
            }
        }
    }
}
