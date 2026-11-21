package com.ferreteria.movil.ui.common

import android.view.Gravity
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.ferreteria.movil.R
import com.ferreteria.movil.databinding.ItemBurbujaBinding

/** Burbujas de chat (WhatsApp y asistente IA) con el mismo estilo de las maquetas. */
object Burbujas {

    enum class Tipo { CLIENTE, BOT, VENDEDOR }

    fun pintar(b: ItemBurbujaBinding, tipo: Tipo, texto: String, autor: String?, hora: String) {
        val ctx = b.root.context
        val (fondo, colorAutor, derecha) = when (tipo) {
            Tipo.CLIENTE -> Triple(R.drawable.bg_burbuja_cliente, R.color.texto_2, false)
            Tipo.BOT -> Triple(R.drawable.bg_burbuja_bot, R.color.burbuja_bot_texto, true)
            Tipo.VENDEDOR -> Triple(R.drawable.bg_burbuja_vendedor, R.color.ambar_texto, true)
        }
        b.burbuja.setBackgroundResource(fondo)
        (b.burbuja.layoutParams as LinearLayout.LayoutParams).gravity = if (derecha) Gravity.END else Gravity.START
        b.txtTexto.text = texto
        b.txtHora.text = hora
        b.txtAutor.visible(!autor.isNullOrBlank())
        b.txtAutor.text = autor
        b.txtAutor.setTextColor(ContextCompat.getColor(ctx, colorAutor))
    }
}
