package com.ferreteria.movil.ui.detalle

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.ferreteria.movil.R
import com.ferreteria.movil.data.local.TokenManager
import com.ferreteria.movil.data.model.Producto
import com.ferreteria.movil.data.remote.ApiClient
import com.ferreteria.movil.data.repository.ProductoRepositoryImpl
import com.ferreteria.movil.databinding.ActivityDetalleProductoBinding
import com.ferreteria.movil.ui.common.Resource

/**
 * Pantalla de formulario para creación, visualización detallada, edición y eliminación de productos.
 * Realiza peticiones directas contra los endpoints de Django:
 * - POST /api/productos/
 * - PUT /api/productos/{id}/
 * - DELETE /api/productos/{id}/
 */
class DetalleProductoActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PRODUCTO = "extra_producto"
    }

    private lateinit var binding: ActivityDetalleProductoBinding
    private lateinit var viewModel: DetalleProductoViewModel
    private var productoOriginal: Producto? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetalleProductoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val context = applicationContext
        val tokenManager = TokenManager.getInstance(context)
        val apiService = ApiClient.getApiService(context)
        val productoRepository = ProductoRepositoryImpl(apiService, tokenManager)
        val factory = DetalleProductoViewModelFactory(productoRepository)
        viewModel = ViewModelProvider(this, factory)[DetalleProductoViewModel::class.java]

        @Suppress("DEPRECATION")
        productoOriginal = intent.getSerializableExtra(EXTRA_PRODUCTO) as? Producto

        setupToolbar()
        setupForm()
        observeViewModel()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        if (productoOriginal != null) {
            binding.toolbar.title = getString(R.string.title_editar_producto)
        } else {
            binding.toolbar.title = getString(R.string.title_nuevo_producto)
        }
    }

    private fun setupForm() {
        val prod = productoOriginal
        if (prod != null) {
            // Modo Edición / Detalle: Rellenar campos existentes
            binding.etCodigo.setText(prod.codigo)
            binding.etNombre.setText(prod.nombre)
            binding.etDescripcion.setText(prod.descripcion.orEmpty())
            binding.etCategoria.setText(prod.categoria.toString())
            binding.etMarca.setText(prod.marca?.toString().orEmpty())
            binding.etPrecioCompra.setText(prod.precioCompra.toString())
            binding.etPrecioVenta.setText(prod.precioVenta.toString())
            binding.etStockActual.setText(prod.stockActual.toString())
            binding.etStockMinimo.setText(prod.stockMinimo.toString())
            binding.switchActivo.isChecked = prod.activo

            // Alerta visual de stock si aplica
            if (prod.isStockCritico()) {
                binding.layoutStockWarningDetail.visibility = View.VISIBLE
                binding.tvStockWarningDetail.text =
                    "¡Alerta de Inventario! Stock actual (${prod.stockActual}) es menor o igual al mínimo (${prod.stockMinimo})."
            } else {
                binding.layoutStockWarningDetail.visibility = View.GONE
            }

            // Habilitar botón de eliminación
            binding.btnEliminar.visibility = View.VISIBLE
            binding.btnEliminar.setOnClickListener {
                confirmarEliminacion(prod)
            }
        } else {
            // Modo Creación
            binding.layoutStockWarningDetail.visibility = View.GONE
            binding.btnEliminar.visibility = View.GONE
        }

        binding.btnGuardar.setOnClickListener {
            viewModel.guardarProducto(
                existingId = productoOriginal?.id,
                codigo = binding.etCodigo.text?.toString().orEmpty(),
                nombre = binding.etNombre.text?.toString().orEmpty(),
                descripcion = binding.etDescripcion.text?.toString(),
                categoriaStr = binding.etCategoria.text?.toString().orEmpty(),
                marcaStr = binding.etMarca.text?.toString(),
                precioCompraStr = binding.etPrecioCompra.text?.toString().orEmpty(),
                precioVentaStr = binding.etPrecioVenta.text?.toString().orEmpty(),
                stockActualStr = binding.etStockActual.text?.toString().orEmpty(),
                stockMinimoStr = binding.etStockMinimo.text?.toString().orEmpty(),
                activo = binding.switchActivo.isChecked
            )
        }
    }

    private fun observeViewModel() {
        viewModel.validationError.observe(this) { errorMsg ->
            if (!errorMsg.isNullOrBlank()) {
                AlertDialog.Builder(this)
                    .setTitle("Dato requerido")
                    .setMessage(errorMsg)
                    .setPositiveButton("Corregir", null)
                    .show()
                viewModel.clearValidationError()
            }
        }

        viewModel.operationState.observe(this) { resource ->
            when (resource) {
                is Resource.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.btnGuardar.isEnabled = false
                    binding.btnEliminar.isEnabled = false
                }
                is Resource.Success -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnGuardar.isEnabled = true
                    binding.btnEliminar.isEnabled = true
                    Toast.makeText(this, resource.data, Toast.LENGTH_LONG).show()
                    finish()
                }
                is Resource.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnGuardar.isEnabled = true
                    binding.btnEliminar.isEnabled = true
                    showErrorDialog(resource.message)
                    viewModel.clearOperationState()
                }
                null -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnGuardar.isEnabled = true
                    binding.btnEliminar.isEnabled = true
                }
            }
        }
    }

    private fun confirmarEliminacion(producto: Producto) {
        val prodId = producto.id
        if (prodId == null) {
            Toast.makeText(this, "El producto no tiene un ID válido.", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.confirm_delete_title)
            .setMessage(getString(R.string.confirm_delete_msg, producto.nombre))
            .setPositiveButton(R.string.btn_eliminar) { _, _ ->
                viewModel.eliminarProducto(prodId)
            }
            .setNegativeButton(R.string.btn_cancelar, null)
            .show()
    }

    private fun showErrorDialog(message: String) {
        AlertDialog.Builder(this)
            .setTitle("Error en la Operación")
            .setMessage(message)
            .setPositiveButton("Aceptar", null)
            .show()
    }
}
