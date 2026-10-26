import { useCallback, useEffect, useState } from 'react'
import type { Identity, AttendanceRecord, AttendanceHistoryEntry } from '../types'
import { api } from '../api'
import { formatDateTime, spatialLabel, temporalLabel } from '../format'
import { Notice } from '../components/Notice'
import { Loading } from '../components/Loading'
import { StatusPill } from '../components/StatusPill'

export function HistoryPage({ identity }: { identity: Identity }) {
  const [records, setRecords] = useState<AttendanceRecord[]>([])
  const [history, setHistory] = useState<Record<string, AttendanceHistoryEntry[]>>({})
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [historyLoading, setHistoryLoading] = useState<string | null>(null)
  const [error, setError] = useState('')
  const reload = useCallback(async () => {
    setError('')
    setBusy(true)
    try { setRecords(await api.attendance()); setHistory({}) }
    catch (cause) { setError(cause instanceof Error ? cause.message : 'No se pudieron cargar tus asistencias.') }
    finally { setLoading(false); setBusy(false) }
  }, [])
  useEffect(() => { void reload() }, [reload])
  async function loadHistory(id: string) {
    setHistoryLoading(id)
    setError('')
    try { const entries = await api.attendanceHistory(id); setHistory((current) => ({ ...current, [id]: entries })) }
    catch (cause) { setError(cause instanceof Error ? cause.message : 'No se pudieron cargar los cambios.') }
    finally { setHistoryLoading(null) }
  }
  if (loading) return <Loading label="Cargando tus asistencias." />
  return <div className="history-page">
    <div className="page-heading"><div><span className="eyebrow">Área docente · {identity.code}</span><h1>Mis asistencias</h1><p>Consulta el estado actual y los cambios de cada registro.</p></div><button className="button button-secondary" onClick={() => void reload()} disabled={busy || historyLoading !== null}>Actualizar asistencias</button></div>
    {error && <Notice tone="error">{error}</Notice>}
    <section className="panel records-panel"><div className="panel-header"><h2>Registros de asistencia</h2><span className="count-badge">{records.length}</span></div>
      {records.length === 0 ? <p className="empty-inline">Aún no tienes asistencias registradas.</p> : records.map((item) => <article key={item.id} className="attendance-history-card">
        <div className="panel-header"><div><h3>{item.className}</h3><small>{formatDateTime(item.receivedAt)}</small></div><StatusPill status={item.status} /></div>
        <p>{spatialLabel(item.spatialResult)} · {temporalLabel(item.temporalResult)}</p>
        {item.reason && <p><strong>Motivo: </strong>{item.reason}</p>}{item.justification && <p>{item.justification}</p>}
        <button className="button button-quiet button-small" disabled={historyLoading !== null} onClick={() => void loadHistory(item.id)}>{historyLoading === item.id ? 'Cargando cambios…' : 'Ver cambios del registro'}</button>
        {history[item.id] && <ol className="attendance-timeline">{history[item.id].map((entry) => <li key={entry.id}><strong>{entry.previousStatus === null ? 'Registro de asistencia' : 'Revisión de asistencia'}</strong><span>{formatDateTime(entry.changedAt)}{entry.reviewerName ? ` · ${entry.reviewerName}` : ''}</span><div>{entry.previousStatus && <><StatusPill status={entry.previousStatus} /> → </>}<StatusPill status={entry.status} /></div>{entry.comment && <p>{entry.comment}</p>}</li>)}</ol>}
      </article>)}
    </section>
  </div>
}
