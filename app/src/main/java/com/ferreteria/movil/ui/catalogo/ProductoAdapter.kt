package com.ferreteria.movil.ui.catalogo

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ferreteria.movil.R
import com.ferreteria.movil.data.model.Producto
import com.ferreteria.movil.databinding.ItemProductoBinding
import java.util.Locale

/**
 * Adaptador de RecyclerView para la lista de productos del inventario.
 * Resalta visualmente aquellos productos cuyo stock_actual <= stock_minimo.
 */
class ProductoAdapter(
    private val onItemClick: (Producto) -> Unit
) : ListAdapter<Producto, ProductoAdapter.ProductoViewHolder>(ProductoDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductoViewHolder {
        val binding = ItemProductoBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ProductoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ProductoViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ProductoViewHolder(
        private val binding: ItemProductoBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(producto: Producto) {
            val context = binding.root.context

            binding.tvNombre.text = producto.nombre
            binding.tvCodigo.text = producto.codigo
            binding.tvPrecioVenta.text = String.format(Locale.US, "S/. %.2f", producto.precioVenta)

            if (!producto.descripcion.isNullOrBlank()) {
                binding.tvDescripcion.visibility = View.VISIBLE
                binding.tvDescripcion.text = producto.descripcion
            } else {
                binding.tvDescripcion.visibility = View.GONE
            }

            // REQUERIMIENTO: Resaltado visual para stock_actual <= stock_minimo
            if (producto.isStockCritico()) {
                binding.layoutStockBadge.setBackgroundResource(R.drawable.bg_badge_low_stock)
                binding.ivStockAlert.visibility = View.VISIBLE
                binding.tvStockInfo.setTextColor(ContextCompat.getColor(context, R.color.stock_low))
                binding.tvStockInfo.text = "CRÍTICO: ${producto.stockActual} (Mín: ${producto.stockMinimo})"
                binding.cardProducto.strokeColor = ContextCompat.getColor(context, R.color.stock_low)
            } else {
                binding.layoutStockBadge.setBackgroundResource(R.drawable.bg_badge_ok_stock)
                binding.ivStockAlert.visibility = View.GONE
                binding.tvStockInfo.setTextColor(ContextCompat.getColor(context, R.color.stock_ok))
                binding.tvStockInfo.text = "Stock: ${producto.stockActual} (Mín: ${producto.stockMinimo})"
                binding.cardProducto.strokeColor = ContextCompat.getColor(context, R.color.divider)
            }

            binding.root.setOnClickListener {
                onItemClick(producto)
            }
        }
    }

    class ProductoDiffCallback : DiffUtil.ItemCallback<Producto>() {
        override fun areItemsTheSame(oldItem: Producto, newItem: Producto): Boolean {
            return oldItem.id == newItem.id && oldItem.codigo == newItem.codigo
        }

        override fun areContentsTheSame(oldItem: Producto, newItem: Producto): Boolean {
            return oldItem == newItem
        }
    }
}
