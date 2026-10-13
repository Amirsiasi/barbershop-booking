import { useEffect } from 'react'

/** Side panel (drawer) used in the admin area. Closes with Escape or a click on the backdrop. */
export default function Panel({ title, onClose, children }) {
  useEffect(() => {
    const onKey = (e) => e.key === 'Escape' && onClose()
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  return (
    <div className="panel-backdrop" onClick={onClose}>
      <div className="panel" role="dialog" aria-modal="true" aria-label={title} onClick={(e) => e.stopPropagation()}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'start', gap: 12 }}>
          <h2>{title}</h2>
          <button className="icon-btn" onClick={onClose} aria-label="Schließen">×</button>
        </div>
        {children}
      </div>
    </div>
  )
}
