import type { AttendanceStatus, LocationIssue, SpatialResult, TemporalResult } from './types'

export function mexicoDate(now = new Date()): string {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'America/Mexico_City', year: 'numeric', month: '2-digit', day: '2-digit',
  }).formatToParts(now)
  const values = Object.fromEntries(parts.map((part) => [part.type, part.value]))
  return `${values.year}-${values.month}-${values.day}`
}

export function formatDateTime(value: string, timeZone = 'America/Mexico_City'): string {
  return new Intl.DateTimeFormat('es-MX', {
    dateStyle: 'medium', timeStyle: 'short', timeZone,
  }).format(new Date(value))
}

export function formatTime(value: string): string {
  return value.slice(0, 5)
}

export function spatialLabel(value: SpatialResult): string {
  const labels: Record<SpatialResult, string> = {
    ALLOWED_MODULE: 'Dentro de un módulo permitido',
    OTHER_CAMPUS_SPACE: 'Dentro del campus, fuera del módulo permitido',
    OUTSIDE_CAMPUS: 'Fuera del campus',
    NO_LOCATION: 'Sin ubicación',
    STALE: 'Ubicación antigua',
    INACCURATE: 'Precisión insuficiente',
    UNCERTAIN: 'Posición incierta cerca de un límite',
    UNCONFIGURED: 'Falta configurar la geocerca o asignación',
  }
  return labels[value]
}

export function temporalLabel(value: TemporalResult): string {
  return value === 'IN_WINDOW' ? 'Dentro de la ventana de registro' : 'Fuera de la ventana de registro'
}

export function statusLabel(value: AttendanceStatus): string {
  return value === 'CONFIRMED' ? 'Registrada' : 'Pendiente de revisión'
}

export function issueLabel(value: LocationIssue | null): string {
  if (value === 'PERMISSION_DENIED') return 'El permiso de ubicación fue denegado.'
  if (value === 'TIMEOUT') return 'El dispositivo tardó demasiado en responder.'
  if (value === 'UNAVAILABLE') return 'El dispositivo no pudo obtener una ubicación.'
  return 'No hay coordenadas disponibles.'
}

export function isFreshPosition(capturedAt: string, maxAgeSeconds: number, now = Date.now()): boolean {
  const age = now - Date.parse(capturedAt)
  return Number.isFinite(age) && age >= 0 && age <= maxAgeSeconds * 1000
}

export function formatCoordinates(latitude: number, longitude: number): string {
  return `${latitude.toFixed(5)}, ${longitude.toFixed(5)}`
}

export function formatHash(hash: string): string {
  if (!hash || hash.length <= 24) return hash
  return `${hash.slice(0, 12)}…${hash.slice(-10)}`
}
