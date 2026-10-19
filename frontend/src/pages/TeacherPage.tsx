import { useCallback, useEffect, useMemo, useState } from 'react'
import type { Identity, AppConfig, AttendanceCheck, AttendancePayload, AttendanceRecord, Campus, ClassAssignment, Geofence, LocationIssue, Position } from '../types'
import { api, ApiError } from '../api'
import { formatCoordinates, formatDateTime, formatTime, isFreshPosition, issueLabel, mexicoDate, temporalLabel } from '../format'
import { MapView } from '../components/MapView'
import { Notice } from '../components/Notice'
import { Loading } from '../components/Loading'
import { SpatialPill, StatusPill } from '../components/StatusPill'

type SendAttempt = {
  key: string
  body: AttendancePayload
  state: 'uncertain' | 'not-found' | 'rejected'
  message: string
}

function idempotencyKey(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) return crypto.randomUUID()
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (char) => {
    const random = Math.random() * 16 | 0
    return (char === 'x' ? random : (random & 3) | 8).toString(16)
  })
}

function geolocationIssue(error: GeolocationPositionError): LocationIssue {
  if (error.code === error.PERMISSION_DENIED) return 'PERMISSION_DENIED'
  if (error.code === error.TIMEOUT) return 'TIMEOUT'
  return 'UNAVAILABLE'
}

interface TeacherPageProps { identity: Identity }

export function TeacherPage({ identity }: TeacherPageProps) {
  const today = useMemo(() => mexicoDate(), [])
  const [config, setConfig] = useState<AppConfig | null>(null)
  const [campuses, setCampuses] = useState<Campus[]>([])
  const [geofences, setGeofences] = useState<Geofence[]>([])
  const [viewCampusId, setViewCampusId] = useState('')
  const [classes, setClasses] = useState<ClassAssignment[]>([])
  const [selectedClassId, setSelectedClassId] = useState('')
  const [position, setPosition] = useState<Position | null>(null)
  const [locationIssue, setLocationIssue] = useState<LocationIssue | null>(null)
  const [checkingLocation, setCheckingLocation] = useState(false)
  const [incidentMode, setIncidentMode] = useState(false)
  const [check, setCheck] = useState<AttendanceCheck | null>(null)
  const [checkBusy, setCheckBusy] = useState(false)
  const [checkError, setCheckError] = useState('')
  const [reason, setReason] = useState('')
  const [justification, setJustification] = useState('')
  const [locationAgeNow, setLocationAgeNow] = useState(Date.now())
  const [fitCampusKey, setFitCampusKey] = useState(0)
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')
  const [attempt, setAttempt] = useState<SendAttempt | null>(null)
  const [sendBusy, setSendBusy] = useState(false)
  const [record, setRecord] = useState<AttendanceRecord | null>(null)
  const [sendMessage, setSendMessage] = useState('')

  useEffect(() => {
    let active = true
    Promise.all([api.config(), api.campuses(), api.geofences(), api.classesForDate(today)])
      .then(([nextConfig, nextCampuses, nextFences, nextClasses]) => {
        if (!active) return
        setConfig(nextConfig)
        setCampuses(nextCampuses)
        setGeofences(nextFences)
        setClasses(nextClasses)
        setViewCampusId(nextCampuses.find((campus) => campus.active)?.id ?? nextCampuses[0]?.id ?? '')
        const first = nextClasses.find((item) => item.occurrence && item.active)
        if (first) setSelectedClassId(first.id)
      })
      .catch((error: unknown) => setLoadError(error instanceof Error ? error.message : 'No se pudieron cargar los datos.'))
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [today])

  useEffect(() => {
    if (!position) return
    const timer = window.setInterval(() => setLocationAgeNow(Date.now()), 1000)
    return () => window.clearInterval(timer)
  }, [position])

  const selectedClass = classes.find((item) => item.id === selectedClassId) ?? null
  const selectedCampus = campuses.find((item) => item.id === selectedClass?.campusId)
  const mapCampusId = selectedClass?.campusId ?? viewCampusId
  const mapCampus = campuses.find((item) => item.id === mapCampusId)
  const mapGeofences = geofences.filter((item) => item.campusId === mapCampusId)
  const allowedModules = geofences.filter((item) => selectedClass?.allowedModuleIds.includes(item.id))
  const fresh = Boolean(position && config && isFreshPosition(position.capturedAt, config.maxAgeSeconds, locationAgeNow))
  const effectiveLocation = locationIssue ? null : position
  const currentOccurrenceId = selectedClass?.occurrence?.id ?? ''
  const needsJustification = Boolean(check?.requiresJustification || incidentMode || locationIssue)
  const reasonOptions = check?.spatialResult === 'OTHER_CAMPUS_SPACE'
    ? ['Clase en Cineteca', 'Actividad en otro espacio del campus', 'Otro']
    : check?.spatialResult === 'OUTSIDE_CAMPUS'
      ? ['Clase virtual acordada con el grupo', 'Actividad externa', 'Otro']
      : check?.temporalResult === 'OUTSIDE_WINDOW'
        ? ['Excepción por horario', 'Otro']
        : ['No pude obtener ubicación', 'La posición requiere revisión', 'Actividad académica excepcional', 'Otro']

  const invalidateCheck = useCallback(() => {
    setCheck(null)
    setCheckError('')
    setIncidentMode(false)
  }, [])

  function refreshLocation() {
    invalidateCheck()
    setSendMessage('')
    if (!navigator.geolocation) {
      setLocationIssue('UNAVAILABLE')
      setCheckingLocation(false)
      setCheckError(window.isSecureContext
        ? 'Este navegador no ofrece la API de ubicación. Puedes reportar una incidencia con justificación.'
        : 'La ubicación requiere un contexto seguro: usa localhost en desarrollo o HTTPS desde otro dispositivo. Puedes reportar una incidencia con justificación.')
      return
    }
    setCheckingLocation(true)
    navigator.geolocation.getCurrentPosition(
      (result) => {
        setPosition({
          latitude: result.coords.latitude,
          longitude: result.coords.longitude,
          accuracyMeters: result.coords.accuracy,
          capturedAt: new Date(result.timestamp).toISOString(),
        })
        setLocationIssue(null)
        setLocationAgeNow(Date.now())
        setIncidentMode(false)
        setCheckingLocation(false)
      },
      (error) => {
        const issue = geolocationIssue(error)
        setLocationIssue(issue)
        setCheckingLocation(false)
        setCheckError(issueLabel(issue))
      },
      { enableHighAccuracy: true, timeout: 12000, maximumAge: 0 },
    )
  }

  async function runCheck() {
    if (!currentOccurrenceId) {
      setCheckError('No hay una ocurrencia de clase disponible para la fecha seleccionada.')
      return
    }
    setCheckBusy(true)
    setCheckError('')
    setCheck(null)
    try {
      const result = await api.checkAttendance({
        occurrenceId: currentOccurrenceId,
        location: effectiveLocation,
        locationIssue,
      })
      setCheck(result)
      if (!result.requiresJustification) {
        setReason('')
        setJustification('')
      }
    } catch (error) {
      setCheckError(error instanceof Error ? error.message : 'No se pudo comprobar la ubicación y el horario.')
    } finally {
      setCheckBusy(false)
    }
  }

  async function sendBody(body: AttendancePayload, key: string) {
    setSendBusy(true)
    setSendMessage('')
    try {
      const saved = await api.createAttendance(body, key)
      setRecord(saved)
      setAttempt(null)
      setSendMessage('El backend confirmó el registro y guardó su evidencia.')
      setReason('')
      setJustification('')
      setCheck(null)
    } catch (error) {
      const message = error instanceof Error ? error.message : 'No se pudo completar el envío.'
      const uncertain = !(error instanceof ApiError) || error.status >= 500 || error.status === 409
      setAttempt({ key, body, state: uncertain ? 'uncertain' : 'rejected', message })
      setSendMessage(uncertain
        ? 'No se recibió una confirmación. Consulta el resultado con la misma clave antes de volver a enviar.'
        : `El servidor rechazó el envío. Conservamos los datos y la clave para resolverlo: ${message}`)
    } finally {
      setSendBusy(false)
    }
  }

  async function registerAttendance() {
    if (!selectedClass?.occurrence || !check) return
    if (!attempt && !incidentMode && (!position || !config || locationIssue || !isFreshPosition(position.capturedAt, config.maxAgeSeconds, locationAgeNow))) {
      setCheckError('Actualiza la ubicación o elige explícitamente reportar una incidencia para continuar con revisión.')
      return
    }
    if (needsJustification && (!reason.trim() || !justification.trim())) {
      setCheckError('Selecciona un motivo y escribe una justificación antes de continuar.')
      return
    }
    if (attempt) {
      await sendBody(attempt.body, attempt.key)
      return
    }
    const body: AttendancePayload = {
      occurrenceId: selectedClass.occurrence.id,
      location: effectiveLocation,
      locationIssue,
      reason: needsJustification ? reason : null,
      justification: needsJustification ? justification.trim() : null,
    }
    await sendBody(body, idempotencyKey())
  }

  async function resolveAttempt() {
    if (!attempt) return
    setSendBusy(true)
    setSendMessage('Consultando el resultado de ese envío…')
    try {
      const found = await api.attendanceByKey(attempt.key)
      setRecord(found)
      setAttempt(null)
      setSendMessage('El servidor ya había recibido este registro. Se recuperó el resultado guardado.')
      setCheck(null)
    } catch (error) {
      if (error instanceof ApiError && error.status === 404) {
        setAttempt({ ...attempt, state: 'not-found', message: 'No existe un registro guardado con esta clave.' })
        setSendMessage('El servidor no encontró un registro con esta clave. Puedes reintentar con la misma clave e información.')
      } else {
        setSendMessage(error instanceof Error ? error.message : 'No se pudo consultar el resultado. Conservamos la clave.')
      }
    } finally {
      setSendBusy(false)
    }
  }

  function editAttempt() {
    setAttempt(null)
    setRecord(null)
    setSendMessage('La consulta confirmó que aún no existe un registro. Se generará otra clave al enviar la versión editada.')
  }

  function selectClass(id: string) {
    setSelectedClassId(id)
    setCheck(null)
    setCheckError('')
    setIncidentMode(false)
    setAttempt(null)
    setRecord(null)
    setSendMessage('')
    setReason('')
    setJustification('')
  }

  const locationAgeText = position
    ? `${Math.max(0, Math.floor((locationAgeNow - Date.parse(position.capturedAt)) / 1000))} s desde la lectura`
    : 'Aún no se ha solicitado la ubicación'
  const registerDisabled = !selectedClass?.occurrence || !check || checkBusy || sendBusy || (needsJustification && (!reason.trim() || !justification.trim())) || Boolean(attempt && attempt.state !== 'not-found' && attempt.state !== 'rejected')

  if (loading) return <Loading label="Cargando clases, campus y política de ubicación…" />

  return (
    <div className="teacher-page">
      <div className="page-heading">
        <div><span className="eyebrow">Área docente</span><h1>Registro de asistencia</h1><p>Hola, {identity.name}. Selecciona la ocurrencia de hoy y actualiza la ubicación cuando estés listo.</p></div>
        <div className="date-chip"><span className="date-icon">◷</span><span><small>Fecha local</small><strong>{new Intl.DateTimeFormat('es-MX', { dateStyle: 'full', timeZone: 'America/Mexico_City' }).format(new Date(`${today}T12:00:00`))}</strong></span></div>
      </div>

      {loadError && <Notice tone="error" title="No fue posible cargar el área docente">{loadError}<span className="inline-link"> Comprueba que el backend esté disponible y vuelve a cargar la página.</span></Notice>}
      {!loadError && <>
        {classes.length === 0 && <Notice tone="warning" title="No hay asignaciones para hoy">Puedes consultar el mapa. Para un registro ordinario, un administrador debe configurar una clase con horario y módulos permitidos.</Notice>}
        {geofences.length === 0 && <Notice tone="warning" title="Geocercas sin configurar">La falta de polígonos no aprobará una asistencia. El administrador puede configurar campus y módulos desde su panel.</Notice>}

        <div className="teacher-layout">
          <section className="panel map-panel">
            <div className="panel-header"><div><span className="eyebrow">Ubicación de referencia</span><h2>Mapa del campus</h2></div><div className="map-heading-controls">
              {selectedClass ? <span className="map-campus-context">{mapCampus?.name ?? 'Campus de la clase'}</span> : <select aria-label="Campus a consultar" value={viewCampusId} onChange={(event) => setViewCampusId(event.target.value)}><option value="">Selecciona campus</option>{campuses.map((campus) => <option key={campus.id} value={campus.id}>{campus.name}</option>)}</select>}
              <button className="button button-quiet button-small" onClick={() => setFitCampusKey((value) => value + 1)}>Ver campus completo</button>
            </div></div>
            <MapView geofences={mapGeofences} location={position} fitCampusKey={fitCampusKey} />
            <div className="map-caption"><span><i className="legend-campus" />Campus</span><span><i className="legend-module" />Módulos</span><span><i className="legend-position" />Posición del dispositivo</span></div>
            <div className="map-disclaimer">Las geocercas se definen desde administración. La ubicación del navegador puede variar y no identifica un salón o piso.</div>
          </section>

          <aside className="panel attendance-panel">
            <div className="panel-header compact"><div><span className="eyebrow">Paso 1 de 3</span><h2>Clase y horario</h2></div><span className="step-number">01</span></div>
            <label className="field-label" htmlFor="class-select">Clase de hoy</label>
            <select id="class-select" value={selectedClassId} onChange={(event) => selectClass(event.target.value)} disabled={Boolean(attempt)}>
              <option value="">Selecciona una clase</option>
              {classes.map((item) => <option key={item.id} value={item.id} disabled={!item.active || !item.occurrence}>{item.name}{item.occurrence ? ` · ${formatTime(item.startTime)}–${formatTime(item.endTime)}` : ' · sin ocurrencia hoy'}</option>)}
            </select>
            {selectedClass?.occurrence ? <div className="class-summary">
              <div className="summary-line"><span>Horario</span><strong>{formatTime(selectedClass.startTime)}–{formatTime(selectedClass.endTime)}</strong></div>
              <div className="summary-line"><span>Campus</span><strong>{selectedCampus?.name ?? 'No configurado'}</strong></div>
              <div className="summary-line summary-modules"><span>Módulos permitidos</span><strong>{allowedModules.length ? allowedModules.map((item) => item.name).join(', ') : 'Sin módulos asignados'}</strong></div>
            </div> : <p className="field-hint">{selectedClass ? 'Esta clase no tiene una ocurrencia para la fecha local de hoy.' : 'Elige una clase que tenga horario y ocurrencia.'}</p>}

            <div className="panel-divider" />
            <div className="panel-header compact panel-step"><div><span className="eyebrow">Paso 2 de 3</span><h2>Comprobar ubicación</h2></div><span className="step-number">02</span></div>
            <div className="location-card">
              <div className="location-icon" aria-hidden="true">⌖</div>
              <div className="location-details">
                <strong>{locationIssue ? 'Ubicación no disponible' : position ? formatCoordinates(position.latitude, position.longitude) : 'Sin ubicación todavía'}</strong>
                <span>{locationIssue ? issueLabel(locationIssue) : position ? `${Math.round(position.accuracyMeters)} m de precisión · ${locationAgeText}` : 'Se solicitará al presionar el botón.'}</span>
              </div>
              <span className={`location-status ${fresh && !locationIssue ? 'is-fresh' : ''}`} title={fresh ? 'Lectura reciente' : 'Actualiza la ubicación'} />
            </div>
            <button className="button button-secondary button-full" onClick={refreshLocation} disabled={checkingLocation || Boolean(attempt)}>
              {checkingLocation ? <><span className="spinner small" /> Solicitando posición…</> : 'Actualizar ubicación'}
            </button>
            <div className="precision-note">Política de demostración: máximo {config?.maxAccuracyMeters ?? '—'} m de precisión y {config?.maxAgeSeconds ?? '—'} s de antigüedad. Puedes continuar con una justificación si no se obtiene una lectura fiable.</div>
            <button className="button button-quiet button-full check-button" onClick={() => void runCheck()} disabled={!currentOccurrenceId || checkBusy || Boolean(attempt)}>
              {checkBusy ? 'Consultando al servidor…' : 'Comprobar ubicación y horario'}
            </button>

            {(!fresh || locationIssue || check?.spatialResult === 'NO_LOCATION' || check?.spatialResult === 'STALE' || check?.spatialResult === 'INACCURATE' || check?.spatialResult === 'UNCERTAIN') && <button className={`button ${incidentMode ? 'button-secondary' : 'button-warning'} button-full incident-button`} onClick={() => { setIncidentMode(true); if (!check) void runCheck(); }} disabled={Boolean(attempt) || checkBusy}>
              {incidentMode ? 'Incidencia seleccionada · pendiente de revisión' : 'Reportar incidencia de ubicación'}
            </button>}
            {incidentMode && <p className="field-hint">Enviarás una excepción con los datos disponibles; el resultado quedará pendiente de revisión y no se inventarán coordenadas.</p>}

            {(check || checkError) && <div className="check-result" aria-live="polite">
              {checkError && <Notice tone="error">{checkError}</Notice>}
              {check && <>
                <span className="eyebrow">Resultado calculado por el servidor</span>
                <SpatialPill result={check.spatialResult} />
                <div className="check-detail"><span>{temporalLabel(check.temporalResult)}</span><span>{check.status === 'CONFIRMED' ? 'Podría registrarse como confirmada' : 'Se enviará para revisión'}</span></div>
                <p>{check.message}</p>
              </>}
            </div>}

            {needsJustification && <div className="justification-block">
              <div className="justification-heading"><span className="step-number mini">03</span><div><strong>Justificación requerida</strong><small>El registro quedará pendiente de revisión.</small></div></div>
              <label className="field-label" htmlFor="reason">Motivo</label>
              <select id="reason" value={reason} onChange={(event) => setReason(event.target.value)} disabled={Boolean(attempt)}>
                <option value="">Selecciona un motivo</option>
                {reasonOptions.map((option) => <option key={option} value={option}>{option}</option>)}
              </select>
              <label className="field-label" htmlFor="justification">Descripción</label>
              <textarea id="justification" rows={3} maxLength={1000} placeholder="Explica brevemente la situación para revisión." value={justification} onChange={(event) => setJustification(event.target.value)} disabled={Boolean(attempt)} />
              <span className="field-hint">Esta explicación no equivale a una aprobación administrativa.</span>
            </div>}

            {attempt && <Notice tone={attempt.state === 'uncertain' ? 'warning' : 'error'} title={attempt.state === 'uncertain' ? 'Envío sin confirmación' : 'Envío sin registro confirmado'}>
              <p>{attempt.message}</p>
              <div className="attempt-key">Clave: <code>{attempt.key}</code></div>
              {attempt.state === 'uncertain' && <button className="button button-quiet button-small" onClick={() => void resolveAttempt()} disabled={sendBusy}>Consultar resultado</button>}
              {attempt.state === 'not-found' && <div className="button-row">
                <button className="button button-secondary button-small" onClick={() => void registerAttendance()} disabled={sendBusy}>{sendBusy ? 'Enviando…' : 'Reintentar con la misma clave'}</button>
                <button className="button button-quiet button-small" onClick={editAttempt}>Modificar datos</button>
              </div>}
              {attempt.state === 'rejected' && <div className="button-row">
                <button className="button button-secondary button-small" onClick={() => void registerAttendance()} disabled={sendBusy}>Reintentar con la misma clave</button>
                <button className="button button-quiet button-small" onClick={editAttempt}>Editar y crear otro intento</button>
              </div>}
            </Notice>}
            {sendMessage && !attempt && <Notice tone={record ? (record.status === 'CONFIRMED' ? 'success' : 'warning') : 'info'} title={record ? (record.status === 'CONFIRMED' ? 'Asistencia registrada' : 'Registro pendiente de revisión') : undefined}>
              {sendMessage}{record && <div className="record-result"><StatusPill status={record.status} /><span>Recibido {formatDateTime(record.receivedAt)}</span></div>}
            </Notice>}
            <button className="button button-primary button-full register-button" onClick={() => void registerAttendance()} disabled={registerDisabled}>
              {sendBusy ? 'Guardando registro…' : needsJustification ? 'Enviar para revisión' : 'Registrar asistencia'}
              <span aria-hidden="true">→</span>
            </button>
            {!check && <span className="field-hint centered">Primero actualiza la ubicación y comprueba el resultado en el servidor.</span>}
            {check && !fresh && !locationIssue && !incidentMode && <span className="field-hint centered">La ubicación debe seguir siendo reciente al enviar; actualízala y vuelve a comprobar, o reporta una incidencia.</span>}
          </aside>
        </div>
      </>}
    </div>
  )
}
