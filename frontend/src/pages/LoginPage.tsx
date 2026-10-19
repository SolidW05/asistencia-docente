import { useState, type FormEvent } from 'react'
import { ApiError } from '../api'
import { ErrorText } from '../components/Notice'

interface LoginPageProps {
  onLogin: (code: string, password: string) => Promise<void>
  sessionError: string
}

export function LoginPage({ onLogin, sessionError }: LoginPageProps) {
  const [code, setCode] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!/^\d{7}$/.test(code)) {
      setError('El código docente debe contener exactamente siete dígitos.')
      return
    }
    const submittedPassword = password
    setPassword('')
    setError('')
    setSubmitting(true)
    try {
      await onLogin(code, submittedPassword)
    } catch (cause) {
      const message = cause instanceof ApiError ? cause.message : cause instanceof Error ? cause.message : 'No se pudo iniciar sesión.'
      setError(message)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="login-screen">
      <section className="login-art" aria-label="Asistencia docente">
        <div className="login-art-inner">
          <span className="eyebrow light">CUTonalá · prototipo académico</span>
          <div className="login-emblem">A</div>
          <h1>Asistencia desde el lugar donde das clase.</h1>
          <p>Consulta la geocerca de tu módulo, valida el horario y conserva un historial verificable de tus registros.</p>
          <div className="login-art-note"><span className="status-dot" />La ubicación solo se solicita cuando tú la actualizas.</div>
          <div className="art-contours" aria-hidden="true"><i /><i /><i /><i /></div>
        </div>
      </section>
      <section className="login-panel">
        <div className="login-card">
          <span className="eyebrow">Acceso de demostración</span>
          <h2>Bienvenido</h2>
          <p className="muted">Usa un código de siete dígitos. La contraseña es un campo de demostración.</p>
          <div className="simulation-banner"><span aria-hidden="true">◉</span><span><strong>Autenticación institucional simulada</strong><small>Este prototipo no se conecta a la plataforma universitaria.</small></span></div>
          {(sessionError || error) && <div className="notice notice-error" role="alert">{error || sessionError}</div>}
          <form onSubmit={submit} className="login-form">
            <label className="field-label" htmlFor="teacher-code">Código docente</label>
            <input id="teacher-code" autoComplete="username" inputMode="numeric" maxLength={7} pattern="[0-9]{7}" placeholder="0000000" value={code} onChange={(event) => setCode(event.target.value.replace(/\D/g, '').slice(0, 7))} required />
            <ErrorText>{code && code.length !== 7 ? 'Escribe los siete dígitos del código.' : undefined}</ErrorText>
            <label className="field-label" htmlFor="password">Contraseña de demostración</label>
            <input id="password" type="password" autoComplete="current-password" value={password} onChange={(event) => setPassword(event.target.value)} required />
            <button className="button button-primary button-full" type="submit" disabled={submitting}>{submitting ? 'Ingresando…' : 'Iniciar sesión'} <span aria-hidden="true">→</span></button>
          </form>
        </div>
        <p className="login-legal">Prototipo para evaluación interna. Sin datos precargados; la identidad se crea al iniciar sesión.</p>
      </section>
    </main>
  )
}
