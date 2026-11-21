package com.ferreteria.movil.ui.vender

import android.os.Bundle
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.ferreteria.movil.R
import com.ferreteria.movil.data.Repositorio
import com.ferreteria.movil.data.model.Producto
import com.ferreteria.movil.databinding.ActivityListaBinding
import com.ferreteria.movil.ui.common.BaseActivity
import com.ferreteria.movil.ui.common.Formato
import com.ferreteria.movil.ui.common.Resource
import com.ferreteria.movil.ui.common.visible
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Consulta rápida de precio y stock (cuando un cliente pregunta "¿a cuánto está…?"). */
class ConsultaActivity : BaseActivity() {

    private lateinit var binding: ActivityListaBinding
    private lateinit var repo: Repositorio
    private var trabajo: Job? = null
    private val adaptador = ProductoAdapter(cantidadDe = { 0 }, alAgregar = { detalle(it) }, alQuitar = {}, soloConsulta = true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityListaBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repo = Repositorio(this)

        binding.txtTitulo.text = "Consultar precio"
        binding.etBuscar.hint = "¿Qué producto busca el cliente?"
        binding.btnVolver.setOnClickListener { finish() }
        binding.lista.layoutManager = LinearLayoutManager(this)
        binding.lista.adapter = adaptador
        binding.refrescar.setColorSchemeResources(R.color.navy)
        binding.refrescar.setOnRefreshListener { buscar(0) }
        binding.etBuscar.doAfterTextChanged { buscar(300) }
        binding.etBuscar.requestFocus()
        buscar(0)
    }

    private fun buscar(espera: Long) {
        trabajo?.cancel()
        trabajo = lifecycleScope.launch {
            if (espera > 0) delay(espera)
            binding.refrescar.isRefreshing = true
            val texto = binding.etBuscar.text.toString().trim()
            when (val r = repo.productos(texto)) {
                is Resource.Success -> {
                    adaptador.submitList(r.data.sortedBy { it.nombre })
                    binding.txtVacio.visible(r.data.isEmpty())
                    binding.txtVacio.text = "No encontramos \"$texto\"."
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

    private fun detalle(p: Producto) {
        MaterialAlertDialogBuilder(this)
            .setTitle(p.nombre)
            .setMessage(
                "Precio al cliente: ${Formato.soles(Formato.conIgv(p.precioVenta))}${Formato.sufijoPrecio(p.unidadMedida)} (incluye IGV)\n" +
                    "Stock: ${p.stockActual} ${Formato.unidad(p.unidadMedida)} (mínimo ${p.stockMinimo})\n" +
                    "Categoría: ${p.categoriaNombre.orEmpty()}\n" +
                    "Marca: ${p.marcaNombre ?: "-"}\n" +
                    "Código: ${p.codigo}"
            )
            .setPositiveButton("Cerrar", null)
            .show()
    }
}
