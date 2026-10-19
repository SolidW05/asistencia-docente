import type { ReactNode } from 'react'

export function Notice({ tone = 'info', title, children }: { tone?: 'info' | 'success' | 'warning' | 'error'; title?: string; children: ReactNode }) {
  return <div className={`notice notice-${tone}`} role={tone === 'error' ? 'alert' : 'status'}>
    {title && <strong>{title}</strong>}
    <div>{children}</div>
  </div>
}

export function ErrorText({ children }: { children?: ReactNode }) {
  if (!children) return null
  return <p className="field-error" role="alert">{children}</p>
}
