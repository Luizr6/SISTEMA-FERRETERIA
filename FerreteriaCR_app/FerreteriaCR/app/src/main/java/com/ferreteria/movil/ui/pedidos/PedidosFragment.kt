package com.ferreteria.movil.ui.pedidos

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ferreteria.movil.R
import com.ferreteria.movil.data.Repositorio
import com.ferreteria.movil.data.model.Pedido
import com.ferreteria.movil.databinding.FragmentPedidosBinding
import com.ferreteria.movil.databinding.ItemPedidoBinding
import com.ferreteria.movil.ui.common.Formato
import com.ferreteria.movil.ui.common.Resource
import com.ferreteria.movil.ui.common.VmFactory
import com.ferreteria.movil.ui.common.visible
import com.ferreteria.movil.ui.main.MainActivity
import com.ferreteria.movil.ui.main.Recargable
import com.google.android.material.chip.Chip
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class PedidosViewModel(private val repo: Repositorio) : ViewModel() {

    private val _pedidos = MutableLiveData<List<Pedido>>()
    val pedidos: LiveData<List<Pedido>> = _pedidos

    private val _cargando = MutableLiveData(false)
    val cargando: LiveData<Boolean> = _cargando

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    /** PENDIENTE | APROBADO | null (todos) */
    var estado: String? = "PENDIENTE"
    /** WHATSAPP | MOSTRADOR | MOVIL | null (todos) */
    var origen: String? = null

    private var trabajo: Job? = null

    fun cargar() {
        trabajo?.cancel()
        trabajo = viewModelScope.launch {
            _cargando.value = true
            when (val r = repo.pedidos(estado, origen)) {
                is Resource.Success -> {
                    // Por confirmar: primero el que más espera. Los demás: el más reciente primero.
                    _pedidos.value = if (estado == "PENDIENTE") r.data.sortedBy { it.fecha } else r.data.sortedByDescending { it.fecha }
                    _error.value = null
                }
                is Resource.Error -> _error.value = r.message
                Resource.Loading -> Unit
            }
            _cargando.value = false
        }
    }
}

/** Lista de pedidos antes del detalle: con muchos pedidos se ve ordenado y se sabe cuál atender primero. */
class PedidosFragment : Fragment(), Recargable {

    private var _binding: FragmentPedidosBinding? = null
    private val binding get() = _binding!!
    private val vm: PedidosViewModel by viewModels { VmFactory { PedidosViewModel(Repositorio(requireContext())) } }
    private var todos: List<Pedido> = emptyList()
    private val adaptador = PedidoAdapter { abrir(it) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPedidosBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.lista.layoutManager = LinearLayoutManager(requireContext())
        binding.lista.adapter = adaptador

        val estados = listOf("PENDIENTE" to "Por confirmar", "APROBADO" to "Aprobados", null to "Todos")
        estados.forEach { (_, nombre) -> binding.pestanas.addTab(binding.pestanas.newTab().setText(nombre)) }
        binding.pestanas.getTabAt(estados.indexOfFirst { it.first == vm.estado }.coerceAtLeast(0))?.select()
        binding.pestanas.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                vm.estado = estados[tab.position].first
                vm.cargar()
            }
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = vm.cargar()
        })

        val origenes = listOf<Pair<String?, String>>(null to "Todos", "WHATSAPP" to "WhatsApp", "MOSTRADOR" to "Mostrador", "MOVIL" to "App")
        origenes.forEach { (codigo, nombre) ->
            val chip = layoutInflater.inflate(R.layout.chip_filtro, binding.grupoOrigen, false) as Chip
            chip.id = View.generateViewId()
            chip.text = nombre
            chip.tag = codigo
            chip.isChecked = codigo == vm.origen
            binding.grupoOrigen.addView(chip)
        }
        binding.grupoOrigen.setOnCheckedStateChangeListener { g, ids ->
            val chip = ids.firstOrNull()?.let { g.findViewById<Chip>(it) } ?: return@setOnCheckedStateChangeListener
            vm.origen = chip.tag as String?
            vm.cargar()
        }

        binding.etBuscar.doAfterTextChanged { filtrar() }
        binding.refrescar.setColorSchemeResources(R.color.navy)
        binding.refrescar.setOnRefreshListener { vm.cargar() }

        vm.cargando.observe(viewLifecycleOwner) { binding.refrescar.isRefreshing = it }
        vm.pedidos.observe(viewLifecycleOwner) {
            todos = it
            filtrar()
        }
        vm.error.observe(viewLifecycleOwner) { msg ->
            if (msg != null) {
                binding.txtVacio.visible(true)
                binding.txtVacio.text = msg
            }
        }
    }

    /** Al volver del detalle (aprobado o rechazado) la lista se actualiza sola. */
    override fun onResume() {
        super.onResume()
        if (!isHidden) vm.cargar()
    }

    private fun filtrar() {
        val q = binding.etBuscar.text.toString().trim().lowercase()
        val lista = if (q.isBlank()) todos else todos.filter {
            it.numero.orEmpty().lowercase().contains(q) ||
                it.nombreCliente.lowercase().contains(q) ||
                it.contactoTelefono.orEmpty().contains(q)
        }
        adaptador.actualizar(lista)
        binding.txtAyuda.text = if (vm.estado == "PENDIENTE") "Ordenados del que más espera al más reciente" else "Del más reciente al más antiguo"
        binding.txtVacio.visible(lista.isEmpty())
        binding.txtVacio.text = when {
            q.isNotBlank() -> "Ningún pedido coincide con \"$q\"."
            vm.estado == "PENDIENTE" -> "No hay pedidos por confirmar. ¡Todo al día!"
            else -> "No hay pedidos para mostrar."
        }
    }

    private fun abrir(p: Pedido) {
        val id = p.id ?: return
        startActivity(Intent(requireContext(), PedidoDetalleActivity::class.java).putExtra(PedidoDetalleActivity.EXTRA_ID, id))
    }

    override fun recargar() {
        if (_binding != null) {
            vm.cargar()
            (activity as? MainActivity)?.refrescarInsignias()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class PedidoAdapter(private val alTocar: (Pedido) -> Unit) : RecyclerView.Adapter<PedidoAdapter.VH>() {

    private var items: List<Pedido> = emptyList()

    fun actualizar(nuevos: List<Pedido>) {
        items = nuevos
        notifyDataSetChanged()
    }

    class VH(val b: ItemPedidoBinding) : RecyclerView.ViewHolder(b.root)

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemPedidoBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val p = items[position]
        val b = holder.b
        val productos = p.detalles?.size ?: 0
        b.txtNumero.text = p.numero ?: "Pedido"
        b.txtCliente.text = p.nombreCliente
        b.txtDetalle.text = "${Formato.origen(p.origen)} · $productos producto(s)"
        b.txtTotal.text = Formato.soles(p.totalEstimado)

        val minutos = Formato.minutosDesde(p.fecha)
        when {
            p.esPendiente && minutos >= 30 -> {
                Formato.pintarEtiqueta(b.txtTiempo, R.color.ambar_texto, R.color.ambar_suave, "Espera ${tiempo(minutos)}")
                b.root.setBackgroundResource(R.drawable.bg_tarjeta_ambar)
            }
            p.esPendiente -> {
                Formato.pintarEtiqueta(b.txtTiempo, R.color.texto_2, R.color.fondo, Formato.haceTiempo(p.fecha))
                b.root.setBackgroundResource(R.drawable.bg_tarjeta)
            }
            else -> {
                val (color, fondo) = when (p.estado) {
                    "APROBADO" -> R.color.verde to R.color.verde_fondo
                    "RECHAZADO", "CANCELADO" -> R.color.rojo to R.color.rojo_fondo
                    else -> R.color.texto_2 to R.color.fondo
                }
                Formato.pintarEtiqueta(b.txtTiempo, color, fondo, Formato.estadoPedido(p.estado))
                b.root.setBackgroundResource(R.drawable.bg_tarjeta)
            }
        }
        b.root.setOnClickListener { alTocar(p) }
    }

    private fun tiempo(minutos: Long) = if (minutos < 60) "$minutos min" else "${minutos / 60} h ${minutos % 60} min"
}
