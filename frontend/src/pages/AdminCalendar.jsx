import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react'
import { api } from '../lib/api.js'
import {
  addDays, dayNumber, duration, euro, instantFor, isoDateIn, isoWeekday, longDate, minutesIn, monthShort, timeIn,
  todayIn, weekdayShort,
} from '../lib/time.js'
import Panel from '../components/Panel.jsx'

/**
 * The team calendar: a time grid with one column per barber (day view) or per day (3 days / week).
 * Every barber has an own colour, so in the multi-day views you still see at a glance whose appointment it is.
 * A click into an empty spot enters a new appointment there (phone call, walk-in).
 */

export const STATUS_LABEL = { PENDING: 'Anfrage, unbestätigt', CONFIRMED: 'Bestätigt', COMPLETED: 'Erschienen', NO_SHOW: 'Nicht erschienen', CANCELLED: 'Storniert' }

// soft fill + strong edge per barber, taken from the shop palette (green, brass, pole red, ...)
// light fill + strong edge per barber. The edges are a colour-blind-checked palette (also used in the statistics),
// so a barber has the same colour everywhere
const COLORS = [
  ['#d3ecdf', '#1a8a55'], ['#d7e6f8', '#2a78d6'], ['#fbe8cc', '#e08a10'],
  ['#f7dbe6', '#d55181'], ['#e0dcf7', '#6250d6'], ['#f5d8d5', '#c23b30'],
]
const VIEWS = [['day', 'Tag', 1], ['3', '3 Tage', 3], ['week', 'Woche', 7]]
const VIEW_KEY = 'barberbook.calendar'
const SOURCE_LABEL = { WHATSAPP: 'WhatsApp', ADMIN: 'Eingetragen', ONLINE: 'Online' }

const readPrefs = () => { try { return JSON.parse(localStorage.getItem(VIEW_KEY)) || {} } catch { return {} } }
const writePrefs = (v) => { try { localStorage.setItem(VIEW_KEY, JSON.stringify(v)) } catch { /* private mode */ } }
const toMin = (t) => Number(t.slice(0, 2)) * 60 + Number(t.slice(3, 5))
const hhmmOf = (min) => `${String(Math.floor(min / 60)).padStart(2, '0')}:${String(min % 60).padStart(2, '0')}`
const mondayOf = (iso) => addDays(iso, 1 - isoWeekday(iso))

/** Strong colour of a barber, by his position in the team list (same order in calendar and statistics). */
export const barberColor = (barbers, barberId) => {
  const i = barbers.findIndex((b) => b.id === barberId)
  return COLORS[(i < 0 ? 0 : i) % COLORS.length][1]
}

export default function AdminCalendar({ auth, me, shop }) {
  const tz = me.timeZone
  const isOwner = me.role === 'OWNER'
  const mayActFor = (barberId) => isOwner || me.barberId === barberId
  const colorOf = useCallback((barberId) => {
    const i = me.barbers.findIndex((b) => b.id === barberId)
    return COLORS[(i < 0 ? 0 : i) % COLORS.length]
  }, [me.barbers])

  const prefs = useMemo(readPrefs, [])
  const [view, setView] = useState(prefs.view || (window.innerWidth < 720 ? '3' : 'day'))
  const [team, setTeam] = useState(prefs.team ?? 'all') // 'all' or a barber id
  const [date, setDate] = useState(() => todayIn(tz))
  const [data, setData] = useState(null)
  const [error, setError] = useState(null)
  const [selected, setSelected] = useState(null)
  const [creating, setCreating] = useState(null) // { barberId, date, time }
  const [blocking, setBlocking] = useState(false)
  const [menu, setMenu] = useState(false)
  const [, tick] = useState(0)

  useEffect(() => { writePrefs({ view, team }) }, [view, team])

  const days = VIEWS.find(([v]) => v === view)[2]
  const from = view === 'week' ? mondayOf(date) : date
  const dates = Array.from({ length: days }, (_, i) => addDays(from, i))
  const today = todayIn(tz)

  const load = useCallback(() => {
    setError(null)
    return api.admin.range(auth, from, days).then(setData).catch((e) => setError(e.message))
  }, [auth, from, days])

  useEffect(() => { setData(null); load() }, [load])
  useEffect(() => { // fresh data and a moving "now" line
    const id = setInterval(() => { load(); tick((n) => n + 1) }, 60_000)
    return () => clearInterval(id)
  }, [load])

  const shown = team === 'all' ? me.barbers : me.barbers.filter((b) => b.id === team)
  const shownIds = new Set(shown.map((b) => b.id))
  const appts = (data?.appointments || []).filter((a) => shownIds.has(a.barberId))
  const visible = appts.filter((a) => a.status !== 'CANCELLED')
  const absences = (data?.absences || []).filter((a) => shownIds.has(a.barberId))

  // columns: barbers on one day, or days with everybody (filtered) in it
  const columns = view === 'day'
    ? shown.map((b) => ({ key: `b${b.id}`, date, barberId: b.id, barber: b }))
    : dates.map((d) => ({ key: d, date: d, barberId: team === 'all' ? null : team }))

  // visible hours: from the earliest opening to the latest closing of the shown days, widened for
  // appointments the team entered outside the opening hours
  const hoursOf = (d) => shop?.openingHours.find((h) => h.dayOfWeek === isoWeekday(d))
  const { startMin, endMin } = useMemo(() => {
    let s = Infinity
    let e = -Infinity
    dates.forEach((d) => {
      const h = hoursOf(d)
      if (h) { s = Math.min(s, toMin(h.open)); e = Math.max(e, toMin(h.close)) }
    })
    visible.forEach((a) => { s = Math.min(s, minutesIn(a.start, tz)); e = Math.max(e, minutesIn(a.end, tz) || 1440) })
    if (s === Infinity) { s = 9 * 60; e = 19 * 60 }
    return { startMin: Math.max(0, Math.floor(s / 60) * 60 - 60), endMin: Math.min(1440, Math.ceil(e / 60) * 60 + 60) }
  }, [data, shop, from, days]) // eslint-disable-line react-hooks/exhaustive-deps

  const HOUR = 88
  const px = (min) => ((min - startMin) / 60) * HOUR
  const height = px(endMin)
  const nowMin = minutesIn(new Date().toISOString(), tz)
  const showNow = dates.includes(today) && nowMin >= startMin && nowMin <= endMin

  // scroll to "now" (or the first appointment) when the shown period changes
  const frame = useRef(null)
  const scrolledFor = useRef(null)
  useLayoutEffect(() => {
    if (!data || !frame.current || scrolledFor.current === from + view) return
    scrolledFor.current = from + view
    const first = visible.length ? Math.min(...visible.map((a) => minutesIn(a.start, tz))) : startMin + 60
    const target = showNow ? nowMin - 60 : first - 30
    frame.current.scrollTop = Math.max(0, px(target))
  })

  const step = (dir) => setDate(addDays(date, dir * days))
  const label = view === 'day'
    ? (date === today ? `Heute, ${longDate(date).split(', ')[1] || ''}` : longDate(date))
    : `${dayNumber(dates[0])}. ${monthShort(dates[0])} – ${dayNumber(dates[days - 1])}. ${monthShort(dates[days - 1])}`

  const active = visible
  const revenue = active.filter((a) => a.status !== 'NO_SHOW').reduce((s, a) => s + a.priceCents, 0)
  const cancelled = appts.length - visible.length
  const pending = visible.filter((a) => a.status === 'PENDING').length

  const setStatus = async (appt, status) => {
    try {
      const updated = await api.admin.setStatus(auth, appt.id, status)
      setSelected(updated)
      load()
    } catch (e) {
      setError(e.message)
    }
  }

  /** Click into an empty spot: new appointment at that time, rounded to 15 minutes. */
  const clickGrid = (col, e) => {
    if (e.target !== e.currentTarget) return
    const rect = e.currentTarget.getBoundingClientRect()
    const min = Math.floor((startMin + ((e.clientY - rect.top) / HOUR) * 60) / 15) * 15
    const barberId = col.barberId ?? (mayActFor(me.barberId) && me.barberId ? me.barberId : shown.find((b) => mayActFor(b.id))?.id)
    if (!barberId || !mayActFor(barberId)) return
    if (col.date < today) return
    setCreating({ barberId, date: col.date, time: hhmmOf(Math.max(0, Math.min(1439, min))) })
  }

  // close the "Neu" menu on any outside click
  const menuRef = useRef(null)
  useEffect(() => {
    if (!menu) return
    const close = (e) => !menuRef.current?.contains(e.target) && setMenu(false)
    document.addEventListener('mousedown', close)
    return () => document.removeEventListener('mousedown', close)
  }, [menu])

  return (
    <>
      <main className="kal-page">
        <div className="kal-toolbar">
          <div className="kal-toolbar__left">
            <button className="kal-btn" onClick={() => setDate(today)} disabled={dates.includes(today) && (view !== 'day' || date === today)}>Heute</button>
            <div className="kal-datenav">
              <button className="kal-btn kal-btn--icon" onClick={() => step(-1)} aria-label="Zurück">‹</button>
              <label className="kal-date">
                <span>{label}</span>
                <input type="date" value={date} onChange={(e) => e.target.value && setDate(e.target.value)} aria-label="Datum wählen" />
              </label>
              <button className="kal-btn kal-btn--icon" onClick={() => step(1)} aria-label="Weiter">›</button>
            </div>
          </div>
          <div className="kal-toolbar__right">
            <select className="kal-btn kal-select" value={team} aria-label="Wer wird angezeigt"
                    onChange={(e) => setTeam(e.target.value === 'all' ? 'all' : Number(e.target.value))}>
              <option value="all">Ganzes Team</option>
              {me.barbers.map((b) => <option key={b.id} value={b.id}>{b.id === me.barberId ? `${b.name} (ich)` : b.name}</option>)}
            </select>
            <div className="kal-seg" role="group" aria-label="Ansicht">
              {VIEWS.map(([v, l]) => (
                <button key={v} aria-pressed={view === v} onClick={() => setView(v)}>{l}</button>
              ))}
            </div>
            <div className="kal-menu" ref={menuRef}>
              <button className="btn kal-add" onClick={() => setMenu((m) => !m)} aria-expanded={menu} aria-haspopup="menu">
                Neu <span aria-hidden="true">▾</span>
              </button>
              {menu && (
                <div className="kal-menu__list" role="menu">
                  <button role="menuitem" onClick={() => { setMenu(false); setCreating({ barberId: me.barberId && mayActFor(me.barberId) ? me.barberId : shown.find((b) => mayActFor(b.id))?.id, date: date < today ? today : date, time: '' }) }}>
                    <strong>Termin eintragen</strong><span>Anruf oder Kunde im Shop</span>
                  </button>
                  <button role="menuitem" onClick={() => { setMenu(false); setBlocking(true) }}>
                    <strong>Zeit blockieren</strong><span>Pause, Urlaub, Arzttermin</span>
                  </button>
                </div>
              )}
            </div>
          </div>
        </div>

        <p className="kal-summary">
          <span><b>{active.length}</b> {active.length === 1 ? 'Termin' : 'Termine'}</span>
          {isOwner && <span><b>{euro(revenue)}</b> erwartet</span>}
          {pending > 0 && <span className="kal-summary__pending"><b>{pending}</b> {pending === 1 ? 'Anfrage offen' : 'Anfragen offen'}</span>}
          {cancelled > 0 && <span><b>{cancelled}</b> storniert</span>}
          <span className="kal-summary__hint">Tipp: In eine freie Stelle klicken, um einen Termin einzutragen.</span>
        </p>

        {error && <p className="alert" role="alert">{error}</p>}

        {!data ? <div className="skeleton" style={{ height: 520 }} /> : (
          <div className="kal-frame" ref={frame}>
            <div className={`kal-grid kal-grid--${view === 'day' ? 'team' : 'days'}`} style={{ '--cols': columns.length, '--hour': `${HOUR}px` }}>
              <div className="kal-corner" />
              {columns.map((c) => (
                <div key={c.key} className={`kal-head ${c.date === today && view !== 'day' ? 'is-today' : ''}`}>
                  {view === 'day' ? (
                    <>
                      <span className="kal-avatar" style={{ '--ring': colorOf(c.barberId)[1] }} aria-hidden="true">{c.barber.name[0]}</span>
                      <span className="kal-head__name">{c.barber.name}{c.barberId === me.barberId && <em> (ich)</em>}</span>
                      <span className="kal-head__count">{visible.filter((a) => a.barberId === c.barberId).length} Termine</span>
                    </>
                  ) : (
                    <>
                      <span className="kal-head__wd">{weekdayShort(c.date)}</span>
                      <span className="kal-head__day">{dayNumber(c.date)}</span>
                      <span className="kal-head__count">{hoursOf(c.date) ? `${visible.filter((a) => isoDateIn(a.start, tz) === c.date).length} Termine` : 'geschlossen'}</span>
                    </>
                  )}
                </div>
              ))}

              <div className="kal-gutter" style={{ height }}>
                {Array.from({ length: (endMin - startMin) / 60 }, (_, i) => (
                  <span key={i} style={{ top: i * HOUR }}>{String(startMin / 60 + i).padStart(2, '0')}:00</span>
                ))}
                {showNow && <span className="kal-gutter__now" style={{ top: px(nowMin) }}>{hhmmOf(nowMin)}</span>}
              </div>

              {columns.map((c) => {
                const h = hoursOf(c.date)
                const inCol = (a) => isoDateIn(a.start, tz) === c.date && (c.barberId == null || a.barberId === c.barberId)
                const colAppts = visible.filter(inCol)
                const colAbs = absences.filter((x) => (c.barberId == null || x.barberId === c.barberId)
                  && isoDateIn(x.start, tz) <= c.date && isoDateIn(x.end, tz) >= c.date)
                const lanes = layout(colAppts, tz)
                const clickable = c.date >= today && (c.barberId == null ? shown.some((b) => mayActFor(b.id)) : mayActFor(c.barberId))
                return (
                  <div key={c.key} className={`kal-col ${clickable ? 'is-clickable' : ''} ${c.date === today && view !== 'day' ? 'is-today' : ''}`}
                       style={{ height }} onClick={(e) => clickGrid(c, e)}
                       title={clickable ? 'Klicken, um hier einen Termin einzutragen' : undefined}>
                    {!h && <div className="kal-closed" style={{ top: 0, height }}><span>Geschlossen</span></div>}
                    {h && toMin(h.open) > startMin && <div className="kal-closed" style={{ top: 0, height: px(toMin(h.open)) }} />}
                    {h && toMin(h.close) < endMin && <div className="kal-closed" style={{ top: px(toMin(h.close)), height: height - px(toMin(h.close)) }} />}

                    {colAbs.map((x) => {
                      const s = isoDateIn(x.start, tz) < c.date ? startMin : Math.max(startMin, minutesIn(x.start, tz))
                      const e = isoDateIn(x.end, tz) > c.date ? endMin : Math.min(endMin, minutesIn(x.end, tz))
                      if (e <= s) return null
                      return (
                        <div key={`abs-${x.id}`} className="kal-block" style={{ top: px(s), height: Math.max(22, px(e) - px(s)) }}>
                          <span>{x.reason || 'Blockiert'}{c.barberId == null && ` · ${me.barbers.find((b) => b.id === x.barberId)?.name}`}</span>
                          {mayActFor(x.barberId) && (
                            <button className="link-btn" onClick={async () => { await api.admin.unblock(auth, x.id); load() }}>freigeben</button>
                          )}
                        </div>
                      )
                    })}

                    {colAppts.map((a) => {
                      const top = px(minutesIn(a.start, tz))
                      const hgt = Math.max(22, px(minutesIn(a.end, tz) || 1440) - top - 2)
                      const [bg, edge] = colorOf(a.barberId)
                      const { lane, of } = lanes.get(a.id)
                      const short = hgt < 40 // one line: time and name
                      const roomy = hgt >= 66 // room for the service
                      const narrow = of > 1 && view !== 'day'
                      return (
                        <button key={a.id} className={`kal-ev kal-ev--${a.status} ${short ? 'is-short' : ''} ${narrow ? 'is-narrow' : ''}`}
                                title={`${timeIn(a.start, tz)} – ${timeIn(a.end, tz)} ${a.customerName}, ${a.serviceName} (${a.barberName})`}
                                style={{ top, height: hgt, '--bg': bg, '--edge': edge, left: `calc(${(lane / of) * 100}% + 3px)`, width: `calc(${100 / of}% - 6px)` }}
                                onClick={() => setSelected(a)}>
                          <span className="kal-ev__time">
                            {timeIn(a.start, tz)} – {timeIn(a.end, tz)}
                            {a.status === 'PENDING' && <i className="kal-tag">Anfrage</i>}
                            {a.status === 'COMPLETED' && <i className="kal-tag kal-tag--ok" aria-label="erschienen">✓</i>}
                            {a.status === 'NO_SHOW' && <i className="kal-tag kal-tag--bad">nicht da</i>}
                          </span>
                          <strong className="kal-ev__who">{narrow ? a.customerName.split(' ')[0] : a.customerName}</strong>
                          {roomy && !narrow && <span className="kal-ev__svc">{a.serviceName}</span>}
                          {hgt >= 84 && c.barberId == null && <span className="kal-ev__barber">{a.barberName}</span>}
                        </button>
                      )
                    })}

                    {showNow && c.date === today && <div className="kal-now" style={{ top: px(nowMin) }} aria-hidden="true" />}
                  </div>
                )
              })}
            </div>
          </div>
        )}

        {view !== 'day' && team === 'all' && (
          <ul className="kal-legend" aria-label="Farben">
            {me.barbers.map((b) => <li key={b.id}><span style={{ background: colorOf(b.id)[1] }} />{b.name}</li>)}
          </ul>
        )}
      </main>

      {selected && <AppointmentPanel appt={selected} tz={tz} canAct={mayActFor(selected.barberId)}
                                     onClose={() => setSelected(null)} onStatus={setStatus} />}
      {blocking && (
        <BlockPanel me={me} date={date} tz={tz} onClose={() => setBlocking(false)}
                    onSave={async (payload) => { await api.admin.block(auth, payload); setBlocking(false); load() }} />
      )}
      {creating && (
        <NewAppointmentPanel me={me} shop={shop} tz={tz} initial={creating} mayActFor={mayActFor} onClose={() => setCreating(null)}
                             onSave={async (payload) => {
                               const created = await api.admin.createAppointment(auth, payload)
                               setCreating(null)
                               setDate(isoDateIn(created.start, tz))
                               await load()
                               setSelected(created)
                             }} />
      )}
    </>
  )
}

/** Side-by-side lanes for appointments that overlap in one column (e.g. two barbers at 10:00 in the week view). */
function layout(appts, tz) {
  const result = new Map()
  const sorted = [...appts].sort((a, b) => a.start.localeCompare(b.start) || a.barberId - b.barberId)
  let cluster = []
  let clusterEnd = -1
  const flush = () => {
    const laneEnds = []
    cluster.forEach((a) => {
      const s = minutesIn(a.start, tz)
      let lane = laneEnds.findIndex((end) => end <= s)
      if (lane < 0) { lane = laneEnds.length; laneEnds.push(0) }
      laneEnds[lane] = minutesIn(a.end, tz) || 1440
      result.set(a.id, { lane, of: 0 })
    })
    cluster.forEach((a) => { result.get(a.id).of = laneEnds.length })
    cluster = []
  }
  sorted.forEach((a) => {
    const s = minutesIn(a.start, tz)
    if (cluster.length && s >= clusterEnd) flush()
    cluster.push(a)
    clusterEnd = Math.max(clusterEnd, minutesIn(a.end, tz) || 1440)
  })
  if (cluster.length) flush()
  return result
}

// ---- side panels -----------------------------------------------------------------------------------

function AppointmentPanel({ appt, tz, canAct, onClose, onStatus }) {
  const past = new Date(appt.start) < new Date()
  return (
    <Panel title={appt.customerName} onClose={onClose}>
      <span className={`badge badge--${appt.status}`}>{STATUS_LABEL[appt.status]}</span>
      <dl>
        <dt>Zeit</dt><dd>{longDate(isoDateIn(appt.start, tz))}<br />{timeIn(appt.start, tz)} – {timeIn(appt.end, tz)} Uhr</dd>
        <dt>Leistung</dt><dd>{appt.serviceName}</dd>
        <dt>Preis</dt><dd>{euro(appt.priceCents)}</dd>
        <dt>Barber</dt><dd>{appt.barberName}</dd>
        <dt>Telefon</dt><dd><a href={`tel:${appt.phone}`}>{appt.phone}</a></dd>
        {appt.email && <><dt>E-Mail</dt><dd><a href={`mailto:${appt.email}`}>{appt.email}</a></dd></>}
        {appt.note && <><dt>Wunsch</dt><dd>{appt.note}</dd></>}
        <dt>Über</dt><dd>{SOURCE_LABEL[appt.source] || appt.source}</dd>
        {appt.status === 'PENDING' && appt.pendingUntil && <><dt>Reserviert bis</dt><dd>{timeIn(appt.pendingUntil, tz)} Uhr</dd></>}
      </dl>
      {!canAct && (
        <p className="alert alert--info">Das ist ein Termin von {appt.barberName}. Ändern können ihn nur {appt.barberName} oder der Inhaber.</p>
      )}
      {canAct && appt.status === 'PENDING' && (
        <div className="panel__actions">
          <button className="btn" onClick={() => onStatus(appt, 'CONFIRMED')}>Anfrage bestätigen</button>
          <button className="btn btn--ghost" onClick={() => onStatus(appt, 'CANCELLED')}>Ablehnen</button>
        </div>
      )}
      {canAct && appt.status !== 'CANCELLED' && appt.status !== 'PENDING' && (
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
  const choices = me.role === 'OWNER' ? me.barbers : me.barbers.filter((b) => b.id === me.barberId)
  const [barberId, setBarberId] = useState(me.barberId && choices.some((b) => b.id === me.barberId) ? me.barberId : choices[0]?.id)
  const [day, setDay] = useState(date)
  const [from, setFrom] = useState('13:00')
  const [to, setTo] = useState('14:00')
  const [reason, setReason] = useState('Mittagspause')
  const [error, setError] = useState(null)

  const submit = async (e) => {
    e.preventDefault()
    setError(null)
    try {
      await onSave({ barberId: Number(barberId), start: instantFor(day, from, tz), end: instantFor(day, to, tz), reason })
    } catch (err) {
      setError(err.message)
    }
  }

  return (
    <Panel title="Zeit blockieren" onClose={onClose}>
      <p className="section-sub">In dieser Zeit kann online niemand buchen.</p>
      <form className="form" onSubmit={submit}>
        {error && <p className="alert">{error}</p>}
        <div className="field">
          <label htmlFor="b">Barber</label>
          <select id="b" value={barberId} onChange={(e) => setBarberId(e.target.value)}>
            {choices.map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
          </select>
        </div>
        <div className="field"><label htmlFor="bd">Tag</label><input id="bd" type="date" value={day} onChange={(e) => setDay(e.target.value)} /></div>
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

function NewAppointmentPanel({ me, shop, tz, initial, mayActFor, onClose, onSave }) {
  const choices = me.barbers.filter((b) => mayActFor(b.id))
  const [form, setForm] = useState({
    barberId: initial.barberId ?? choices[0]?.id, date: initial.date, time: initial.time,
    firstName: '', lastName: '', phone: '', email: '', note: '',
  })
  const [serviceIds, setServiceIds] = useState([])
  const [errors, setErrors] = useState({})
  const [message, setMessage] = useState(null)
  const [busy, setBusy] = useState(false)
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })

  const chosen = serviceIds.map((id) => shop.services.find((s) => s.id === id)).filter(Boolean)
  const minutes = chosen.reduce((n, s) => n + s.durationMinutes, 0)
  const cents = chosen.reduce((n, s) => n + s.priceCents, 0)
  const end = form.time && minutes ? hhmmOf((toMin(form.time) + minutes) % 1440) : null
  // keep the order of the price list, so "Schneiden + Bart" reads the same way as online
  const order = (ids) => shop.services.map((s) => s.id).filter((id) => ids.includes(id))
  const toggle = (id) => setServiceIds((ids) => order(ids.includes(id) ? ids.filter((x) => x !== id) : [...ids, id]))

  const groups = useMemo(() => {
    const m = new Map()
    shop.services.forEach((s) => m.set(s.category, [...(m.get(s.category) || []), s]))
    return [...m.entries()]
  }, [shop.services])

  const submit = async (e) => {
    e.preventDefault()
    setErrors({}); setMessage(null)
    if (!form.time) { setErrors({ start: 'Bitte wähle eine Uhrzeit.' }); return }
    if (!serviceIds.length) { setErrors({ serviceIds: 'Bitte wähle mindestens eine Leistung.' }); return }
    setBusy(true)
    try {
      await onSave({
        barberId: Number(form.barberId), serviceIds, start: instantFor(form.date, form.time, tz),
        firstName: form.firstName, lastName: form.lastName, phone: form.phone, email: form.email || null, note: form.note || null,
      })
    } catch (err) {
      setErrors(err.fieldErrors || {})
      setMessage(err.message)
    } finally {
      setBusy(false)
    }
  }

  const err = (k) => errors[k] && <p className="field__error">{errors[k]}</p>

  return (
    <Panel title="Termin eintragen" onClose={onClose}>
      <p className="section-sub">Für Anrufe und Kunden, die direkt im Shop sind.</p>
      <form className="form" onSubmit={submit} noValidate>
        {message && <p className="alert" role="alert">{message}</p>}
        <div className="field">
          <label htmlFor="na-b">Barber</label>
          <select id="na-b" value={form.barberId ?? ''} onChange={set('barberId')}>
            {choices.map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
          </select>
        </div>
        <div className="form__row">
          <div className="field"><label htmlFor="na-d">Tag</label><input id="na-d" type="date" value={form.date} onChange={set('date')} /></div>
          <div className={`field ${errors.start ? 'field--error' : ''}`}>
            <label htmlFor="na-t">Uhrzeit</label>
            <input id="na-t" type="time" value={form.time} step={300} onChange={set('time')} />
            {err('start')}
          </div>
        </div>

        <fieldset className="na-services">
          <legend>Leistungen <em>(eine oder mehrere)</em></legend>
          {groups.map(([cat, items]) => (
            <div key={cat}>
              <p className="na-services__cat">{cat}</p>
              {items.map((s) => (
                <label key={s.id} className="na-service">
                  <input type="checkbox" checked={serviceIds.includes(s.id)} onChange={() => toggle(s.id)} />
                  <span>{s.name}<small>{duration(s.durationMinutes)}</small></span>
                  <b>{euro(s.priceCents)}</b>
                </label>
              ))}
            </div>
          ))}
          {err('serviceIds')}
        </fieldset>
        {chosen.length > 0 && (
          <p className="na-total">{form.time ? `${form.time} – ${end} Uhr` : duration(minutes)} · <b>{euro(cents)}</b></p>
        )}

        <div className="form__row">
          <div className={`field ${errors.firstName ? 'field--error' : ''}`}>
            <label htmlFor="na-fn">Vorname</label><input id="na-fn" value={form.firstName} onChange={set('firstName')} />{err('firstName')}
          </div>
          <div className="field"><label htmlFor="na-ln">Nachname <em>(optional)</em></label><input id="na-ln" value={form.lastName} onChange={set('lastName')} /></div>
        </div>
        <div className={`field ${errors.phone ? 'field--error' : ''}`}>
          <label htmlFor="na-p">Telefon</label>
          <input id="na-p" type="tel" inputMode="tel" value={form.phone} onChange={set('phone')} placeholder="0660 123 45 67" />{err('phone')}
        </div>
        <div className={`field ${errors.email ? 'field--error' : ''}`}>
          <label htmlFor="na-e">E-Mail <em>(optional, dann bekommt der Kunde eine Bestätigung)</em></label>
          <input id="na-e" type="email" value={form.email} onChange={set('email')} />{err('email')}
        </div>
        <div className="field"><label htmlFor="na-n">Notiz <em>(optional)</em></label><input id="na-n" value={form.note} onChange={set('note')} maxLength={300} /></div>
        <button className="btn btn--block" disabled={busy}>{busy ? 'Wird eingetragen …' : 'Termin eintragen'}</button>
      </form>
    </Panel>
  )
}
