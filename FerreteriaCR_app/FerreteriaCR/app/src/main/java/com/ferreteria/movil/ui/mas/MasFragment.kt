package com.ferreteria.movil.ui.mas

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkManager
import com.ferreteria.movil.BuildConfig
import com.ferreteria.movil.data.Repositorio
import com.ferreteria.movil.data.local.CarritoStore
import com.ferreteria.movil.data.local.TokenManager
import com.ferreteria.movil.data.model.MetodosPago
import com.ferreteria.movil.data.model.Venta
import com.ferreteria.movil.databinding.FragmentMasBinding
import com.ferreteria.movil.notificaciones.AlertasWorker
import com.ferreteria.movil.ui.caja.CajaActivity
import com.ferreteria.movil.ui.caja.CajasActivity
import com.ferreteria.movil.ui.clientes.ClientesActivity
import com.ferreteria.movil.ui.common.Fechas
import com.ferreteria.movil.ui.common.Formato
import com.ferreteria.movil.ui.common.Resource
import com.ferreteria.movil.ui.common.aviso
import com.ferreteria.movil.ui.common.irAlLogin
import com.ferreteria.movil.ui.common.visible
import com.ferreteria.movil.ui.main.Recargable
import com.ferreteria.movil.ui.pin.PinActivity
import com.ferreteria.movil.ui.vender.ConsultaActivity
import com.ferreteria.movil.ui.ventas.VentasActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * Más: mi día (cuadre por forma de pago), herramientas, opciones del Administrador y la cuenta.
 * El Vendedor no ve la sección "Solo administrador".
 */
class MasFragment : Fragment(), Recargable {

    private var _binding: FragmentMasBinding? = null
    private val binding get() = _binding!!
    private lateinit var sesion: TokenManager
    private lateinit var repo: Repositorio
    private var ventasHoy: List<Venta> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMasBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        sesion = TokenManager.getInstance(requireContext())
        repo = Repositorio(requireContext())
        val admin = sesion.isAdmin()
        val usuario = sesion.getUsername()

        binding.txtAvatar.text = usuario.take(2).uppercase()
        binding.txtUsuario.text = usuario.replaceFirstChar { it.uppercase() }
        binding.txtRol.text = "${Formato.rol(sesion.getRol())} · usuario: $usuario"
        binding.lblBloqueAdmin.visible(admin)
        binding.bloqueAdmin.visible(admin)
        binding.txtVersion.text = "Ferretería C&R · versión ${BuildConfig.VERSION_NAME}"

        binding.opMiDia.setOnClickListener { mostrarMiDia() }
        binding.opCaja.setOnClickListener { startActivity(Intent(requireContext(), CajaActivity::class.java)) }
        binding.opCajas.setOnClickListener { startActivity(Intent(requireContext(), CajasActivity::class.java)) }
        binding.opConsulta.setOnClickListener { startActivity(Intent(requireContext(), ConsultaActivity::class.java)) }
        binding.opVentas.setOnClickListener { startActivity(Intent(requireContext(), VentasActivity::class.java)) }
        binding.opClientes.setOnClickListener { startActivity(Intent(requireContext(), ClientesActivity::class.java)) }
        binding.opPorVendedor.setOnClickListener { ventasPorVendedor() }
        binding.opAnular.setOnClickListener {
            startActivity(Intent(requireContext(), VentasActivity::class.java).putExtra(VentasActivity.EXTRA_PARA_ANULAR, true))
        }
        binding.opServidor.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Servidor conectado")
                .setMessage("${sesion.getBaseUrl()}\n\nPara cambiarlo, cierra sesión y toca \"Servidor\" en la pantalla de ingreso.")
                .setPositiveButton("Entendido", null)
                .show()
        }
        binding.opPin.setOnClickListener {
            startActivity(Intent(requireContext(), PinActivity::class.java).putExtra(PinActivity.EXTRA_CAMBIAR, true))
        }
        binding.opSalir.setOnClickListener { cerrarSesion() }
        cargarMiDia()
    }

    override fun onResume() {
        super.onResume()
        cargarMiCaja()
    }

    private fun cargarMiCaja() {
        lifecycleScope.launch {
            val r = repo.cajaActual()
            if (_binding == null) return@launch
            binding.txtMiCaja.text = when (r) {
                is Resource.Success -> r.data.caja?.let {
                    "Abierta desde las ${Formato.hora(it.fechaApertura)} · debería haber ${Formato.soles(it.resumen?.efectivoEsperado)}"
                } ?: "Cerrada · ábrela para poder cobrar"
                is Resource.Error -> r.message
                Resource.Loading -> ""
            }
        }
    }

    private fun cargarMiDia() {
        val hoy = Fechas.hoy()
        lifecycleScope.launch {
            when (val r = repo.ventas(soloMias = true, desde = hoy, hasta = hoy)) {
                is Resource.Success -> {
                    ventasHoy = r.data.filter { it.estado != "ANULADA" }
                    if (_binding != null) {
                        binding.txtMiDia.text = "${ventasHoy.size} venta(s) hoy · ${Formato.soles(ventasHoy.sumOf { it.total ?: 0.0 })}"
                    }
                }
                is Resource.Error -> if (_binding != null) binding.txtMiDia.text = r.message
                Resource.Loading -> Unit
            }
        }
    }

    private fun mostrarMiDia() {
        val porMetodo = ventasHoy.groupBy { it.metodoPago ?: "EFECTIVO" }
        val lineas = if (porMetodo.isEmpty()) {
            "Aún no registraste ventas hoy."
        } else {
            porMetodo.entries.sortedByDescending { e -> e.value.sumOf { it.total ?: 0.0 } }.joinToString("\n") { (metodo, lista) ->
                "${MetodosPago.etiqueta(metodo)}: ${Formato.soles(lista.sumOf { it.total ?: 0.0 })} (${lista.size})"
            }
        }
        val efectivo = porMetodo["EFECTIVO"].orEmpty().sumOf { it.total ?: 0.0 }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Mi día · ${Formato.hoyLargo()}")
            .setMessage(
                "$lineas\n\nTotal: ${Formato.soles(ventasHoy.sumOf { it.total ?: 0.0 })}\n" +
                    "Efectivo que debería estar en caja: ${Formato.soles(efectivo)}\n\n" +
                    "Yape, Plin, tarjeta y transferencias no están en el cajón."
            )
            .setPositiveButton("Cerrar", null)
            .show()
    }

    private fun ventasPorVendedor() {
        val hoy = Fechas.hoy()
        lifecycleScope.launch {
            when (val r = repo.resumenVentas(soloMias = false, desde = hoy, hasta = hoy)) {
                is Resource.Success -> {
                    val lista = r.data.porVendedor.orEmpty()
                    val texto = if (lista.isEmpty()) {
                        "Todavía no hay ventas hoy."
                    } else {
                        lista.sortedByDescending { it.total ?: 0.0 }.joinToString("\n") {
                            "${it.vendedor.orEmpty()}: ${Formato.soles(it.total)} · ${it.cantidad ?: 0} venta(s)"
                        } + "\n\nTotal de la tienda: ${Formato.soles(r.data.totalVendido)}"
                    }
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Ventas por vendedor (hoy)")
                        .setMessage(texto)
                        .setPositiveButton("Cerrar", null)
                        .show()
                }
                is Resource.Error -> requireContext().aviso(r.message)
                Resource.Loading -> Unit
            }
        }
    }

    private fun cerrarSesion() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("¿Cerrar sesión?")
            .setMessage("La próxima vez tendrás que ingresar con tu usuario y contraseña y crear tu PIN.")
            .setPositiveButton("Cerrar sesión") { _, _ ->
                val ctx = requireContext().applicationContext
                WorkManager.getInstance(ctx).cancelUniqueWork(AlertasWorker.NOMBRE)
                CarritoStore.getInstance(ctx).vaciar()
                sesion.clear()
                irAlLogin(requireContext())
                requireActivity().finish()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    override fun recargar() {
        if (_binding != null) cargarMiDia()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
