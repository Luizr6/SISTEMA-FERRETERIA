package com.ferreteria.movil.ui.login

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.ferreteria.movil.R
import com.ferreteria.movil.data.local.TokenManager
import com.ferreteria.movil.data.remote.ApiClient
import com.ferreteria.movil.data.repository.AuthRepositoryImpl
import com.ferreteria.movil.databinding.ActivityLoginBinding
import com.ferreteria.movil.ui.catalogo.CatalogoActivity
import com.ferreteria.movil.ui.common.Resource

/**
 * Pantalla de inicio de sesión de la aplicación móvil de ferretería.
 * Admite autenticación desacoplada con modo Mock (para pruebas mientras el backend
 * culmina la configuración de JWT) o mediante el endpoint real de la API Django.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var viewModel: LoginViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val tokenManager = TokenManager.getInstance(this)
        val apiService = ApiClient.getApiService(this)
        val authRepository = AuthRepositoryImpl(apiService, tokenManager)
        val factory = LoginViewModelFactory(authRepository)
        viewModel = ViewModelProvider(this, factory)[LoginViewModel::class.java]

        // Si ya existe una sesión activa, navegamos directamente al catálogo
        if (viewModel.isUserLoggedIn()) {
            goToCatalogo()
            return
        }

        setupViews()
        observeViewModel()
    }

    private fun setupViews() {
        binding.switchMockMode.isChecked = viewModel.isMockMode.value ?: true
        updateMockModeExplanation(binding.switchMockMode.isChecked)

        binding.switchMockMode.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setMockMode(isChecked)
            updateMockModeExplanation(isChecked)
        }

        binding.btnLogin.setOnClickListener {
            val username = binding.etUsername.text?.toString().orEmpty()
            val password = binding.etPassword.text?.toString().orEmpty()
            viewModel.login(username, password)
        }
    }

    private fun updateMockModeExplanation(isMock: Boolean) {
        if (isMock) {
            binding.tvMockInfo.text = getString(R.string.mock_login_info)
            binding.tvMockInfo.setTextColor(getColor(R.color.secondary_dark))
        } else {
            binding.tvMockInfo.text = "Modo API Real: Consumirá POST /api/login/ en el servidor Django."
            binding.tvMockInfo.setTextColor(getColor(R.color.primary))
        }
    }

    private fun observeViewModel() {
        viewModel.loginState.observe(this) { resource ->
            when (resource) {
                is Resource.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.btnLogin.isEnabled = false
                }
                is Resource.Success -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnLogin.isEnabled = true
                    val user = resource.data.username ?: "Usuario"
                    val rol = resource.data.rol ?: "VENDEDOR"
                    Toast.makeText(
                        this,
                        getString(R.string.login_success, user, rol),
                        Toast.LENGTH_SHORT
                    ).show()
                    goToCatalogo()
                }
                is Resource.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnLogin.isEnabled = true
                    showErrorDialog(resource.message)
                    viewModel.clearState()
                }
                null -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnLogin.isEnabled = true
                }
            }
        }
    }

    private fun showErrorDialog(message: String) {
        AlertDialog.Builder(this)
            .setTitle("Atención")
            .setMessage(message)
            .setPositiveButton("Aceptar", null)
            .show()
    }

    private fun goToCatalogo() {
        val intent = Intent(this, CatalogoActivity::class.java)
        startActivity(intent)
        finish()
    }
}
