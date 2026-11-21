package com.ferreteria.movil.ui.common

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ferreteria.movil.data.remote.SesionEvents
import com.ferreteria.movil.ui.login.LoginActivity

/**
 * Base de las pantallas con sesión: si el refresh token ya no sirve (por ejemplo, el
 * Administrador desactivó al usuario), vuelve al login con un mensaje claro.
 */
abstract class BaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SesionEvents.expirada.observe(this) { expirada ->
            if (expirada == true) irAlLogin(this, "Tu sesión terminó. Vuelve a ingresar.")
        }
    }
}

fun irAlLogin(context: Context, mensaje: String? = null) {
    context.startActivity(Intent(context, LoginActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        mensaje?.let { putExtra(LoginActivity.EXTRA_MENSAJE, it) }
    })
}

/** Factory genérica para ViewModels con dependencias. */
class VmFactory<VM : ViewModel>(private val crear: () -> VM) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = crear() as T
}

fun Context.aviso(texto: String) = Toast.makeText(this, texto, Toast.LENGTH_LONG).show()

fun View.visible(si: Boolean) {
    visibility = if (si) View.VISIBLE else View.GONE
}
