package com.ferreteria.movil.ui.catalogo

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.ferreteria.movil.R
import com.ferreteria.movil.data.local.TokenManager
import com.ferreteria.movil.data.model.Producto
import com.ferreteria.movil.data.remote.ApiClient
import com.ferreteria.movil.data.repository.AuthRepositoryImpl
import com.ferreteria.movil.data.repository.ProductoRepositoryImpl
import com.ferreteria.movil.databinding.ActivityCatalogoBinding
import com.ferreteria.movil.ui.common.Resource
import com.ferreteria.movil.ui.detalle.DetalleProductoActivity
import com.ferreteria.movil.ui.login.LoginActivity
import com.google.android.material.snackbar.Snackbar

/**
 * Pantalla principal del catálogo e inventario móvil de la ferretería.
 * Consume en tiempo real el endpoint GET /api/productos/, resalta productos con stock
 * crítico y permite filtrado local por nombre o código.
 */
class CatalogoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCatalogoBinding
    private lateinit var viewModel: CatalogoViewModel
    private lateinit var adapter: ProductoAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCatalogoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val context = applicationContext
        val tokenManager = TokenManager.getInstance(context)
        val apiService = ApiClient.getApiService(context)
        val productoRepository = ProductoRepositoryImpl(apiService, tokenManager)
        val authRepository = AuthRepositoryImpl(apiService, tokenManager)

        val factory = CatalogoViewModelFactory(productoRepository, authRepository)
        viewModel = ViewModelProvider(this, factory)[CatalogoViewModel::class.java]

        setupUI()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        // Recargar inventario al volver de la pantalla de creación/edición/eliminación
        viewModel.loadProductos()
    }

    private fun setupUI() {
        // Información de usuario en cabecera
        binding.tvUserInfo.text = getString(
            R.string.login_success,
            viewModel.getCurrentUsername(),
            viewModel.getCurrentRol()
        )

        // Configuración del RecyclerView
        adapter = ProductoAdapter { producto ->
            openDetalleProducto(producto)
        }
        binding.rvProductos.layoutManager = LinearLayoutManager(this)
        binding.rvProductos.adapter = adapter

        // Swipe to Refresh
        binding.swipeRefreshLayout.setOnRefreshListener {
            viewModel.loadProductos()
        }
        binding.swipeRefreshLayout.setColorSchemeResources(R.color.primary, R.color.secondary)

        // Botón de recarga manual
        binding.btnRefresh.setOnClickListener {
            viewModel.loadProductos()
        }

        // Botón de reintento en vista vacía/error
        binding.btnRetry.setOnClickListener {
            viewModel.loadProductos()
        }

        // Botón de cerrar sesión
        binding.btnLogout.setOnClickListener {
            confirmLogout()
        }

        // Filtro de búsqueda en tiempo real
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.filter(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Botón flotante para crear nuevo producto
        binding.fabAddProducto.setOnClickListener {
            openDetalleProducto(null)
        }
    }

    private fun observeViewModel() {
        // Estado general de la petición de productos
        viewModel.productosState.observe(this) { resource ->
            when (resource) {
                is Resource.Loading -> {
                    if (!binding.swipeRefreshLayout.isRefreshing) {
                        binding.progressBar.visibility = View.VISIBLE
                    }
                    binding.layoutEmptyState.visibility = View.GONE
                }
                is Resource.Success -> {
                    binding.progressBar.visibility = View.GONE
                    binding.swipeRefreshLayout.isRefreshing = false
                }
                is Resource.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.swipeRefreshLayout.isRefreshing = false
                    showErrorMessage(resource.message, resource.statusCode)
                    if (adapter.itemCount == 0) {
                        binding.layoutEmptyState.visibility = View.VISIBLE
                        binding.tvEmptyMessage.text = resource.message
                    }
                }
            }
        }

        // Lista filtrada reactiva
        viewModel.filteredList.observe(this) { list ->
            adapter.submitList(list)
            if (list.isEmpty()) {
                binding.layoutEmptyState.visibility = View.VISIBLE
                val query = binding.etSearch.text?.toString().orEmpty()
                if (query.isNotEmpty()) {
                    binding.tvEmptyMessage.text = getString(R.string.empty_search_results)
                } else {
                    binding.tvEmptyMessage.text = getString(R.string.no_products_found)
                }
            } else {
                binding.layoutEmptyState.visibility = View.GONE
            }
        }

        // Contador de productos con stock crítico (<= stock_minimo)
        viewModel.stockCriticoCount.observe(this) { count ->
            if (count > 0) {
                binding.layoutCriticoAlert.visibility = View.VISIBLE
                binding.tvCriticoAlert.text = resources.getQuantityString(
                    R.plurals.stock_critico_count,
                    count,
                    count
                )
            } else {
                binding.layoutCriticoAlert.visibility = View.GONE
            }
        }
    }

    private fun openDetalleProducto(producto: Producto?) {
        val intent = Intent(this, DetalleProductoActivity::class.java).apply {
            putExtra(DetalleProductoActivity.EXTRA_PRODUCTO, producto)
        }
        startActivity(intent)
    }

    private fun confirmLogout() {
        AlertDialog.Builder(this)
            .setTitle(R.string.logout)
            .setMessage(R.string.logout_confirmation)
            .setPositiveButton("Sí, Salir") { _, _ ->
                viewModel.logout()
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .setNegativeButton(R.string.btn_cancelar, null)
            .show()
    }

    private fun showErrorMessage(message: String, statusCode: Int? = null) {
        val snackbar = Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
        if (statusCode == 401) {
            snackbar.setAction("Iniciar Sesión") {
                viewModel.logout()
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
        } else {
            snackbar.setAction(R.string.retry) { viewModel.loadProductos() }
        }
        snackbar.show()
    }
}
