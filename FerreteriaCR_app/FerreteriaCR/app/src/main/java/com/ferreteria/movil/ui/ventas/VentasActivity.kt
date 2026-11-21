package com.ferreteria.movil.ui.ventas

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ferreteria.movil.R
import com.ferreteria.movil.data.Repositorio
import com.ferreteria.movil.data.local.TokenManager
import com.ferreteria.movil.data.model.MetodosPago
import com.ferreteria.movil.data.model.Venta
import com.ferreteria.movil.databinding.ActivityListaBinding
import com.ferreteria.movil.databinding.ItemPedidoBinding
import com.ferreteria.movil.ui.common.BaseActivity
import com.ferreteria.movil.ui.common.Fechas
import com.ferreteria.movil.ui.common.Formato
import com.ferreteria.movil.ui.common.Resource
import com.ferreteria.movil.ui.common.visible
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Historial de ventas de los últimos 30 días. El Vendedor ve las suyas; el Administrador, todas.
 * Desde el detalle se ve o comparte la boleta, y el Administrador puede anular.
 */
class VentasActivity : BaseActivity() {

    companion object {
        /** Abre la lista con el aviso de cómo anular (desde Más > Anular una venta). */
        const val EXTRA_PARA_ANULAR = "para_anular"
    }

    private lateinit var binding: ActivityListaBinding
    private lateinit var repo: Repositorio
    private var trabajo: Job? = null
    private var ventas: List<Venta> = emptyList()
    private var soloMias = true

    private val adaptador = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = ventas.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
            object : RecyclerView.ViewHolder(ItemPedidoBinding.inflate(LayoutInflater.from(parent.context), parent, false).root) {}

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val v = ventas[position]
            val b = ItemPedidoBinding.bind(holder.itemView)
            b.txtNumero.text = v.numero ?: "Venta"
            b.txtCliente.text = v.nombreCliente
            val vendedor = if (soloMias) "" else " · ${v.vendedorNombre.orEmpty()}"
            b.txtDetalle.text = "${Formato.fechaHora(v.fecha)} · ${MetodosPago.etiqueta(v.metodoPago)}$vendedor"
            b.txtTotal.text = Formato.soles(v.total)
            if (v.anulada) {
                Formato.pintarEtiqueta(b.txtTiempo, R.color.rojo, R.color.rojo_fondo, "Anulada")
            } else {
                Formato.pintarEtiqueta(b.txtTiempo, R.color.navy, R.color.divisor, Formato.origen(v.origen))
            }
            holder.itemView.setOnClickListener {
                val id = v.id ?: return@setOnClickListener
                startActivity(Intent(this@VentasActivity, VentaDetalleActivity::class.java).putExtra(VentaDetalleActivity.EXTRA_ID, id))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityListaBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repo = Repositorio(this)
        soloMias = !TokenManager.getInstance(this).isAdmin()

        binding.txtTitulo.text = if (soloMias) "Mis ventas" else "Ventas de la tienda"
        binding.etBuscar.hint = "N.° de boleta o cliente"
        binding.btnVolver.setOnClickListener { finish() }
        if (intent.getBooleanExtra(EXTRA_PARA_ANULAR, false)) {
            binding.txtAyuda.visible(true)
            binding.txtAyuda.text = "Toca la venta que quieres anular. El stock vuelve al inventario."
        }
        binding.lista.layoutManager = LinearLayoutManager(this)
        binding.lista.adapter = adaptador
        binding.refrescar.setColorSchemeResources(R.color.navy)
        binding.refrescar.setOnRefreshListener { buscar(0) }
        binding.etBuscar.doAfterTextChanged { buscar(400) }
    }

    override fun onResume() {
        super.onResume()
        buscar(0)   // al volver de una anulación la lista se actualiza
    }

    private fun buscar(espera: Long) {
        trabajo?.cancel()
        trabajo = lifecycleScope.launch {
            if (espera > 0) delay(espera)
            binding.refrescar.isRefreshing = true
            when (val r = repo.ventas(soloMias, Fechas.haceDias(30), Fechas.hoy(), binding.etBuscar.text.toString().trim())) {
                is Resource.Success -> {
                    ventas = r.data.sortedByDescending { it.fecha }
                    adaptador.notifyDataSetChanged()
                    binding.txtVacio.visible(ventas.isEmpty())
                    binding.txtVacio.text = "No hay ventas en los últimos 30 días."
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
}
