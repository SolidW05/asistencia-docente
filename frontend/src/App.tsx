import { lazy, Suspense, useEffect, useState } from 'react'
import { Navigate, Route, Routes, useNavigate } from 'react-router-dom'
import { api, ApiError } from './api'
import type { Identity } from './types'
import { AppShell } from './components/AppShell'
import { Loading } from './components/Loading'

const LoginPage = lazy(() => import('./pages/LoginPage').then((module) => ({ default: module.LoginPage })))
const TeacherPage = lazy(() => import('./pages/TeacherPage').then((module) => ({ default: module.TeacherPage })))
const AdminPage = lazy(() => import('./pages/AdminPage').then((module) => ({ default: module.AdminPage })))
const HistoryPage = lazy(() => import('./pages/HistoryPage').then((module) => ({ default: module.HistoryPage })))

export function App() {
  const [identity, setIdentity] = useState<Identity | null>(null)
  const [checkingSession, setCheckingSession] = useState(true)
  const [sessionError, setSessionError] = useState('')
  const navigate = useNavigate()

  useEffect(() => {
    let alive = true
    api.me()
      .then((value) => { if (alive) setIdentity(value) })
      .catch((error: unknown) => {
        if (!alive) return
        if (!(error instanceof ApiError && error.status === 401)) {
          setSessionError(error instanceof Error ? error.message : 'No se pudo consultar la sesión.')
        }
      })
      .finally(() => { if (alive) setCheckingSession(false) })
    return () => { alive = false }
  }, [])

  async function handleLogin(code: string, password: string): Promise<void> {
    const nextIdentity = await api.login(code, password)
    setIdentity(nextIdentity)
    setSessionError('')
    navigate(nextIdentity.role === 'ADMIN' ? '/admin' : '/docente', { replace: true })
  }

  async function handleLogout(): Promise<void> {
    await api.logout()
    setIdentity(null)
    navigate('/login', { replace: true })
  }

  if (checkingSession) {
    return <main className="splash" aria-live="polite"><span className="spinner" /> Comprobando sesión segura…</main>
  }

  return (
    <Suspense fallback={<Loading label="Abriendo tu espacio…" />}>
    <Routes>
      <Route path="/login" element={identity ? <Navigate to={identity.role === 'ADMIN' ? '/admin' : '/docente'} replace /> : <LoginPage onLogin={handleLogin} sessionError={sessionError} />} />
      <Route path="/" element={<Navigate to={identity ? (identity.role === 'ADMIN' ? '/admin' : '/docente') : '/login'} replace />} />
      <Route path="/docente" element={identity?.role === 'TEACHER' ? <AppShell identity={identity} onLogout={handleLogout}><TeacherPage identity={identity} /></AppShell> : <Navigate to={identity ? '/admin' : '/login'} replace />} />
      <Route path="/historial" element={identity?.role === 'TEACHER' ? <AppShell identity={identity} onLogout={handleLogout}><HistoryPage identity={identity} /></AppShell> : <Navigate to={identity ? '/admin' : '/login'} replace />} />
      <Route path="/admin" element={identity?.role === 'ADMIN' ? <AppShell identity={identity} onLogout={handleLogout}><AdminPage /></AppShell> : <Navigate to={identity ? '/docente' : '/login'} replace />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
    </Suspense>
  )
}
