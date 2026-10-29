export default function PageHeader({ title, subtitle, children }) {
  return (
    <div className="mb-5 flex flex-wrap items-end justify-between gap-x-6 gap-y-3">
      <div className="min-w-0">
        <h1 className="text-[22px] font-semibold leading-tight">{title}</h1>
        {subtitle && <p className="mt-1 max-w-2xl text-sm text-gris-500">{subtitle}</p>}
      </div>
      {children && <div className="flex flex-wrap items-center gap-2">{children}</div>}
    </div>
  )
}
