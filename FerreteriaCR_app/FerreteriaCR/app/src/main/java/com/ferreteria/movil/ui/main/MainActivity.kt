package com.ferreteria.movil.ui.main

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ferreteria.movil.R
import com.ferreteria.movil.data.Repositorio
import com.ferreteria.movil.databinding.ActivityMainBinding
import com.ferreteria.movil.notificaciones.AlertasWorker
import com.ferreteria.movil.ui.common.BaseActivity
import com.ferreteria.movil.ui.common.Resource
import com.ferreteria.movil.ui.inicio.InicioFragment
import com.ferreteria.movil.ui.mas.MasFragment
import com.ferreteria.movil.ui.pedidos.PedidosFragment
import com.ferreteria.movil.ui.vender.VenderFragment
import com.ferreteria.movil.ui.whatsapp.ChatsFragment
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Pantalla principal con las 5 pestañas: Inicio, Vender, Pedidos, WhatsApp y Más.
 * Las pestañas se conservan en memoria (no se recargan al cambiar).
 * Cada minuto actualiza los números rojos de Pedidos y WhatsApp.
 */
class MainActivity : BaseActivity() {

    companion object {
        const val EXTRA_PESTANA = "pestana"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var repo: Repositorio

    private val permisoNotificaciones =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repo = Repositorio(this)

        binding.barraInferior.setOnItemSelectedListener { item ->
            mostrar(item.itemId)
            true
        }
        binding.barraInferior.setOnItemReselectedListener { item ->
            (supportFragmentManager.findFragmentByTag(tag(item.itemId)) as? Recargable)?.recargar()
        }

        if (savedInstanceState == null) {
            val inicial = intent.getIntExtra(EXTRA_PESTANA, R.id.nav_inicio)
            binding.barraInferior.selectedItemId = inicial
            mostrar(inicial)
        }

        pedirPermisoNotificaciones()
        AlertasWorker.programar(this)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    refrescarInsignias()
                    delay(60_000)
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        val pestana = intent.getIntExtra(EXTRA_PESTANA, 0)
        if (pestana != 0) irA(pestana)
    }

    /** Cambia de pestaña desde un fragmento (por ejemplo, "Ver pedidos" en el Inicio). */
    fun irA(itemId: Int) {
        binding.barraInferior.selectedItemId = itemId
    }

    private fun tag(itemId: Int) = "pestana_$itemId"

    private fun crear(itemId: Int): Fragment = when (itemId) {
        R.id.nav_vender -> VenderFragment()
        R.id.nav_pedidos -> PedidosFragment()
        R.id.nav_whatsapp -> ChatsFragment()
        R.id.nav_mas -> MasFragment()
        else -> InicioFragment()
    }

    private fun mostrar(itemId: Int) {
        val fm = supportFragmentManager
        val tx = fm.beginTransaction()
        fm.fragments.forEach { if (it.tag?.startsWith("pestana_") == true) tx.hide(it) }
        val existente = fm.findFragmentByTag(tag(itemId))
        if (existente != null) {
            if (existente.isHidden) (existente as? Recargable)?.alVolver()
            tx.show(existente)
        } else {
            tx.add(R.id.contenedor, crear(itemId), tag(itemId))
        }
        tx.commitNow()
    }

    /** Números rojos: pedidos por confirmar y mensajes de WhatsApp sin leer. */
    fun refrescarInsignias() {
        lifecycleScope.launch {
            val pedidos = repo.resumenPedidos()
            if (pedidos is Resource.Success) {
                pintarInsignia(R.id.nav_pedidos, pedidos.data.pendientes ?: 0)
            }
            val chats = repo.conversaciones()
            if (chats is Resource.Success) {
                pintarInsignia(R.id.nav_whatsapp, chats.data.sumOf { it.noLeidos ?: 0 })
            }
        }
    }

    private fun pintarInsignia(itemId: Int, cantidad: Int) {
        if (cantidad > 0) {
            binding.barraInferior.getOrCreateBadge(itemId).apply {
                number = cantidad
                backgroundColor = ContextCompat.getColor(this@MainActivity, R.color.rojo)
                badgeTextColor = ContextCompat.getColor(this@MainActivity, R.color.blanco)
                isVisible = true
            }
        } else {
            binding.barraInferior.removeBadge(itemId)
        }
    }

    private fun pedirPermisoNotificaciones() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permisoNotificaciones.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/** Pestañas que se pueden recargar al volver a tocarlas o al mostrarse de nuevo. */
interface Recargable {
    fun recargar()
    fun alVolver() = recargar()
}
