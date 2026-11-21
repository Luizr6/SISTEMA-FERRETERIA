package com.ferreteria.movil.ui.vender

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ferreteria.movil.R
import com.ferreteria.movil.data.Repositorio
import com.ferreteria.movil.data.local.CarritoStore
import com.ferreteria.movil.data.local.TokenManager
import com.ferreteria.movil.data.model.Categoria
import com.ferreteria.movil.data.model.Producto
import com.ferreteria.movil.databinding.FragmentVenderBinding
import com.ferreteria.movil.databinding.ItemProductoBinding
import com.ferreteria.movil.ui.caja.CajaActivity
import com.ferreteria.movil.ui.common.Formato
import com.ferreteria.movil.ui.common.Resource
import com.ferreteria.movil.ui.common.VmFactory
import com.ferreteria.movil.ui.common.aviso
import com.ferreteria.movil.ui.common.visible
import com.ferreteria.movil.ui.main.Recargable
import com.google.android.material.chip.Chip
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class VenderViewModel(private val repo: Repositorio) : ViewModel() {

    private val _productos = MutableLiveData<List<Producto>>()
    val productos: LiveData<List<Producto>> = _productos

    private val _categorias = MutableLiveData<List<Categoria>>()
    val categorias: LiveData<List<Categoria>> = _categorias

    private val _cargando = MutableLiveData(false)
    val cargando: LiveData<Boolean> = _cargando

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    var busqueda: String = ""
        private set
    var categoria: Int? = null
        private set

    private var trabajo: Job? = null

    fun cargarCategorias() {
        viewModelScope.launch {
            val r = repo.categorias()
            if (r is Resource.Success) _categorias.value = r.data.filter { (it.totalProductos ?: 1) > 0 }
        }
    }

    fun buscar(texto: String) {
        if (texto == busqueda) return
        busqueda = texto
        cargar(espera = 300)
    }

    fun elegirCategoria(id: Int?) {
        if (id == categoria) return
        categoria = id
        cargar()
    }

    fun cargar(espera: Long = 0) {
        trabajo?.cancel()
        trabajo = viewModelScope.launch {
            if (espera > 0) delay(espera)
            _cargando.value = true
            when (val r = repo.productos(busqueda, categoria)) {
                is Resource.Success -> {
                    _productos.value = r.data.sortedWith(compareBy<Producto> { it.isAgotado() }.thenBy { it.nombre })
                    _error.value = null
                }
                is Resource.Error -> _error.value = r.message
                Resource.Loading -> Unit
            }
            _cargando.value = false
        }
    }
}

/**
 * Vender: buscar o filtrar por categoría, sumar con +/− y cobrar.
 * El precio que se muestra ya incluye IGV (el que paga el cliente).
 */
class VenderFragment : Fragment(), Recargable {

    private var _binding: FragmentVenderBinding? = null
    private val binding get() = _binding!!
    private lateinit var carrito: CarritoStore
    private lateinit var adaptador: ProductoAdapter

    private val vm: VenderViewModel by viewModels { VmFactory { VenderViewModel(Repositorio(requireContext())) } }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentVenderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        carrito = CarritoStore.getInstance(requireContext())
        binding.txtVendedor.text = "Caja…"
        binding.txtVendedor.setOnClickListener { startActivity(Intent(requireContext(), CajaActivity::class.java)) }

        adaptador = ProductoAdapter(
            cantidadDe = { id -> carrito.cantidadDe(id) },
            alAgregar = { p -> agregar(p) },
            alQuitar = { p -> p.id?.let { carrito.cambiarCantidad(it, carrito.cantidadDe(it) - 1) } }
        )
        binding.lista.layoutManager = LinearLayoutManager(requireContext())
        binding.lista.adapter = adaptador

        binding.etBuscar.setText(vm.busqueda)
        binding.etBuscar.doAfterTextChanged {
            val texto = it?.toString().orEmpty()
            binding.btnLimpiar.visible(texto.isNotEmpty())
            vm.buscar(texto.trim())
        }
        binding.btnLimpiar.setOnClickListener { binding.etBuscar.setText("") }

        binding.refrescar.setColorSchemeResources(R.color.navy)
        binding.refrescar.setOnRefreshListener { vm.cargar() }
        binding.btnCobrar.setOnClickListener { startActivity(Intent(requireContext(), CobroActivity::class.java)) }

        vm.cargando.observe(viewLifecycleOwner) { binding.refrescar.isRefreshing = it }
        vm.categorias.observe(viewLifecycleOwner) { pintarCategorias(it) }
        vm.productos.observe(viewLifecycleOwner) { lista ->
            adaptador.submitList(lista)
            carrito.refrescarDesdeCatalogo(lista)
            binding.txtVacio.visible(lista.isEmpty())
            binding.txtVacio.text = when {
                vm.busqueda.isNotBlank() -> "No encontramos \"${vm.busqueda}\".\nPrueba con otra palabra o en \"Todas\"."
                else -> "No hay productos en esta categoría."
            }
        }
        vm.error.observe(viewLifecycleOwner) { msg ->
            if (msg != null && adaptador.itemCount == 0) {
                binding.txtVacio.visible(true)
                binding.txtVacio.text = msg
            }
        }
        carrito.items.observe(viewLifecycleOwner) {
            adaptador.notifyDataSetChanged()
            pintarBarra()
        }

        vm.cargarCategorias()
        vm.cargar()
    }

    private fun agregar(p: Producto) {
        val id = p.id ?: return
        val actual = carrito.cantidadDe(id)
        if (actual >= p.stockActual) {
            requireContext().aviso("Solo hay ${p.stockActual} ${Formato.unidad(p.unidadMedida)} de ${p.nombre}.")
            return
        }
        carrito.agregar(p, 1)
    }

    private fun pintarCategorias(lista: List<Categoria>) {
        val grupo = binding.grupoCategorias
        grupo.setOnCheckedStateChangeListener(null)
        grupo.removeAllViews()
        val opciones = listOf<Pair<Int?, String>>(null to "Todas") + lista.map { it.id to it.nombre }
        opciones.forEach { (id, nombre) ->
            val chip = layoutInflater.inflate(R.layout.chip_filtro, grupo, false) as Chip
            chip.text = nombre
            chip.id = View.generateViewId()
            chip.tag = id
            chip.isChecked = id == vm.categoria
            grupo.addView(chip)
        }
        grupo.setOnCheckedStateChangeListener { g, ids ->
            val chip = ids.firstOrNull()?.let { g.findViewById<Chip>(it) } ?: return@setOnCheckedStateChangeListener
            vm.elegirCategoria(chip.tag as Int?)
        }
    }

    private fun pintarBarra() {
        val unidades = carrito.totalUnidades()
        binding.barraCobro.visible(unidades > 0)
        binding.txtCantidadCarrito.text = "${carrito.actuales().size} producto(s) · incluye IGV"
        binding.txtTotalCarrito.text = Formato.soles(carrito.total())
    }

    override fun onResume() {
        super.onResume()
        pintarCaja()
    }

    /** Píldora de la barra: estado de mi caja. Tocarla abre Mi caja. */
    private fun pintarCaja() {
        viewLifecycleOwner.lifecycleScope.launch {
            val r = Repositorio(requireContext()).cajaActual()
            val b = _binding ?: return@launch
            val caja = (r as? Resource.Success)?.data?.caja
            if (r is Resource.Success && caja == null) {
                b.txtVendedor.text = "Caja cerrada"
                b.txtVendedor.setTextColor(requireContext().getColor(R.color.ambar))
                b.txtVendedor.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0)
            } else {
                b.txtVendedor.text = if (caja != null) "Caja abierta · ${Formato.hora(caja.fechaApertura)}"
                    else TokenManager.getInstance(requireContext()).getUsername()
                b.txtVendedor.setTextColor(0xFFD1FAE5.toInt())
                b.txtVendedor.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.bg_punto_verde, 0, 0, 0)
            }
        }
    }

    override fun recargar() {
        if (_binding != null) {
            vm.cargar()
            pintarCaja()
        }
    }

    /** Al volver a la pestaña solo se repinta el carrito (no se recarga el catálogo). */
    override fun alVolver() {
        if (_binding != null) {
            pintarBarra()
            pintarCaja()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class ProductoAdapter(
    private val cantidadDe: (Int) -> Int,
    private val alAgregar: (Producto) -> Unit,
    private val alQuitar: (Producto) -> Unit,
    /** true en "Consultar precio": solo muestra, sin botones de agregar. */
    private val soloConsulta: Boolean = false
) : ListAdapter<Producto, ProductoAdapter.VH>(Diff) {

    object Diff : DiffUtil.ItemCallback<Producto>() {
        override fun areItemsTheSame(a: Producto, b: Producto) = a.id == b.id
        override fun areContentsTheSame(a: Producto, b: Producto) = a == b
    }

    class VH(val b: ItemProductoBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemProductoBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val p = getItem(position)
        val b = holder.b
        val ctx = b.root.context
        val cantidad = p.id?.let(cantidadDe) ?: 0
        val agotado = p.isAgotado()

        b.txtNombre.text = p.nombre
        b.txtPrecio.text = Formato.soles(Formato.conIgv(p.precioVenta)) + Formato.sufijoPrecio(p.unidadMedida)
        val marca = p.marcaNombre?.takeIf { it.isNotBlank() }
        if (agotado) {
            b.txtDetalle.text = listOfNotNull(marca, "Agotado").joinToString(" · ")
            b.txtDetalle.setTextColor(ctx.getColor(R.color.rojo))
        } else {
            b.txtDetalle.text = listOfNotNull(marca, "${p.stockActual} ${Formato.unidad(p.unidadMedida)}").joinToString(" · ")
            b.txtDetalle.setTextColor(ctx.getColor(if (p.isStockCritico()) R.color.ambar_texto else R.color.texto_2))
        }

        b.root.setBackgroundResource(if (cantidad > 0) R.drawable.bg_tarjeta_ambar else R.drawable.bg_tarjeta)
        b.root.alpha = if (agotado) 0.7f else 1f
        b.btnAgregar.visible(!soloConsulta && cantidad == 0)
        b.stepper.visible(!soloConsulta && cantidad > 0)
        b.btnAgregar.isEnabled = !agotado
        b.btnAgregar.backgroundTintList = android.content.res.ColorStateList.valueOf(
            ctx.getColor(if (agotado) R.color.divisor else R.color.navy)
        )
        b.btnAgregar.contentDescription = "Agregar ${p.nombre}"
        b.txtCantidad.text = cantidad.toString()
        b.btnAgregar.setOnClickListener { alAgregar(p) }
        b.btnMas.setOnClickListener { alAgregar(p) }
        b.btnMenos.setOnClickListener { alQuitar(p) }
        if (soloConsulta) b.root.setOnClickListener { alAgregar(p) } else b.root.setOnClickListener(null)
    }
}
