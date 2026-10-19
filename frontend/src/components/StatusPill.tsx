import type { AttendanceStatus, SpatialResult } from '../types'
import { spatialLabel, statusLabel } from '../format'

export function StatusPill({ status }: { status: AttendanceStatus }) {
  return <span className={`pill ${status === 'CONFIRMED' ? 'pill-success' : 'pill-pending'}`}>{statusLabel(status)}</span>
}

export function SpatialPill({ result }: { result: SpatialResult }) {
  const success = result === 'ALLOWED_MODULE'
  const warning = ['OUTSIDE_CAMPUS', 'OTHER_CAMPUS_SPACE', 'NO_LOCATION', 'STALE', 'INACCURATE', 'UNCERTAIN', 'UNCONFIGURED'].includes(result)
  return <span className={`pill ${success ? 'pill-success' : warning ? 'pill-pending' : ''}`}>{spatialLabel(result)}</span>
}
