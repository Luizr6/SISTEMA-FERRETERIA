package com.ferreteria.movil.ui.caja

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ferreteria.movil.R
import com.ferreteria.movil.data.Repositorio
import com.ferreteria.movil.data.local.TokenManager
import com.ferreteria.movil.data.model.Caja
import com.ferreteria.movil.databinding.ActivityListaBinding
import com.ferreteria.movil.databinding.ItemPedidoBinding
import com.ferreteria.movil.ui.common.BaseActivity
import com.ferreteria.movil.ui.common.Fechas
import com.ferreteria.movil.ui.common.Formato
import com.ferreteria.movil.ui.common.Resource
import com.ferreteria.movil.ui.common.visible
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Cajas de los últimos 30 días. El Administrador ve las de todos los vendedores (con faltantes);
 * el Vendedor, solo las suyas. Tocar una abre su detalle y arqueo.
 */
class CajasActivity : BaseActivity() {

    private lateinit var binding: ActivityListaBinding
    private lateinit var repo: Repositorio
    private var cajas: List<Caja> = emptyList()
    private var admin = false

    private val adaptador = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = cajas.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
            object : RecyclerView.ViewHolder(ItemPedidoBinding.inflate(LayoutInflater.from(parent.context), parent, false).root) {}

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val c = cajas[position]
            val b = ItemPedidoBinding.bind(holder.itemView)
            b.txtNumero.text = "Caja del ${Formato.fechaHora(c.fechaApertura)}"
            b.txtCliente.text = if (admin) c.usuarioNombre.orEmpty() else "Inicial ${Formato.soles(c.montoInicial)}"
            val ventas = c.resumen?.cantidadVentas ?: 0
            b.txtDetalle.text = if (c.abierta) {
                "Abierta · $ventas venta(s)"
            } else {
                "Cerró ${Formato.hora(c.fechaCierre)} · $ventas venta(s) · contado ${Formato.soles(c.montoContado)}"
            }
            b.txtTotal.text = Formato.soles(if (c.abierta) c.resumen?.efectivoEsperado else c.montoEsperado)
            val dif = c.diferencia ?: 0.0
            when {
                c.abierta -> Formato.pintarEtiqueta(b.txtTiempo, R.color.verde_oscuro, R.color.verde_fondo, "Abierta")
                dif < 0 -> Formato.pintarEtiqueta(b.txtTiempo, R.color.rojo, R.color.rojo_fondo, "Falta ${Formato.soles(abs(dif))}")
                dif > 0 -> Formato.pintarEtiqueta(b.txtTiempo, R.color.ambar_texto, R.color.ambar_fondo, "Sobra ${Formato.soles(dif)}")
                else -> Formato.pintarEtiqueta(b.txtTiempo, R.color.navy, R.color.divisor, "Cuadrada")
            }
            holder.itemView.setOnClickListener {
                val id = c.id ?: return@setOnClickListener
                startActivity(Intent(this@CajasActivity, CajaActivity::class.java).putExtra(CajaActivity.EXTRA_ID, id))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityListaBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repo = Repositorio(this)
        admin = TokenManager.getInstance(this).isAdmin()

        binding.txtTitulo.text = if (admin) "Cajas de los vendedores" else "Mis cajas"
        (binding.etBuscar.parent as android.view.View).visible(false)
        binding.txtAyuda.visible(true)
        binding.txtAyuda.text = "Últimos 30 días. Toca una caja para ver su arqueo."
        binding.btnVolver.setOnClickListener { finish() }
        binding.lista.layoutManager = LinearLayoutManager(this)
        binding.lista.adapter = adaptador
        binding.refrescar.setColorSchemeResources(R.color.navy)
        binding.refrescar.setOnRefreshListener { cargar() }
    }

    override fun onResume() {
        super.onResume()
        cargar()
    }

    private fun cargar() {
        lifecycleScope.launch {
            binding.refrescar.isRefreshing = true
            when (val r = repo.cajas(Fechas.haceDias(30), Fechas.hoy())) {
                is Resource.Success -> {
                    cajas = r.data.sortedByDescending { it.fechaApertura }
                    adaptador.notifyDataSetChanged()
                    val faltantes = cajas.count { !it.abierta && (it.diferencia ?: 0.0) < 0 }
                    if (admin && faltantes > 0) {
                        binding.txtAyuda.text = "Últimos 30 días · $faltantes caja(s) con faltante. Toca una para ver su arqueo."
                    }
                    binding.txtVacio.visible(cajas.isEmpty())
                    binding.txtVacio.text = "No hay cajas en los últimos 30 días."
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
