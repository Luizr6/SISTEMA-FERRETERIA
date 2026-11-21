package com.ferreteria.movil.ui.asistente

import android.os.Bundle
import android.view.inputmethod.EditorInfo
import androidx.lifecycle.lifecycleScope
import com.ferreteria.movil.data.Repositorio
import com.ferreteria.movil.databinding.ActivityAsistenteBinding
import com.ferreteria.movil.databinding.ItemBurbujaBinding
import com.ferreteria.movil.ui.common.BaseActivity
import com.ferreteria.movil.ui.common.Burbujas
import com.ferreteria.movil.ui.common.Resource
import com.google.android.material.chip.Chip
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Asistente IA del Administrador. La pregunta va a Django (/api/asistente/), que arma los datos
 * reales del negocio y le pide a la IA que los resuma. Si la IA no está disponible, Django
 * responde con un resumen básico calculado sin IA.
 */
class AsistenteActivity : BaseActivity() {

    companion object {
        /** Pregunta para enviar al abrir. Vacía = resumen del día. */
        const val EXTRA_PREGUNTA = "pregunta"
    }

    private lateinit var binding: ActivityAsistenteBinding
    private lateinit var repo: Repositorio
    private var esperando = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAsistenteBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repo = Repositorio(this)

        binding.btnVolver.setOnClickListener { finish() }
        binding.btnEnviar.setOnClickListener { enviarEscrita() }
        binding.etPregunta.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE) {
                enviarEscrita()
                true
            } else {
                false
            }
        }

        listOf(
            "Resumen de hoy" to "",
            "¿Qué me falta comprar?" to "¿Qué productos me falta comprar y cuántos?",
            "Vendedores" to "¿Cómo van las ventas de cada vendedor hoy?",
            "Pedidos" to "¿Cuántos pedidos tengo por confirmar y cuál es el más urgente?"
        ).forEach { (etiqueta, pregunta) ->
            binding.grupoPreguntas.addView(Chip(this).apply {
                text = etiqueta
                setOnClickListener { preguntar(pregunta, etiqueta) }
            })
        }

        if (savedInstanceState == null) {
            val inicial = intent.getStringExtra(EXTRA_PREGUNTA)
            if (inicial != null) {
                preguntar(inicial, inicial.ifBlank { "Resumen de hoy" })
            } else {
                agregar(Burbujas.Tipo.CLIENTE, "Hola. Puedo resumirte las ventas, decirte qué falta reponer o cómo van los pedidos. ¿Qué necesitas saber?", "Asistente")
                binding.etPregunta.requestFocus()
            }
        }
    }

    private fun enviarEscrita() {
        val texto = binding.etPregunta.text.toString().trim()
        if (texto.isBlank()) return
        binding.etPregunta.setText("")
        preguntar(texto, texto)
    }

    private fun preguntar(pregunta: String, comoSeMuestra: String) {
        if (esperando) return
        esperando = true
        agregar(Burbujas.Tipo.VENDEDOR, comoSeMuestra, "Tú")
        val pensando = agregar(Burbujas.Tipo.CLIENTE, "Revisando los datos de la tienda…", "Asistente")
        binding.btnEnviar.isEnabled = false

        lifecycleScope.launch {
            val r = repo.preguntar(pregunta)
            binding.listaMensajes.removeView(pensando.root)
            when (r) {
                is Resource.Success -> {
                    val d = r.data
                    val autor = if (d.modo == "IA") "Asistente IA" else "Resumen automático"
                    agregar(Burbujas.Tipo.CLIENTE, d.respuesta.orEmpty().replace("*", ""), autor)
                    binding.txtModo.text = if (d.modo == "IA") {
                        "IA activa · datos reales de la tienda"
                    } else {
                        "Sin IA en este momento · resumen calculado por el sistema"
                    }
                }
                is Resource.Error -> agregar(Burbujas.Tipo.CLIENTE, r.message, "Aviso")
                Resource.Loading -> Unit
            }
            esperando = false
            binding.btnEnviar.isEnabled = true
        }
    }

    private fun agregar(tipo: Burbujas.Tipo, texto: String, autor: String): ItemBurbujaBinding {
        val b = ItemBurbujaBinding.inflate(layoutInflater, binding.listaMensajes, false)
        Burbujas.pintar(b, tipo, texto, autor, SimpleDateFormat("HH:mm", Locale.US).format(Date()))
        binding.listaMensajes.addView(b.root)
        binding.scroll.post { binding.scroll.fullScroll(android.view.View.FOCUS_DOWN) }
        return b
    }
}
