package com.ferreteria.movil.ui.inicio

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ferreteria.movil.R
import com.ferreteria.movil.data.Repositorio
import com.ferreteria.movil.data.local.TokenManager
import com.ferreteria.movil.data.model.Alerta
import com.ferreteria.movil.data.model.MetodosPago
import com.ferreteria.movil.databinding.FragmentInicioBinding
import com.ferreteria.movil.databinding.ItemAlertaBinding
import com.ferreteria.movil.databinding.ItemFilaMontoBinding
import com.ferreteria.movil.ui.asistente.AsistenteActivity
import com.ferreteria.movil.ui.common.Fechas
import com.ferreteria.movil.ui.common.Formato
import com.ferreteria.movil.ui.common.Resource
import com.ferreteria.movil.ui.common.VmFactory
import com.ferreteria.movil.ui.common.visible
import com.ferreteria.movil.ui.main.MainActivity
import com.ferreteria.movil.ui.main.Recargable
import com.google.android.material.chip.Chip
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

/** Datos del Inicio, ya calculados para pintar. */
data class EstadoInicio(
    val ventasHoy: Double = 0.0,
    val cantidadHoy: Int = 0,
    /** % frente a ayer (solo Administrador); null si no hay con qué comparar. */
    val variacion: Int? = null,
    val pedidosPendientes: Int = 0,
    val kpi2: Int = 0,
    /** Vendedor: método de pago -> monto cobrado hoy. */
    val cuadre: List<Pair<String, Double>> = emptyList(),
    val alertas: List<Alerta> = emptyList(),
    val error: String? = null
)

class InicioViewModel(private val repo: Repositorio, private val esAdmin: Boolean) : ViewModel() {

    private val _estado = MutableLiveData<EstadoInicio>()
    val estado: LiveData<EstadoInicio> = _estado

    private val _cargando = MutableLiveData(false)
    val cargando: LiveData<Boolean> = _cargando

    fun cargar() {
        _cargando.value = true
        viewModelScope.launch {
            _estado.value = if (esAdmin) cargarAdmin() else cargarVendedor()
            _cargando.value = false
        }
    }

    private suspend fun cargarAdmin(): EstadoInicio {
        val dash = viewModelScope.async { repo.dashboard() }
        val alertas = viewModelScope.async { repo.alertas() }
        val d = dash.await()
        if (d !is Resource.Success) return EstadoInicio(error = (d as? Resource.Error)?.message)
        val datos = d.data
        val hoy = datos.ventasHoy?.total ?: 0.0
        val ayer = datos.ultimos7Dias?.firstOrNull { it.dia?.take(10) == Fechas.haceDias(1) }?.total ?: 0.0
        val variacion = if (ayer > 0) Math.round((hoy - ayer) / ayer * 100).toInt() else null
        val agotados = datos.stockCritico.orEmpty().count { (it.stockActual ?: 0) <= 0 }
        val lista = (alertas.await() as? Resource.Success)?.data?.alertas.orEmpty()
        return EstadoInicio(
            ventasHoy = hoy,
            cantidadHoy = datos.ventasHoy?.cantidad ?: 0,
            variacion = variacion,
            pedidosPendientes = datos.pedidosPendientes ?: 0,
            kpi2 = agotados,
            alertas = lista
        )
    }

    private suspend fun cargarVendedor(): EstadoInicio {
        val hoy = Fechas.hoy()
        val ventas = viewModelScope.async { repo.ventas(soloMias = true, desde = hoy, hasta = hoy) }
        val pedidos = viewModelScope.async { repo.resumenPedidos() }
        val chats = viewModelScope.async { repo.conversaciones() }
        val v = ventas.await()
        if (v !is Resource.Success) return EstadoInicio(error = (v as? Resource.Error)?.message)
        val completadas = v.data.filter { it.estado != "ANULADA" }
        val cuadre = completadas.groupBy { it.metodoPago ?: "EFECTIVO" }
            .map { (metodo, lista) -> MetodosPago.etiqueta(metodo) to lista.sumOf { it.total ?: 0.0 } }
            .sortedByDescending { it.second }
        return EstadoInicio(
            ventasHoy = completadas.sumOf { it.total ?: 0.0 },
            cantidadHoy = completadas.size,
            pedidosPendientes = (pedidos.await() as? Resource.Success)?.data?.pendientes ?: 0,
            kpi2 = (chats.await() as? Resource.Success)?.data?.sumOf { it.noLeidos ?: 0 } ?: 0,
            cuadre = cuadre
        )
    }
}

class InicioFragment : Fragment(), Recargable {

    private var _binding: FragmentInicioBinding? = null
    private val binding get() = _binding!!
    private lateinit var sesion: TokenManager

    private val vm: InicioViewModel by viewModels {
        val s = TokenManager.getInstance(requireContext())
        VmFactory { InicioViewModel(Repositorio(requireContext()), s.isAdmin()) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentInicioBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        sesion = TokenManager.getInstance(requireContext())
        val admin = sesion.isAdmin()

        binding.txtFecha.text = Formato.hoyLargo()
        binding.txtSaludo.text = "Hola, ${sesion.getUsername().replaceFirstChar { it.uppercase() }}"
        binding.txtRol.text = Formato.rol(sesion.getRol())
        binding.txtTituloVentas.text = if (admin) "Ventas de hoy (toda la tienda)" else "Mis ventas de hoy"
        binding.lblKpi2.text = if (admin) "Productos agotados" else "Mensajes sin leer"
        binding.bloqueAsistente.visible(admin)
        binding.bloqueCuadre.visible(!admin)

        binding.kpi1.setOnClickListener { (activity as? MainActivity)?.irA(R.id.nav_pedidos) }
        binding.kpi2.setOnClickListener {
            if (admin) {
                startActivity(Intent(requireContext(), AsistenteActivity::class.java)
                    .putExtra(AsistenteActivity.EXTRA_PREGUNTA, "¿Qué productos me falta comprar y cuántos?"))
            } else {
                (activity as? MainActivity)?.irA(R.id.nav_whatsapp)
            }
        }

        if (admin) crearPreguntasRapidas()

        binding.refrescar.setColorSchemeResources(R.color.navy)
        binding.refrescar.setOnRefreshListener { vm.cargar() }
        vm.cargando.observe(viewLifecycleOwner) { binding.refrescar.isRefreshing = it }
        vm.estado.observe(viewLifecycleOwner) { pintar(it, admin) }
        vm.cargar()
    }

    private fun crearPreguntasRapidas() {
        val preguntas = listOf(
            "Resumen de hoy" to "",
            "¿Qué me falta comprar?" to "¿Qué productos me falta comprar y cuántos?",
            "¿Cómo van los vendedores?" to "¿Cómo van las ventas de cada vendedor hoy?",
            "Otra pregunta…" to null
        )
        binding.grupoPreguntas.removeAllViews()
        preguntas.forEach { (etiqueta, pregunta) ->
            val chip = Chip(requireContext()).apply {
                text = etiqueta
                setTextColor(resources.getColor(R.color.blanco, null))
                chipBackgroundColor = android.content.res.ColorStateList.valueOf(resources.getColor(R.color.navy_2, null))
                chipStrokeColor = android.content.res.ColorStateList.valueOf(resources.getColor(R.color.navy_borde, null))
                chipStrokeWidth = resources.displayMetrics.density
                setOnClickListener {
                    val i = Intent(requireContext(), AsistenteActivity::class.java)
                    if (pregunta != null) i.putExtra(AsistenteActivity.EXTRA_PREGUNTA, pregunta)
                    startActivity(i)
                }
            }
            binding.grupoPreguntas.addView(chip)
        }
    }

    private fun pintar(e: EstadoInicio, admin: Boolean) {
        binding.txtError.visible(e.error != null)
        binding.txtError.text = e.error
        binding.txtVentasHoy.text = Formato.soles(e.ventasHoy)
        binding.txtDetalleVentas.text = "${e.cantidadHoy} venta(s) completada(s) hoy"
        binding.txtKpi1.text = e.pedidosPendientes.toString()
        binding.txtKpi2.text = e.kpi2.toString()

        val v = e.variacion
        binding.txtComparacion.visible(admin && v != null)
        if (v != null) {
            val sube = v >= 0
            Formato.pintarEtiqueta(
                binding.txtComparacion,
                if (sube) R.color.verde else R.color.rojo,
                if (sube) R.color.verde_fondo else R.color.rojo_fondo,
                "${if (sube) "+" else ""}$v % vs ayer"
            )
        }

        if (!admin) {
            binding.listaCuadre.removeAllViews()
            val filas = e.cuadre.ifEmpty { listOf("Aún no hay ventas hoy" to 0.0) }
            filas.forEach { (metodo, monto) ->
                val fila = ItemFilaMontoBinding.inflate(layoutInflater, binding.listaCuadre, false)
                fila.txtEtiqueta.text = metodo
                fila.txtMonto.text = Formato.soles(monto)
                binding.listaCuadre.addView(fila.root)
            }
        }

        if (admin) {
            binding.bloqueAlertas.visible(e.alertas.isNotEmpty())
            binding.listaAlertas.removeAllViews()
            e.alertas.take(5).forEach { a ->
                val item = ItemAlertaBinding.inflate(layoutInflater, binding.listaAlertas, false)
                item.txtTitulo.text = a.titulo
                item.txtMensaje.text = a.mensaje
                item.punto.setBackgroundResource(
                    when (a.nivel) {
                        "CRITICO" -> R.drawable.bg_punto_rojo
                        "ALERTA" -> R.drawable.bg_punto_naranja
                        else -> R.drawable.bg_punto_verde
                    }
                )
                item.root.setOnClickListener {
                    when (a.tipo) {
                        "PEDIDOS_PENDIENTES" -> (activity as? MainActivity)?.irA(R.id.nav_pedidos)
                        else -> startActivity(Intent(requireContext(), AsistenteActivity::class.java)
                            .putExtra(AsistenteActivity.EXTRA_PREGUNTA, "Explícame esta alerta y qué hago: ${a.titulo}. ${a.mensaje}"))
                    }
                }
                binding.listaAlertas.addView(item.root)
            }
        }
    }

    override fun recargar() {
        if (_binding != null) vm.cargar()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
