import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  CheckCircle2, ClipboardList, FileText, Loader2, Minus, PackageSearch, Plus, Printer, Trash2, UserPlus, Wallet,
} from 'lucide-react'
import { categoriasService, productosService } from '../services/catalogo'
import { cajaService, carritoService, clientesService, ventasService } from '../services/comercial'
import { errorMessage, METODOS_PAGO, money } from '../utils/format'
import { useToast } from '../components/ui/Toast'
import { useAvisos } from '../context/AvisosContext'
import PageHeader from '../components/ui/PageHeader'
import Spinner, { Aviso, Vacio } from '../components/ui/Spinner'
import Modal from '../components/ui/Modal'
import { Buscador, Campo } from '../components/ui/Filtros'
import ClienteForm from '../components/ventas/ClienteForm'
import useCarga from '../hooks/useCarga'

/**
 * Punto de venta (CU08). El carrito vive en el servidor (FER-45): el backend calcula IGV y totales
 * y avisa si algún producto ya no tiene stock. Se puede cobrar o guardar como pedido por aprobar.
 */
export default function Vender() {
  const toast = useToast()
  const avisos = useAvisos()
  const [busqueda, setBusqueda] = useState('')
  const [categoria, setCategoria] = useState('')
  const [carrito, setCarrito] = useState(null)
  const [ocupado, setOcupado] = useState(false)
  const [venta, setVenta] = useState({ cliente: '', metodo_pago: 'EFECTIVO', observacion: '' })
  const [nuevoCliente, setNuevoCliente] = useState(false)
  const [listo, setListo] = useState(null) // {tipo:'venta'|'pedido', dato}
  // Caja del vendedor: sin caja abierta no se puede cobrar (sí guardar como pedido)
  const [caja, setCaja] = useState(undefined)
  useEffect(() => { cajaService.actual().then(setCaja).catch(() => setCaja(null)) }, [])

  const { data: productos, recargar: recargarProductos } = useCarga(
    () => productosService.listar({ activo: true, search: busqueda || undefined, categoria: categoria || undefined }),
    [busqueda, categoria], { retraso: 200, inicial: null })
  const { data: categorias } = useCarga(() => categoriasService.listar(), [], { inicial: [] })
  const categoriasConProductos = (categorias || []).filter((c) => c.total_productos > 0)
  const { data: clientes, setData: setClientes } = useCarga(() => clientesService.listar({ activo: true }), [], { inicial: [] })

  useEffect(() => {
    carritoService.ver().then(setCarrito).catch((e) => toast(errorMessage(e), 'error'))
  }, [toast])

  const accion = useCallback(async (fn) => {
    setOcupado(true)
    try {
      setCarrito(await fn())
    } catch (e) {
      toast(errorMessage(e), 'error')
    } finally {
      setOcupado(false)
    }
  }, [toast])

  const enCarrito = useMemo(() => Object.fromEntries((carrito?.items || []).map((i) => [i.producto, i])), [carrito])

  const cobrar = async () => {
    setOcupado(true)
    try {
      const v = await carritoService.confirmar({
        cliente: venta.cliente ? Number(venta.cliente) : null,
        metodo_pago: venta.metodo_pago, observacion: venta.observacion,
      })
      setListo({ tipo: 'venta', dato: v })
      setCarrito(await carritoService.ver())
      recargarProductos() // el stock cambió
      setVenta({ cliente: '', metodo_pago: 'EFECTIVO', observacion: '' })
    } catch (e) {
      toast(errorMessage(e), 'error')
    } finally {
      setOcupado(false)
    }
  }

  const guardarPedido = async () => {
    const cli = clientes.find((c) => String(c.id) === String(venta.cliente))
    if (!cli) return toast('Para guardar un pedido elige o registra al cliente.', 'error')
    setOcupado(true)
    try {
      const p = await carritoService.enviarPedido({ cliente: cli.id, observacion: venta.observacion })
      setListo({ tipo: 'pedido', dato: p })
      setCarrito(await carritoService.ver())
      avisos.refrescar()
    } catch (e) {
      toast(errorMessage(e), 'error')
    } finally {
      setOcupado(false)
    }
  }

  return (
    <>
      <PageHeader title="Vender" subtitle="Busca, agrega y cobra. El stock se descuenta al confirmar la venta.">
        {caja && (
          <Link to="/caja" className="flex items-center gap-2 rounded-full border border-gris-200 bg-superficie px-3 py-1.5 text-[13px] font-semibold text-gris-700 hover:border-gris-300">
            <span className="h-2 w-2 rounded-full bg-exito-500" /> Caja abierta · {new Date(caja.fecha_apertura).toLocaleTimeString('es-PE', { hour: '2-digit', minute: '2-digit' })}
          </Link>
        )}
      </PageHeader>

      {caja === null && (
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3 rounded-panel border border-aviso-500/30 bg-aviso-50 px-4 py-3">
          <p className="flex items-center gap-2 text-sm font-medium text-aviso-700">
            <Wallet className="h-4 w-4 shrink-0" /> Tu caja está cerrada. Ábrela para poder cobrar (puedes guardar pedidos igual).
          </p>
          <Link to="/caja" className="btn-primary">Abrir caja</Link>
        </div>
      )}

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_400px]">
        {/* Catálogo */}
        <section className="min-w-0">
          <Buscador value={busqueda} onChange={setBusqueda} placeholder="Busca por nombre, código o marca" />
          {categoriasConProductos.length > 0 && (
            <div className="-mx-1 mt-3 flex gap-1.5 overflow-x-auto px-1 pb-1 lg:flex-wrap lg:overflow-visible" role="tablist" aria-label="Categorías">
              {[{ id: '', nombre: 'Todas' }, ...categoriasConProductos].map((c) => {
                const activa = String(categoria) === String(c.id)
                return (
                  <button key={c.id || 'todas'} role="tab" aria-selected={activa} onClick={() => setCategoria(c.id)}
                    className={`shrink-0 whitespace-nowrap rounded-full border px-3 py-1.5 text-[13px] font-semibold transition ${
                      activa ? 'border-acento-600 bg-acento-600 text-white'
                        : 'border-gris-200 bg-superficie text-gris-600 hover:border-gris-300 hover:text-gris-900'}`}>
                    {c.nombre}
                  </button>
                )
              })}
            </div>
          )}
          <div className="mt-3">
            {productos === null ? <Spinner /> : productos.length === 0 ? (
              <div className="panel">
                {categoria && !busqueda
                  ? <Vacio icon={PackageSearch} titulo="Sin productos en esta categoría" texto="Elige otra categoría o busca por nombre." />
                  : <Vacio icon={PackageSearch} titulo="Nada con ese nombre" texto={categoria ? 'No está en esta categoría. Prueba en "Todas".' : 'Prueba con el código o con otra palabra.'} />}
              </div>
            ) : (
              <ul className="grid gap-2 sm:grid-cols-2">
                {productos.map((p) => {
                  const item = enCarrito[p.id]
                  const sinStock = p.stock_actual === 0
                  return (
                    <li key={p.id}>
                      <button disabled={sinStock || ocupado}
                        onClick={() => accion(() => carritoService.agregar(p.id, 1))}
                        className={`group flex w-full items-center gap-3 rounded-[12px] border bg-superficie p-3 text-left transition disabled:cursor-not-allowed ${
                          item ? 'border-acento-600 ring-1 ring-acento-600' : 'border-gris-200/80 hover:border-gris-300'} ${sinStock ? 'opacity-50' : ''}`}>
                        <div className="min-w-0 flex-1">
                          <span className="etiqueta">{p.codigo}</span>
                          <p className="mt-1 truncate text-sm font-semibold">{p.nombre}</p>
                          <p className="text-xs text-gris-500">
                            {sinStock ? 'Agotado' : `${p.stock_actual} ${p.unidad_medida.toLowerCase()} disponibles`}
                          </p>
                        </div>
                        <div className="text-right">
                          <p className="cifra font-semibold text-gris-900">{money(p.precio_venta)}</p>
                          {item
                            ? <span className="cifra mt-1 inline-block rounded-full bg-acento-600 px-2 text-xs font-semibold leading-5 text-white">× {item.cantidad}</span>
                            : !sinStock && <Plus className="ml-auto mt-1 h-4 w-4 text-gris-500 group-hover:text-acento-600" />}
                        </div>
                      </button>
                    </li>
                  )
                })}
              </ul>
            )}
          </div>
        </section>

        {/* Ticket */}
        <aside id="ticket" className="scroll-mt-20 lg:sticky lg:top-6 lg:self-start">
          <div className="panel overflow-hidden">
            <div className="flex items-center justify-between border-b border-gris-200 px-5 py-3.5">
              <h2 className="text-[15px] font-semibold">Ticket de venta</h2>
              <span className="flex items-center gap-2 text-[13px] text-gris-500">
                {ocupado && <Loader2 className="h-4 w-4 animate-spin" />}
                {carrito?.total_items || 0} {carrito?.total_items === 1 ? "unidad" : "unidades"}
              </span>
            </div>

            {carrito === null ? <Spinner /> : carrito.items.length === 0 ? (
              <p className="px-5 py-10 text-center text-sm text-gris-600">Toca un producto para agregarlo.</p>
            ) : (
              <ul className="max-h-[38vh] divide-y divide-dashed divide-gris-200 overflow-y-auto px-5">
                {carrito.items.map((i) => (
                  <li key={i.id} className="py-3">
                    <div className="flex items-start justify-between gap-2">
                      <p className="text-sm font-semibold leading-snug">{i.nombre}</p>
                      <p className="cifra shrink-0 text-sm font-semibold">{money(i.subtotal)}</p>
                    </div>
                    <div className="mt-1.5 flex items-center justify-between">
                      <span className="text-xs text-gris-500">{money(i.precio_unitario)} c/u</span>
                      <div className="flex items-center gap-1">
                        <button aria-label="Quitar uno" disabled={ocupado} className="icon-btn h-7 w-7 border border-gris-200"
                          onClick={() => accion(() => (i.cantidad > 1 ? carritoService.cambiar(i.id, i.cantidad - 1) : carritoService.quitar(i.id)))}>
                          <Minus className="h-3.5 w-3.5" /></button>
                        <span className="cifra w-8 text-center text-sm font-semibold">{i.cantidad}</span>
                        <button aria-label="Agregar uno" disabled={ocupado || i.cantidad >= i.stock_disponible} className="icon-btn h-7 w-7 border border-gris-200"
                          onClick={() => accion(() => carritoService.cambiar(i.id, i.cantidad + 1))}>
                          <Plus className="h-3.5 w-3.5" /></button>
                        <button aria-label="Quitar del ticket" disabled={ocupado} className="icon-btn h-7 w-7 hover:text-peligro-600"
                          onClick={() => accion(() => carritoService.quitar(i.id))}><Trash2 className="h-3.5 w-3.5" /></button>
                      </div>
                    </div>
                    {!i.stock_suficiente && <p className="mt-1 text-xs font-semibold text-peligro-600">Solo hay {i.stock_disponible} en stock.</p>}
                  </li>
                ))}
              </ul>
            )}

            {carrito?.items.length > 0 && (
              <div className="space-y-3.5 border-t border-gris-100 bg-gris-50 px-5 py-4">
                <Campo label="Cliente">
                  <div className="flex gap-1">
                    <select className="input" value={venta.cliente} onChange={(e) => setVenta({ ...venta, cliente: e.target.value })}>
                      <option value="">Clientes varios</option>
                      {clientes.map((c) => <option key={c.id} value={c.id}>{c.nombre}{c.numero_documento ? ` (${c.numero_documento})` : ''}</option>)}
                    </select>
                    <button className="btn-secondary px-2.5" title="Registrar cliente" aria-label="Registrar cliente"
                      onClick={() => setNuevoCliente(true)}><UserPlus className="h-4 w-4" /></button>
                  </div>
                </Campo>
                <div>
                  <span className="label">Pago</span>
                  <div className="flex flex-wrap gap-1.5" role="radiogroup" aria-label="Método de pago">
                    {METODOS_PAGO.map(([v, l]) => (
                      <button key={v} role="radio" aria-checked={venta.metodo_pago === v}
                        onClick={() => setVenta({ ...venta, metodo_pago: v })}
                        className={`rounded-full border px-3 py-1 text-[13px] font-semibold transition ${
                          venta.metodo_pago === v ? 'border-acento-600 bg-acento-600 text-white' : 'border-gris-200 bg-superficie text-gris-600 hover:border-gris-300'}`}>
                        {l}
                      </button>
                    ))}
                  </div>
                </div>
                <input className="input" placeholder="Observación (opcional)" aria-label="Observación"
                  value={venta.observacion} onChange={(e) => setVenta({ ...venta, observacion: e.target.value })} />

                <dl className="space-y-1 border-t border-dashed border-gris-200 pt-3 text-sm">
                  <div className="flex justify-between"><dt className="text-gris-600">Subtotal</dt><dd className="cifra">{money(carrito.subtotal)}</dd></div>
                  <div className="flex justify-between"><dt className="text-gris-600">IGV 18 %</dt><dd className="cifra">{money(carrito.igv)}</dd></div>
                  <div className="flex items-baseline justify-between pt-1">
                    <dt className="text-[15px] font-semibold text-gris-900">Total</dt>
                    <dd className="cifra text-2xl font-semibold leading-none text-gris-900">{money(carrito.total)}</dd>
                  </div>
                </dl>

                {carrito.alertas.length > 0 && <Aviso tono="aviso">{carrito.alertas.join(' ')}</Aviso>}

                <button className="btn-primary w-full py-3 text-base" disabled={ocupado || !carrito.puede_confirmar || caja === null}
                  onClick={cobrar} title={caja === null ? 'Abre tu caja para cobrar' : undefined}>
                  {caja === null ? 'Abre tu caja para cobrar' : `Cobrar ${money(carrito.total)}`}
                </button>
                <div className="flex gap-2">
                  <button className="btn-secondary flex-1" disabled={ocupado} onClick={guardarPedido}
                    title="No descuenta stock: queda pendiente hasta que alguien lo apruebe">
                    <ClipboardList className="h-4 w-4" /> Guardar como pedido
                  </button>
                  <button className="btn-ghost" disabled={ocupado} onClick={() => accion(() => carritoService.vaciar())}>Vaciar</button>
                </div>
              </div>
            )}
          </div>
        </aside>
      </div>

      {/* Móvil: el ticket queda abajo, así que mostramos el total siempre a la vista */}
      {carrito?.items.length > 0 && (
        <div className="fixed inset-x-0 bottom-0 z-20 flex items-center justify-between gap-3 border-t border-gris-200 bg-superficie px-4 py-3 shadow-flota lg:hidden">
          <span>
            <span className="block text-xs text-gris-500">{carrito.total_items} {carrito.total_items === 1 ? "unidad" : "unidades"}</span>
            <span className="cifra text-lg font-semibold">{money(carrito.total)}</span>
          </span>
          <a href="#ticket" className="btn-primary">Ver ticket y cobrar</a>
        </div>
      )}
      {carrito?.items.length > 0 && <div className="h-16 lg:hidden" />}

      <Modal open={nuevoCliente} onClose={() => setNuevoCliente(false)} size="sm" title="Registrar cliente">
        {nuevoCliente && (
          <ClienteForm onCancel={() => setNuevoCliente(false)}
            onSaved={(c) => { setClientes((l) => [...l, c]); setVenta((v) => ({ ...v, cliente: String(c.id) })); setNuevoCliente(false) }} />
        )}
      </Modal>

      <Modal open={!!listo} onClose={() => setListo(null)} size="sm" title={listo?.tipo === 'venta' ? 'Venta registrada' : 'Pedido guardado'}>
        {listo && (
          <div className="text-center">
            <CheckCircle2 className="mx-auto h-12 w-12 text-exito-500" />
            {listo.tipo === 'venta' ? (
              <>
                <p className="mt-3 text-2xl font-semibold text-gris-900">{money(listo.dato.total)}</p>
                <p className="text-sm text-gris-600">{String(listo.dato.numero).startsWith('F') ? 'Factura' : 'Boleta'} {listo.dato.numero}, pagada con {listo.dato.metodo_pago_display.toLowerCase()}.</p>
                <div className="mt-5 flex flex-col gap-2">
                  <button className="btn-dark" onClick={() => ventasService.abrirComprobante(listo.dato.id, listo.dato.numero)
                    .catch((e) => toast(errorMessage(e), 'error'))}>
                    <FileText className="h-4 w-4" /> Ver comprobante PDF
                  </button>
                  <button className="btn-secondary" onClick={() => ventasService.abrirComprobante(listo.dato.id, listo.dato.numero, 'TICKET')
                    .catch((e) => toast(errorMessage(e), 'error'))}>
                    <Printer className="h-4 w-4" /> Imprimir ticket (80 mm)
                  </button>
                  <button className="btn-primary" onClick={() => setListo(null)}>Nueva venta</button>
                </div>
              </>
            ) : (
              <>
                <p className="mt-3 text-xl font-semibold text-gris-900">{listo.dato.numero}</p>
                <p className="text-sm text-gris-600">
                  Quedó pendiente por {money(listo.dato.total_estimado)}. El stock no se mueve hasta que se apruebe.
                </p>
                <div className="mt-5 flex flex-col gap-2">
                  <Link to="/pedidos" className="btn-dark">Ir a pedidos</Link>
                  <button className="btn-primary" onClick={() => setListo(null)}>Seguir vendiendo</button>
                </div>
              </>
            )}
          </div>
        )}
      </Modal>
    </>
  )
}
