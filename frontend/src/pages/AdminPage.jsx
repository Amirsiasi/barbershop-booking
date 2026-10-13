import { useCallback, useEffect, useState } from 'react'
import { api } from '../lib/api.js'
import { addDays, euro, hhmm, instantFor, isoWeekday, longDate, minutesIn, timeIn, todayIn } from '../lib/time.js'
import AdminServices from './AdminServices.jsx'
import Panel from '../components/Panel.jsx'

const STORAGE_KEY = 'barberbook.admin'
const HOUR_PX = 96
const STATUS_LABEL = { CONFIRMED: 'Bestätigt', COMPLETED: 'Erschienen', NO_SHOW: 'Nicht erschienen', CANCELLED: 'Storniert' }

const readAuth = () => { try { return sessionStorage.getItem(STORAGE_KEY) } catch { return null } }
const writeAuth = (v) => { try { v ? sessionStorage.setItem(STORAGE_KEY, v) : sessionStorage.removeItem(STORAGE_KEY) } catch { /* private mode */ } }

export default function AdminPage({ shop }) {
  const [auth, setAuth] = useState(readAuth)
  const [me, setMe] = useState(null)

  useEffect(() => {
    if (!auth) return
    api.admin.me(auth).then(setMe).catch(() => { writeAuth(null); setAuth(null) })
  }, [auth])

  const logout = () => { writeAuth(null); setAuth(null); setMe(null) }

  if (!auth || !me) return <Login onLogin={(a, m) => { writeAuth(a); setAuth(a); setMe(m) }} />
  return <AdminShell auth={auth} me={me} shop={shop} onLogout={logout} />
}

const TABS = [['kalender', 'Kalender'], ['leistungen', 'Leistungen & Preise']]

/** Top bar with the shop name, the two admin areas and logout. */
function AdminShell({ auth, me, shop, onLogout }) {
  const [tab, setTab] = useState(() => (window.location.hash === '#leistungen' ? 'leistungen' : 'kalender'))
  const open = (t) => { setTab(t); window.history.replaceState(null, '', t === 'kalender' ? '#' : `#${t}`) }

  return (
    <div className="admin">
      <div className="admin-bar">
        <div className="wrap">
          <strong>{me.shopName}</strong>
          <nav className="admin-tabs" aria-label="Bereiche">
            {TABS.map(([id, label]) => (
              <button key={id} className="admin-tab" aria-current={tab === id ? 'page' : undefined} onClick={() => open(id)}>
                {label}
              </button>
            ))}
          </nav>
          <button className="link-btn" onClick={onLogout}>Abmelden</button>
        </div>
      </div>
      {tab === 'kalender'
        ? <Dashboard auth={auth} me={me} shop={shop} />
        : <AdminServices auth={auth} />}
    </div>
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
    const auth = btoa(unescape(encodeURIComponent(`${username}:${password}`)))
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

function Dashboard({ auth, me, shop }) {
  const tz = me.timeZone
  const [date, setDate] = useState(() => todayIn(tz))
  const [day, setDay] = useState(null)
  const [error, setError] = useState(null)
  const [selected, setSelected] = useState(null)
  const [blocking, setBlocking] = useState(false)

  const load = useCallback(() => {
    setError(null)
    api.admin.day(auth, date).then(setDay).catch((e) => setError(e.message))
  }, [auth, date])

  useEffect(() => { setDay(null); load() }, [load])
  useEffect(() => { // keep the calendar fresh when new online bookings come in
    const id = setInterval(load, 60_000)
    return () => clearInterval(id)
  }, [load])

  const hours = shop?.openingHours.find((h) => h.dayOfWeek === isoWeekday(date))
  const startMin = hours ? Number(hhmm(hours.open).slice(0, 2)) * 60 : 9 * 60
  const endMin = hours ? Math.ceil(minutesOf(hours.close) / 60) * 60 : 19 * 60
  const px = (min) => ((min - startMin) / 60) * HOUR_PX

  const active = day?.appointments.filter((a) => a.status !== 'CANCELLED') || []
  const revenue = active.filter((a) => a.status !== 'NO_SHOW').reduce((s, a) => s + a.priceCents, 0)
  const isToday = date === todayIn(tz)
  const nowMin = minutesIn(new Date().toISOString(), tz)

  const setStatus = async (appt, status) => {
    try {
      const updated = await api.admin.setStatus(auth, appt.id, status)
      setSelected(updated)
      load()
    } catch (e) {
      setError(e.message)
    }
  }

  return (
    <>
      <main className="wrap" style={{ paddingBottom: 48 }}>
        <div className="daynav">
          <h1>{isToday ? 'Heute' : longDate(date)}</h1>
          <button className="icon-btn" onClick={() => setDate(addDays(date, -1))} aria-label="Vorheriger Tag">‹</button>
          <input type="date" value={date} onChange={(e) => e.target.value && setDate(e.target.value)} className="icon-btn"
                 style={{ width: 'auto', borderRadius: 999, padding: '0 14px' }} aria-label="Datum wählen" />
          <button className="icon-btn" onClick={() => setDate(addDays(date, 1))} aria-label="Nächster Tag">›</button>
          {!isToday && <button className="btn btn--ghost" onClick={() => setDate(todayIn(tz))}>Heute</button>}
          <button className="btn" onClick={() => setBlocking(true)}>Zeit blockieren</button>
        </div>

        {error && <p className="alert" role="alert">{error}</p>}

        <div className="stats">
          <div className="stat"><strong>{active.length}</strong><span>Termine</span></div>
          <div className="stat"><strong>{euro(revenue)}</strong><span>Umsatz erwartet</span></div>
          <div className="stat"><strong>{day?.appointments.filter((a) => a.status === 'CANCELLED').length ?? 0}</strong><span>Storniert</span></div>
        </div>

        {!hours && <p className="alert alert--info">An diesem Tag ist der Shop geschlossen.</p>}

        {!day ? <div className="skeleton" style={{ height: 480 }} /> : (
          <div className="calendar" style={{ '--cols': me.barbers.length, '--hour': `${HOUR_PX}px` }}>
            <div className="cal-head">
              <div />
              {me.barbers.map((b) => <div key={b.id}>{b.name}</div>)}
            </div>
            <div className="cal-body" style={{ height: px(endMin) + 8 }}>
              <div className="cal-times">
                {Array.from({ length: (endMin - startMin) / 60 + 1 }, (_, i) => (
                  <span key={i} style={{ top: i * HOUR_PX + (i === 0 ? 8 : 0) }}>{String(startMin / 60 + i).padStart(2, '0')}:00</span>
                ))}
              </div>
              {me.barbers.map((b) => (
                <div className="cal-col" key={b.id}>
                  {isToday && nowMin > startMin && nowMin < endMin && <div className="now-line" style={{ top: px(nowMin) }} aria-hidden="true" />}
                  {day.absences.filter((x) => x.barberId === b.id).map((x) => {
                    const top = Math.max(0, px(minutesIn(x.start, tz)))
                    const bottom = Math.min(px(endMin), px(minutesIn(x.end, tz)))
                    return (
                      <div key={`abs-${x.id}`} className="block" style={{ top, height: Math.max(24, bottom - top) }}>
                        {x.reason || 'Blockiert'}{' '}
                        <button className="link-btn" onClick={async () => { await api.admin.unblock(auth, x.id); load() }}>entfernen</button>
                      </div>
                    )
                  })}
                  {day.appointments.filter((a) => a.barberId === b.id).map((a) => {
                    const top = px(minutesIn(a.start, tz))
                    const height = px(minutesIn(a.end, tz)) - top
                    return (
                      <button key={a.id} className={`appt appt--${a.status}`} style={{ top, height: Math.max(26, height - 2) }}
                              onClick={() => setSelected(a)}>
                        <strong>{timeIn(a.start, tz)} {a.customerName}</strong>
                        {a.serviceName}
                      </button>
                    )
                  })}
                </div>
              ))}
            </div>
          </div>
        )}
      </main>

      {selected && <AppointmentPanel appt={selected} tz={tz} onClose={() => setSelected(null)} onStatus={setStatus} />}
      {blocking && (
        <BlockPanel me={me} date={date} tz={tz} onClose={() => setBlocking(false)}
                    onSave={async (payload) => { await api.admin.block(auth, payload); setBlocking(false); load() }} />
      )}
    </>
  )
}

const minutesOf = (t) => Number(t.slice(0, 2)) * 60 + Number(t.slice(3, 5))

function AppointmentPanel({ appt, tz, onClose, onStatus }) {
  const past = new Date(appt.start) < new Date()
  return (
    <Panel title={appt.customerName} onClose={onClose}>
      <span className={`badge badge--${appt.status}`}>{STATUS_LABEL[appt.status]}</span>
      <dl>
        <dt>Zeit</dt><dd>{timeIn(appt.start, tz)} – {timeIn(appt.end, tz)} Uhr</dd>
        <dt>Leistung</dt><dd>{appt.serviceName}</dd>
        <dt>Preis</dt><dd>{euro(appt.priceCents)}</dd>
        <dt>Barber</dt><dd>{appt.barberName}</dd>
        <dt>Telefon</dt><dd><a href={`tel:${appt.phone}`}>{appt.phone}</a></dd>
        {appt.email && <><dt>E-Mail</dt><dd><a href={`mailto:${appt.email}`}>{appt.email}</a></dd></>}
        {appt.note && <><dt>Wunsch</dt><dd>{appt.note}</dd></>}
      </dl>
      {appt.status !== 'CANCELLED' && (
        <div className="panel__actions">
          {appt.status !== 'COMPLETED' && <button className="btn" onClick={() => onStatus(appt, 'COMPLETED')}>Ist erschienen</button>}
          {past && appt.status !== 'NO_SHOW' && <button className="btn btn--ghost" onClick={() => onStatus(appt, 'NO_SHOW')}>Nicht erschienen</button>}
          {appt.status === 'CONFIRMED' && <button className="btn btn--danger" onClick={() => onStatus(appt, 'CANCELLED')}>Termin stornieren</button>}
        </div>
      )}
    </Panel>
  )
}

function BlockPanel({ me, date, tz, onClose, onSave }) {
  const [barberId, setBarberId] = useState(me.barbers[0]?.id)
  const [from, setFrom] = useState('13:00')
  const [to, setTo] = useState('14:00')
  const [reason, setReason] = useState('Mittagspause')
  const [error, setError] = useState(null)

  const submit = async (e) => {
    e.preventDefault()
    setError(null)
    try {
      await onSave({ barberId: Number(barberId), start: instantFor(date, from, tz), end: instantFor(date, to, tz), reason })
    } catch (err) {
      setError(err.message)
    }
  }

  return (
    <Panel title="Zeit blockieren" onClose={onClose}>
      <p className="section-sub">{longDate(date)}. In dieser Zeit kann online niemand buchen.</p>
      <form className="form" onSubmit={submit}>
        {error && <p className="alert">{error}</p>}
        <div className="field">
          <label htmlFor="b">Barber</label>
          <select id="b" value={barberId} onChange={(e) => setBarberId(e.target.value)}>
            {me.barbers.map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
          </select>
        </div>
        <div className="form__row">
          <div className="field"><label htmlFor="f">Von</label><input id="f" type="time" value={from} step={900} onChange={(e) => setFrom(e.target.value)} /></div>
          <div className="field"><label htmlFor="t">Bis</label><input id="t" type="time" value={to} step={900} onChange={(e) => setTo(e.target.value)} /></div>
        </div>
        <div className="field"><label htmlFor="r">Grund</label><input id="r" value={reason} onChange={(e) => setReason(e.target.value)} maxLength={120} /></div>
        <button className="btn btn--block">Blockieren</button>
      </form>
    </Panel>
  )
}
