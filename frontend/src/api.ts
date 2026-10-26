import type {
  ApiErrorBody,
  AppConfig,
  AdminAttendanceRecord,
  AttendanceHistoryEntry,
  AttendanceCheck,
  AttendancePayload,
  AttendanceRecord,
  Campus,
  Chain,
  ChainVerification,
  ClassAssignment,
  Geofence,
  Identity,
  TeacherOption,
} from './types'

const API = '/api'

export class ApiError extends Error {
  status: number
  code: string

  constructor(status: number, body: ApiErrorBody) {
    super(body.message || 'No se pudo completar la solicitud.')
    this.name = 'ApiError'
    this.status = status
    this.code = body.code || 'REQUEST_FAILED'
  }
}

interface CsrfState {
  token: string
  headerName: string
}

let csrfState: CsrfState | null = null

async function readError(response: Response): Promise<ApiError> {
  let body: ApiErrorBody = { code: `HTTP_${response.status}`, message: 'No se pudo completar la solicitud.' }
  try {
    const parsed = (await response.json()) as Partial<ApiErrorBody>
    body = { code: parsed.code ?? body.code, message: parsed.message ?? body.message }
  } catch {
    if (!response.ok) body.message = response.statusText || body.message
  }
  return new ApiError(response.status, body)
}

export async function refreshCsrf(): Promise<void> {
  const response = await fetch(`${API}/auth/csrf`, { credentials: 'same-origin' })
  if (!response.ok) throw await readError(response)
  csrfState = (await response.json()) as CsrfState
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const method = (init.method ?? 'GET').toUpperCase()
  const headers = new Headers(init.headers)
  if (init.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    if (!csrfState) await refreshCsrf()
    if (csrfState) headers.set(csrfState.headerName, csrfState.token)
  }
  const response = await fetch(`${API}${path}`, { ...init, method, headers, credentials: 'same-origin' })
  if (!response.ok) throw await readError(response)
  if (response.status === 204) return undefined as T
  return (await response.json()) as T
}

export const api = {
  async login(code: string, password: string): Promise<Identity> {
    const identity = await request<Identity>('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ code, password }),
    })
    // El login ya fue confirmado; si falla renovar CSRF, se reintenta antes de la siguiente escritura.
    csrfState = null
    try { await refreshCsrf() } catch { csrfState = null }
    return identity
  },
  async me(): Promise<Identity> {
    return request('/auth/me')
  },
  async logout(): Promise<void> {
    await request<void>('/auth/logout', { method: 'POST' })
    csrfState = null
    try { await refreshCsrf() } catch { csrfState = null }
  },
  config: () => request<AppConfig>('/config'),
  campuses: () => request<Campus[]>('/campuses'),
  geofences: () => request<Geofence[]>('/geofences'),
  classesForDate: (date: string) => request<ClassAssignment[]>(`/classes?date=${encodeURIComponent(date)}`),
  checkAttendance: (body: Pick<AttendancePayload, 'occurrenceId' | 'location' | 'locationIssue'>) =>
    request<AttendanceCheck>('/attendance/check', { method: 'POST', body: JSON.stringify(body) }),
  createAttendance: (body: AttendancePayload, key: string) =>
    request<AttendanceRecord>('/attendance', {
      method: 'POST',
      headers: { 'Idempotency-Key': key },
      body: JSON.stringify(body),
    }),
  attendanceByKey: (key: string) => request<AttendanceRecord>(`/attendance/requests/${encodeURIComponent(key)}`),
  attendance: () => request<AttendanceRecord[]>('/attendance'),
  attendanceHistory: (id: string) => request<AttendanceHistoryEntry[]>(`/attendance/${id}/history`),
  adminAttendance: () => request<AdminAttendanceRecord[]>('/admin/attendance'),
  reviewAttendance: (id: string, comment: string) => request<AttendanceRecord>(`/admin/attendance/${id}/review`, { method: 'POST', body: JSON.stringify({ status: 'CONFIRMED', comment }) }),
  createHead: (code: string) => request<TeacherOption>('/admin/teachers', { method: 'POST', body: JSON.stringify({ code }) }),
  chain: () => request<Chain>('/chain'),
  verifyChain: () => request<ChainVerification>('/chain/verify'),
  adminTeachers: () => request<TeacherOption[]>('/admin/teachers'),
  adminClasses: () => request<ClassAssignment[]>('/admin/classes'),
  saveCampus: (id: string | null, body: Pick<Campus, 'name' | 'active'>) =>
    request<Campus>(id ? `/admin/campuses/${id}` : '/admin/campuses', {
      method: id ? 'PUT' : 'POST', body: JSON.stringify(body),
    }),
  saveGeofence: (id: string | null, body: Omit<Geofence, 'id' | 'version'> & { version?: number }) =>
    request<Geofence>(id ? `/admin/geofences/${id}` : '/admin/geofences', {
      method: id ? 'PUT' : 'POST', body: JSON.stringify(body),
    }),
  saveClass: (id: string | null, body: Omit<ClassAssignment, 'id' | 'version' | 'occurrence' | 'teacherId'> & { teacherId?: string; teacherCode?: string; version?: number }) =>
    request<ClassAssignment>(id ? `/admin/classes/${id}` : '/admin/classes', {
      method: id ? 'PUT' : 'POST', body: JSON.stringify(body),
    }),
}
