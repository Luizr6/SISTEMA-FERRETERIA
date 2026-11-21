package com.ferreteria.movil.ui.pin

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.ferreteria.movil.R
import com.ferreteria.movil.data.local.TokenManager
import com.ferreteria.movil.databinding.ActivityPinBinding
import com.ferreteria.movil.ui.common.Formato
import com.ferreteria.movil.ui.common.irAlLogin
import com.ferreteria.movil.ui.main.MainActivity

/**
 * PIN de 4 dígitos:
 *  - CREAR: después del primer login en el celular (se escribe dos veces).
 *  - DESBLOQUEAR: al abrir la app o al volver después de 30 minutos.
 * Tras 5 intentos fallidos se cierra la sesión y se pide usuario y contraseña.
 */
class PinActivity : AppCompatActivity() {

    companion object {
        /** true cuando viene del login o de abrir la app (al terminar abre el inicio). */
        const val EXTRA_ABRIR_INICIO = "abrir_inicio"

        /** true desde "Más > Cambiar PIN": crea uno nuevo y se puede volver atrás. */
        const val EXTRA_CAMBIAR = "cambiar_pin"
    }

    private lateinit var binding: ActivityPinBinding
    private lateinit var sesion: TokenManager
    private val digitos = StringBuilder()
    private var creando = false
    private var primerPin: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPinBinding.inflate(layoutInflater)
        setContentView(binding.root)
        sesion = TokenManager.getInstance(this)

        if (!sesion.isLoggedIn()) {
            irAlLogin(this)
            finish()
            return
        }
        val cambiando = intent.getBooleanExtra(EXTRA_CAMBIAR, false)
        creando = !sesion.tienePin() || cambiando
        actualizarTextos()

        val teclas = listOf(
            binding.tecla0, binding.tecla1, binding.tecla2, binding.tecla3, binding.tecla4,
            binding.tecla5, binding.tecla6, binding.tecla7, binding.tecla8, binding.tecla9
        )
        teclas.forEachIndexed { numero, tecla -> tecla.setOnClickListener { escribir(numero) } }
        binding.teclaBorrar.setOnClickListener {
            if (digitos.isNotEmpty()) digitos.deleteCharAt(digitos.length - 1)
            pintarPuntos()
        }
        binding.btnUsarClave.setOnClickListener { cerrarSesion(null) }

        // Atrás no salta el PIN: manda la app al fondo
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (cambiando) finish() else moveTaskToBack(true)
            }
        })
    }

    private fun actualizarTextos() {
        val nombre = sesion.getUsername().replaceFirstChar { it.uppercase() }
        if (creando) {
            binding.txtTitulo.text = if (primerPin == null) "Crea tu PIN" else "Confirma tu PIN"
            binding.txtSubtitulo.text = if (primerPin == null) {
                "Elige 4 dígitos. Lo usarás para entrar rápido sin escribir tu contraseña."
            } else {
                "Escribe el mismo PIN otra vez"
            }
            binding.btnUsarClave.visibility = View.GONE
            binding.txtAyuda.text = "Ingresaste como ${Formato.rol(sesion.getRol()).lowercase()}"
        } else {
            binding.txtTitulo.text = "Hola, $nombre"
            binding.txtSubtitulo.text = "Ingresa tu PIN para continuar"
            binding.btnUsarClave.visibility = View.VISIBLE
            binding.txtAyuda.text = "Se pide al abrir la app o al volver después de ${TokenManager.MINUTOS_BLOQUEO} minutos"
        }
    }

    private fun escribir(numero: Int) {
        if (digitos.length >= 4) return
        digitos.append(numero)
        binding.txtError.text = ""
        pintarPuntos()
        if (digitos.length == 4) binding.root.postDelayed({ completar(digitos.toString()) }, 120)
    }

    private fun completar(pin: String) {
        digitos.clear()
        if (creando) {
            val anterior = primerPin
            when {
                anterior == null -> primerPin = pin
                anterior == pin -> {
                    sesion.guardarPin(pin)
                    terminar()
                    return
                }
                else -> {
                    primerPin = null
                    binding.txtError.text = "Los PIN no coinciden. Empecemos de nuevo."
                }
            }
            actualizarTextos()
            pintarPuntos()
            return
        }

        if (sesion.verificarPin(pin)) {
            terminar()
            return
        }
        val restantes = TokenManager.MAX_INTENTOS_PIN - sesion.intentosFallidos()
        if (restantes <= 0) {
            cerrarSesion("Demasiados intentos con el PIN. Ingresa con tu usuario y contraseña.")
            return
        }
        binding.txtError.text = "PIN incorrecto. Te quedan $restantes intento(s)."
        binding.filaPuntos.animate().translationX(18f).setDuration(60).withEndAction {
            binding.filaPuntos.animate().translationX(-18f).setDuration(60).withEndAction {
                binding.filaPuntos.animate().translationX(0f).setDuration(60).start()
            }.start()
        }.start()
        pintarPuntos()
    }

    private fun pintarPuntos() {
        val puntos = listOf(binding.punto1, binding.punto2, binding.punto3, binding.punto4)
        puntos.forEachIndexed { i, v ->
            v.setBackgroundResource(if (i < digitos.length) R.drawable.bg_pin_lleno else R.drawable.bg_pin_vacio)
        }
    }

    private fun terminar() {
        sesion.marcarActividad()
        if (intent.getBooleanExtra(EXTRA_ABRIR_INICIO, false)) {
            startActivity(Intent(this, MainActivity::class.java))
        }
        finish()
    }

    private fun cerrarSesion(mensaje: String?) {
        sesion.clear()
        irAlLogin(this, mensaje)
        finish()
    }
}
