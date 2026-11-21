package com.ferreteria.movil.ui.pedidos

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import androidx.lifecycle.lifecycleScope
import com.ferreteria.movil.R
import com.ferreteria.movil.data.Repositorio
import com.ferreteria.movil.data.model.MetodosPago
import com.ferreteria.movil.data.model.Pedido
import com.ferreteria.movil.databinding.ActivityPedidoDetalleBinding
import com.ferreteria.movil.databinding.ItemLineaBinding
import com.ferreteria.movil.ui.caja.ofrecerAbrirCaja
import com.ferreteria.movil.ui.common.BaseActivity
import com.ferreteria.movil.ui.common.Formato
import com.ferreteria.movil.ui.common.Resource
import com.ferreteria.movil.ui.common.aviso
import com.ferreteria.movil.ui.common.visible
import com.ferreteria.movil.ui.ventas.VentaDetalleActivity
import com.ferreteria.movil.ui.whatsapp.ChatActivity
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * Detalle de un pedido. Aprobar genera la venta y descuenta el stock (lo hace Django);
 * rechazar pide un motivo. Si vino de WhatsApp, se puede abrir el chat del cliente.
 */
class PedidoDetalleActivity : BaseActivity() {

    companion object {
        const val EXTRA_ID = "pedido_id"
    }

    private lateinit var binding: ActivityPedidoDetalleBinding
    private lateinit var repo: Repositorio
    private var pedido: Pedido? = null
    private var metodo = "EFECTIVO"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPedidoDetalleBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repo = Repositorio(this)

        binding.btnVolver.setOnClickListener { finish() }
        binding.btnAprobar.setOnClickListener { confirmarAprobacion() }
        binding.btnRechazar.setOnClickListener { pedirMotivo() }
        binding.btnChat.setOnClickListener { abrirChat() }
        crearMetodosPago()
        cargar()
    }

    private fun cargar() {
        val id = intent.getIntExtra(EXTRA_ID, 0)
        lifecycleScope.launch {
            when (val r = repo.pedido(id)) {
                is Resource.Success -> pintar(r.data)
                is Resource.Error -> {
                    aviso(r.message)
                    finish()
                }
                Resource.Loading -> Unit
            }
        }
    }

    private fun crearMetodosPago() {
        listOf("EFECTIVO", "YAPE", "TRANSFERENCIA", "PLIN", "TARJETA").forEach { codigo ->
            val chip = layoutInflater.inflate(R.layout.chip_filtro, binding.grupoPago, false) as Chip
            chip.id = View.generateViewId()
            chip.text = MetodosPago.etiqueta(codigo)
            chip.tag = codigo
            chip.isChecked = codigo == metodo
            binding.grupoPago.addView(chip)
        }
        binding.grupoPago.setOnCheckedStateChangeListener { g, ids ->
            metodo = ids.firstOrNull()?.let { g.findViewById<Chip>(it)?.tag as? String } ?: metodo
        }
    }

    private fun pintar(p: Pedido) {
        pedido = p
        binding.progreso.visible(false)
        binding.contenido.visible(true)
        binding.txtTitulo.text = "Pedido ${p.numero.orEmpty()}"
        binding.txtFecha.text = Formato.fechaHora(p.fecha)

        val (color, fondo) = when (p.estado) {
            "PENDIENTE" -> R.color.ambar_texto to R.color.ambar_suave
            "APROBADO" -> R.color.verde to R.color.verde_fondo
            else -> R.color.rojo to R.color.rojo_fondo
        }
        Formato.pintarEtiqueta(binding.txtEstado, color, fondo, Formato.estadoPedido(p.estado))
        val esWhatsApp = p.origen == "WHATSAPP"
        Formato.pintarEtiqueta(
            binding.txtOrigen,
            if (esWhatsApp) R.color.verde_oscuro else R.color.navy,
            if (esWhatsApp) R.color.verde_fondo else R.color.divisor,
            Formato.origen(p.origen)
        )

        binding.txtCliente.text = p.nombreCliente
        binding.txtTelefono.text = p.contactoTelefono.orEmpty()
        binding.txtTelefono.visible(!p.contactoTelefono.isNullOrBlank())
        binding.btnChat.visible(esWhatsApp && !p.contactoTelefono.isNullOrBlank())

        binding.listaProductos.removeAllViews()
        p.detalles.orEmpty().forEach { d ->
            val fila = ItemLineaBinding.inflate(layoutInflater, binding.listaProductos, false)
            val cantidad = d.cantidad ?: 0
            val precio = Formato.conIgv(d.precioUnitario ?: 0.0)
            fila.txtNombre.text = d.productoNombre
            fila.txtDetalle.text = "$cantidad × ${Formato.soles(precio)}"
            if (p.esPendiente && d.stockSuficiente == false) {
                fila.txtDetalle.text = "$cantidad × ${Formato.soles(precio)} · solo hay ${d.stockDisponible ?: 0} en stock"
                fila.txtDetalle.setTextColor(getColor(R.color.rojo))
            }
            fila.txtMonto.text = Formato.soles(Formato.redondear(precio * cantidad))
            binding.listaProductos.addView(fila.root)
        }
        binding.txtTotal.text = Formato.soles(p.totalEstimado)
        binding.txtNotaStock.visible(p.esPendiente)

        binding.txtObservacion.visible(!p.observacion.isNullOrBlank())
        binding.txtObservacion.text = "Nota: ${p.observacion.orEmpty()}"

        binding.bloquePago.visible(p.esPendiente)
        binding.barraAcciones.visible(p.esPendiente)

        val resultado = when (p.estado) {
            "APROBADO" -> "Aprobado por ${p.revisadoPorNombre ?: "-"} el ${Formato.fechaHora(p.fechaRevision)}."
            "RECHAZADO" -> "Rechazado por ${p.revisadoPorNombre ?: "-"}: ${p.motivoRechazo.orEmpty()}"
            "CANCELADO" -> "Cancelado: ${p.motivoRechazo.orEmpty()}"
            else -> null
        }
        binding.txtResultado.visible(resultado != null)
        binding.txtResultado.text = resultado
        val ventaId = p.venta
        binding.btnVenta.visible(ventaId != null)
        binding.btnVenta.text = "Ver boleta ${p.ventaNumero.orEmpty()}"
        binding.btnVenta.setOnClickListener {
            if (ventaId != null) {
                startActivity(Intent(this, VentaDetalleActivity::class.java).putExtra(VentaDetalleActivity.EXTRA_ID, ventaId))
            }
        }
    }

    private fun confirmarAprobacion() {
        val p = pedido ?: return
        val faltan = p.detalles.orEmpty().filter { it.stockSuficiente == false }
        if (faltan.isNotEmpty()) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Falta stock")
                .setMessage("No alcanza el stock de: ${faltan.joinToString { it.productoNombre.orEmpty() }}.\nAvisa al cliente por el chat o rechaza el pedido.")
                .setPositiveButton("Entendido", null)
                .show()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("¿Aprobar y cobrar?")
            .setMessage("Se registrará la venta por ${Formato.soles(p.totalEstimado)} en ${MetodosPago.etiqueta(metodo)} y se descontará el stock.")
            .setPositiveButton("Aprobar") { _, _ -> aprobar(p) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun aprobar(p: Pedido) {
        val id = p.id ?: return
        bloquear(true)
        lifecycleScope.launch {
            when (val r = repo.aprobarPedido(id, metodo)) {
                is Resource.Success -> {
                    aviso("Pedido aprobado. Venta ${r.data.ventaNumero.orEmpty()} registrada.")
                    pintar(r.data)
                }
                is Resource.Error -> if (!ofrecerAbrirCaja(r.message)) aviso(r.message)
                Resource.Loading -> Unit
            }
            bloquear(false)
        }
    }

    private fun pedirMotivo() {
        val p = pedido ?: return
        val id = p.id ?: return
        val campo = EditText(this).apply {
            hint = "Ej.: el cliente ya no lo necesita"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        val contenedor = LinearLayout(this).apply {
            setPadding(56, 16, 56, 0)
            addView(campo, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Rechazar pedido")
            .setMessage("Escribe el motivo. Quedará registrado en el pedido.")
            .setView(contenedor)
            .setPositiveButton("Rechazar") { _, _ ->
                val motivo = campo.text.toString().trim()
                if (motivo.isBlank()) {
                    aviso("Escribe un motivo para rechazar.")
                    return@setPositiveButton
                }
                bloquear(true)
                lifecycleScope.launch {
                    when (val r = repo.rechazarPedido(id, motivo)) {
                        is Resource.Success -> {
                            aviso("Pedido rechazado.")
                            pintar(r.data)
                        }
                        is Resource.Error -> aviso(r.message)
                        Resource.Loading -> Unit
                    }
                    bloquear(false)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun bloquear(si: Boolean) {
        binding.btnAprobar.isEnabled = !si
        binding.btnRechazar.isEnabled = !si
    }

    /** Busca el chat de ese número y lo abre. */
    private fun abrirChat() {
        val telefono = pedido?.contactoTelefono.orEmpty().filter { it.isDigit() }
        if (telefono.isBlank()) return
        lifecycleScope.launch {
            when (val r = repo.conversaciones(telefono)) {
                is Resource.Success -> {
                    val conv = r.data.firstOrNull { it.telefono == telefono } ?: r.data.firstOrNull()
                    val convId = conv?.id
                    if (convId == null) {
                        aviso("Ese cliente ya no tiene un chat en el sistema (pudo haberse eliminado).")
                    } else {
                        startActivity(Intent(this@PedidoDetalleActivity, ChatActivity::class.java).putExtra(ChatActivity.EXTRA_ID, convId))
                    }
                }
                is Resource.Error -> aviso(r.message)
                Resource.Loading -> Unit
            }
        }
    }
}
