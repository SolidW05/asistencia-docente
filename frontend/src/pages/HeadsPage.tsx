import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { api } from '../api'
import type { TeacherOption } from '../types'
import { Notice, ErrorText } from '../components/Notice'
import { Loading } from '../components/Loading'

export function HeadsPage() {
  const [people, setPeople] = useState<TeacherOption[]>([])
  const [code, setCode] = useState('')
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const reload = useCallback(async () => {
    try { setPeople(await api.adminTeachers()) }
    catch (cause) { setError(cause instanceof Error ? cause.message : 'No se pudieron consultar los jefes.') }
    finally { setLoading(false) }
  }, [])
  useEffect(() => { void reload() }, [reload])
  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setSaving(true); setError(''); setNotice('')
    try {
      const saved = await api.createHead(code.trim())
      setPeople((current) => [...current.filter((person) => person.id !== saved.id), saved])
      setCode(''); setNotice(saved.role === 'ADMIN' ? `El código ${saved.code} ya es administrador principal y conserva ese acceso.` : `El código ${saved.code} tiene acceso como jefe. Al iniciar sesión abrirá su administración.`)
    } catch (cause) { setError(cause instanceof Error ? cause.message : 'No se pudo dar de alta al jefe.') }
    finally { setSaving(false) }
  }
  if (loading) return <Loading label="Cargando jefes." />
  return <div><div className="page-heading"><div><span className="eyebrow">Administración principal</span><h1>Jefes</h1><p>Da acceso a quienes crearán clases y revisarán sus asistencias.</p></div></div>
    {notice && <Notice tone="success">{notice}</Notice>}
    <div className="class-admin-layout"><section className="panel admin-panel"><h2>Dar de alta a un jefe</h2><p className="section-intro">Ingresa su código docente. Si la cuenta todavía no existe, se creará al guardar.</p><form className="form-stack" onSubmit={save}><label className="field-label" htmlFor="head-code">Código docente</label><input id="head-code" inputMode="numeric" pattern="[0-9]{7}" maxLength={7} value={code} onChange={(event) => setCode(event.target.value)} required /><ErrorText>{error}</ErrorText><button className="button button-primary" disabled={saving}>{saving ? 'Guardando…' : 'Dar acceso como jefe'}</button></form></section>
    <section className="panel admin-panel"><h2>Jefes registrados</h2>{people.filter((person) => person.role === 'JEFE').length === 0 ? <p className="empty-inline">Todavía no hay jefes registrados.</p> : <div className="table-scroll"><table><thead><tr><th>Nombre</th><th>Código</th></tr></thead><tbody>{people.filter((person) => person.role === 'JEFE').map((person) => <tr key={person.id}><td>{person.name}</td><td>{person.code}</td></tr>)}</tbody></table></div>}</section></div>
  </div>
}
