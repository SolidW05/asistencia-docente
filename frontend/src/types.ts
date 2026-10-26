export type Role = 'TEACHER' | 'JEFE' | 'ADMIN'

export interface Identity {
  id: string
  code: string
  name: string
  role: Role
}

export interface AppConfig {
  maxAccuracyMeters: number
  maxAgeSeconds: number
  earlyMinutes: number
  lateMinutes: number
  timeZone: string
}

export interface Campus {
  id: string
  name: string
  active: boolean
}

export interface PolygonGeometry {
  type: 'Polygon'
  coordinates: number[][][]
}

export interface Geofence {
  id: string
  campusId: string
  name: string
  type: 'CAMPUS' | 'MODULE'
  parentId: string | null
  geometry: PolygonGeometry
  active: boolean
  version: number
}

export interface Occurrence {
  id: string
  date: string
  startAt: string
  endAt: string
}

export interface ClassAssignment {
  id: string
  name: string
  teacherId: string
  campusId: string
  allowedModuleIds: string[]
  dayOfWeek: number
  startTime: string
  endTime: string
  active: boolean
  version: number
  occurrence: Occurrence | null
}

export interface TeacherOption {
  id: string
  code: string
  name: string
  role: Role
}

export interface Position {
  latitude: number
  longitude: number
  accuracyMeters: number
  capturedAt: string
}

export type LocationIssue = 'PERMISSION_DENIED' | 'TIMEOUT' | 'UNAVAILABLE'

export type SpatialResult =
  | 'ALLOWED_MODULE'
  | 'OTHER_CAMPUS_SPACE'
  | 'OUTSIDE_CAMPUS'
  | 'NO_LOCATION'
  | 'STALE'
  | 'INACCURATE'
  | 'UNCERTAIN'
  | 'UNCONFIGURED'

export type TemporalResult = 'IN_WINDOW' | 'OUTSIDE_WINDOW'
export type AttendanceStatus = 'CONFIRMED' | 'PENDING_REVIEW'

export interface AttendanceCheck {
  spatialResult: SpatialResult
  temporalResult: TemporalResult
  status: AttendanceStatus
  requiresJustification: boolean
  message: string
}

export interface AttendanceRecord {
  id: string
  className: string
  occurrenceId: string
  receivedAt: string
  status: AttendanceStatus
  spatialResult: SpatialResult
  temporalResult: TemporalResult
  reason: string | null
  justification: string | null
  location: Position | null
}

export interface AttendancePayload {
  occurrenceId: string
  location: Position | null
  locationIssue: LocationIssue | null
  reason: string | null
  justification: string | null
}

export interface ChainBlock {
  id: string
  index: number
  eventType: string
  teacherId: string | null
  attendanceId: string | null
  serverAt: string | null
  payload: Record<string, unknown> | string
  previousHash: string
  hash: string
  formatVersion: number
}

export interface Chain {
  chainId: string
  teacherId: string
  lastIndex: number
  lastHash: string
  totalBlocks: number
  genesisPhrase: string
  genesisHash: string
  blocks: ChainBlock[]
}

export interface ChainVerification {
  valid: boolean
  checkedBlocks: number
  firstError: string | null
}

export interface ApiErrorBody {
  code: string
  message: string
}

export interface AttendanceHistoryEntry {
  id: string
  previousStatus: AttendanceStatus | null
  status: AttendanceStatus
  changedAt: string
  reviewerName: string | null
  comment: string | null
}

export interface AdminAttendanceRecord extends AttendanceRecord {
  teacherName: string
  teacherCode: string
  classId: string
}
