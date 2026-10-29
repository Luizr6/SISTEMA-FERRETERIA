import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { Bot, ChevronLeft, FlaskConical, Loader2, MessageCircle, Send, UserRound } from 'lucide-react'
import { whatsappService } from '../services/comercial'
import { errorMessage, hace, hora } from '../utils/format'
import { useToast } from '../components/ui/Toast'
import { useAvisos } from '../context/AvisosContext'
import PageHeader from '../components/ui/PageHeader'
import Spinner, { Vacio } from '../components/ui/Spinner'
import Modal from '../components/ui/Modal'
import { Buscador, Campo } from '../components/ui/Filtros'
import useCarga from '../hooks/useCarga'

/** Convierte *negrita* del formato de WhatsApp en <b> sin usar HTML crudo */
function TextoWa({ texto }) {
  return texto.split('\n').map((linea, i) => (
    <span key={i} className="block">
      {linea.split(/(\*[^*]+\*)/g).map((t, j) =>
        t.startsWith('*') && t.endsWith('*') && t.length > 2 ? <b key={j}>{t.slice(1, -1)}</b> : t)}
    </span>
  ))
}

function Chat({ conv, onVolver, onCambio }) {
  const toast = useToast()
  const [mensajes, setMensajes] = useState(null)
  const [texto, setTexto] = useState('')
  const [enviando, setEnviando] = useState(false)
  const fin = useRef(null)

  useEffect(() => {
    let vivo = true
    const cargar = () => whatsappService.mensajes(conv.id).then((m) => vivo && setMensajes(m)).catch(() => {})
    setMensajes(null)
    cargar()
    const t = setInterval(cargar, 8000)
    return () => { vivo = false; clearInterval(t) }
  }, [conv.id])

  useEffect(() => { fin.current?.scrollIntoView({ block: 'end' }) }, [mensajes?.length])

  const enviar = async (e) => {
    e.preventDefault()
    if (!texto.trim()) return
    setEnviando(true)
    try {
      const m = await whatsappService.responder(conv.id, texto.trim())
      setMensajes((l) => [...(l || []), m])
      setTexto('')
    } catch (err) {
      toast(errorMessage(err), 'error')
    } finally {
      setEnviando(false)
    }
  }

  const cambiarBot = async () => {
    try {
      onCambio(await whatsappService.cambiarBot(conv.id, !conv.bot_activo))
    } catch (e) {
      toast(errorMessage(e), 'error')
    }
  }

  return (
    <div className="panel flex h-[min(72vh,720px)] flex-col overflow-hidden">
      <div className="flex items-center gap-3 border-b border-gris-100 px-4 py-3">
        <button className="icon-btn lg:hidden" onClick={onVolver} aria-label="Volver"><ChevronLeft className="h-5 w-5" /></button>
        <span className="flex h-10 w-10 items-center justify-center rounded-full bg-exito-50 font-display font-semibold uppercase text-exito-700">
          {(conv.nombre || conv.telefono)[0]}
        </span>
        <div className="min-w-0 flex-1">
          <p className="truncate font-semibold">{conv.nombre || conv.telefono}</p>
          <p className="cifra text-xs text-gris-500">+{conv.telefono}{conv.cliente_nombre ? `, cliente ${conv.cliente_nombre}` : ''}</p>
        </div>
        <button onClick={cambiarBot} role="switch" aria-checked={conv.bot_activo}
          title={conv.bot_activo ? 'El bot responde solo. Desactívalo para atender tú.' : 'Solo responden personas.'}
          className={`flex items-center gap-2 rounded-full border px-3 py-1.5 text-xs font-semibold transition ${
            conv.bot_activo ? 'border-exito-100 bg-exito-50 text-exito-700' : 'border-gris-200 bg-superficie text-gris-600'}`}>
          <Bot className="h-3.5 w-3.5" /> Bot {conv.bot_activo ? 'activo' : 'en pausa'}
        </button>
      </div>

      <div className="flex-1 overflow-y-auto bg-gris-50 px-4 py-4" style={{ backgroundImage: 'radial-gradient(rgb(var(--gris-200)) 1px, transparent 1.2px)', backgroundSize: '18px 18px' }}>
        {mensajes === null ? <Spinner /> : (
          <ul className="space-y-2.5">
            {mensajes.map((m) => {
              const sale = m.direccion === 'SALIENTE'
              return (
                <li key={m.id} className={`flex ${sale ? 'justify-end' : 'justify-start'}`}>
                  <div className={`max-w-[82%] rounded-[16px] px-3.5 py-2 text-[14px] leading-snug shadow-sm ${
                    sale ? 'rounded-br-[4px] bg-acento-600 text-white' : 'rounded-bl-[4px] border border-gris-200 bg-superficie text-gris-900'}`}>
                    {sale && (
                      <p className={`mb-0.5 flex items-center gap-1 text-[11px] font-semibold text-white/80`}>
                        {m.intencion === 'MANUAL' ? <><UserRound className="h-3 w-3" /> {m.enviado_por_nombre || 'Vendedor'}</> : <><Bot className="h-3 w-3" /> Bot</>}
                      </p>
                    )}
                    <TextoWa texto={m.texto} />
                    <p className={`mt-1 flex flex-wrap items-center justify-end gap-x-2 text-[11px] ${sale ? 'text-white/70' : 'text-gris-500'}`}>
                      {m.pedido_numero && (
                        <Link to="/pedidos" className={`font-semibold underline ${sale ? 'text-white' : 'text-acento-600'}`}>{m.pedido_numero}</Link>
                      )}
                      {sale && m.estado_envio === 'SIMULADO' && <span>simulado</span>}
                      {sale && m.estado_envio === 'ERROR' && <span className="font-medium" title={m.error}>no se envió</span>}
                      <span>{hora(m.fecha)}</span>
                    </p>
                  </div>
                </li>
              )
            })}
            <li ref={fin} />
          </ul>
        )}
      </div>

      <form onSubmit={enviar} className="flex gap-2 border-t border-gris-100 bg-superficie p-3">
        <input className="input flex-1" placeholder="Escribe una respuesta…" aria-label="Respuesta"
          value={texto} onChange={(e) => setTexto(e.target.value)} />
        <button className="btn-primary px-3.5" disabled={enviando || !texto.trim()} aria-label="Enviar">
          {enviando ? <Loader2 className="h-4 w-4 animate-spin" /> : <Send className="h-4 w-4" />}
        </button>
      </form>
    </div>
  )
}

function Simulador({ onEnviado, onCerrar }) {
  const toast = useToast()
  const [f, setF] = useState({ telefono: '51999888777', nombre: 'Cliente de prueba', texto: '' })
  const [ocupado, setOcupado] = useState(false)
  const ejemplos = ['Hola', 'precio martillo', 'stock taladro', 'quiero 2 MAR-001, 1 PIN-001']

  const enviar = async (e) => {
    e?.preventDefault()
    setOcupado(true)
    try {
      await whatsappService.simular(f)
      setF({ ...f, texto: '' })
      onEnviado(f.telefono)
    } catch (err) {
      toast(errorMessage(err), 'error')
    } finally {
      setOcupado(false)
    }
  }

  return (
    <form onSubmit={enviar} className="space-y-3">
      <p className="text-sm text-gris-600">
        Escribe como si fueras un cliente. El mensaje pasa por el mismo bot que usa el webhook de Meta,
        así que puedes probar consultas y pedidos sin una cuenta de WhatsApp Business.
      </p>
      <div className="grid gap-3 sm:grid-cols-2">
        <Campo label="Teléfono (con 51)"><input className="input" required value={f.telefono} onChange={(e) => setF({ ...f, telefono: e.target.value })} /></Campo>
        <Campo label="Nombre"><input className="input" value={f.nombre} onChange={(e) => setF({ ...f, nombre: e.target.value })} /></Campo>
      </div>
      <Campo label="Mensaje del cliente">
        <input className="input" required autoFocus value={f.texto} onChange={(e) => setF({ ...f, texto: e.target.value })} />
      </Campo>
      <div className="flex flex-wrap gap-1.5">
        {ejemplos.map((t) => (
          <button type="button" key={t} onClick={() => setF({ ...f, texto: t })}
            className="rounded-full border border-gris-200 bg-superficie px-2.5 py-1 text-xs text-gris-600 hover:border-gris-300 hover:text-gris-900">{t}</button>
        ))}
      </div>
      <div className="flex justify-end gap-2 pt-1">
        <button type="button" className="btn-secondary" onClick={onCerrar}>Cerrar</button>
        <button className="btn-primary" disabled={ocupado}>{ocupado && <Loader2 className="h-4 w-4 animate-spin" />} Enviar mensaje</button>
      </div>
    </form>
  )
}

export default function WhatsApp() {
  const avisos = useAvisos()
  const [search, setSearch] = useState('')
  const [sel, setSel] = useState(null)
  const [simular, setSimular] = useState(false)
  const { data: estado } = useCarga(() => whatsappService.estado(), [])
  const { data: convs, setData: setConvs, recargar } = useCarga(
    () => whatsappService.conversaciones({ search: search || undefined }), [search], { retraso: 200 })

  useEffect(() => {
    const t = setInterval(recargar, 15000)
    return () => clearInterval(t)
  }, [recargar])

  const abrir = (c) => {
    setSel(c)
    setConvs((l) => l.map((x) => (x.id === c.id ? { ...x, no_leidos: 0 } : x)))
    setTimeout(avisos.refrescar, 600)
  }

  const trasSimular = async (telefono) => {
    const lista = await whatsappService.conversaciones()
    setConvs(lista)
    const c = lista.find((x) => x.telefono === telefono.replace(/\D/g, ''))
    if (c) setSel({ ...c })
    avisos.refrescar()
  }

  return (
    <>
      <PageHeader title="WhatsApp" subtitle="Conversaciones del bot con tus clientes. Puedes tomar el chat y responder tú.">
        <button className="btn-secondary" onClick={() => setSimular(true)}><FlaskConical className="h-4 w-4" /> Simular cliente</button>
      </PageHeader>

      {estado?.modo === 'SIMULADO' && (
        <p className="mb-4 rounded-[12px] border border-aviso-200 bg-aviso-50 px-4 py-2.5 text-sm text-aviso-700">
          Modo de prueba: el bot responde y guarda todo, pero no envía mensajes reales hasta que configures
          las credenciales de WhatsApp Cloud API en el <code>.env</code> del backend.
        </p>
      )}

      <div className="grid gap-5 lg:grid-cols-[340px_1fr]">
        <section className={sel ? 'hidden lg:block' : ''}>
          <Buscador value={search} onChange={setSearch} placeholder="Nombre o número" />
          <div className="mt-3">
            {convs === null ? <Spinner /> : convs.length === 0 ? (
              <div className="panel">
                <Vacio icon={MessageCircle} titulo="Sin conversaciones" texto="Cuando un cliente escriba al número de la ferretería, aparecerá aquí.">
                  <button className="btn-primary" onClick={() => setSimular(true)}>Probar con un mensaje</button>
                </Vacio>
              </div>
            ) : (
              <ul className="space-y-1.5">
                {convs.map((c) => (
                  <li key={c.id}>
                    <button onClick={() => abrir(c)} aria-current={sel?.id === c.id}
                      className={`flex w-full items-center gap-3 rounded-[12px] border bg-superficie p-3 text-left transition ${
                        sel?.id === c.id ? 'border-acento-600 ring-1 ring-acento-600' : 'border-gris-200/80 hover:border-gris-300'}`}>
                      <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-exito-50 font-display font-semibold uppercase text-exito-700">
                        {(c.nombre || c.telefono)[0]}
                      </span>
                      <span className="min-w-0 flex-1">
                        <span className="flex items-baseline justify-between gap-2">
                          <span className="truncate font-semibold">{c.nombre || `+${c.telefono}`}</span>
                          <span className="shrink-0 text-xs text-gris-500">{hace(c.ultimo_mensaje)}</span>
                        </span>
                        <span className="flex items-center justify-between gap-2">
                          <span className="truncate text-sm text-gris-600">{c.ultimo_texto.replace(/\*/g, '')}</span>
                          {c.no_leidos > 0 && (
                            <span className="cifra shrink-0 rounded-full bg-acento-600 px-1.5 text-[11px] font-semibold leading-[18px] text-white">{c.no_leidos}</span>
                          )}
                        </span>
                      </span>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </section>

        <section className={sel ? '' : 'hidden lg:block'}>
          {sel ? (
            <Chat key={sel.id} conv={sel} onVolver={() => setSel(null)}
              onCambio={(c) => { setSel(c); setConvs((l) => l.map((x) => (x.id === c.id ? c : x))) }} />
          ) : (
            <div className="panel flex h-[min(72vh,720px)] items-center justify-center">
              <Vacio icon={MessageCircle} titulo="Elige una conversación" texto="Verás lo que preguntó el cliente y lo que respondió el bot." />
            </div>
          )}
        </section>
      </div>

      <Modal open={simular} onClose={() => setSimular(false)} title="Simular un mensaje de cliente">
        {simular && <Simulador onCerrar={() => setSimular(false)} onEnviado={trasSimular} />}
      </Modal>
    </>
  )
}
