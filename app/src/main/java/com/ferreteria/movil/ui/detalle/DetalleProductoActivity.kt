package com.ferreteria.movil.ui.detalle

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Pantalla de detalle, creación y edición de productos contra la API de Django.
 */
class DetalleProductoActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PRODUCTO = "extra_producto"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }
}
