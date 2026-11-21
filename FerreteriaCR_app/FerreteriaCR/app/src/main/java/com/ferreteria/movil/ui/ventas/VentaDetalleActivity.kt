package com.ferreteria.movil.ui.ventas

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.ferreteria.movil.R
import com.ferreteria.movil.data.Repositorio
import com.ferreteria.movil.data.local.TokenManager
import com.ferreteria.movil.data.model.MetodosPago
import com.ferreteria.movil.data.model.Venta
import com.ferreteria.movil.databinding.ActivityVentaDetalleBinding
import com.ferreteria.movil.databinding.ItemFilaMontoBinding
import com.ferreteria.movil.databinding.ItemLineaBinding
import com.ferreteria.movil.ui.common.BaseActivity
import com.ferreteria.movil.ui.common.Formato
import com.ferreteria.movil.ui.common.Resource
import com.ferreteria.movil.ui.common.aviso
import com.ferreteria.movil.ui.common.visible
import com.ferreteria.movil.ui.main.MainActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.io.File

/**
 * Detalle de una venta: boleta en PDF (ver o compartir por WhatsApp) y, solo el Administrador,
 * anular con motivo (el stock vuelve al inventario).
 */
class VentaDetalleActivity : BaseActivity() {

    companion object {
        const val EXTRA_ID = "venta_id"
        /** true cuando se acaba de cobrar: muestra el aviso de éxito y "Nueva venta". */
        const val EXTRA_NUEVA = "nueva"
    }

    private lateinit var binding: ActivityVentaDetalleBinding
    private lateinit var repo: Repositorio
    private var venta: Venta? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVentaDetalleBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repo = Repositorio(this)
        val nueva = intent.getBooleanExtra(EXTRA_NUEVA, false)

        binding.btnVolver.setOnClickListener { finish() }
        binding.txtExito.visible(nueva)
        binding.btnNueva.visible(nueva)
        binding.btnNueva.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_PESTANA, R.id.nav_vender)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            finish()
        }
        binding.btnCompartir.setOnClickListener { abrirPdf(compartir = true) }
        binding.btnVerPdf.setOnClickListener { abrirPdf(compartir = false) }
        binding.btnAnular.setOnClickListener { pedirMotivo() }
        cargar()
    }

    private fun cargar() {
        lifecycleScope.launch {
            when (val r = repo.venta(intent.getIntExtra(EXTRA_ID, 0))) {
                is Resource.Success -> pintar(r.data)
                is Resource.Error -> {
                    aviso(r.message)
                    finish()
                }
                Resource.Loading -> Unit
            }
        }
    }

    private fun pintar(v: Venta) {
        venta = v
        binding.progreso.visible(false)
        binding.contenido.visible(true)
        binding.txtTitulo.text = "Boleta ${v.numero.orEmpty()}"
        binding.txtFecha.text = Formato.fechaHora(v.fecha)
        binding.txtCliente.text = v.nombreCliente
        binding.txtDatos.text = listOfNotNull(
            v.clienteDocumento?.takeIf { it.isNotBlank() },
            "Pago: ${MetodosPago.etiqueta(v.metodoPago)}",
            "Vendedor: ${v.vendedorNombre.orEmpty()}",
            v.pedidoNumero?.let { "Pedido: $it" },
            v.observacion?.takeIf { it.isNotBlank() }?.let { "Nota: $it" }
        ).joinToString("\n")

        binding.listaProductos.removeAllViews()
        v.detalles.orEmpty().forEach { d ->
            val fila = ItemLineaBinding.inflate(layoutInflater, binding.listaProductos, false)
            fila.txtNombre.text = d.productoNombre
            fila.txtDetalle.text = "${d.cantidad ?: 0} × ${Formato.soles(d.precioUnitario)} (sin IGV)"
            fila.txtMonto.text = Formato.soles(d.subtotal)
            binding.listaProductos.addView(fila.root)
        }
        binding.listaTotales.removeAllViews()
        listOf("Subtotal" to v.subtotal, "IGV (18 %)" to v.igv, "Total" to v.total).forEach { (etiqueta, monto) ->
            val fila = ItemFilaMontoBinding.inflate(layoutInflater, binding.listaTotales, false)
            fila.txtEtiqueta.text = etiqueta
            fila.txtMonto.text = Formato.soles(monto)
            if (etiqueta == "Total") {
                fila.txtEtiqueta.setTypeface(null, android.graphics.Typeface.BOLD)
                fila.txtMonto.textSize = 20f
            }
            binding.listaTotales.addView(fila.root)
        }

        binding.txtAnulada.visible(v.anulada)
        binding.txtAnulada.text = "Venta anulada por ${v.anuladaPorNombre.orEmpty()}: ${v.motivoAnulacion.orEmpty()}"
        binding.btnAnular.visible(!v.anulada && TokenManager.getInstance(this).isAdmin())
    }

    private fun abrirPdf(compartir: Boolean) {
        val v = venta ?: return
        val id = v.id ?: return
        binding.btnCompartir.isEnabled = false
        binding.btnVerPdf.isEnabled = false
        lifecycleScope.launch {
            when (val r = repo.descargarComprobante(id, v.numero.orEmpty())) {
                is Resource.Success -> lanzar(r.data, compartir, v)
                is Resource.Error -> aviso(r.message)
                Resource.Loading -> Unit
            }
            binding.btnCompartir.isEnabled = true
            binding.btnVerPdf.isEnabled = true
        }
    }

    private fun lanzar(archivo: File, compartir: Boolean, v: Venta) {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", archivo)
        val intent = if (compartir) {
            Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "Boleta ${v.numero.orEmpty()} de Ferretería C&R. Total ${Formato.soles(v.total)}. ¡Gracias por su compra!")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.let { Intent.createChooser(it, "Compartir boleta") }
        } else {
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            aviso("No hay una app para abrir PDF en este celular. Usa \"Compartir\".")
        }
    }

    private fun pedirMotivo() {
        val v = venta ?: return
        val id = v.id ?: return
        val campo = EditText(this).apply {
            hint = "Ej.: cantidad mal registrada"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        val contenedor = LinearLayout(this).apply {
            setPadding(56, 16, 56, 0)
            addView(campo, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Anular ${v.numero.orEmpty()}")
            .setMessage("El stock vuelve al inventario y la venta queda marcada como anulada. Escribe el motivo.")
            .setView(contenedor)
            .setPositiveButton("Anular") { _, _ ->
                val motivo = campo.text.toString().trim()
                if (motivo.length < 5) {
                    aviso("Escribe un motivo un poco más claro.")
                    return@setPositiveButton
                }
                lifecycleScope.launch {
                    when (val r = repo.anularVenta(id, motivo)) {
                        is Resource.Success -> {
                            aviso("Venta anulada.")
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
