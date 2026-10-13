import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { api } from '../lib/api.js'
import { downloadIcs } from '../lib/ics.js'
import {
  dayNumber, duration, euro, hourIn, longDate, longDateOfInstant, monthShort, timeIn, todayIn, weekdayShort,
} from '../lib/time.js'
import Ticket, { appointmentRows, appointmentTotal } from '../components/Ticket.jsx'

/**
 * The booking flow on its own page (/buchen), one step at a time:
 * Leistungen › Barber › Zeit › Bestätigen. A summary card with the shop, the chosen services and the
 * total stays on the right (on phones: a bar at the bottom). The step lives in the URL (?schritt=2),
 * so the browser's back button works as expected.
 */

const STEPS = [
  [1, 'Leistungen', 'Leistungen auswählen'],
  [2, 'Barber', 'Barber auswählen'],
  [3, 'Zeit', 'Zeit auswählen'],
  [4, 'Bestätigen', 'Prüfen und bestätigen'],
]
const STORE = 'mrartist.booking'
const FORM_ID = 'bf-customer-form'

const load = () => {
  try { return JSON.parse(sessionStorage.getItem(STORE)) || {} } catch { return {} }
}
const save = (v) => {
  try { v ? sessionStorage.setItem(STORE, JSON.stringify(v)) : sessionStorage.removeItem(STORE) } catch { /* private mode */ }
}

export default function BookingFlow({ shop }) {
  if (!shop) return <div className="page-center" aria-busy="true"><div className="skeleton" style={{ width: 'min(100%, 720px)', height: 320 }} /></div>
  return <Flow shop={shop} />
}

function Flow({ shop }) {
  const tz = shop.timeZone
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()

  const initial = useMemo(() => {
    const saved = load()
    const known = new Set(shop.services.map((s) => s.id))
    const pre = Number(params.get('leistung'))
    let ids = (saved.serviceIds || []).filter((id) => known.has(id))
    if (known.has(pre) && !ids.includes(pre)) ids = [...ids, pre]
    const barberOk = saved.barber === 'any' || shop.barbers.some((b) => b.id === saved.barber)
    return { ids, barber: barberOk ? saved.barber : null }
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  const [serviceIds, setServiceIds] = useState(initial.ids)
  const [barber, setBarber] = useState(initial.barber) // null | 'any' | id
  const [date, setDate] = useState(null)
  const [slot, setSlot] = useState(null)
  const [booked, setBooked] = useState(null)
  const [notice, setNotice] = useState(null)
  const [sending, setSending] = useState(false)

  useEffect(() => { save({ serviceIds, barber }) }, [serviceIds, barber])

  const chosen = serviceIds.map((id) => shop.services.find((s) => s.id === id)).filter(Boolean)
  const totalMinutes = chosen.reduce((n, s) => n + s.durationMinutes, 0)
  const totalCents = chosen.reduce((n, s) => n + s.priceCents, 0)
  const barberId = barber === 'any' ? null : barber
  const barberObj = shop.barbers.find((b) => b.id === barber)

  // the furthest step the current choices allow; a reload on ?schritt=4 with nothing chosen lands on step 1
  const maxStep = !chosen.length ? 1 : barber === null ? 2 : !slot ? 3 : 4
  const step = Math.min(Number(params.get('schritt')) || 1, maxStep)

  const goTo = (n) => {
    setNotice(null)
    setParams(n === 1 ? {} : { schritt: String(n) })
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }
  const back = () => (step > 1 ? goTo(step - 1) : navigate('/'))

  const toggleService = (id) => {
    setServiceIds((ids) => (ids.includes(id) ? ids.filter((x) => x !== id) : [...ids, id]))
    setSlot(null) // a different length means different free times
  }
  const chooseBarber = (b) => {
    setBarber(b)
    setSlot(null)
    goTo(3)
  }

  const canContinue = step === 1 ? chosen.length > 0 : step === 2 ? barber !== null : step === 3 ? !!slot : !sending
  const next = () => { if (step < 4) goTo(step + 1) }

  const book = async (form) => {
    setSending(true)
    try {
      const appt = await api.book({ serviceIds, barberId, start: slot.start, ...form })
      save(null)
      setBooked(appt)
      window.scrollTo({ top: 0 })
    } catch (err) {
      if (err.status === 409) {
        setSlot(null)
        setParams({ schritt: '3' })
        setNotice(err.message)
        return null
      }
      throw err
    } finally {
      setSending(false)
    }
  }

  if (booked) {
    return (
      <div className="bf">
        <TopBar step={5} onBack={() => navigate('/')} onCrumb={() => {}} />
        <Confirmation appt={booked} onAgain={() => { setBooked(null); setServiceIds([]); setBarber(null); setSlot(null); setDate(null); goTo(1) }} />
      </div>
    )
  }

  const summary = (
    <Summary shop={shop} chosen={chosen} barberLabel={barber === null ? null : barber === 'any' ? (slot ? 'Wer gerade frei ist' : 'Egal, wer frei ist') : barberObj?.name}
             slot={slot} tz={tz} totalMinutes={totalMinutes} totalCents={totalCents} />
  )
  // different keys on purpose: React must not reuse the "Weiter" button as the submit button, otherwise the
  // click that opens step 4 would immediately submit the still empty form
  const action = (
    step < 4
      ? <button key="next" type="button" className="btn btn--block" disabled={!canContinue} onClick={next}>Weiter</button>
      : <button key="submit" className="btn btn--block" type="submit" form={FORM_ID} disabled={sending}>{sending ? 'Wird gebucht …' : 'Verbindlich buchen'}</button>
  )

  return (
    <div className="bf">
      <TopBar step={step} onBack={back} onCrumb={(n) => n < step && goTo(n)} />

      <div className="wrap bf__grid">
        <main className="bf__main">
          <h1 className="bf__title">{STEPS[step - 1][2]}</h1>
          {notice && <p className="alert" role="alert">{notice}</p>}

          {step === 1 && <ServiceStep services={shop.services} selected={serviceIds} onToggle={toggleService} />}
          {step === 2 && <BarberStep barbers={shop.barbers} selected={barber} onSelect={chooseBarber} />}
          {step === 3 && (
            <TimeStep tz={tz} serviceIds={serviceIds} barberId={barberId} date={date} setDate={setDate}
                      slot={slot} onSelect={setSlot} />
          )}
          {step === 4 && (
            <>
              <div className="bf-only-mobile bf-mobile-summary">{summary}</div>
              <CustomerForm onSubmit={book} />
            </>
          )}
        </main>

        <aside className="bf__aside" aria-label="Deine Auswahl">
          <div className="bf-card">
            {summary}
            <div className="bf-card__action">{action}</div>
          </div>
        </aside>
      </div>

      <div className="bf-bar" role="region" aria-label="Auswahl und weiter">
        <div className="bf-bar__info">
          <strong>{chosen.length ? euro(totalCents) : 'Noch nichts gewählt'}</strong>
          {chosen.length > 0 && <span>{chosen.length === 1 ? '1 Leistung' : `${chosen.length} Leistungen`} · {duration(totalMinutes)}</span>}
        </div>
        {action}
      </div>
    </div>
  )
}

// ---- top bar with breadcrumb ---------------------------------------------------------------------

function TopBar({ step, onBack, onCrumb }) {
  return (
    <header className="bf-top">
      <div className="wrap bf-top__inner">
        <button className="bf-icon" onClick={onBack} aria-label="Zurück">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round"><path d="M19 12H5M11 18l-6-6 6-6" /></svg>
        </button>
        <nav aria-label="Buchungsschritte" className="bf-crumbs-nav">
          <ol className="bf-crumbs">
            {STEPS.map(([n, label]) => (
              <li key={n} className={n === step ? 'is-current' : n < step ? 'is-done' : ''}>
                {n < step && step <= 4
                  ? <button onClick={() => onCrumb(n)}>{label}</button>
                  : <span aria-current={n === step ? 'step' : undefined}>{label}</span>}
              </li>
            ))}
          </ol>
        </nav>
        <Link className="bf-icon" to="/" aria-label="Buchung schließen">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round"><path d="M6 6l12 12M18 6L6 18" /></svg>
        </Link>
      </div>
    </header>
  )
}

// ---- summary card --------------------------------------------------------------------------------

function Stars({ value }) {
  return (
    <span className="bf-stars" aria-hidden="true">
      {[1, 2, 3, 4, 5].map((i) => {
        const fill = Math.max(0, Math.min(1, value - i + 1)) * 100
        return <span key={i} className="bf-star" style={{ '--fill': `${fill}%` }}>★</span>
      })}
    </span>
  )
}

export function ShopBadge({ shop }) {
  const rating = shop.rating != null ? Number(shop.rating) : null
  return (
    <div className="bf-shop">
      {shop.photoUrl
        ? <img className="bf-shop__photo" src={shop.photoUrl} alt={`${shop.name} von innen`} />
        : <div className="bf-shop__photo bf-shop__photo--empty" aria-hidden="true"><div className="pole" /></div>}
      <div className="bf-shop__text">
        <strong>{shop.name}</strong>
        {rating != null && (
          <span className="bf-rating">
            <b>{rating.toLocaleString('de-AT', { minimumFractionDigits: 1, maximumFractionDigits: 1 })}</b>
            <Stars value={rating} />
            {shop.reviewCount > 0 && <span className="bf-rating__count">({shop.reviewCount.toLocaleString('de-AT')})</span>}
            <span className="sr-only">{`${rating} von 5 Sternen${shop.reviewCount ? `, ${shop.reviewCount} Bewertungen` : ''}`}</span>
          </span>
        )}
        <span className="bf-shop__addr">{shop.street}, {shop.zipCity}</span>
      </div>
    </div>
  )
}

function Summary({ shop, chosen, barberLabel, slot, tz, totalMinutes, totalCents }) {
  return (
    <>
      <ShopBadge shop={shop} />
      <div className="bf-sum">
        {chosen.length === 0 && <p className="bf-sum__empty">Noch keine Leistung ausgewählt</p>}
        {chosen.length > 0 && (
          <ul className="bf-sum__items">
            {chosen.map((s) => (
              <li key={s.id}>
                <span>{s.name}<small>{duration(s.durationMinutes)}{barberLabel ? ` · ${barberLabel}` : ''}</small></span>
                <b>{euro(s.priceCents)}</b>
              </li>
            ))}
          </ul>
        )}
        {slot && (
          <p className="bf-sum__when">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true"><rect x="3" y="5" width="18" height="16" rx="2" /><path d="M3 10h18M8 3v4M16 3v4" /></svg>
            {longDateOfInstant(slot.start, tz)}, {timeIn(slot.start, tz)} Uhr
          </p>
        )}
        <div className="bf-sum__total">
          <span>Gesamt{chosen.length > 0 && <small>{duration(totalMinutes)} · Bezahlung im Shop</small>}</span>
          <strong>{chosen.length ? euro(totalCents) : 'kostenlos'}</strong>
        </div>
      </div>
    </>
  )
}

// ---- step 1: services ----------------------------------------------------------------------------

function ServiceStep({ services, selected, onToggle }) {
  const groups = useMemo(() => {
    const m = new Map()
    services.forEach((s) => m.set(s.category, [...(m.get(s.category) || []), s]))
    return [...m.entries()]
  }, [services])
  const [active, setActive] = useState(groups[0]?.[0])
  const sections = useRef({})
  const pills = useRef({})

  // highlight the pill of the category that is currently on screen
  useEffect(() => {
    if (!('IntersectionObserver' in window)) return
    const io = new IntersectionObserver((entries) => {
      const visible = entries.filter((e) => e.isIntersecting).sort((a, b) => a.boundingClientRect.top - b.boundingClientRect.top)[0]
      if (visible) setActive(visible.target.dataset.cat)
    }, { rootMargin: '-140px 0px -55% 0px' })
    Object.values(sections.current).forEach((el) => el && io.observe(el))
    return () => io.disconnect()
  }, [groups])

  useEffect(() => {
    pills.current[active]?.scrollIntoView({ block: 'nearest', inline: 'nearest' })
  }, [active])

  const jump = (cat) => {
    setActive(cat)
    sections.current[cat]?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }

  return (
    <>
      {groups.length > 1 && (
        <div className="bf-pills" role="group" aria-label="Kategorien">
          {groups.map(([cat]) => (
            <button key={cat} ref={(el) => (pills.current[cat] = el)} className="bf-pill"
                    aria-pressed={active === cat} onClick={() => jump(cat)}>{cat}</button>
          ))}
        </div>
      )}
      {groups.map(([cat, items]) => (
        <section key={cat} className="bf-group" data-cat={cat} ref={(el) => (sections.current[cat] = el)}>
          <h2>{cat}</h2>
          <div className="bf-services">
            {items.map((s) => {
              const on = selected.includes(s.id)
              return (
                <button key={s.id} className="bf-service" aria-pressed={on} onClick={() => onToggle(s.id)}>
                  <span className="bf-service__text">
                    <span className="bf-service__name">{s.name}</span>
                    <span className="bf-service__meta">{duration(s.durationMinutes)}</span>
                    {s.description && <span className="bf-service__desc">{s.description}</span>}
                    <span className="bf-service__price">{euro(s.priceCents)}</span>
                  </span>
                  <span className="bf-add" aria-hidden="true">
                    {on
                      ? <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round"><path d="M5 12.5l4.5 4.5L19 7.5" /></svg>
                      : <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round"><path d="M12 5v14M5 12h14" /></svg>}
                  </span>
                  <span className="sr-only">{on ? ' – ausgewählt, zum Entfernen tippen' : ' – hinzufügen'}</span>
                </button>
              )
            })}
          </div>
        </section>
      ))}
    </>
  )
}

// ---- step 2: barber ------------------------------------------------------------------------------

function BarberStep({ barbers, selected, onSelect }) {
  return (
    <div className="bf-barbers">
      <button className="bf-barber" aria-pressed={selected === 'any'} onClick={() => onSelect('any')}>
        <span className="avatar avatar--any" aria-hidden="true">✦</span>
        <span className="bf-barber__name">Egal, wer frei ist</span>
        <span className="bf-barber__bio">Die meisten freien Termine</span>
      </button>
      {barbers.map((b) => (
        <button key={b.id} className="bf-barber" aria-pressed={selected === b.id} onClick={() => onSelect(b.id)}>
          <span className="avatar" aria-hidden="true">{b.name[0]}</span>
          <span className="bf-barber__name">{b.name}</span>
          {b.bio && <span className="bf-barber__bio">{b.bio}</span>}
        </button>
      ))}
    </div>
  )
}

// ---- step 3: time --------------------------------------------------------------------------------

function TimeStep({ tz, serviceIds, barberId, date, setDate, slot, onSelect }) {
  const [days, setDays] = useState(null)
  const [slots, setSlots] = useState(null)
  const [error, setError] = useState(null)
  const key = serviceIds.join(',')

  useEffect(() => {
    let alive = true
    setDays(null)
    api.days(serviceIds, barberId, todayIn(tz), 14)
      .then((d) => {
        if (!alive) return
        setDays(d)
        if (!date || !d.some((x) => x.date === date && x.freeSlots > 0)) {
          const first = d.find((x) => x.freeSlots > 0)
          setDate(first ? first.date : null)
        }
      })
      .catch((e) => alive && setError(e.message))
    return () => { alive = false }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key, barberId, tz])

  useEffect(() => {
    if (!date) { setSlots(null); return }
    let alive = true
    setSlots(null)
    api.slots(serviceIds, barberId, date)
      .then((r) => alive && setSlots(r.slots))
      .catch((e) => alive && setError(e.message))
    return () => { alive = false }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [date, key, barberId])

  if (error) return <p className="alert">{error}</p>
  if (!days) return <div className="skeleton" style={{ height: 84 }} />

  const groups = [
    ['Vormittag', (h) => h < 12],
    ['Nachmittag', (h) => h >= 12 && h < 17],
    ['Abend', (h) => h >= 17],
  ]

  return (
    <>
      <div className="days" role="group" aria-label="Tag wählen">
        {days.map((d) => (
          <button key={d.date} className="day" disabled={d.freeSlots === 0} aria-pressed={d.date === date}
                  onClick={() => { setDate(d.date); onSelect(null) }}
                  aria-label={`${longDate(d.date)}, ${d.open ? `${d.freeSlots} freie Termine` : 'geschlossen'}`}>
            <span>{weekdayShort(d.date)}</span>
            <strong>{dayNumber(d.date)}</strong>
            <small>{!d.open ? 'zu' : d.freeSlots > 0 ? monthShort(d.date) : d.date === todayIn(tz) ? 'vorbei' : 'voll'}</small>
          </button>
        ))}
      </div>

      {!date && <p className="empty">In den nächsten 14 Tagen ist leider nichts mehr frei. Ruf am besten direkt im Shop an.</p>}
      {date && <h2 className="bf-date">{longDate(date)}</h2>}
      {date && !slots && <div className="skeleton" style={{ height: 120 }} />}
      {date && slots && slots.length === 0 && <p className="empty">An diesem Tag ist nichts mehr frei. Wähle einen anderen Tag.</p>}
      {date && slots && slots.length > 0 && groups.map(([label, test]) => {
        const items = slots.filter((s) => test(hourIn(s.start, tz)))
        if (!items.length) return null
        return (
          <div className="slot-group" key={label}>
            <h4>{label}</h4>
            <div className="slots">
              {items.map((s) => (
                <button key={s.start} className="slot" aria-pressed={slot?.start === s.start} onClick={() => onSelect(s)}>
                  {timeIn(s.start, tz)}
                </button>
              ))}
            </div>
          </div>
        )
      })}
    </>
  )
}

// ---- step 4: customer details --------------------------------------------------------------------

function CustomerForm({ onSubmit }) {
  const [form, setForm] = useState({ firstName: '', lastName: '', phone: '', email: '', note: '', privacyAccepted: false })
  const [errors, setErrors] = useState({})
  const [message, setMessage] = useState(null)

  const set = (k) => (e) => setForm({ ...form, [k]: e.target.type === 'checkbox' ? e.target.checked : e.target.value })

  const submit = async (e) => {
    e.preventDefault()
    setErrors({}); setMessage(null)
    try {
      await onSubmit(form)
    } catch (err) {
      setErrors(err.fieldErrors || {})
      setMessage(err.message)
    }
  }

  const field = (key, label, props = {}) => (
    <div className={`field ${errors[key] ? 'field--error' : ''}`}>
      <label htmlFor={key}>{label}</label>
      <input id={key} value={form[key]} onChange={set(key)} aria-invalid={!!errors[key]}
             aria-describedby={errors[key] ? `${key}-err` : undefined} {...props} />
      {errors[key] && <p className="field__error" id={`${key}-err`}>{errors[key]}</p>}
    </div>
  )

  return (
    <form id={FORM_ID} className="form bf-form" onSubmit={submit} noValidate>
      {message && <p className="alert" role="alert">{message}</p>}
      <div className="form__row">
        {field('firstName', 'Vorname', { autoComplete: 'given-name', required: true })}
        {field('lastName', 'Nachname', { autoComplete: 'family-name', required: true })}
      </div>
      <div className="form__row">
        {field('phone', 'Telefonnummer', { type: 'tel', autoComplete: 'tel', inputMode: 'tel', placeholder: '0660 123 45 67', required: true })}
        {field('email', <>E-Mail <em>(für die Bestätigung)</em></>, { type: 'email', autoComplete: 'email' })}
      </div>
      <div className="field">
        <label htmlFor="note">Wunsch an den Barber <em>(optional)</em></label>
        <textarea id="note" value={form.note} onChange={set('note')} maxLength={300} placeholder="z. B. Skin Fade, Bart nur konturieren" />
      </div>
      <label className="check">
        <input type="checkbox" checked={form.privacyAccepted} onChange={set('privacyAccepted')} />
        <span>Ich bin einverstanden, dass meine Daten für diesen Termin gespeichert werden. <a href="/datenschutz" target="_blank">Datenschutz</a></span>
      </label>
      {errors.privacyAccepted && <p className="field__error">{errors.privacyAccepted}</p>}
      <p className="bf-policy">Kostenlos stornieren bis 2 Stunden vorher, über den Link in deiner Bestätigung.</p>
    </form>
  )
}

// ---- done ----------------------------------------------------------------------------------------

function Confirmation({ appt, onAgain }) {
  return (
    <section className="book">
      <div className="wrap done" role="status">
        <div className="done__mark" aria-hidden="true">
          <svg width="30" height="30" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round"><path d="M5 12.5l4.5 4.5L19 7.5" /></svg>
        </div>
        <h2 className="section-title">Bis bald, {appt.customerFirstName}!</h2>
        <p className="section-sub">Dein Termin ist gebucht. Speichere dir den Link unten, damit du ihn ändern oder absagen kannst.</p>
        <Ticket title={`${timeIn(appt.start, appt.timeZone)} Uhr`} subtitle={appt.shopName} rows={appointmentRows(appt)} total={appointmentTotal(appt)} />
        <div className="done__actions">
          <button className="btn" onClick={() => downloadIcs(appt)}>In Kalender speichern</button>
          <Link className="btn btn--ghost" to={`/termin/${appt.manageToken}`}>Termin verwalten</Link>
          <button className="btn btn--ghost" onClick={onAgain}>Weiteren Termin buchen</button>
        </div>
      </div>
    </section>
  )
}
