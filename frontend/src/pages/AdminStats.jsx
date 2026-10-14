import { useEffect, useMemo, useState } from 'react'
import { api } from '../lib/api.js'
import { addDays, euro, todayIn } from '../lib/time.js'
import { barberColor } from './AdminCalendar.jsx'

/**
 * "Statistik" for the owner: key figures with the change against the period before, revenue and
 * appointments per week / month / year, per barber, top services and where bookings come from.
 * Charts are plain HTML (no chart library): every bar has a hover/focus tooltip, and the table view
 * shows every number without hovering.
 */

const UNITS = [['week', 'Wochen', 'Woche'], ['month', 'Monate', 'Monat'], ['year', 'Jahre', 'Jahr']]
// single-series charts use the shop's own green and brass, so they never look like a barber's colour;
// the sources get their own checked hues (violet / magenta / amber)
const SOURCE = { ONLINE: ['Online gebucht', '#6250d6'], WHATSAPP: ['WhatsApp', '#d55181'], ADMIN: ['Selbst eingetragen', '#e08a10'] }
const REVENUE = '#2b5446'
const COUNT = '#a8792c'

const MONTHS = ['Jän', 'Feb', 'Mär', 'Apr', 'Mai', 'Jun', 'Jul', 'Aug', 'Sep', 'Okt', 'Nov', 'Dez']
const MONTHS_LONG = ['Jänner', 'Februar', 'März', 'April', 'Mai', 'Juni', 'Juli', 'August', 'September', 'Oktober', 'November', 'Dezember']
const d = (iso) => new Date(iso + 'T12:00:00Z')

/** ISO week number of a date string. */
function isoWeek(iso) {
  const t = d(iso)
  t.setUTCDate(t.getUTCDate() + 4 - (t.getUTCDay() || 7))
  const y0 = new Date(Date.UTC(t.getUTCFullYear(), 0, 1))
  return Math.ceil(((t - y0) / 86400000 + 1) / 7)
}

function bucketLabel(unit, start, i) {
  const x = d(start)
  if (unit === 'year') return { short: String(x.getUTCFullYear()), long: `Jahr ${x.getUTCFullYear()}` }
  if (unit === 'month') {
    const m = x.getUTCMonth()
    return { short: MONTHS[m], sub: m === 0 || i === 0 ? String(x.getUTCFullYear()) : null, long: `${MONTHS_LONG[m]} ${x.getUTCFullYear()}` }
  }
  const end = d(addDays(start, 6))
  return {
    short: `KW ${isoWeek(start)}`,
    long: `KW ${isoWeek(start)}: ${x.getUTCDate()}. ${MONTHS[x.getUTCMonth()]} – ${end.getUTCDate()}. ${MONTHS[end.getUTCMonth()]} ${end.getUTCFullYear()}`,
  }
}

function rangeText(unit, from, to) {
  const a = d(from)
  const b = d(to)
  if (unit === 'year') return `${a.getUTCFullYear()} – ${b.getUTCFullYear()}`
  if (unit === 'month') return `${MONTHS[a.getUTCMonth()]} ${a.getUTCFullYear()} – ${MONTHS[b.getUTCMonth()]} ${b.getUTCFullYear()}`
  return `${a.getUTCDate()}. ${MONTHS[a.getUTCMonth()]} – ${b.getUTCDate()}. ${MONTHS[b.getUTCMonth()]} ${b.getUTCFullYear()}`
}

const compact = (n) => (n >= 1000 ? `${Math.round(n / 100) / 10}`.replace('.', ',') + ' Tsd.' : String(n))
const euroShort = (cents) => (cents >= 100000 ? `€ ${compact(Math.round(cents / 100))}` : euro(cents))
const pct = (n) => `${n.toLocaleString('de-AT', { maximumFractionDigits: 0 })} %`

/** "Nice" axis maximum and step, so ticks read 0 / 500 / 1.000 instead of 0 / 437 / 874. */
function niceScale(max, ticks = 4) {
  if (max <= 0) return { top: ticks, step: 1 }
  const raw = max / ticks
  const pow = 10 ** Math.floor(Math.log10(raw))
  const step = [1, 2, 2.5, 5, 10].map((m) => m * pow).find((s) => s >= raw)
  return { top: step * ticks, step }
}

export default function AdminStats({ auth, me }) {
  const tz = me.timeZone
  const [unit, setUnit] = useState('month')
  const [end, setEnd] = useState(() => todayIn(tz))
  const [barberId, setBarberId] = useState('all')
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [table, setTable] = useState(false)

  useEffect(() => {
    let alive = true
    setLoading(true); setError(null)
    api.admin.stats(auth, unit, end, barberId === 'all' ? null : barberId)
      .then((r) => alive && setData(r))
      .catch((e) => alive && setError(e.message))
      .finally(() => alive && setLoading(false))
    return () => { alive = false }
  }, [auth, unit, end, barberId])

  // page through time by a whole window (12 weeks, 12 months, 5 years)
  const shift = (dir) => {
    if (!data) return
    const days = Math.round((d(data.to) - d(data.from)) / 86400000) + 1
    if (dir < 0) { setEnd(addDays(data.from, -1)); return }
    const next = addDays(data.to, days)
    setEnd(next > todayIn(tz) ? todayIn(tz) : next)
  }
  const isCurrent = data && data.to >= todayIn(tz)

  const buckets = useMemo(() => (data?.buckets || []).map((b, i) => ({ ...b, ...bucketLabel(unit, b.start, i) })), [data, unit])
  const unitName = UNITS.find(([u]) => u === unit)
  const rangeLabel = data ? rangeText(unit, data.from, data.to) : ''

  return (
    <main className="wrap stats-page" style={{ paddingBottom: 56 }}>
      <div className="daynav"><h1>Statistik</h1></div>

      <div className="st-filters" role="group" aria-label="Filter">
        <div className="kal-seg" role="group" aria-label="Zeitraum">
          {UNITS.map(([u, l]) => (
            <button key={u} aria-pressed={unit === u} onClick={() => { setUnit(u); setEnd(todayIn(tz)) }}>{l}</button>
          ))}
        </div>
        <div className="kal-datenav">
          <button className="kal-btn kal-btn--icon" onClick={() => shift(-1)} aria-label="Früherer Zeitraum">‹</button>
          <span className="kal-date st-range">{rangeLabel || '…'}</span>
          <button className="kal-btn kal-btn--icon" onClick={() => shift(1)} disabled={isCurrent} aria-label="Späterer Zeitraum">›</button>
        </div>
        <select className="kal-btn kal-select" value={barberId} aria-label="Barber"
                onChange={(e) => setBarberId(e.target.value === 'all' ? 'all' : Number(e.target.value))}>
          <option value="all">Ganzes Team</option>
          {me.barbers.map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
        </select>
        <button className="kal-btn st-table-toggle" aria-pressed={table} onClick={() => setTable((t) => !t)}>
          {table ? 'Diagramme zeigen' : 'Als Tabelle'}
        </button>
      </div>

      {error && <p className="alert" role="alert">{error}</p>}
      {!data && !error && <div className="skeleton" style={{ height: 420 }} />}

      {data && (
        <div className={`st-body ${loading ? 'is-loading' : ''}`} aria-busy={loading}>
          <section className="st-kpis" aria-label="Kennzahlen">
            <Kpi label="Umsatz" value={euro(Math.round(data.totals.revenueCents / 100) * 100)} now={data.totals.revenueCents} before={data.previous.revenueCents} />
            <Kpi label="Termine" value={data.totals.appointments} now={data.totals.appointments} before={data.previous.appointments} />
            <Kpi label="Ø pro Termin" value={euro(data.totals.averageCents)} now={data.totals.averageCents} before={data.previous.averageCents} />
            <Kpi label="Neukunden" value={data.totals.newCustomers} now={data.totals.newCustomers} before={data.previous.newCustomers} />
            <Kpi label="Storniert" value={data.totals.cancelled}
                 note={rate(data.totals.cancelled, data.totals.appointments + data.totals.cancelled)} now={data.totals.cancelled} before={data.previous.cancelled} lowerIsBetter />
            <Kpi label="Nicht erschienen" value={data.totals.noShows}
                 note={rate(data.totals.noShows, data.totals.appointments)} now={data.totals.noShows} before={data.previous.noShows} lowerIsBetter />
          </section>
          <p className="st-note">
            Vergleich mit den {buckets.length} {unitName[1]} davor. Umsatz = bestätigte und erledigte Termine, inklusive bereits gebuchter Termine in der Zukunft. Bezahlt wird im Shop.
          </p>

          {table ? <StatsTable buckets={buckets} data={data} /> : (
            <>
              <div className="st-grid">
                <Card title="Umsatz" subtitle={`pro ${unitName[2]}`}>
                  <Columns items={buckets} value={(b) => b.revenueCents} color={REVENUE} format={euro} axis={euroShort}
                           extra={(b) => `${b.appointments} Termine`} currentLast={isCurrent} />
                </Card>
                <Card title="Termine" subtitle={`pro ${unitName[2]}`}>
                  <Columns items={buckets} value={(b) => b.appointments} color={COUNT} format={(n) => `${n} Termine`} axis={String}
                           extra={(b) => `${b.cancelled} storniert · ${b.noShows} nicht erschienen`} currentLast={isCurrent} />
                </Card>
              </div>
              <div className="st-grid st-grid--3">
                <Card title="Pro Barber" subtitle="Umsatz und Termine">
                  <HBars items={data.barbers.map((b) => ({ key: b.barberId, label: b.name, value: b.revenueCents, sub: `${b.appointments} Termine`, color: barberColor(me.barbers, b.barberId) }))}
                         format={euro} empty="Keine Termine in diesem Zeitraum." />
                </Card>
                <Card title="Beliebteste Leistungen" subtitle="wie oft gebucht">
                  <HBars items={data.services.map((s) => ({ key: s.name, label: s.name, value: s.count, sub: euro(s.revenueCents), color: REVENUE }))}
                         format={(n) => `${n}×`} empty="Noch keine Leistungen gebucht." />
                </Card>
                <Card title="Woher die Termine kommen" subtitle="Anteil aller Termine">
                  <Sources sources={data.sources} />
                </Card>
              </div>
            </>
          )}
        </div>
      )}
    </main>
  )
}

const rate = (part, whole) => (whole > 0 ? `${pct((part / whole) * 100)} Quote` : null)

function Kpi({ label, value, note, now, before, lowerIsBetter = false }) {
  let delta = null
  if (before > 0) {
    const change = ((now - before) / before) * 100
    const good = lowerIsBetter ? change < 0 : change > 0
    delta = Math.abs(change) < 0.5
      ? <span className="st-delta">gleich wie davor</span>
      : (
        <span className={`st-delta ${good ? 'is-good' : 'is-bad'}`}>
          <span aria-hidden="true">{change > 0 ? '▲' : '▼'}</span> {pct(Math.abs(change))} {change > 0 ? 'mehr' : 'weniger'}
        </span>
      )
  } else if (now > 0) {
    delta = <span className="st-delta">neu in diesem Zeitraum</span>
  }
  return (
    <div className="st-kpi">
      <span className="st-kpi__label">{label}</span>
      <strong className="st-kpi__value">{value}</strong>
      {note && <span className="st-kpi__note">{note}</span>}
      {delta}
    </div>
  )
}

function Card({ title, subtitle, children }) {
  return (
    <section className="st-card">
      <h2>{title}{subtitle && <span> {subtitle}</span>}</h2>
      {children}
    </section>
  )
}

/** Column chart, one series. Direct labels only on the highest and the last column; everything else on hover. */
function Columns({ items, value, color, format, axis, extra, currentLast }) {
  const [hover, setHover] = useState(null)
  const max = Math.max(0, ...items.map(value))
  const { top, step } = niceScale(max)
  const ticks = Array.from({ length: Math.round(top / step) + 1 }, (_, i) => i * step)
  const maxIndex = items.findIndex((b) => value(b) === max && max > 0)

  return (
    <div className="st-cols" style={{ '--color': color }}>
      <div className="st-cols__plot">
        {ticks.map((t) => (
          <div key={t} className="st-tick" style={{ bottom: `${(t / top) * 100}%` }}><span>{axis(t)}</span></div>
        ))}
        <div className="st-cols__bars">
          {items.map((b, i) => {
            const v = value(b)
            const h = (v / top) * 100
            const running = currentLast && i === items.length - 1
            const showLabel = v > 0 && (i === maxIndex || i === items.length - 1)
            return (
              <button key={b.start} className={`st-col ${hover === i ? 'is-hover' : ''}`}
                      onPointerEnter={() => setHover(i)} onPointerLeave={() => setHover(null)}
                      onFocus={() => setHover(i)} onBlur={() => setHover(null)}
                      aria-label={`${b.long}: ${format(v)}${running ? ', läuft noch' : ''}`}>
                <span className="st-col__bar-wrap">
                  {showLabel && <span className={`st-col__label ${i === maxIndex ? '' : 'is-secondary'}`} style={{ bottom: `calc(${h}% + 4px)` }}>{axis === String ? v : euroShort(v)}</span>}
                  <span className={`st-col__bar ${running ? 'is-running' : ''}`} style={{ height: `${Math.max(v > 0 ? 1.5 : 0, h)}%` }} />
                </span>
                {hover === i && (
                  <span className="st-tip" role="tooltip">
                    <strong>{format(v)}</strong>
                    <span>{b.long}{running ? ' (läuft noch)' : ''}</span>
                    {extra && <span>{extra(b)}</span>}
                  </span>
                )}
              </button>
            )
          })}
        </div>
      </div>
      <div className="st-cols__x" aria-hidden="true">
        {items.map((b) => <span key={b.start}>{b.short}{b.sub && <small>{b.sub}</small>}</span>)}
      </div>
    </div>
  )
}

/** Horizontal bars, value at the tip. */
function HBars({ items, format, empty }) {
  const max = Math.max(0, ...items.map((x) => x.value))
  if (!items.length || max === 0) return <p className="st-empty">{empty}</p>
  return (
    <ul className="st-hbars">
      {items.map((x) => (
        <li key={x.key} title={`${x.label}: ${format(x.value)} · ${x.sub}`}>
          <span className="st-hbars__label">{x.label}<small>{x.sub}</small></span>
          <span className="st-hbars__track">
            <span className="st-hbars__bar" style={{ width: `${Math.max(1, (x.value / max) * 100)}%`, background: x.color }} />
            <b>{format(x.value)}</b>
          </span>
        </li>
      ))}
    </ul>
  )
}

function Sources({ sources }) {
  const total = sources.reduce((n, s) => n + s.appointments, 0)
  if (!total) return <p className="st-empty">Keine Termine in diesem Zeitraum.</p>
  const order = ['ONLINE', 'WHATSAPP', 'ADMIN'].filter((k) => sources.some((s) => s.source === k && s.appointments > 0))
  const of = (k) => sources.find((s) => s.source === k)?.appointments || 0
  return (
    <>
      <div className="st-stack" role="img" aria-label={order.map((k) => `${SOURCE[k][0]} ${pct((of(k) / total) * 100)}`).join(', ')}>
        {order.map((k) => <span key={k} style={{ flexGrow: of(k), background: SOURCE[k][1] }} title={`${SOURCE[k][0]}: ${of(k)}`} />)}
      </div>
      <ul className="st-legend">
        {order.map((k) => (
          <li key={k}>
            <span className="st-legend__key" style={{ background: SOURCE[k][1] }} />
            <span>{SOURCE[k][0]}</span>
            <b>{pct((of(k) / total) * 100)}</b>
            <small>{of(k)}</small>
          </li>
        ))}
      </ul>
    </>
  )
}

function StatsTable({ buckets, data }) {
  return (
    <div className="st-card st-table-wrap">
      <table className="st-table">
        <thead>
          <tr><th>Zeitraum</th><th>Termine</th><th>Umsatz</th><th>Storniert</th><th>Nicht erschienen</th></tr>
        </thead>
        <tbody>
          {buckets.map((b) => (
            <tr key={b.start}>
              <th scope="row">{b.long}</th><td>{b.appointments}</td><td>{euro(b.revenueCents)}</td><td>{b.cancelled}</td><td>{b.noShows}</td>
            </tr>
          ))}
        </tbody>
        <tfoot>
          <tr><th scope="row">Summe</th><td>{data.totals.appointments}</td><td>{euro(data.totals.revenueCents)}</td><td>{data.totals.cancelled}</td><td>{data.totals.noShows}</td></tr>
        </tfoot>
      </table>
      <h3>Pro Barber</h3>
      <table className="st-table">
        <thead><tr><th>Barber</th><th>Termine</th><th>Umsatz</th></tr></thead>
        <tbody>{data.barbers.map((b) => <tr key={b.barberId}><th scope="row">{b.name}</th><td>{b.appointments}</td><td>{euro(b.revenueCents)}</td></tr>)}</tbody>
      </table>
      <h3>Leistungen</h3>
      <table className="st-table">
        <thead><tr><th>Leistung</th><th>Gebucht</th><th>Umsatz</th></tr></thead>
        <tbody>{data.services.map((s) => <tr key={s.name}><th scope="row">{s.name}</th><td>{s.count}</td><td>{euro(s.revenueCents)}</td></tr>)}</tbody>
      </table>
    </div>
  )
}
