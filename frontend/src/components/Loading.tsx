export function Loading({ label = 'Cargando…' }: { label?: string }) {
  return <div className="loading-state"><span className="spinner" />{label}</div>
}
