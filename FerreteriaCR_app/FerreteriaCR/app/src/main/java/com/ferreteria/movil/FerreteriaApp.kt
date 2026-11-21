package com.ferreteria.movil

import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Bundle
import com.ferreteria.movil.data.local.TokenManager
import com.ferreteria.movil.ui.login.LoginActivity
import com.ferreteria.movil.ui.pin.PinActivity

/**
 * Bloqueo por inactividad: si el usuario sale de la app (a WhatsApp, Instagram...) y vuelve
 * después de 30 minutos, se pide el PIN. Si vuelve antes, entra directo.
 */
class FerreteriaApp : Application(), Application.ActivityLifecycleCallbacks {

    private var visibles = 0

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(this)
        crearCanalNotificaciones()
    }

    override fun onActivityStarted(activity: Activity) {
        val volvioALaApp = visibles == 0
        visibles++
        if (!volvioALaApp || activity is PinActivity || activity is LoginActivity) return
        val sesion = TokenManager.getInstance(this)
        if (sesion.isLoggedIn() && sesion.tienePin() && sesion.debeBloquear()) {
            activity.startActivity(Intent(activity, PinActivity::class.java))
        }
    }

    override fun onActivityStopped(activity: Activity) {
        visibles = (visibles - 1).coerceAtLeast(0)
        // Momento en que el usuario dejó la app: desde aquí se cuentan los 30 minutos
        if (visibles == 0) TokenManager.getInstance(this).marcarActividad()
    }

    override fun onActivityResumed(activity: Activity) {
        if (activity !is PinActivity) TokenManager.getInstance(this).marcarActividad()
    }

    private fun crearCanalNotificaciones() {
        val canal = NotificationChannel(CANAL_ALERTAS, getString(R.string.canal_alertas), NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = getString(R.string.canal_alertas_desc) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit

    companion object {
        const val CANAL_ALERTAS = "alertas_tienda"
    }
}
