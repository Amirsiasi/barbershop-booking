import { useEffect, useState } from 'react'
import { api } from '../lib/api.js'
import AdminServices from './AdminServices.jsx'
import AdminCalendar from './AdminCalendar.jsx'
import AdminShop from './AdminShop.jsx'
import AdminStats from './AdminStats.jsx'
import AdminRequests from './AdminRequests.jsx'
import AdminTeam from './AdminTeam.jsx'
import Panel from '../components/Panel.jsx'

const STORAGE_KEY = 'barberbook.admin'

const readAuth = () => { try { return sessionStorage.getItem(STORAGE_KEY) } catch { return null } }
const writeAuth = (v) => { try { v ? sessionStorage.setItem(STORAGE_KEY, v) : sessionStorage.removeItem(STORAGE_KEY) } catch { /* private mode */ } }

const basicAuth = (username, password) => btoa(unescape(encodeURIComponent(`${username}:${password}`)))

export default function AdminPage({ shop }) {
  const [auth, setAuth] = useState(readAuth)
  const [me, setMe] = useState(null)

  useEffect(() => {
    if (!auth) return
    api.admin.me(auth).then(setMe).catch(() => { writeAuth(null); setAuth(null) })
  }, [auth])

  const logout = () => { writeAuth(null); setAuth(null); setMe(null) }
  const applyAuth = (a, m) => { writeAuth(a); setAuth(a); setMe(m) }

  if (!auth || !me) return <Login onLogin={applyAuth} />

  // start password from the owner: the user must choose an own one before anything else
  if (me.mustChangePassword) {
    return (
      <div className="admin page-center">
        <div className="login">
          <h1>Willkommen, {me.barberName || me.username}</h1>
          <p className="section-sub">Bitte lege jetzt ein eigenes Passwort fest. Das Startpasswort gilt danach nicht mehr.</p>
          <PasswordForm auth={auth} onDone={(newPassword) => {
            const a = basicAuth(me.username, newPassword)
            api.admin.me(a).then((m) => applyAuth(a, m))
          }} />
          <button className="link-btn" style={{ marginTop: 16 }} onClick={logout}>Abmelden</button>
        </div>
      </div>
    )
  }

  const refreshMe = () => api.admin.me(auth).then(setMe).catch(() => {})
  return <AdminShell auth={auth} me={me} shop={shop} onLogout={logout} onTeamChange={refreshMe}
                     onPasswordChanged={(pw) => applyAuth(basicAuth(me.username, pw), me)} />
}

const ALL_TABS = [['kalender', 'Kalender'], ['anfragen', 'Anfragen'], ['team', 'Team'], ['leistungen', 'Leistungen & Preise'], ['statistik', 'Statistik'], ['shop', 'Shop-Auftritt']]
const BARBER_TABS = ['kalender', 'anfragen']
const ROLE_LABEL = { OWNER: 'Inhaber', BARBER: 'Barber' }

/** Top bar with the shop name, the admin areas allowed for this role, the account and logout. */
function AdminShell({ auth, me, shop, onLogout, onTeamChange, onPasswordChanged }) {
  const isOwner = me.role === 'OWNER'
  const tabs = isOwner ? ALL_TABS : ALL_TABS.filter(([id]) => BARBER_TABS.includes(id))
  const [tab, setTab] = useState(() => tabs.find(([id]) => `#${id}` === window.location.hash)?.[0] || 'kalender')
  const [openRequests, setOpenRequests] = useState(0)
  const [account, setAccount] = useState(false)
  const open = (t) => { setTab(t); window.history.replaceState(null, '', t === 'kalender' ? '#' : `#${t}`) }

  // keep the counter fresh on every tab, so a new WhatsApp request is noticed
  useEffect(() => {
    const load = () => api.admin.requests(auth).then((r) => setOpenRequests(r.length)).catch(() => {})
    load()
    const id = setInterval(load, 30_000)
    return () => clearInterval(id)
  }, [auth])

  return (
    <div className="admin">
      <div className="admin-bar">
        <div className="wrap">
          <strong>{me.shopName}</strong>
          <nav className="admin-tabs" aria-label="Bereiche">
            {tabs.map(([id, label]) => (
              <button key={id} className="admin-tab" aria-current={tab === id ? 'page' : undefined} onClick={() => open(id)}>
                {label}
                {id === 'anfragen' && openRequests > 0 && <span className="tab-count" aria-label={`${openRequests} offen`}>{openRequests}</span>}
              </button>
            ))}
          </nav>
          <button className="account-btn" onClick={() => setAccount(true)}>
            {me.barberName || me.username}
            <span>{ROLE_LABEL[me.role]}</span>
          </button>
          <button className="link-btn" onClick={onLogout}>Abmelden</button>
        </div>
      </div>
      {tab === 'kalender' && <AdminCalendar auth={auth} me={me} shop={shop} />}
      {tab === 'anfragen' && <AdminRequests auth={auth} tz={me.timeZone} onChange={setOpenRequests} isOwner={isOwner} />}
      {tab === 'team' && isOwner && <AdminTeam auth={auth} me={me} onChange={onTeamChange} />}
      {tab === 'leistungen' && isOwner && <AdminServices auth={auth} />}
      {tab === 'statistik' && isOwner && <AdminStats auth={auth} me={me} />}
      {tab === 'shop' && isOwner && <AdminShop auth={auth} shop={shop} />}

      {account && (
        <Panel title="Mein Konto" onClose={() => setAccount(false)}>
          <dl>
            <dt>Benutzer</dt><dd>{me.username}</dd>
            <dt>Rolle</dt><dd>{ROLE_LABEL[me.role]}</dd>
            {me.barberName && <><dt>Barber</dt><dd>{me.barberName}</dd></>}
          </dl>
          {me.emergencyLogin ? (
            <p className="alert alert--info">
              Das ist der Notfall-Zugang aus der Server-Einstellung. Sein Passwort wird in der <code>.env</code> geändert.
              Für den Alltag lieber im Tab Team einen persönlichen Login anlegen.
            </p>
          ) : (
            <>
              <h3 style={{ margin: '8px 0 12px' }}>Passwort ändern</h3>
              <PasswordForm auth={auth} onDone={(pw) => { onPasswordChanged(pw); setAccount(false) }} />
            </>
          )}
        </Panel>
      )}
    </div>
  )
}

function PasswordForm({ auth, onDone }) {
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [repeat, setRepeat] = useState('')
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  const submit = async (e) => {
    e.preventDefault()
    setError(null)
    if (next.length < 8) return setError('Das neue Passwort braucht mindestens 8 Zeichen.')
    if (next !== repeat) return setError('Die beiden neuen Passwörter stimmen nicht überein.')
    setBusy(true)
    try {
      await api.admin.changePassword(auth, current, next)
      onDone(next)
    } catch (err) {
      setError(Object.values(err.fieldErrors || {})[0] || err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="form" onSubmit={submit}>
      {error && <p className="alert" role="alert">{error}</p>}
      <div className="field">
        <label htmlFor="pw-current">Aktuelles Passwort</label>
        <input id="pw-current" type="password" value={current} onChange={(e) => setCurrent(e.target.value)} autoComplete="current-password" required />
      </div>
      <div className="field">
        <label htmlFor="pw-new">Neues Passwort <em>(mind. 8 Zeichen)</em></label>
        <input id="pw-new" type="password" value={next} onChange={(e) => setNext(e.target.value)} autoComplete="new-password" required />
      </div>
      <div className="field">
        <label htmlFor="pw-repeat">Neues Passwort wiederholen</label>
        <input id="pw-repeat" type="password" value={repeat} onChange={(e) => setRepeat(e.target.value)} autoComplete="new-password" required />
      </div>
      <button className="btn btn--block" disabled={busy}>{busy ? 'Speichert …' : 'Passwort speichern'}</button>
    </form>
  )
}

function Login({ onLogin }) {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  const submit = async (e) => {
    e.preventDefault()
    setBusy(true); setError(null)
    const auth = basicAuth(username, password)
    try {
      onLogin(auth, await api.admin.me(auth))
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="admin page-center">
      <form className="login form" onSubmit={submit}>
        <div>
          <h1>Team-Login</h1>
          <p className="section-sub" style={{ margin: 0 }}>Kalender und Termine verwalten</p>
        </div>
        {error && <p className="alert" role="alert">{error}</p>}
        <div className="field">
          <label htmlFor="u">Benutzername</label>
          <input id="u" value={username} onChange={(e) => setUsername(e.target.value)} autoComplete="username" required />
        </div>
        <div className="field">
          <label htmlFor="p">Passwort</label>
          <input id="p" type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" required />
        </div>
        <button className="btn btn--block" disabled={busy}>{busy ? 'Prüfe …' : 'Anmelden'}</button>
      </form>
    </div>
  )
}

