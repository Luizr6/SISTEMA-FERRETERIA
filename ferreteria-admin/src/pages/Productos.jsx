import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { FileUp, History, Package, Pencil, Plus, Trash2 } from 'lucide-react'
import { categoriasService, marcasService, productosService, proveedoresService } from '../services/catalogo'
import { errorMessage, limpiar, money } from '../utils/format'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../components/ui/Toast'
import PageHeader from '../components/ui/PageHeader'
import Spinner, { Vacio } from '../components/ui/Spinner'
import Modal from '../components/ui/Modal'
import Badge from '../components/ui/Badge'
import Wincha from '../components/ui/Wincha'
import { Buscador } from '../components/ui/Filtros'
import ProductoForm from '../components/productos/ProductoForm'
import Kardex from '../components/productos/Kardex'
import ImportarCsv from '../components/productos/ImportarCsv'
import useCarga from '../hooks/useCarga'

export default function Productos() {
  const { isAdmin } = useAuth()
  const toast = useToast()
  const [params] = useSearchParams()

  const [filtros, setFiltros] = useState({
    search: '', categoria: '', proveedor: '', stock: params.get('stock') || '', activo: '',
  })
  const [aux, setAux] = useState({ categorias: [], marcas: [], proveedores: [] })
  const [editando, setEditando] = useState(null)
  const [aEliminar, setAEliminar] = useState(null)
  const [kardex, setKardex] = useState(null)
  const [importar, setImportar] = useState(false)

  const { data: productos, recargar } = useCarga(
    () => productosService.listar(limpiar(filtros)), [JSON.stringify(filtros)], { retraso: 250 })

  useEffect(() => {
    Promise.all([categoriasService.listar(), marcasService.listar(), proveedoresService.listar()])
      .then(([categorias, marcas, proveedores]) => setAux({ categorias, marcas, proveedores }))
      .catch((e) => toast(errorMessage(e), 'error'))
  }, [toast])

  const f = (k) => (e) => setFiltros({ ...filtros, [k]: e.target.value })

  const eliminar = async () => {
    try {
      await productosService.eliminar(aEliminar.id)
      toast(`${aEliminar.nombre} eliminado.`)
      recargar()
    } catch (e) {
      toast(errorMessage(e), 'error')
    }
    setAEliminar(null)
  }

  return (
    <>
      <PageHeader title="Productos" subtitle="Catálogo, precios y stock de la ferretería.">
        {isAdmin && (
          <>
            <button className="btn-secondary" onClick={() => setImportar(true)}><FileUp className="h-4 w-4" /> Importar CSV</button>
            <button className="btn-primary" onClick={() => setEditando({})}><Plus className="h-4 w-4" /> Nuevo producto</button>
          </>
        )}
      </PageHeader>

      <div className="mb-4 grid gap-2.5 sm:grid-cols-2 lg:grid-cols-[2fr_1fr_1fr_1fr]">
        <Buscador value={filtros.search} onChange={(v) => setFiltros({ ...filtros, search: v })}
          placeholder="Buscar por código, nombre o marca" />
        <select className="input" value={filtros.categoria} onChange={f('categoria')} aria-label="Categoría">
          <option value="">Todas las categorías</option>
          {aux.categorias.map((c) => <option key={c.id} value={c.id}>{c.nombre}</option>)}
        </select>
        <select className="input" value={filtros.proveedor} onChange={f('proveedor')} aria-label="Proveedor">
          <option value="">Todos los proveedores</option>
          {aux.proveedores.map((p) => <option key={p.id} value={p.id}>{p.razon_social}</option>)}
        </select>
        <select className="input" value={filtros.stock} onChange={f('stock')} aria-label="Estado de stock">
          <option value="">Cualquier stock</option>
          <option value="critico">Por reponer (bajo y agotado)</option>
          <option value="bajo">Solo stock bajo</option>
          <option value="agotado">Solo agotados</option>
          <option value="normal">Stock normal</option>
        </select>
      </div>

      <div className="panel overflow-hidden">
        {productos === null ? <Spinner /> : productos.length === 0 ? (
          <Vacio icon={Package} titulo="No hay productos con esos filtros"
            texto="Prueba con otra búsqueda o limpia los filtros." />
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="tabla w-full min-w-[860px]">
                <thead><tr>
                  <th className="th">Producto</th>
                  <th className="th">Categoría</th>
                  <th className="th text-right">Precio</th>
                  <th className="th">Stock</th>
                  <th className="th text-right"><span className="sr-only">Acciones</span></th>
                </tr></thead>
                <tbody>
                  {productos.map((p) => (
                    <tr key={p.id} className={p.activo ? '' : 'opacity-55'}>
                      <td className="td">
                        <div className="flex items-start gap-3">
                          <div className="min-w-0">
                            <div className="flex flex-wrap items-center gap-2">
                              <span className="etiqueta">{p.codigo}</span>
                              {!p.activo && <Badge>Inactivo</Badge>}
                            </div>
                            <p className="mt-1 font-semibold text-gris-900">{p.nombre}</p>
                            <p className="text-[13px] text-gris-600">
                              {[p.marca_nombre, p.proveedor_nombre].filter(Boolean).join(', ') || 'Sin marca ni proveedor'}
                            </p>
                          </div>
                        </div>
                      </td>
                      <td className="td text-gris-600">{p.categoria_nombre}</td>
                      <td className="td text-right">
                        <p className="cifra text-[15px] font-semibold">{money(p.precio_venta)}</p>
                        <p className="text-xs text-gris-500">{money(Number(p.precio_venta) * 1.18)} con IGV</p>
                      </td>
                      <td className="td"><Wincha stock={p.stock_actual} minimo={p.stock_minimo} unidad={p.unidad_medida} /></td>
                      <td className="td">
                        <div className="flex justify-end gap-0.5">
                          <button title="Ver movimientos (kardex)" aria-label="Ver kardex" onClick={() => setKardex(p)} className="icon-btn">
                            <History className="h-4 w-4" /></button>
                          {isAdmin && (
                            <>
                              <button title="Editar" aria-label="Editar" onClick={() => setEditando(p)} className="icon-btn">
                                <Pencil className="h-4 w-4" /></button>
                              <button title="Eliminar" aria-label="Eliminar" onClick={() => setAEliminar(p)}
                                className="icon-btn hover:bg-peligro-50 hover:text-peligro-600"><Trash2 className="h-4 w-4" /></button>
                            </>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <p className="border-t border-gris-100 px-4 py-2.5 text-sm text-gris-600">{productos.length} productos</p>
          </>
        )}
      </div>

      <Modal open={!!editando} onClose={() => setEditando(null)} size="lg"
        title={editando?.id ? `Editar ${editando.nombre}` : 'Nuevo producto'}>
        {editando && (
          <ProductoForm producto={editando} {...aux}
            onCategoriaCreada={(c) => setAux((a) => ({ ...a, categorias: [...a.categorias, c] }))}
            onMarcaCreada={(m) => setAux((a) => ({ ...a, marcas: [...a.marcas, m] }))}
            onCancel={() => setEditando(null)}
            onSaved={() => { setEditando(null); toast('Producto guardado.'); recargar() }} />
        )}
      </Modal>

      <Modal open={!!kardex} onClose={() => setKardex(null)} size="lg" title={`Kardex de ${kardex?.nombre || ''}`}
        subtitle="Cada entrada y salida de stock, de la más reciente a la más antigua.">
        {kardex && <Kardex producto={kardex} />}
      </Modal>

      <Modal open={importar} onClose={() => setImportar(false)} title="Importar productos desde CSV">
        {importar && <ImportarCsv onListo={() => recargar()} onCerrar={() => setImportar(false)} />}
      </Modal>

      <Modal open={!!aEliminar} onClose={() => setAEliminar(null)} size="sm" title="Eliminar producto"
        footer={<>
          <button className="btn-secondary" onClick={() => setAEliminar(null)}>Cancelar</button>
          <button className="btn-danger" onClick={eliminar}>Eliminar</button>
        </>}>
        <p className="text-sm text-gris-600">
          ¿Eliminar <b className="text-gris-900">{aEliminar?.nombre}</b>? Si ya tiene ventas o movimientos, el sistema no lo
          permitirá: en ese caso edítalo y desmarca «Disponible para la venta».
        </p>
      </Modal>
    </>
  )
}
