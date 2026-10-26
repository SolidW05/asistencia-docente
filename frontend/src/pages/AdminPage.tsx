import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react'
import '@geoman-io/leaflet-geoman-free'
import '@geoman-io/leaflet-geoman-free/dist/leaflet-geoman.css'
import type { Identity, Campus, ClassAssignment, Geofence, PolygonGeometry, TeacherOption } from '../types'
import { api, ApiError } from '../api'
import { ErrorText, Notice } from '../components/Notice'
import { Loading } from '../components/Loading'
import { MapView } from '../components/MapView'

type CampusForm = { id: string | null; name: string; active: boolean }
type FenceForm = { id: string | null; name: string; campusId: string; type: 'CAMPUS' | 'MODULE'; parentId: string; active: boolean; version?: number; geometry: PolygonGeometry | null }
type ClassForm = { id: string | null; name: string; teacherId: string; teacherCode: string; campusId: string; allowedModuleIds: string[]; dayOfWeek: number; startTime: string; endTime: string; active: boolean; version?: number }

const EMPTY_CAMPUS: CampusForm = { id: null, name: '', active: true }
const EMPTY_FENCE: FenceForm = { id: null, name: '', campusId: '', type: 'CAMPUS', parentId: '', active: true, geometry: null }
const EMPTY_CLASS: ClassForm = { id: null, name: '', teacherId: '', teacherCode: '', campusId: '', allowedModuleIds: [], dayOfWeek: 1, startTime: '08:00', endTime: '09:00', active: true }
const DAYS = ['Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado', 'Domingo']

export function AdminPage({ identity }: { identity: Identity }) {
  const isAdmin = identity.role === 'ADMIN'
  const [activeTab, setActiveTab] = useState<'geofences' | 'classes'>(isAdmin ? 'geofences' : 'classes')
  const [campuses, setCampuses] = useState<Campus[]>([])
  const [fences, setFences] = useState<Geofence[]>([])
  const [classes, setClasses] = useState<ClassAssignment[]>([])
  const [teachers, setTeachers] = useState<TeacherOption[]>([])
  const [campusForm, setCampusForm] = useState<CampusForm>(EMPTY_CAMPUS)
  const [fenceForm, setFenceForm] = useState<FenceForm>(EMPTY_FENCE)
  const [classForm, setClassForm] = useState<ClassForm>(EMPTY_CLASS)
  const [selectedFenceId, setSelectedFenceId] = useState('')
  const [selectedClassId, setSelectedClassId] = useState('')
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')
  const [campusError, setCampusError] = useState('')
  const [fenceError, setFenceError] = useState('')
  const [classError, setClassError] = useState('')
  const [savingCampus, setSavingCampus] = useState(false)
  const [savingFence, setSavingFence] = useState(false)
  const [savingClass, setSavingClass] = useState(false)
  const [notice, setNotice] = useState('')
  const [mapVersion, setMapVersion] = useState(0)
  const [fitCampusKey, setFitCampusKey] = useState(0)

  const reload = useCallback(async () => {
    const [nextCampuses, nextFences, nextClasses, nextTeachers] = await Promise.all([
      api.campuses(), api.geofences(), api.adminClasses(), api.adminTeachers(),
    ])
    setCampuses(nextCampuses)
    setFences(nextFences)
    setClasses(nextClasses)
    setTeachers(nextTeachers)
    setFenceForm((current) => current.campusId || nextCampuses.length === 0 ? current : { ...current, campusId: nextCampuses[0].id })
    setClassForm((current) => ({
      ...current,
      campusId: current.campusId || nextCampuses[0]?.id || '',
      teacherId: current.teacherId || nextTeachers.find((teacher) => teacher.role === 'TEACHER')?.id || '',
    }))
    setMapVersion((current) => current + 1)
  }, [])

  useEffect(() => {
    reload().catch((error: unknown) => setLoadError(error instanceof Error ? error.message : 'No se pudieron cargar los datos administrativos.'))
      .finally(() => setLoading(false))
  }, [reload])

  const selectedFence = fences.find((item) => item.id === selectedFenceId) ?? null
  const selectedClass = classes.find((item) => item.id === selectedClassId) ?? null
  const mapFences = useMemo(() => fences.filter((item) => item.campusId === fenceForm.campusId), [fenceForm.campusId, fences])
  const eligibleModules = fences.filter((item) => item.type === 'MODULE' && item.campusId === classForm.campusId)
  const fenceParentOptions = fences.filter((item) => item.type === 'CAMPUS' && item.campusId === fenceForm.campusId)

  useEffect(() => {
    if (!selectedFence) return
    setFenceForm({
      id: selectedFence.id,
      name: selectedFence.name,
      campusId: selectedFence.campusId,
      type: selectedFence.type,
      parentId: selectedFence.parentId ?? '',
      active: selectedFence.active,
      version: selectedFence.version,
      geometry: selectedFence.geometry,
    })
  }, [selectedFence])

  useEffect(() => {
    if (!selectedClass) return
    setClassForm({
      id: selectedClass.id,
      name: selectedClass.name,
      teacherId: selectedClass.teacherId,
      teacherCode: '',
      campusId: selectedClass.campusId,
      allowedModuleIds: selectedClass.allowedModuleIds,
      dayOfWeek: selectedClass.dayOfWeek,
      startTime: selectedClass.startTime,
      endTime: selectedClass.endTime,
      active: selectedClass.active,
      version: selectedClass.version,
    })
  }, [selectedClass])

  const onGeometryChange = useCallback((geometry: PolygonGeometry) => {
    setFenceForm((current) => ({ ...current, geometry }))
    setFenceError('')
    setNotice('Geometría actualizada en el mapa. Guarda los cambios para enviarla al servidor.')
  }, [])

  async function saveCampus(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSavingCampus(true)
    setCampusError('')
    setNotice('')
    try {
      const saved = await api.saveCampus(campusForm.id, { name: campusForm.name.trim(), active: campusForm.active })
      setCampuses((current) => current.some((item) => item.id === saved.id) ? current.map((item) => item.id === saved.id ? saved : item) : [...current, saved])
      setCampusForm({ id: saved.id, name: saved.name, active: saved.active })
      setFenceForm((current) => ({ ...current, campusId: current.campusId || saved.id }))
      setNotice('Campus guardado. Recuerda que el polígono debe configurarse como geocerca de campus.')
    } catch (error) { setCampusError(error instanceof Error ? error.message : 'No se pudo guardar el campus.') }
    finally { setSavingCampus(false) }
  }

  async function saveFence(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!fenceForm.geometry) { setFenceError('Dibuja un polígono sobre el mapa antes de guardar.'); return }
    if (!fenceForm.campusId) { setFenceError('Selecciona un campus antes de guardar.'); return }
    if (fenceForm.type === 'MODULE' && !fenceForm.parentId) { setFenceError('Selecciona la geocerca campus que contendrá este módulo.'); return }
    setSavingFence(true)
    setFenceError('')
    setNotice('')
    try {
      const saved = await api.saveGeofence(fenceForm.id, {
        campusId: fenceForm.campusId,
        name: fenceForm.name.trim(),
        type: fenceForm.type,
        parentId: fenceForm.type === 'MODULE' ? fenceForm.parentId : null,
        geometry: fenceForm.geometry,
        active: fenceForm.active,
        ...(fenceForm.id && fenceForm.version !== undefined ? { version: fenceForm.version } : {}),
      })
      await reload()
      setSelectedFenceId(saved.id)
      setFenceForm({ id: saved.id, name: saved.name, campusId: saved.campusId, type: saved.type, parentId: saved.parentId ?? '', active: saved.active, version: saved.version, geometry: saved.geometry })
      setNotice('Geocerca guardada. Su versión quedó disponible para los siguientes registros.')
    } catch (error) {
      const message = error instanceof Error ? error.message : 'No se pudo guardar la geocerca.'
      setFenceError(error instanceof ApiError && error.status === 422 ? `${message} Revisa que el polígono no tenga autointersecciones y que el módulo quede dentro del campus.` : message)
    } finally { setSavingFence(false) }
  }

  async function saveClass(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (classForm.allowedModuleIds.length === 0) { setClassError('Asigna al menos un módulo permitido para la clase.'); return }
    setSavingClass(true)
    setClassError('')
    setNotice('')
    try {
      const saved = await api.saveClass(classForm.id, {
        name: classForm.name.trim(),
        ...(classForm.teacherCode.trim() ? { teacherCode: classForm.teacherCode.trim() } : { teacherId: classForm.teacherId }),
        campusId: classForm.campusId,
        allowedModuleIds: classForm.allowedModuleIds,
        dayOfWeek: classForm.dayOfWeek,
        startTime: classForm.startTime,
        endTime: classForm.endTime,
        active: classForm.active,
        ...(classForm.id && classForm.version !== undefined ? { version: classForm.version } : {}),
      })
      await reload()
      setSelectedClassId(saved.id)
      setNotice('Clase, horario y módulos permitidos guardados.')
    } catch (error) { setClassError(error instanceof Error ? error.message : 'No se pudo guardar la clase.') }
    finally { setSavingClass(false) }
  }

  function chooseFence(id: string) {
    setSelectedFenceId(id)
    setFenceError('')
    setNotice('')
    if (!id) {
      setFenceForm((current) => ({ ...EMPTY_FENCE, campusId: current.campusId || campuses[0]?.id || '' }))
      return
    }
  }

  function chooseClass(id: string) {
    setSelectedClassId(id)
    setClassError('')
    setNotice('')
    if (!id) setClassForm((current) => ({ ...EMPTY_CLASS, campusId: current.campusId || campuses[0]?.id || '', teacherId: current.teacherId || teachers.find((teacher) => teacher.role === 'TEACHER')?.id || '' }))
  }

  if (loading) return <Loading label="Cargando configuración…" />

  return (
    <div className="admin-page">
      <div className="page-heading">
        <div><span className="eyebrow">Configuración del prototipo</span><h1>Administración</h1><p>Define las áreas del campus y relaciona clases con docentes, horarios y módulos.</p></div>
        <span className="admin-badge"><span className="badge-dot" />{isAdmin ? 'Administrador' : 'Jefe'}</span>
      </div>
      {loadError && <Notice tone="error" title="No fue posible cargar la administración">{loadError}</Notice>}
      {notice && <Notice tone="success">{notice}</Notice>}
      <div className="admin-tabs" role="tablist" aria-label="Secciones administrativas">
        {isAdmin && <button className={isAdmin && activeTab === 'geofences' ? 'active' : ''} role="tab" aria-selected={activeTab === 'geofences'} onClick={() => setActiveTab('geofences')}><span>◈</span> Campus y geocercas</button>}
        <button className={activeTab === 'classes' ? 'active' : ''} role="tab" aria-selected={activeTab === 'classes'} onClick={() => setActiveTab('classes')}><span>▦</span> Clases y horarios</button>
      </div>

      {isAdmin && activeTab === 'geofences' ? <div className="admin-geofence-layout">
        <div className="admin-form-column">
          <section className="panel admin-panel">
            <div className="panel-header"><div><span className="eyebrow">Nivel 1</span><h2>Campus</h2></div><span className="step-number">01</span></div>
            <p className="section-intro">Crea el nombre del campus. Su polígono se registra como una geocerca independiente.</p>
            <label className="field-label" htmlFor="campus-existing">Campus configurados</label>
            <select id="campus-existing" value={campusForm.id ?? ''} onChange={(event) => {
              const value = event.target.value
              if (!value) setCampusForm(EMPTY_CAMPUS)
              else { const found = campuses.find((item) => item.id === value); if (found) setCampusForm({ id: found.id, name: found.name, active: found.active }) }
            }}>
              <option value="">Crear campus</option>{campuses.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}
            </select>
            <form onSubmit={saveCampus} className="form-stack">
              <label className="field-label" htmlFor="campus-name">Nombre</label>
              <input id="campus-name" value={campusForm.name} onChange={(event) => setCampusForm({ ...campusForm, name: event.target.value })} maxLength={120} required />
              <label className="check-row"><input type="checkbox" checked={campusForm.active} onChange={(event) => setCampusForm({ ...campusForm, active: event.target.checked })} /> Campus activo</label>
              <ErrorText>{campusError}</ErrorText>
              <button className="button button-secondary button-full" type="submit" disabled={savingCampus}>{savingCampus ? 'Guardando…' : campusForm.id ? 'Guardar campus' : 'Crear campus'}</button>
            </form>
          </section>

          <section className="panel admin-panel">
            <div className="panel-header"><div><span className="eyebrow">Nivel 2</span><h2>Geocerca</h2></div><span className="step-number">02</span></div>
            <label className="field-label" htmlFor="fence-existing">Geocercas configuradas</label>
            <select id="fence-existing" value={selectedFenceId} onChange={(event) => chooseFence(event.target.value)}>
              <option value="">Crear geocerca</option>{fences.map((item) => <option key={item.id} value={item.id}>{item.type === 'CAMPUS' ? 'Campus' : 'Módulo'} · {item.name}</option>)}
            </select>
            <form onSubmit={saveFence} className="form-stack">
              <label className="field-label" htmlFor="fence-name">Nombre</label>
              <input id="fence-name" value={fenceForm.name} onChange={(event) => setFenceForm({ ...fenceForm, name: event.target.value })} maxLength={120} required />
              <div className="field-grid">
                <div><label className="field-label" htmlFor="fence-campus">Campus</label><select id="fence-campus" value={fenceForm.campusId} onChange={(event) => setFenceForm({ ...fenceForm, campusId: event.target.value, parentId: '' })} required><option value="">Selecciona</option>{campuses.map((campus) => <option key={campus.id} value={campus.id}>{campus.name}</option>)}</select></div>
                <div><label className="field-label" htmlFor="fence-type">Tipo</label><select id="fence-type" value={fenceForm.type} onChange={(event) => setFenceForm({ ...fenceForm, type: event.target.value as FenceForm['type'], parentId: '' })}><option value="CAMPUS">Campus</option><option value="MODULE">Módulo</option></select></div>
              </div>
              {fenceForm.type === 'MODULE' && <><label className="field-label" htmlFor="fence-parent">Geocerca campus contenedora</label><select id="fence-parent" value={fenceForm.parentId} onChange={(event) => setFenceForm({ ...fenceForm, parentId: event.target.value })} required><option value="">Selecciona una geocerca</option>{fenceParentOptions.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></>}
              <label className="check-row"><input type="checkbox" checked={fenceForm.active} onChange={(event) => setFenceForm({ ...fenceForm, active: event.target.checked })} /> Geocerca activa</label>
              <div className="geometry-status"><span className={fenceForm.geometry ? 'geometry-ok' : ''}>{fenceForm.geometry ? '✓ Geometría lista para guardar' : '◌ Dibuja el polígono en el mapa'}</span>{fenceForm.version !== undefined && <small>Versión {fenceForm.version}</small>}</div>
              <ErrorText>{fenceError}</ErrorText>
              <button className="button button-primary button-full" type="submit" disabled={savingFence || !campuses.length}>{savingFence ? 'Guardando…' : fenceForm.id ? 'Guardar cambios' : 'Crear geocerca'}</button>
            </form>
          </section>
        </div>

        <section className="panel admin-map-panel">
          <div className="panel-header"><div><span className="eyebrow">Editor de polígonos</span><h2>{fenceForm.id ? fenceForm.name || 'Editando geocerca' : 'Dibuja sobre el mapa'}</h2></div><button className="button button-quiet button-small" onClick={() => setFitCampusKey((value) => value + 1)}>Ver campus completo</button></div>
          <MapView geofences={mapFences} editable selectedId={selectedFenceId || null} onGeometryChange={onGeometryChange} mapKey={mapVersion} fitCampusKey={fitCampusKey} />
          <div className="map-editor-instructions"><strong>Cómo editar</strong><span>Selecciona una geocerca para mover sus vértices; usa la herramienta de polígono en el mapa para dibujar o reemplazar el contorno. Guarda para validar y persistir.</span></div>
          <div className="map-caption"><span><i className="legend-campus" />Campus</span><span><i className="legend-module" />Módulos</span></div>
          <Notice tone="info">El servidor valida el GeoJSON y la pertenencia del módulo al campus.</Notice>
        </section>
      </div> : <div className="class-admin-layout">
        <section className="panel admin-panel class-config-panel">
          <div className="panel-header"><div><span className="eyebrow">Asignación docente</span><h2>Clase y horario</h2></div><span className="step-number">01</span></div>
          <label className="field-label" htmlFor="class-existing">Clases configuradas</label>
          <select id="class-existing" value={selectedClassId} onChange={(event) => chooseClass(event.target.value)}><option value="">Crear clase</option>{classes.map((item) => <option key={item.id} value={item.id}>{item.name} · {teachers.find((person) => person.id === item.teacherId)?.name ?? 'Docente'}</option>)}</select>
          <form onSubmit={saveClass} className="form-stack">
            <label className="field-label" htmlFor="class-name">Nombre de la clase</label>
            <input id="class-name" value={classForm.name} onChange={(event) => setClassForm({ ...classForm, name: event.target.value })} maxLength={120} required />
            <label className="field-label" htmlFor="class-teacher">Docente</label>
            <select id="class-teacher" value={classForm.teacherId} onChange={(event) => setClassForm({ ...classForm, teacherId: event.target.value, teacherCode: '' })} required={!classForm.teacherCode}><option value="">Selecciona docente</option>{teachers.filter((teacher) => teacher.role === 'TEACHER').map((teacher) => <option key={teacher.id} value={teacher.id}>{teacher.name} · {teacher.code}</option>)}</select>
            <label className="field-label" htmlFor="class-teacher-code">O asignar por código docente</label>
            <input id="class-teacher-code" inputMode="numeric" pattern="[0-9]{7}" maxLength={7} value={classForm.teacherCode} onChange={(event) => setClassForm({ ...classForm, teacherCode: event.target.value, teacherId: '' })} placeholder="Siete dígitos" />
            <small className="field-hint">Si aún no existe, su cuenta se crea al guardar la clase.</small>
            <label className="field-label" htmlFor="class-campus">Campus</label>
            <select id="class-campus" value={classForm.campusId} onChange={(event) => setClassForm({ ...classForm, campusId: event.target.value, allowedModuleIds: [] })} required><option value="">Selecciona campus</option>{campuses.map((campus) => <option key={campus.id} value={campus.id}>{campus.name}</option>)}</select>
            <div className="field-grid field-grid-three">
              <div><label className="field-label" htmlFor="class-day">Día</label><select id="class-day" value={classForm.dayOfWeek} onChange={(event) => setClassForm({ ...classForm, dayOfWeek: Number(event.target.value) })}>{DAYS.map((day, index) => <option value={index + 1} key={day}>{day}</option>)}</select></div>
              <div><label className="field-label" htmlFor="class-start">Inicio</label><input id="class-start" type="time" value={classForm.startTime} onChange={(event) => setClassForm({ ...classForm, startTime: event.target.value })} required /></div>
              <div><label className="field-label" htmlFor="class-end">Fin</label><input id="class-end" type="time" value={classForm.endTime} onChange={(event) => setClassForm({ ...classForm, endTime: event.target.value })} required /></div>
            </div>
            <label className="field-label">Módulos permitidos</label>
            <div className="module-picker">
              {eligibleModules.length === 0 ? <span className="field-hint">No hay módulos activos o configurados para este campus.</span> : eligibleModules.map((module) => <label className="check-row" key={module.id}><input type="checkbox" checked={classForm.allowedModuleIds.includes(module.id)} onChange={(event) => setClassForm((current) => ({ ...current, allowedModuleIds: event.target.checked ? [...current.allowedModuleIds, module.id] : current.allowedModuleIds.filter((id) => id !== module.id) }))} />{module.name}{!module.active && <small className="muted"> · inactivo</small>}</label>)}
            </div>
            <label className="check-row"><input type="checkbox" checked={classForm.active} onChange={(event) => setClassForm({ ...classForm, active: event.target.checked })} /> Clase activa</label>
            <ErrorText>{classError}</ErrorText>
            <button className="button button-primary button-full" type="submit" disabled={savingClass}>{savingClass ? 'Guardando…' : classForm.id ? 'Guardar cambios' : 'Crear clase'}</button>
            {classForm.version !== undefined && <span className="field-hint">Versión {classForm.version} · actualizada con control de concurrencia.</span>}
          </form>
        </section>
        <section className="panel class-help-panel">
          <span className="step-number">02</span><span className="eyebrow">Configuración necesaria</span><h2>El registro ordinario depende de la asignación.</h2><p>El sistema cruza la ocurrencia del día con los módulos permitidos y la geocerca del campus. Si no hay clase aplicable, podrás consultar el mapa, pero no se aprobará una asistencia por falta de configuración.</p>
          <div className="config-list"><div><span className="config-index">A</span><span><strong>Geocerca de campus</strong><small>Define el polígono que contiene módulos.</small></span></div><div><span className="config-index">B</span><span><strong>Módulo</strong><small>Relaciona cada polígono con su campus.</small></span></div><div><span className="config-index">C</span><span><strong>Clase y horario</strong><small>Asigna docente, día y uno o varios módulos.</small></span></div></div>
        </section>
      </div>}
    </div>
  )
}
