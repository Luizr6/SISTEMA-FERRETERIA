package com.ferreteria.movil.ui.whatsapp

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ferreteria.movil.R
import com.ferreteria.movil.data.Repositorio
import com.ferreteria.movil.data.model.Conversacion
import com.ferreteria.movil.data.model.Mensaje
import com.ferreteria.movil.databinding.ActivityChatBinding
import com.ferreteria.movil.databinding.ItemBurbujaBinding
import com.ferreteria.movil.ui.common.BaseActivity
import com.ferreteria.movil.ui.common.Burbujas
import com.ferreteria.movil.ui.common.Formato
import com.ferreteria.movil.ui.common.Resource
import com.ferreteria.movil.ui.common.aviso
import com.ferreteria.movil.ui.common.visible
import com.ferreteria.movil.ui.pedidos.PedidoDetalleActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Chat de WhatsApp de un cliente. Se ven los mensajes del cliente, del bot y de los vendedores.
 * El vendedor puede responder (Django lo envía por WhatsApp) y pausar el bot para atender él.
 * Mientras la pantalla está abierta, revisa mensajes nuevos cada 5 segundos.
 */
class ChatActivity : BaseActivity() {

    companion object {
        const val EXTRA_ID = "conversacion_id"
    }

    private lateinit var binding: ActivityChatBinding
    private lateinit var repo: Repositorio
    private val adaptador = MensajeAdapter { pedidoId ->
        startActivity(Intent(this, PedidoDetalleActivity::class.java).putExtra(PedidoDetalleActivity.EXTRA_ID, pedidoId))
    }
    private var convId = 0
    private var conversacion: Conversacion? = null
    private var enviando = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repo = Repositorio(this)
        convId = intent.getIntExtra(EXTRA_ID, 0)

        binding.lista.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        binding.lista.adapter = adaptador
        binding.btnVolver.setOnClickListener { finish() }
        binding.btnEnviar.setOnClickListener { enviar() }
        binding.btnBot.setOnClickListener { cambiarBot() }

        cargarConversacion()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    cargarMensajes()
                    delay(5_000)
                }
            }
        }
    }

    private fun cargarConversacion() {
        lifecycleScope.launch {
            when (val r = repo.conversacion(convId)) {
                is Resource.Success -> pintarCabecera(r.data)
                is Resource.Error -> {
                    aviso(r.message)
                    if (r.statusCode == 404) finish()
                }
                Resource.Loading -> Unit
            }
        }
    }

    private suspend fun cargarMensajes() {
        when (val r = repo.mensajes(convId)) {
            is Resource.Success -> {
                binding.progreso.visible(false)
                val habiaAntes = adaptador.itemCount
                adaptador.actualizar(r.data)
                if (r.data.size != habiaAntes) binding.lista.scrollToPosition((r.data.size - 1).coerceAtLeast(0))
            }
            is Resource.Error -> {
                binding.progreso.visible(false)
                if (adaptador.itemCount == 0) aviso(r.message)
            }
            Resource.Loading -> Unit
        }
    }

    private fun pintarCabecera(c: Conversacion) {
        conversacion = c
        binding.txtNombre.text = c.nombreVisible
        binding.txtTelefono.text = c.telefonoVisible
        binding.txtAvatar.text = c.iniciales
        val activo = c.botActivo != false
        binding.btnBot.text = if (activo) "Bot activo" else "Bot en pausa"
        val texto = getColor(if (activo) R.color.verde_oscuro else R.color.ambar_texto)
        binding.btnBot.setTextColor(texto)
        binding.btnBot.iconTint = ColorStateList.valueOf(texto)
        binding.btnBot.backgroundTintList = ColorStateList.valueOf(getColor(if (activo) R.color.verde_fondo else R.color.ambar_suave))
        binding.txtAvisoPausa.visible(!activo)
    }

    private fun cambiarBot() {
        val c = conversacion ?: return
        val activar = c.botActivo == false
        val titulo = if (activar) "¿Reactivar el bot?" else "¿Pausar el bot?"
        val mensaje = if (activar) {
            "El bot volverá a responder automáticamente a este cliente."
        } else {
            "El bot dejará de responder en este chat y tú atenderás al cliente."
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(titulo)
            .setMessage(mensaje)
            .setPositiveButton(if (activar) "Reactivar" else "Pausar") { _, _ ->
                lifecycleScope.launch {
                    when (val r = repo.cambiarBot(convId, activar)) {
                        is Resource.Success -> pintarCabecera(r.data)
                        is Resource.Error -> aviso(r.message)
                        Resource.Loading -> Unit
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun enviar() {
        val texto = binding.etMensaje.text.toString().trim()
        if (texto.isBlank() || enviando) return
        val pausar = conversacion?.botActivo != false
        enviando = true
        binding.btnEnviar.isEnabled = false
        lifecycleScope.launch {
            when (val r = repo.responder(convId, texto)) {
                is Resource.Success -> {
                    binding.etMensaje.setText("")
                    if (r.data.estadoEnvio == "ERROR") {
                        aviso("El mensaje se guardó, pero WhatsApp no lo entregó: ${r.data.error.orEmpty()}")
                    }
                    cargarMensajes()
                    // Si una persona responde, conviene que el bot no le conteste encima
                    if (pausar) sugerirPausa()
                }
                is Resource.Error -> aviso(r.message)
                Resource.Loading -> Unit
            }
            enviando = false
            binding.btnEnviar.isEnabled = true
        }
    }

    private fun sugerirPausa() {
        MaterialAlertDialogBuilder(this)
            .setTitle("¿Pausar el bot en este chat?")
            .setMessage("Así el bot no responde mientras tú atiendes al cliente. Puedes reactivarlo cuando termines.")
            .setPositiveButton("Pausar") { _, _ ->
                lifecycleScope.launch {
                    val r = repo.cambiarBot(convId, false)
                    if (r is Resource.Success) pintarCabecera(r.data)
                }
            }
            .setNegativeButton("Dejar activo", null)
            .show()
    }
}

class MensajeAdapter(private val abrirPedido: (Int) -> Unit) : RecyclerView.Adapter<MensajeAdapter.VH>() {

    private var items: List<Mensaje> = emptyList()

    fun actualizar(nuevos: List<Mensaje>) {
        if (nuevos == items) return
        items = nuevos
        notifyDataSetChanged()
    }

    class VH(val b: ItemBurbujaBinding) : RecyclerView.ViewHolder(b.root)

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemBurbujaBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val m = items[position]
        val tipo = when {
            m.esCliente -> Burbujas.Tipo.CLIENTE
            m.esPersona -> Burbujas.Tipo.VENDEDOR
            else -> Burbujas.Tipo.BOT
        }
        val autor = when (tipo) {
            Burbujas.Tipo.CLIENTE -> null
            Burbujas.Tipo.VENDEDOR -> "${m.enviadoPorNombre ?: "Vendedor"} (vendedor)"
            Burbujas.Tipo.BOT -> "Bot"
        }
        val hora = Formato.horaOFecha(m.fecha) + if (m.estadoEnvio == "ERROR") " · no enviado" else ""
        Burbujas.pintar(holder.b, tipo, m.texto.orEmpty(), autor, hora)

        val pedidoId = m.pedido
        holder.b.txtPedido.visible(pedidoId != null)
        if (pedidoId != null) {
            holder.b.txtPedido.text = "Ver pedido ${m.pedidoNumero.orEmpty()}"
            holder.b.txtPedido.setOnClickListener { abrirPedido(pedidoId) }
        }
    }
}
