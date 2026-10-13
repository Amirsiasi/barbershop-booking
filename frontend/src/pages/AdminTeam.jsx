import { useEffect, useState } from 'react'
import { api } from '../lib/api.js'
import Panel from '../components/Panel.jsx'

/** Admin area "Team": add, edit, remove and re-activate barbers. */
export default function AdminTeam({ auth, onChange }) {
  const [team, setTeam] = useState(null)
  const [error, setError] = useState(null)
  const [notice, setNotice] = useState(null)
  const [editing, setEditing] = useState(null) // a barber, or {} for a new one
  const [confirmRemove, setConfirmRemove] = useState(null)
  const [busy, setBusy] = useState(false)

  const load = () => api.admin.barbers(auth).then(setTeam).catch((e) => setError(e.message))
  useEffect(() => { load() }, []) // eslint-disable-line react-hooks/exhaustive-deps

  const done = (text) => {
    setError(null)
    setNotice(text)
    setTimeout(() => setNotice(null), 5000)
    load()
    onChange?.() // calendar columns and booking page change with the team
  }

  const save = async (form, id) => {
    const saved = id ? await api.admin.updateBarber(auth, id, form) : await api.admin.createBarber(auth, form)
    setEditing(null)
    done(id ? `${saved.name} gespeichert.` : `${saved.name} ist jetzt im Team und online buchbar.`)
  }

  const remove = async (b) => {
    setBusy(true); setError(null)
    try {
      const r = await api.admin.removeBarber(auth, b.id)
      setConfirmRemove(null)
      done(r.message)
    } catch (e) {
      setConfirmRemove(null)
      setNotice(null)
      setError(e.message)
    } finally {
      setBusy(false)
    }
  }

  const reactivate = async (b) => {
    setError(null)
    try {
      await api.admin.updateBarber(auth, b.id, { name: b.name, bio: b.bio, active: true, sortOrder: b.sortOrder })
      done(`${b.name} ist wieder im Team.`)
    } catch (e) {
      setError(e.message)
    }
  }

  const active = team?.filter((b) => b.active) || []
  const former = team?.filter((b) => !b.active) || []

  return (
    <main className="wrap" style={{ paddingBottom: 48 }}>
      <div className="daynav">
        <h1>Team</h1>
        <button className="btn" onClick={() => setEditing({})}>Barber hinzufügen</button>
      </div>
      <p className="section-sub" style={{ marginTop: -6 }}>
        Wer hier aktiv ist, kann online und per WhatsApp gebucht werden und hat eine Spalte im Kalender.
      </p>

      {notice && <p className="alert alert--ok" role="status">{notice}</p>}
      {error && <p className="alert" role="alert">{error}</p>}
      {!team && !error && <div className="skeleton" style={{ height: 200 }} />}

      <ul className="team">
        {active.map((b) => (
          <li key={b.id} className="team-card">
            <span className="avatar" aria-hidden="true">{b.name[0]}</span>
            <div className="team-card__text">
              <strong>{b.name}</strong>
              {b.bio && <span>{b.bio}</span>}
              <span className="team-card__meta">
                {b.upcomingAppointments === 0 ? 'Keine kommenden Termine'
                  : `${b.upcomingAppointments} ${b.upcomingAppointments === 1 ? 'kommender Termin' : 'kommende Termine'}`}
              </span>
            </div>
            {confirmRemove === b.id ? (
              <div className="team-card__confirm">
                <span>{b.name} wirklich entfernen?</span>
                <button className="btn btn--danger" disabled={busy} onClick={() => remove(b)}>Ja, entfernen</button>
                <button className="btn btn--ghost" onClick={() => setConfirmRemove(null)}>Abbrechen</button>
              </div>
            ) : (
              <div className="team-card__actions">
                <button className="btn btn--ghost" onClick={() => setEditing(b)}>Bearbeiten<span className="sr-only"> {b.name}</span></button>
                <button className="btn btn--ghost btn--danger-text" onClick={() => setConfirmRemove(b.id)}>
                  Entfernen<span className="sr-only"> {b.name}</span>
                </button>
              </div>
            )}
          </li>
        ))}
      </ul>

      {former.length > 0 && (
        <section style={{ marginTop: 36 }}>
          <h2 className="team__h">Ehemalige</h2>
          <p className="section-sub">Ausgeblendet, weil es alte Termine mit ihnen gibt. Sie lassen sich jederzeit wieder aktivieren.</p>
          <ul className="team">
            {former.map((b) => (
              <li key={b.id} className="team-card team-card--former">
                <span className="avatar" aria-hidden="true">{b.name[0]}</span>
                <div className="team-card__text"><strong>{b.name}</strong>{b.bio && <span>{b.bio}</span>}</div>
                <div className="team-card__actions">
                  <button className="btn btn--ghost" onClick={() => reactivate(b)}>Wieder aktivieren</button>
                </div>
              </li>
            ))}
          </ul>
        </section>
      )}

      {editing && <BarberPanel barber={editing} onClose={() => setEditing(null)} onSave={save} />}
    </main>
  )
}

function BarberPanel({ barber, onClose, onSave }) {
  const isNew = !barber.id
  const [form, setForm] = useState({ name: barber.name || '', bio: barber.bio || '' })
  const [errors, setErrors] = useState({})
  const [message, setMessage] = useState(null)
  const [busy, setBusy] = useState(false)

  const submit = async (e) => {
    e.preventDefault()
    setBusy(true); setErrors({}); setMessage(null)
    try {
      await onSave({ name: form.name, bio: form.bio, active: true, sortOrder: barber.sortOrder }, barber.id)
    } catch (err) {
      setErrors(err.fieldErrors || {})
      setMessage(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <Panel title={isNew ? 'Barber hinzufügen' : `${barber.name} bearbeiten`} onClose={onClose}>
      <form className="form" onSubmit={submit} noValidate style={{ marginTop: 16 }}>
        {message && <p className="alert" role="alert">{message}</p>}
        <div className={`field ${errors.name ? 'field--error' : ''}`}>
          <label htmlFor="b-name">Name</label>
          <input id="b-name" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} maxLength={80} autoFocus />
          {errors.name && <p className="field__error">{errors.name}</p>}
        </div>
        <div className={`field ${errors.bio ? 'field--error' : ''}`}>
          <label htmlFor="b-bio">Kurzbeschreibung <em>(optional, sehen die Kunden)</em></label>
          <textarea id="b-bio" value={form.bio} onChange={(e) => setForm({ ...form, bio: e.target.value })} maxLength={300}
                    placeholder="z. B. Fades, Bartstyling, spricht Deutsch und Arabisch" />
          {errors.bio && <p className="field__error">{errors.bio}</p>}
        </div>
        {isNew && (
          <p className="alert alert--info">
            Neue Barber sind sofort buchbar, zu den Öffnungszeiten des Shops. Pausen und Urlaub trägst du im Kalender mit „Zeit blockieren“ ein.
          </p>
        )}
        <button className="btn btn--block" disabled={busy}>{busy ? 'Speichert …' : isNew ? 'Hinzufügen' : 'Speichern'}</button>
      </form>
    </Panel>
  )
}
