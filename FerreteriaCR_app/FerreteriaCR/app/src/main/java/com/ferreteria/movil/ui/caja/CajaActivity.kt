package com.ferreteria.movil.ui.caja

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.ferreteria.movil.R
import com.ferreteria.movil.data.Repositorio
import com.ferreteria.movil.data.local.TokenManager
import com.ferreteria.movil.data.model.Caja
import com.ferreteria.movil.data.model.MetodosPago
import com.ferreteria.movil.databinding.ActivityCajaBinding
import com.ferreteria.movil.databinding.ItemFilaMontoBinding
import com.ferreteria.movil.databinding.ItemLineaBinding
import com.ferreteria.movil.ui.common.BaseActivity
import com.ferreteria.movil.ui.common.Formato
import com.ferreteria.movil.ui.common.Resource
import com.ferreteria.movil.ui.common.aviso
import com.ferreteria.movil.ui.common.visible
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Si el backend rechazó un cobro porque la caja está cerrada, ofrece abrirla. Devuelve true si lo manejó.
 */
fun android.app.Activity.ofrecerAbrirCaja(mensaje: String): Boolean {
    if (!mensaje.contains("caja", ignoreCase = true)) return false
    MaterialAlertDialogBuilder(this)
        .setTitle("Tu caja está cerrada")
        .setMessage("$mensaje\n\nÁbrela con el efectivo que tienes para dar vuelto y vuelve a intentarlo.")
        .setPositiveButton("Abrir caja") { _, _ -> startActivity(Intent(this, CajaActivity::class.java)) }
        .setNegativeButton("Ahora no", null)
        .show()
    return true
}

/**
 * Mi caja: abrir con el sencillo, registrar ingresos o egresos y cerrar contando el efectivo.
 * Con EXTRA_ID muestra una caja del historial (el Administrador puede cerrar una que quedó abierta).
 * Es la misma caja que en la web: lo que se cobra en la web o en la app entra aquí.
 */
class CajaActivity : BaseActivity() {

    companion object {
        const val EXTRA_ID = "caja_id"
    }

    private lateinit var binding: ActivityCajaBinding
    private lateinit var repo: Repositorio
    private var caja: Caja? = null
    private var idHistorial = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCajaBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repo = Repositorio(this)
        idHistorial = intent.getIntExtra(EXTRA_ID, 0)

        binding.btnVolver.setOnClickListener { finish() }
        binding.btnAbrir.setOnClickListener { abrir() }
        binding.btnIngreso.setOnClickListener { movimiento("INGRESO") }
        binding.btnEgreso.setOnClickListener { movimiento("EGRESO") }
        binding.btnCerrar.setOnClickListener { cerrar() }
        binding.btnHistorial.setOnClickListener { startActivity(Intent(this, CajasActivity::class.java)) }
    }

    override fun onResume() {
        super.onResume()
        cargar()
    }

    private fun cargar() {
        lifecycleScope.launch {
            if (idHistorial != 0) {
                when (val r = repo.caja(idHistorial)) {
                    is Resource.Success -> pintar(r.data)
                    is Resource.Error -> {
                        aviso(r.message)
                        finish()
                    }
                    Resource.Loading -> Unit
                }
            } else {
                when (val r = repo.cajaActual()) {
                    is Resource.Success -> pintar(r.data.caja)
                    is Resource.Error -> {
                        binding.progreso.visible(false)
                        aviso(r.message)
                    }
                    Resource.Loading -> Unit
                }
            }
        }
    }

    private fun pintar(c: Caja?) {
        caja = c
        binding.progreso.visible(false)
        binding.bloqueAbrir.visible(c == null)
        binding.bloqueCaja.visible(c != null)
        if (c == null) {
            binding.txtTitulo.text = "Mi caja"
            binding.txtSubtitulo.text = "Cerrada"
            return
        }
        val r = c.resumen
        val esMia = c.usuario == TokenManager.getInstance(this).getUserId()
        binding.txtTitulo.text = if (esMia) "Mi caja" else "Caja de ${c.usuarioNombre.orEmpty()}"
        binding.txtSubtitulo.text = if (c.abierta) {
            "Abierta desde las ${Formato.hora(c.fechaApertura)}"
        } else {
            "${Formato.fechaHora(c.fechaApertura)} → ${Formato.fechaHora(c.fechaCierre)}"
        }

        binding.lblEsperado.text = if (c.abierta) "Debería haber en caja (efectivo)" else "Efectivo esperado al cierre"
        binding.txtEsperado.text = Formato.soles(r?.efectivoEsperado)
        binding.listaCuadre.removeAllViews()
        listOf(
            "Monto inicial" to Formato.soles(r?.montoInicial),
            "+ Ventas en efectivo (${r?.cantidadVentas ?: 0})" to Formato.soles(r?.ventasEfectivo),
            "+ Ingresos" to Formato.soles(r?.ingresos),
            "− Egresos" to Formato.soles(r?.egresos)
        ).forEach { (etiqueta, monto) -> fila(binding.listaCuadre, etiqueta, monto) }

        binding.listaOtros.removeAllViews()
        val otros = r?.porMetodo.orEmpty().filter { it.metodoPago != "EFECTIVO" }
        if (otros.isEmpty()) {
            fila(binding.listaOtros, "Sin cobros por Yape, Plin, tarjeta o transferencia", "")
        } else {
            otros.forEach { fila(binding.listaOtros, "${MetodosPago.etiqueta(it.metodoPago)} (${it.cantidad ?: 0})", Formato.soles(it.total)) }
        }
        fila(binding.listaOtros, "Total vendido", Formato.soles(r?.totalVendido))

        binding.listaMovimientos.removeAllViews()
        val movs = c.movimientos.orEmpty()
        if (movs.isEmpty()) {
            fila(binding.listaMovimientos, "Sin movimientos", "")
        }
        movs.forEach { m ->
            val b = ItemLineaBinding.inflate(layoutInflater, binding.listaMovimientos, false)
            val egreso = m.tipo == "EGRESO"
            b.txtNombre.text = m.concepto
            b.txtDetalle.text = "${Formato.hora(m.fecha)} · ${if (egreso) "Egreso" else "Ingreso"} · ${m.usuarioNombre.orEmpty()}"
            b.txtMonto.text = (if (egreso) "−" else "+") + Formato.soles(m.monto)
            b.txtMonto.setTextColor(getColor(if (egreso) R.color.rojo else R.color.verde))
            binding.listaMovimientos.addView(b.root)
        }

        // Caja cerrada: resultado del arqueo, sin botones
        val admin = TokenManager.getInstance(this).isAdmin()
        binding.filaBotones.visible(c.abierta && esMia)
        binding.btnCerrar.visible(c.abierta && (esMia || admin))
        binding.btnHistorial.visible(idHistorial == 0)
        binding.txtArqueo.visible(!c.abierta)
        if (!c.abierta) {
            val dif = c.diferencia ?: 0.0
            val resultado = when {
                dif < 0 -> "Faltante de ${Formato.soles(abs(dif))}"
                dif > 0 -> "Sobrante de ${Formato.soles(dif)}"
                else -> "Caja cuadrada"
            }
            binding.txtArqueo.text = "Contado: ${Formato.soles(c.montoContado)} · Esperado: ${Formato.soles(c.montoEsperado)}\n$resultado" +
                (c.observacion?.takeIf { it.isNotBlank() }?.let { "\n“$it”" } ?: "")
            binding.txtArqueo.setTextColor(getColor(if (dif < 0) R.color.rojo else if (dif > 0) R.color.ambar_texto else R.color.verde))
        }
    }

    private fun fila(contenedor: LinearLayout, etiqueta: String, monto: String) {
        val b = ItemFilaMontoBinding.inflate(layoutInflater, contenedor, false)
        b.txtEtiqueta.text = etiqueta
        b.txtMonto.text = monto
        contenedor.addView(b.root)
    }

    private fun abrir() {
        val monto = binding.etMontoInicial.text?.toString()?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
        binding.btnAbrir.isEnabled = false
        lifecycleScope.launch {
            when (val r = repo.abrirCaja(monto)) {
                is Resource.Success -> {
                    aviso("Caja abierta. Ya puedes cobrar.")
                    binding.etMontoInicial.setText("")
                    pintar(r.data)
                }
                is Resource.Error -> aviso(r.message)
                Resource.Loading -> Unit
            }
            binding.btnAbrir.isEnabled = true
        }
    }

    private fun campo(hint: String, tipo: Int): EditText = EditText(this).apply {
        this.hint = hint
        inputType = tipo
    }

    private fun movimiento(tipo: String) {
        val c = caja ?: return
        val id = c.id ?: return
        val egreso = tipo == "EGRESO"
        val etMonto = campo("Monto (S/)", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        val etConcepto = campo(if (egreso) "Ej.: pago a cargador" else "Ej.: sencillo que dejó el dueño",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        val contenedor = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(56, 16, 56, 0)
            addView(etMonto)
            addView(etConcepto)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(if (egreso) "Registrar egreso" else "Registrar ingreso")
            .setMessage(if (egreso) "Dinero que sale del cajón sin ser venta." else "Dinero que entra al cajón sin ser venta.")
            .setView(contenedor)
            .setPositiveButton("Registrar") { _, _ ->
                val monto = etMonto.text.toString().replace(",", ".").toDoubleOrNull() ?: 0.0
                val concepto = etConcepto.text.toString().trim()
                if (monto <= 0 || concepto.length < 3) {
                    aviso("Escribe el monto y en qué se usó el dinero.")
                    return@setPositiveButton
                }
                lifecycleScope.launch {
                    when (val r = repo.movimientoCaja(id, tipo, monto, concepto)) {
                        is Resource.Success -> {
                            aviso(if (egreso) "Egreso registrado." else "Ingreso registrado.")
                            cargar()
                        }
                        is Resource.Error -> aviso(r.message)
                        Resource.Loading -> Unit
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun cerrar() {
        val c = caja ?: return
        val id = c.id ?: return
        val esperado = c.resumen?.efectivoEsperado ?: 0.0
        val etContado = campo("Efectivo contado (S/)", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        val txtDif = TextView(this).apply {
            textSize = 15f
            setPadding(0, 12, 0, 8)
        }
        val etObs = campo("Observación (opcional)", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        etContado.doAfterTextChanged {
            val contado = it?.toString()?.replace(",", ".")?.toDoubleOrNull()
            if (contado == null) {
                txtDif.text = ""
                return@doAfterTextChanged
            }
            val dif = Formato.redondear(contado - esperado)
            txtDif.text = when {
                dif < 0 -> "Faltante: ${Formato.soles(abs(dif))}"
                dif > 0 -> "Sobrante: ${Formato.soles(dif)}"
                else -> "La caja cuadra exacto."
            }
            txtDif.setTextColor(getColor(if (dif < 0) R.color.rojo else if (dif > 0) R.color.ambar_texto else R.color.verde))
        }
        val contenedor = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(56, 16, 56, 0)
            addView(etContado)
            addView(txtDif)
            addView(etObs)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Cerrar caja")
            .setMessage("Debería haber ${Formato.soles(esperado)} en efectivo. Cuenta los billetes y monedas del cajón.")
            .setView(contenedor)
            .setPositiveButton("Cerrar caja") { _, _ ->
                val contado = etContado.text.toString().replace(",", ".").toDoubleOrNull()
                if (contado == null || contado < 0) {
                    aviso("Escribe cuánto efectivo contaste.")
                    return@setPositiveButton
                }
                lifecycleScope.launch {
                    when (val r = repo.cerrarCaja(id, contado, etObs.text.toString())) {
                        is Resource.Success -> {
                            aviso("Caja cerrada.")
                            idHistorial = r.data.id ?: 0   // se queda mostrando el resultado del arqueo
                            pintar(r.data)
                        }
                        is Resource.Error -> aviso(r.message)
                        Resource.Loading -> Unit
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
