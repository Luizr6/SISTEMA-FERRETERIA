package com.ferreteria.movil.ui.login

import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ferreteria.movil.data.Repositorio
import com.ferreteria.movil.data.local.TokenManager
import com.ferreteria.movil.databinding.ActivityLoginBinding
import com.ferreteria.movil.ui.common.Resource
import com.ferreteria.movil.ui.common.aviso
import com.ferreteria.movil.ui.common.visible
import com.ferreteria.movil.ui.pin.PinActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * Inicio de sesión con usuario y contraseña de Django. Solo se usa la primera vez en el
 * celular o después de cerrar sesión: luego basta el PIN de 4 dígitos.
 * No hay que elegir "vendedor" o "administrador": la app lo sabe por la cuenta.
 */
class LoginActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MENSAJE = "mensaje"
    }

    private lateinit var binding: ActivityLoginBinding
    private lateinit var sesion: TokenManager
    private lateinit var repo: Repositorio

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sesion = TokenManager.getInstance(this)
        repo = Repositorio(this)

        // Sesión guardada: no se pide la contraseña, solo el PIN
        if (sesion.isLoggedIn()) {
            irAlPin()
            return
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        intent.getStringExtra(EXTRA_MENSAJE)?.let { mostrarError(it) }

        binding.btnIngresar.setOnClickListener { ingresar() }
        binding.etClave.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                ingresar()
                true
            } else {
                false
            }
        }
        binding.btnServidor.setOnClickListener { configurarServidor() }
        binding.imgLogo.setOnLongClickListener {
            configurarServidor()
            true
        }
        binding.btnOlvide.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("¿Olvidaste tu contraseña?")
                .setMessage("Pídele al administrador de la tienda que te asigne una nueva desde el panel web (Usuarios).")
                .setPositiveButton("Entendido", null)
                .show()
        }
    }

    private fun ingresar() {
        val usuario = binding.etUsuario.text?.toString().orEmpty().trim()
        val clave = binding.etClave.text?.toString().orEmpty()
        binding.tilUsuario.error = if (usuario.isBlank()) "Escribe tu usuario" else null
        binding.tilClave.error = if (clave.isBlank()) "Escribe tu contraseña" else null
        if (usuario.isBlank() || clave.isBlank()) return

        cargando(true)
        binding.txtMensaje.visible(false)
        lifecycleScope.launch {
            when (val r = repo.login(usuario, clave)) {
                is Resource.Success -> irAlPin()
                is Resource.Error -> {
                    cargando(false)
                    mostrarError(r.message)
                }
                Resource.Loading -> Unit
            }
        }
    }

    /** Primera vez: crear PIN. Con PIN creado: desbloquear. */
    private fun irAlPin() {
        startActivity(Intent(this, PinActivity::class.java).putExtra(PinActivity.EXTRA_ABRIR_INICIO, true))
        finish()
    }

    private fun cargando(si: Boolean) {
        binding.btnIngresar.isEnabled = !si
        binding.btnIngresar.text = if (si) "Ingresando…" else "Ingresar"
        binding.progreso.visible(si)
    }

    private fun mostrarError(texto: String) {
        binding.txtMensaje.text = texto
        binding.txtMensaje.visible(true)
    }

    /**
     * Dirección del backend Django:
     *  - Emulador: http://10.0.2.2:8000/
     *  - Celular en la misma Wi-Fi: http://IP-de-la-PC:8000/
     *  - Celular con datos móviles: la URL de ngrok (https://...ngrok-free.dev/)
     */
    private fun configurarServidor() {
        val campo = EditText(this).apply {
            setText(sesion.getBaseUrl())
            setSelection(text.length)
            hint = "https://tu-dominio.ngrok-free.dev/"
        }
        val contenedor = LinearLayout(this).apply {
            setPadding(56, 24, 56, 0)
            addView(campo, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Dirección del servidor")
            .setMessage("Emulador: http://10.0.2.2:8000/\nCelular con Wi-Fi: http://IP-de-la-PC:8000/\nCelular con datos: la URL de ngrok")
            .setView(contenedor)
            .setPositiveButton("Guardar") { _, _ ->
                val url = campo.text.toString()
                if (url.isNotBlank()) {
                    sesion.setBaseUrl(url)
                    aviso("Servidor: ${sesion.getBaseUrl()}")
                }
            }
            .setNeutralButton("Emulador") { _, _ ->
                sesion.setBaseUrl(TokenManager.DEFAULT_BASE_URL)
                aviso("Servidor: ${TokenManager.DEFAULT_BASE_URL}")
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
