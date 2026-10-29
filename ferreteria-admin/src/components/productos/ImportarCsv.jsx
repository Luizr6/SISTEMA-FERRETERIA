import { useState } from 'react'
import { Download, FileSpreadsheet, Loader2 } from 'lucide-react'
import { productosService } from '../../services/catalogo'
import { errorMessage } from '../../utils/format'
import { Aviso } from '../ui/Spinner'

/** RF11 - carga masiva de productos */
export default function ImportarCsv({ onListo, onCerrar }) {
  const [archivo, setArchivo] = useState(null)
  const [actualizar, setActualizar] = useState(false)
  const [resultado, setResultado] = useState(null)
  const [error, setError] = useState('')
  const [cargando, setCargando] = useState(false)

  const plantilla = async () => {
    const blob = await productosService.plantillaCsv()
    const a = document.createElement('a')
    a.href = URL.createObjectURL(blob)
    a.download = 'plantilla_productos.csv'
    a.click()
  }

  const subir = async () => {
    setError(''); setCargando(true)
    try {
      const r = await productosService.importarCsv(archivo, actualizar)
      setResultado(r)
      onListo()
    } catch (e) {
      // 400 con detalle de filas también llega aquí
      const d = e.response?.data
      if (d && 'creados' in d) setResultado(d)
      else setError(errorMessage(e))
    } finally {
      setCargando(false)
    }
  }

  if (resultado) {
    return (
      <div className="space-y-3">
        <div className="grid grid-cols-3 gap-2 text-center">
          {[['Creados', resultado.creados, 'text-exito-600'], ['Actualizados', resultado.actualizados, 'text-acento-600'],
            ['Con error', resultado.errores.length, 'text-peligro-600']].map(([l, n, c]) => (
            <div key={l} className="rounded-[12px] bg-gris-50 p-3">
              <p className={`cifra text-2xl font-semibold ${c}`}>{n}</p>
              <p className="text-xs text-gris-600">{l}</p>
            </div>
          ))}
        </div>
        {resultado.errores.length > 0 && (
          <ul className="max-h-56 overflow-y-auto rounded-[10px] border border-peligro-100 text-sm">
            {resultado.errores.map((e) => (
              <li key={e.fila} className="border-b border-peligro-50 px-3 py-2 last:border-0">
                <b>Fila {e.fila}:</b> {e.error}
              </li>
            ))}
          </ul>
        )}
        <div className="flex justify-end gap-2">
          <button className="btn-secondary" onClick={() => { setResultado(null); setArchivo(null) }}>Importar otro</button>
          <button className="btn-primary" onClick={onCerrar}>Listo</button>
        </div>
      </div>
    )
  }

  return (
    <div className="space-y-4">
      <p className="text-sm text-gris-600">
        Sube un archivo con las columnas <b>codigo, nombre, categoria, precio_compra, precio_venta</b> (obligatorias) y,
        si quieres, marca, proveedor_ruc, unidad_medida, stock_actual, stock_minimo y descripcion. Sirve el separador coma o punto y coma.
      </p>
      <button type="button" className="btn-ghost -ml-2.5" onClick={plantilla}><Download className="h-4 w-4" /> Descargar plantilla</button>

      <label className={`flex cursor-pointer flex-col items-center rounded-[14px] border-2 border-dashed px-4 py-8 text-center transition ${
        archivo ? 'border-gris-300 bg-gris-100' : 'border-gris-200 hover:border-gris-300 hover:bg-gris-50'}`}>
        <FileSpreadsheet className="h-8 w-8 text-gris-1000" />
        <span className="mt-2 text-sm font-semibold">{archivo ? archivo.name : 'Elige un archivo .csv'}</span>
        <span className="text-xs text-gris-500">máximo 5 MB</span>
        <input type="file" accept=".csv,text/csv" className="sr-only" onChange={(e) => setArchivo(e.target.files[0] || null)} />
      </label>

      <label className="flex items-center gap-2 text-sm">
        <input type="checkbox" className="h-4 w-4 accent-acento-600" checked={actualizar} onChange={(e) => setActualizar(e.target.checked)} />
        Si el código ya existe, actualizar el producto
      </label>
      {error && <Aviso>{error}</Aviso>}
      <div className="flex justify-end gap-2">
        <button className="btn-secondary" onClick={onCerrar}>Cancelar</button>
        <button className="btn-primary" disabled={!archivo || cargando} onClick={subir}>
          {cargando && <Loader2 className="h-4 w-4 animate-spin" />} Importar productos
        </button>
      </div>
    </div>
  )
}
