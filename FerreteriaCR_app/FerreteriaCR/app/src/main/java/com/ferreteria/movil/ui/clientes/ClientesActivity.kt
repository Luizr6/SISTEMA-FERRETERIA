package com.ferreteria.movil.ui.clientes

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ferreteria.movil.R
import com.ferreteria.movil.data.Repositorio
import com.ferreteria.movil.data.model.Cliente
import com.ferreteria.movil.databinding.ActivityListaBinding
import com.ferreteria.movil.databinding.ItemLineaBinding
import com.ferreteria.movil.ui.common.BaseActivity
import com.ferreteria.movil.ui.common.Resource
import com.ferreteria.movil.ui.common.visible
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Clientes registrados (para boletas con DNI o RUC). Se registran al cobrar una venta. */
class ClientesActivity : BaseActivity() {

    private lateinit var binding: ActivityListaBinding
    private lateinit var repo: Repositorio
    private var trabajo: Job? = null
    private var clientes: List<Cliente> = emptyList()

    private val adaptador = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = clientes.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val b = ItemLineaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            b.root.setBackgroundResource(R.drawable.bg_tarjeta)
            b.root.setPadding(36, 28, 36, 28)
            (b.root.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin = 16
            return object : RecyclerView.ViewHolder(b.root) {}
        }
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val c = clientes[position]
            val b = ItemLineaBinding.bind(holder.itemView)
            b.txtNombre.text = c.nombre.orEmpty()
            b.txtDetalle.text = listOfNotNull(c.documento.takeIf { it.isNotBlank() }, c.telefono?.takeIf { it.isNotBlank() })
                .joinToString(" · ")
            b.txtMonto.text = ""
            holder.itemView.setOnClickListener { detalle(c) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityListaBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repo = Repositorio(this)

        binding.txtTitulo.text = "Clientes"
        binding.etBuscar.hint = "Nombre, DNI o RUC"
        binding.btnVolver.setOnClickListener { finish() }
        binding.lista.layoutManager = LinearLayoutManager(this)
        binding.lista.adapter = adaptador
        binding.refrescar.setColorSchemeResources(R.color.navy)
        binding.refrescar.setOnRefreshListener { buscar(0) }
        binding.etBuscar.doAfterTextChanged { buscar(300) }
        buscar(0)
    }

    private fun buscar(espera: Long) {
        trabajo?.cancel()
        trabajo = lifecycleScope.launch {
            if (espera > 0) delay(espera)
            binding.refrescar.isRefreshing = true
            when (val r = repo.clientes(binding.etBuscar.text.toString().trim())) {
                is Resource.Success -> {
                    clientes = r.data
                    adaptador.notifyDataSetChanged()
                    binding.txtVacio.visible(r.data.isEmpty())
                    binding.txtVacio.text = "No hay clientes con ese dato.\nSe registran al cobrar una venta."
                }
                is Resource.Error -> {
                    binding.txtVacio.visible(true)
                    binding.txtVacio.text = r.message
                }
                Resource.Loading -> Unit
            }
            binding.refrescar.isRefreshing = false
        }
    }

    private fun detalle(c: Cliente) {
        val telefono = c.telefono.orEmpty().filter { it.isDigit() }
        val dialogo = MaterialAlertDialogBuilder(this)
            .setTitle(c.nombre.orEmpty())
            .setMessage(
                "Documento: ${c.documento.ifBlank { "-" }}\n" +
                    "Teléfono: ${c.telefono?.ifBlank { null } ?: "-"}\n" +
                    "Correo: ${c.email?.ifBlank { null } ?: "-"}\n" +
                    "Dirección: ${c.direccion?.ifBlank { null } ?: "-"}"
            )
            .setPositiveButton("Cerrar", null)
        if (telefono.isNotBlank()) {
            dialogo.setNeutralButton("Llamar") { _, _ ->
                startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$telefono")))
            }
        }
        dialogo.show()
    }
}
