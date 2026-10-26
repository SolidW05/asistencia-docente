import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { api } from '../api'
import type { AdminAttendanceRecord, Identity } from '../types'
import { Notice, ErrorText } from '../components/Notice'
import { Loading } from '../components/Loading'
import { StatusPill } from '../components/StatusPill'
import { formatDateTime, spatialLabel, temporalLabel } from '../format'

export function ReviewPage({ identity }: { identity: Identity }) {
  const [records, setRecords] = useState<AdminAttendanceRecord[]>([])
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [selected, setSelected] = useState<AdminAttendanceRecord | null>(null)
  const [comment, setComment] = useState('')
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [filter, setFilter] = useState('PENDING_REVIEW')
  const reload = useCallback(async () => {
    setBusy(true); setError('')
    try { setRecords(await api.adminAttendance()) }
    catch (cause) { setError(cause instanceof Error ? cause.message : 'No se pudieron consultar las asistencias.') }
    finally { setLoading(false); setBusy(false) }
  }, [])
  useEffect(() => { void reload() }, [reload])
  async function confirm(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!selected) return
    setBusy(true); setError(''); setNotice('')
    try {
      const saved = await api.reviewAttendance(selected.id, comment.trim())
      setRecords((current) => current.map((item) => item.id === saved.id ? { ...item, ...saved } : item))
      setSelected(null); setComment(''); setNotice('Asistencia confirmada. La revisión quedó guardada en su historial.')
    } catch (cause) { setError(cause instanceof Error ? cause.message : 'No se pudo confirmar la asistencia.') }
    finally { setBusy(false) }
  }
  if (loading) return <Loading label="Cargando asistencias para revisión." />
  const visible = records.filter((record) => !filter || record.status === filter)
  return <div><div className="page-heading"><div><span className="eyebrow">{identity.role === 'ADMIN' ? 'Todas las clases' : 'Mis clases'}</span><h1>Asistencias</h1><p>{identity.role === 'ADMIN' ? 'Consulta las asistencias de todos los docentes y revisa los registros pendientes.' : 'Consulta y revisa las asistencias de las clases que creaste.'}</p></div><button className="button button-secondary" disabled={busy} onClick={() => void reload()}>Actualizar</button></div>
    {error && <Notice tone="error">{error}</Notice>}{notice && <Notice tone="success">{notice}</Notice>}
    {selected && <section className="panel admin-panel"><h2>Confirmar asistencia de {selected.teacherName}</h2><p>{selected.className} · {formatDateTime(selected.receivedAt)}</p><p><strong>Motivo: </strong>{selected.reason || 'Sin motivo adicional'}</p><p>{selected.justification}</p><form className="form-stack" onSubmit={confirm}><label className="field-label" htmlFor="review-comment">Comentario de revisión</label><textarea id="review-comment" value={comment} onChange={(event) => setComment(event.target.value)} maxLength={2000} required /><small className="field-hint">El docente podrá consultar este comentario y el cambio de estado.</small><ErrorText>{!comment.trim() ? 'Escribe el motivo de la confirmación.' : ''}</ErrorText><div className="button-row"><button className="button button-primary" disabled={busy || !comment.trim()}>{busy ? 'Guardando…' : 'Confirmar asistencia'}</button><button type="button" className="button button-quiet" disabled={busy} onClick={() => setSelected(null)}>Cancelar</button></div></form></section>}
    <section className="panel records-panel"><div className="panel-header"><h2>Registros</h2><label>Estado <select value={filter} onChange={(event) => setFilter(event.target.value)}><option value="">Todos</option><option value="PENDING_REVIEW">Pendientes de revisión</option><option value="CONFIRMED">Confirmados</option></select></label></div>
    {visible.length === 0 ? <p className="empty-inline">No hay registros con este estado.</p> : <div className="table-scroll"><table><thead><tr><th>Docente</th><th>Clase y fecha</th><th>Ubicación y horario</th><th>Estado</th><th>Revisión</th></tr></thead><tbody>{visible.map((item) => <tr key={item.id}><td><strong>{item.teacherName}</strong><small>{item.teacherCode}</small></td><td><strong>{item.className}</strong><small>{formatDateTime(item.receivedAt)}</small></td><td>{spatialLabel(item.spatialResult)}<small>{temporalLabel(item.temporalResult)}</small></td><td><StatusPill status={item.status} /></td><td>{item.status === 'PENDING_REVIEW' ? <button className="button button-quiet button-small" disabled={busy} onClick={() => { setSelected(item); setComment(''); setNotice('') }}>Revisar</button> : 'Confirmada'}</td></tr>)}</tbody></table></div>}</section>
  </div>
}
