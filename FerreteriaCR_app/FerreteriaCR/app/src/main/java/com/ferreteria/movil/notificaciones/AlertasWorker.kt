package com.ferreteria.movil.notificaciones

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ferreteria.movil.FerreteriaApp
import com.ferreteria.movil.R
import com.ferreteria.movil.data.Repositorio
import com.ferreteria.movil.data.local.TokenManager
import com.ferreteria.movil.ui.common.Resource
import com.ferreteria.movil.ui.main.MainActivity
import java.util.concurrent.TimeUnit

/**
 * Cada 15 minutos (el mínimo que permite Android) revisa la tienda y avisa:
 *  - Todos: pedidos nuevos por confirmar y mensajes de WhatsApp sin leer.
 *  - Administrador: además, las alertas del backend (agotados, stock mínimo, compras atrasadas,
 *    ventas bajas).
 * Cada aviso tiene un id estable: la misma situación no se notifica dos veces.
 */
class AlertasWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        const val NOMBRE = "alertas_tienda"
        private const val PREFS = "alertas_notificadas"
        private const val KEY_IDS = "ids"

        fun programar(context: Context) {
            val pedido = PeriodicWorkRequestBuilder<AlertasWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NOMBRE, ExistingPeriodicWorkPolicy.KEEP, pedido)
        }
    }

    private data class Aviso(val id: String, val titulo: String, val texto: String, val pestana: Int)

    override suspend fun doWork(): Result {
        val sesion = TokenManager.getInstance(applicationContext)
        if (!sesion.isLoggedIn()) return Result.success()
        val repo = Repositorio(applicationContext)
        val avisos = mutableListOf<Aviso>()

        val pedidos = repo.resumenPedidos()
        if (pedidos is Resource.Success) {
            val n = pedidos.data.pendientes ?: 0
            val wa = pedidos.data.pendientesPorOrigen?.get("WHATSAPP") ?: 0
            if (n > 0) {
                avisos += Aviso(
                    "pedidos-$n-$wa", "$n pedido(s) por confirmar",
                    if (wa > 0) "$wa llegaron por WhatsApp. Revísalos en Pedidos." else "Revísalos en Pedidos.",
                    R.id.nav_pedidos
                )
            }
        }

        val chats = repo.conversaciones()
        if (chats is Resource.Success) {
            val sinLeer = chats.data.filter { (it.noLeidos ?: 0) > 0 }
            val total = sinLeer.sumOf { it.noLeidos ?: 0 }
            if (total > 0) {
                val nombres = sinLeer.take(3).joinToString { it.nombreVisible }
                val ultimo = sinLeer.maxOfOrNull { it.ultimoMensaje.orEmpty() }.orEmpty()
                avisos += Aviso("chats-$total-$ultimo", "$total mensaje(s) de WhatsApp sin leer", "De: $nombres", R.id.nav_whatsapp)
            }
        }

        if (sesion.isAdmin()) {
            val alertas = repo.alertas()
            if (alertas is Resource.Success) {
                alertas.data.alertas.orEmpty()
                    .filter { it.tipo != "PEDIDOS_PENDIENTES" } // ya se avisa arriba
                    .forEach { a ->
                        val id = a.id ?: return@forEach
                        avisos += Aviso("admin-$id", a.titulo.orEmpty(), a.mensaje.orEmpty(), R.id.nav_inicio)
                    }
            }
        }

        notificarNuevos(avisos)
        return Result.success()
    }

    private fun notificarNuevos(avisos: List<Aviso>) {
        val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val yaAvisados = prefs.getStringSet(KEY_IDS, emptySet()).orEmpty()
        val puedeNotificar = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

        if (puedeNotificar) {
            val manager = NotificationManagerCompat.from(applicationContext)
            avisos.filter { it.id !in yaAvisados }.forEach { a ->
                val abrir = Intent(applicationContext, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_PESTANA, a.pestana)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                val pendiente = PendingIntent.getActivity(
                    applicationContext, a.id.hashCode(), abrir,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val notificacion = NotificationCompat.Builder(applicationContext, FerreteriaApp.CANAL_ALERTAS)
                    .setSmallIcon(R.drawable.ic_bell)
                    .setColor(ContextCompat.getColor(applicationContext, R.color.ambar))
                    .setContentTitle(a.titulo)
                    .setContentText(a.texto)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(a.texto))
                    .setContentIntent(pendiente)
                    .setAutoCancel(true)
                    .build()
                try {
                    manager.notify(a.id.hashCode(), notificacion)
                } catch (_: SecurityException) {
                    // Sin permiso de notificaciones: no se avisa
                }
            }
        }
        // Se guardan solo los avisos vigentes: si la situación cambia, se vuelve a avisar
        prefs.edit().putStringSet(KEY_IDS, avisos.map { it.id }.toSet()).apply()
    }
}
