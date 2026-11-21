package com.ferreteria.movil.ui.whatsapp

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ferreteria.movil.R
import com.ferreteria.movil.data.Repositorio
import com.ferreteria.movil.data.model.Conversacion
import com.ferreteria.movil.databinding.FragmentChatsBinding
import com.ferreteria.movil.databinding.ItemChatBinding
import com.ferreteria.movil.ui.common.Formato
import com.ferreteria.movil.ui.common.Resource
import com.ferreteria.movil.ui.common.VmFactory
import com.ferreteria.movil.ui.common.visible
import com.ferreteria.movil.ui.main.MainActivity
import com.ferreteria.movil.ui.main.Recargable
import com.google.android.material.chip.Chip
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ChatsViewModel(private val repo: Repositorio) : ViewModel() {

    private val _chats = MutableLiveData<List<Conversacion>>()
    val chats: LiveData<List<Conversacion>> = _chats

    private val _cargando = MutableLiveData(false)
    val cargando: LiveData<Boolean> = _cargando

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private var trabajo: Job? = null

    fun cargar() {
        trabajo?.cancel()
        trabajo = viewModelScope.launch {
            _cargando.value = true
            when (val r = repo.conversaciones()) {
                is Resource.Success -> {
                    // Primero los que tienen mensajes sin leer, luego el más reciente
                    _chats.value = r.data.sortedWith(
                        compareByDescending<Conversacion> { (it.noLeidos ?: 0) > 0 }.thenByDescending { it.ultimoMensaje }
                    )
                    _error.value = null
                }
                is Resource.Error -> _error.value = r.message
                Resource.Loading -> Unit
            }
            _cargando.value = false
        }
    }
}

/** Bandeja de chats de WhatsApp atendidos por el bot (y por los vendedores cuando lo pausan). */
class ChatsFragment : Fragment(), Recargable {

    private enum class Filtro(val etiqueta: String) { TODOS("Todos"), NO_LEIDOS("No leídos"), PAUSA("Bot en pausa") }

    private var _binding: FragmentChatsBinding? = null
    private val binding get() = _binding!!
    private val vm: ChatsViewModel by viewModels { VmFactory { ChatsViewModel(Repositorio(requireContext())) } }
    private var filtro = Filtro.TODOS
    private var todos: List<Conversacion> = emptyList()
    private val adaptador = ChatAdapter { abrir(it) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentChatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.lista.layoutManager = LinearLayoutManager(requireContext())
        binding.lista.adapter = adaptador

        Filtro.values().forEach { f ->
            val chip = layoutInflater.inflate(R.layout.chip_filtro, binding.grupoFiltro, false) as Chip
            chip.id = View.generateViewId()
            chip.text = f.etiqueta
            chip.tag = f.name
            chip.isChecked = f == filtro
            binding.grupoFiltro.addView(chip)
        }
        binding.grupoFiltro.setOnCheckedStateChangeListener { g, ids ->
            val nombre = ids.firstOrNull()?.let { g.findViewById<Chip>(it)?.tag as? String } ?: return@setOnCheckedStateChangeListener
            filtro = Filtro.valueOf(nombre)
            filtrar()
        }
        binding.etBuscar.doAfterTextChanged { filtrar() }
        binding.refrescar.setColorSchemeResources(R.color.navy)
        binding.refrescar.setOnRefreshListener { vm.cargar() }

        vm.cargando.observe(viewLifecycleOwner) { binding.refrescar.isRefreshing = it }
        vm.chats.observe(viewLifecycleOwner) {
            todos = it
            filtrar()
        }
        vm.error.observe(viewLifecycleOwner) { msg ->
            if (msg != null && todos.isEmpty()) {
                binding.txtVacio.visible(true)
                binding.txtVacio.text = msg
            }
        }
    }

    private fun filtrar() {
        val q = binding.etBuscar.text.toString().trim().lowercase()
        val lista = todos.filter { c ->
            val pasaFiltro = when (filtro) {
                Filtro.TODOS -> true
                Filtro.NO_LEIDOS -> (c.noLeidos ?: 0) > 0
                Filtro.PAUSA -> c.botActivo == false
            }
            pasaFiltro && (q.isBlank() || c.nombreVisible.lowercase().contains(q) || c.telefono.orEmpty().contains(q))
        }
        adaptador.actualizar(lista)
        binding.txtVacio.visible(lista.isEmpty())
        binding.txtVacio.text = when {
            q.isNotBlank() -> "Ningún chat coincide con \"$q\"."
            filtro == Filtro.NO_LEIDOS -> "No hay mensajes sin leer."
            filtro == Filtro.PAUSA -> "El bot está activo en todos los chats."
            else -> "Aún no hay conversaciones de WhatsApp."
        }
    }

    private fun abrir(c: Conversacion) {
        val id = c.id ?: return
        startActivity(Intent(requireContext(), ChatActivity::class.java).putExtra(ChatActivity.EXTRA_ID, id))
    }

    override fun onResume() {
        super.onResume()
        if (!isHidden) recargar()
    }

    override fun recargar() {
        if (_binding != null) {
            vm.cargar()
            (activity as? MainActivity)?.refrescarInsignias()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class ChatAdapter(private val alTocar: (Conversacion) -> Unit) : RecyclerView.Adapter<ChatAdapter.VH>() {

    private var items: List<Conversacion> = emptyList()

    fun actualizar(nuevos: List<Conversacion>) {
        items = nuevos
        notifyDataSetChanged()
    }

    class VH(val b: ItemChatBinding) : RecyclerView.ViewHolder(b.root)

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemChatBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val c = items[position]
        val b = holder.b
        val ctx = b.root.context
        val noLeidos = c.noLeidos ?: 0

        b.txtAvatar.text = c.iniciales
        b.txtNombre.text = c.nombreVisible
        b.txtUltimo.text = c.ultimoTexto.orEmpty().replace("\n", " ")
        b.txtHora.text = Formato.horaOFecha(c.ultimoMensaje)
        b.txtNoLeidos.visible(noLeidos > 0)
        b.txtNoLeidos.text = noLeidos.toString()
        b.txtHora.setTextColor(ctx.getColor(if (noLeidos > 0) R.color.ambar_texto else R.color.texto_2))
        b.txtUltimo.setTextColor(ctx.getColor(if (noLeidos > 0) R.color.texto else R.color.texto_2))
        b.fila.setBackgroundColor(ctx.getColor(if (noLeidos > 0) R.color.ambar_fondo else R.color.superficie))

        val pausado = c.botActivo == false
        b.txtEtiqueta.visible(pausado)
        if (pausado) {
            Formato.pintarEtiqueta(b.txtEtiqueta, R.color.ambar_texto, R.color.ambar_suave, "Bot en pausa · atiende un vendedor")
        }
        b.root.setOnClickListener { alTocar(c) }
    }
}
