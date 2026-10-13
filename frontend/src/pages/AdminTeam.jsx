import { useEffect, useState } from 'react'
import { api } from '../lib/api.js'
import Panel from '../components/Panel.jsx'

/** Admin area "Team": add, edit, remove and re-activate barbers. */
export default function AdminTeam({ auth, me, onChange }) {
  const [team, setTeam] = useState(null)
  const [logins, setLogins] = useState([])
  const [loginFor, setLoginFor] = useState(null) // barber whose login is being created or edited
  const [error, setError] = useState(null)
  const [notice, setNotice] = useState(null)
  const [editing, setEditing] = useState(null) // a barber, or {} for a new one
  const [confirmRemove, setConfirmRemove] = useState(null)
  const [busy, setBusy] = useState(false)

  const load = () => Promise.all([api.admin.barbers(auth), api.admin.staff(auth)])
    .then(([t, l]) => { setTeam(t); setLogins(l) })
    .catch((e) => setError(e.message))
  const loginOf = (barberId) => logins.find((l) => l.barberId === barberId)
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
              <LoginLine login={loginOf(b.id)} onClick={() => setLoginFor(b)} />
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
      {loginFor && (
        <LoginPanel auth={auth} barber={loginFor} login={loginOf(loginFor.id)} isMe={loginOf(loginFor.id)?.username === me?.username}
                    onClose={() => setLoginFor(null)}
                    onDone={(text) => { setLoginFor(null); done(text) }} />
      )}
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

const ROLE = { OWNER: 'Inhaber', BARBER: 'Barber' }

function LoginLine({ login, onClick }) {
  if (!login) {
    return <button className="login-line login-line--none" onClick={onClick}>Kein Login. Login anlegen</button>
  }
  const state = !login.active ? 'gesperrt' : login.mustChangePassword ? 'wartet auf ersten Login' : 'aktiv'
  return (
    <button className={`login-line ${login.active ? '' : 'login-line--off'}`} onClick={onClick}>
      Login <strong>{login.username}</strong>, {ROLE[login.role]}, {state}
    </button>
  )
}

/** A readable start password like "Fade-4821-Kamm" - easy to tell someone, hard to guess. */
function startPassword() {
  const words = ['Fade', 'Kamm', 'Bart', 'Schere', 'Klinge', 'Pomade', 'Spiegel', 'Wachs']
  const [a, b, n] = crypto.getRandomValues(new Uint16Array(3))
  const first = words[a % words.length]
  const second = words.filter((w) => w !== first)[b % (words.length - 1)]
  return `${first}-${(n % 9000) + 1000}-${second}`
}

function LoginPanel({ auth, barber, login, isMe, onClose, onDone }) {
  const [username, setUsername] = useState(login?.username || barber.name.toLowerCase().normalize('NFD').replace(/[^a-z0-9._-]/g, ''))
  const [role, setRole] = useState(login?.role || 'BARBER')
  const [active, setActive] = useState(login?.active ?? true)
  const [password, setPassword] = useState(login ? '' : startPassword())
  const [errors, setErrors] = useState({})
  const [message, setMessage] = useState(null)
  const [busy, setBusy] = useState(false)
  const [created, setCreated] = useState(null) // shown once, so the owner can pass it on

  const run = async (fn) => {
    setBusy(true); setErrors({}); setMessage(null)
    try { await fn() } catch (err) { setErrors(err.fieldErrors || {}); setMessage(err.message) } finally { setBusy(false) }
  }

  const submit = (e) => {
    e.preventDefault()
    run(async () => {
      if (!login) {
        const l = await api.admin.createLogin(auth, { barberId: barber.id, username, password, role })
        setCreated({ username: l.username, password })
        return
      }
      await api.admin.updateLogin(auth, login.id, { role, active })
      if (password) {
        await api.admin.resetPassword(auth, login.id, password)
        setCreated({ username: login.username, password })
        return
      }
      onDone(`Login von ${barber.name} gespeichert.`)
    })
  }

  const remove = () => run(async () => {
    await api.admin.deleteLogin(auth, login.id)
    onDone(`Login von ${barber.name} gelöscht.`)
  })

  if (created) {
    return (
      <Panel title="Zugangsdaten" onClose={() => onDone(`Login für ${barber.name} ist bereit.`)}>
        <p className="section-sub" style={{ marginTop: 12 }}>
          Gib {barber.name} diese Daten persönlich weiter. Beim ersten Login muss {barber.name} ein eigenes Passwort festlegen.
          Das Startpasswort wird hier nicht noch einmal angezeigt.
        </p>
        <div className="credentials">
          <div><span>Adresse</span><strong>{window.location.origin}/admin</strong></div>
          <div><span>Benutzer</span><strong>{created.username}</strong></div>
          <div><span>Startpasswort</span><strong>{created.password}</strong></div>
        </div>
        <button className="btn btn--block" onClick={() => onDone(`Login für ${barber.name} ist bereit.`)}>Fertig</button>
      </Panel>
    )
  }

  return (
    <Panel title={login ? `Login von ${barber.name}` : `Login für ${barber.name}`} onClose={onClose}>
      <form className="form" onSubmit={submit} noValidate style={{ marginTop: 16 }}>
        {message && <p className="alert" role="alert">{message}</p>}

        <div className={`field ${errors.username ? 'field--error' : ''}`}>
          <label htmlFor="l-user">Benutzername</label>
          <input id="l-user" value={username} onChange={(e) => setUsername(e.target.value)} disabled={!!login} autoComplete="off" />
          {errors.username && <p className="field__error">{errors.username}</p>}
        </div>

        <div className="field">
          <label htmlFor="l-role">Rolle</label>
          <select id="l-role" value={role} onChange={(e) => setRole(e.target.value)} disabled={isMe}>
            <option value="BARBER">Barber: Kalender und eigene Termine</option>
            <option value="OWNER">Inhaber: alles, auch Team, Logins und Preise</option>
          </select>
        </div>

        <div className={`field ${errors.password ? 'field--error' : ''}`}>
          <label htmlFor="l-pw">{login ? <>Neues Startpasswort <em>(leer lassen = unverändert)</em></> : 'Startpasswort'}</label>
          <div style={{ display: 'flex', gap: 8 }}>
            <input id="l-pw" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="off" disabled={isMe} />
            {!isMe && <button type="button" className="btn btn--ghost" onClick={() => setPassword(startPassword())}>Neu</button>}
          </div>
          {errors.password && <p className="field__error">{errors.password}</p>}
        </div>

        {login && !isMe && (
          <label className="check">
            <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} />
            <span>Login aktiv. Ohne Häkchen kann sich {barber.name} nicht mehr anmelden.</span>
          </label>
        )}
        {isMe && <p className="alert alert--info">Das ist dein eigener Login. Dein Passwort änderst du oben rechts unter deinem Namen.</p>}

        <button className="btn btn--block" disabled={busy}>{busy ? 'Speichert …' : login ? 'Speichern' : 'Login anlegen'}</button>
        {login && !isMe && (
          <button type="button" className="btn btn--ghost btn--danger-text btn--block" disabled={busy} onClick={remove}>Login löschen</button>
        )}
      </form>
    </Panel>
  )
}
