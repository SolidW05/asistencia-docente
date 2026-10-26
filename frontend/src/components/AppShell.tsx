import type { ReactNode } from 'react'
import { useState } from 'react'
import { NavLink } from 'react-router-dom'
import type { Identity } from '../types'
import { Notice } from './Notice'

interface AppShellProps {
  identity: Identity
  onLogout: () => Promise<void>
  children: ReactNode
}

export function AppShell({ identity, onLogout, children }: AppShellProps) {
  const [logoutError, setLogoutError] = useState('')
  async function logout() {
    setLogoutError('')
    try { await onLogout() }
    catch (error) { setLogoutError(error instanceof Error ? error.message : 'No se pudo cerrar la sesión.') }
  }
  return (
    <div className="app-frame">
      <header className="topbar">
        <NavLink className="brand" to={identity.role !== 'TEACHER' ? '/admin' : '/docente'} aria-label="Inicio">
          <span className="brand-mark">A</span>
          <span><strong>Asistencia docente</strong><small>Prototipo académico · CUTonalá</small></span>
        </NavLink>
        <nav className="topnav" aria-label="Navegación principal">
          {identity.role !== 'TEACHER' ? <>
            <NavLink to="/admin">Administración</NavLink>
            <NavLink to="/revision">Asistencias</NavLink>
            {identity.role === 'ADMIN' && <><NavLink to="/jefes">Jefes</NavLink><NavLink to="/cadena">Cadena</NavLink></>}
          </> : <>
            <NavLink to="/docente">Registro</NavLink>
            <NavLink to="/historial">Mis asistencias</NavLink>
          </>}
        </nav>
        <div className="account-menu">
          <div className="account-copy"><strong>{identity.name}</strong><small>{identity.code} · {identity.role === 'ADMIN' ? 'Administrador' : identity.role === 'JEFE' ? 'Jefe' : 'Docente'}</small></div>
          <button className="button button-quiet button-small" onClick={() => void logout()}>Cerrar sesión</button>
        </div>
      </header>
      <main className="page-content">{logoutError && <Notice tone="error">{logoutError}</Notice>}{children}</main>
      <footer className="app-footer">
        <span>Autenticación institucional simulada</span>
        <span>La posición del dispositivo es una referencia y no identifica un aula o piso.</span>
      </footer>
    </div>
  )
}
