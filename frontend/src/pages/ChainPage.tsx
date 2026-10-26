import { useCallback, useEffect, useState } from 'react'
import type { Chain, ChainVerification } from '../types'
import { api } from '../api'
import { formatDateTime, formatHash } from '../format'
import { Notice } from '../components/Notice'
import { Loading } from '../components/Loading'

export function ChainPage() {
  const [chain, setChain] = useState<Chain | null>(null)
  const [verification, setVerification] = useState<ChainVerification | null>(null)
  const [loading, setLoading] = useState(true)
  const [verifying, setVerifying] = useState(false)
  const [error, setError] = useState('')
  const movements = chain?.blocks.filter((block) => block.eventType !== 'GENESIS') ?? []

  const reload = useCallback(async (includeVerification = true) => {
    setError('')
    try {
      const nextChain = await api.chain()
      setChain(nextChain)
      if (includeVerification) setVerification(await api.verifyChain())
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'No se pudo cargar el historial y la cadena.')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { void reload() }, [reload])

  async function verify() {
    setVerifying(true)
    setError('')
    try { setVerification(await api.verifyChain()) }
    catch (cause) { setError(cause instanceof Error ? cause.message : 'No se pudo verificar la cadena.') }
    finally { setVerifying(false) }
  }

  if (loading) return <Loading label="Cargando historial y cadena…" />

  return (
    <div className="history-page">
      <div className="page-heading">
        <div><span className="eyebrow">Administración principal</span><h1>Cadena global</h1><p>Consulta todos los bloques y verifica la integridad de los registros y sus cambios.</p></div>
        <button className="button button-secondary" onClick={() => void reload()} disabled={verifying}>Actualizar cadena</button>
      </div>
      {error && <Notice tone="error" title="No se pudo completar la consulta">{error}</Notice>}

      <section className="chain-overview">
        <div className="chain-overview-copy"><span className="eyebrow light">Cadena global · {chain?.chainId ?? 'GLOBAL_ATTENDANCE'}</span><h2>Integridad verificable</h2><p>Cada registro y cada cambio de estado agrega un bloque. Esta vista muestra todos los eventos y sus datos.</p></div>
        <div className="chain-metric"><small>Bloques globales</small><strong>{chain?.totalBlocks ?? '—'}</strong></div>
        <div className="chain-metric"><small>Movimientos</small><strong>{chain ? movements.length : '—'}</strong></div>
        <div className="chain-metric"><small>Último índice global</small><strong>{chain?.lastIndex ?? '—'}</strong></div>
        <div className="chain-metric hash-metric"><small>Hash de cabecera global</small><code title={chain?.lastHash}>{chain?.lastHash ?? '—'}</code></div>
      </section>

      {chain && <section className="genesis-card" aria-label="Bloque génesis global">
        <div className="genesis-copy"><span>Bloque génesis compartido · índice 0</span><strong>{chain.genesisPhrase}</strong></div>
        <details className="genesis-hash"><summary>Ver hash SHA-256 completo</summary><code>{chain.genesisHash}</code></details>
      </section>}

      {verification && <section className="verification-card">
        <span className={`verification-icon ${verification.valid ? 'valid' : 'invalid'}`}>{verification.valid ? '✓' : '!'}</span>
        <div className="verification-copy"><strong>{verification.valid ? 'Cadena global íntegra' : 'Se encontró una inconsistencia'}</strong><span>{verification.checkedBlocks} bloques globales revisados{verification.firstError ? ` · ${verification.firstError}` : ' · hashes, enlaces, índices y registros coinciden'}</span></div>
        <button className="button button-quiet button-small" onClick={() => void verify()} disabled={verifying}>{verifying ? 'Verificando…' : 'Verificar de nuevo'}</button>
      </section>}

      <section className="panel history-panel">
        <div className="panel-header"><div><span className="eyebrow">Evidencia por evento</span><h2>Cadena global</h2></div><span className="count-badge">{chain?.totalBlocks ?? 0}</span></div>
        <p className="chain-visibility-note">Acceso exclusivo de ADMIN. Incluye el génesis, los registros y las revisiones de todos los docentes.</p>
        {chain && movements.length === 0 && <div className="empty-state own-empty-state"><span className="empty-icon">◌</span><strong>Sin movimientos de asistencia</strong><p>El génesis existe desde la inicialización. Un registro o una revisión agrega un nuevo movimiento.</p></div>}
        {!chain?.blocks.length ? <div className="empty-state"><span className="empty-icon">◌</span><strong>No hay bloques disponibles</strong><p>No se pudo cargar evidencia de la cadena global.</p></div> : <div className="block-list">
          {chain.blocks.map((block) => <details key={block.id} className="block-card">
            <summary>
              <span className="block-index">#{block.index}</span>
              <span className="block-main"><strong>{block.eventType === 'GENESIS' ? 'GENESIS · bloque compartido' : block.eventType}</strong><small>{block.eventType === 'GENESIS' ? `${block.serverAt ? formatDateTime(block.serverAt) : 'Génesis preexistente'} · bloque inicial global` : `${block.serverAt ? formatDateTime(block.serverAt) : 'Fecha no disponible'}${block.attendanceId ? ` · asistencia ${block.attendanceId.slice(0, 8)}` : ''}`}</small></span>
              <span className="block-hash"><small>SHA-256</small><code>{formatHash(block.hash)}</code></span>
              <span className="details-chevron" aria-hidden="true">⌄</span>
            </summary>
            <div className="block-expanded">
              <div className="hash-pair"><div><small>Hash anterior</small><code>{block.previousHash}</code></div><div><small>Hash actual</small><code>{block.hash}</code></div></div>
              <div className="block-meta"><span>Versión de formato <strong>{block.formatVersion}</strong></span><span>ID de bloque <code>{block.id}</code></span>{block.attendanceId && <span>ID de asistencia <code>{block.attendanceId}</code></span>}</div>
              <div className="payload-title"><strong>Payload canónico</strong><small>Vista de inspección entregada por el backend</small></div>
              <pre className="payload-view">{typeof block.payload === 'string' ? block.payload : JSON.stringify(block.payload, null, 2)}</pre>
            </div>
          </details>)}
        </div>}
      </section>

      <Notice tone="info" title="Alcance de la cadena">Es un encadenamiento criptográfico centralizado para demostración académica. Un administrador con control total de la base de datos podría reescribir datos y recalcular la cadena. El hash no demuestra que la ubicación reportada sea verdadera.</Notice>
    </div>
  )
}
