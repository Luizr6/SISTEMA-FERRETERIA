const tonos = {
  exito: 'bg-exito-50 text-exito-700 ring-exito-500/20',
  aviso: 'bg-aviso-50 text-aviso-700 ring-aviso-500/25',
  peligro: 'bg-peligro-50 text-peligro-700 ring-peligro-500/20',
  acento: 'bg-acento-50 text-acento-700 ring-acento-500/20',
  gris: 'bg-gris-100 text-gris-600 ring-gris-300/60',
}
// nombres anteriores (por compatibilidad con código existente)
Object.assign(tonos, {
  musgo: tonos.exito, green: tonos.exito, senal: tonos.aviso, amber: tonos.aviso, oxido: tonos.peligro,
  red: tonos.peligro, cobalto: tonos.acento, pino: tonos.acento, papel: tonos.gris, slate: tonos.gris,
})

export default function Badge({ tone = 'gris', punto = false, children }) {
  return (
    <span className={`chip ring-1 ring-inset ${tonos[tone] || tonos.gris}`}>
      {punto && <span className="h-1.5 w-1.5 rounded-full bg-current" />}
      {children}
    </span>
  )
}

const ESTADOS = {
  COMPLETADA: ['exito', 'Completada'], ANULADA: ['peligro', 'Anulada'],
  PENDIENTE: ['aviso', 'Pendiente'], APROBADO: ['exito', 'Aprobado'], RECHAZADO: ['peligro', 'Rechazado'],
  CANCELADO: ['gris', 'Cancelado'],
  PARCIAL: ['acento', 'Parcial'], RECIBIDA: ['exito', 'Recibida'],
  NORMAL: ['exito', 'Normal'], BAJO: ['aviso', 'Stock bajo'], AGOTADO: ['peligro', 'Agotado'],
}

export function Estado({ valor, etiqueta }) {
  const [tone, label] = ESTADOS[valor] || ['gris', valor]
  return <Badge tone={tone} punto>{etiqueta || label}</Badge>
}

export const EstadoStock = ({ estado }) => <Estado valor={estado} />
