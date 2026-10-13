import { useEffect, useMemo, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../lib/api.js'
import { downloadIcs } from '../lib/ics.js'
import {
  dayNumber, duration, euro, hourIn, longDate, longDateOfInstant, monthShort, timeIn, todayIn, weekdayShort,
} from '../lib/time.js'
import Hero from '../components/Hero.jsx'
import ShopInfo, { Footer } from '../components/ShopInfo.jsx'
import Ticket, { appointmentRows, appointmentTotal } from '../components/Ticket.jsx'

export default function BookingPage({ shop }) {
  if (!shop) return <Loading />
  return (
    <>
      <Hero shop={shop} />
      <Booking shop={shop} />
      <ShopInfo shop={shop} />
      <Footer shop={shop} />
    </>
  )
}

function Loading() {
  return (
    <div className="page-center" aria-busy="true">
      <div style={{ width: 'min(100%, 520px)', display: 'grid', gap: 12 }}>
        <div className="skeleton" style={{ height: 120 }} />
        <div className="skeleton" style={{ height: 60 }} />
        <div className="skeleton" style={{ height: 60 }} />
      </div>
    </div>
  )
}

// ---------------------------------------------------------------------------------------------

function Booking({ shop }) {
  const tz = shop.timeZone
  const [service, setService] = useState(null)
  const [barber, setBarber] = useState(null) // 'any' or a barber id
  const [date, setDate] = useState(null)
  const [slot, setSlot] = useState(null)
  const [booked, setBooked] = useState(null)
  const [editing, setEditing] = useState(null) // step the user re-opened
  const stepRefs = useRef({})

  const barberId = barber === 'any' ? null : barber
  const barberObj = shop.barbers.find((b) => b.id === barber)

  const active = editing ?? (!service ? 1 : barber === null ? 2 : !slot ? 3 : 4)

  // bring the next step into view after a choice (not on first render)
  const firstRender = useRef(true)
  useEffect(() => {
    if (firstRender.current) { firstRender.current = false; return }
    stepRefs.current[active]?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }, [active])

  const choose = (setter, resetFrom) => (value) => {
    setter(value)
    if (resetFrom <= 3) { setSlot(null) }
    if (resetFrom <= 2) { setDate(null) }
    setEditing(null)
  }

  if (booked) return <Confirmation appt={booked} onAgain={() => { setBooked(null); setSlot(null); setService(null); setBarber(null); setDate(null) }} />

  const stepState = (n) => (n === active ? 'active' : n < active || (n === 4 && slot) ? 'done' : 'locked')
  const slotLabel = slot && `${longDateOfInstant(slot.start, tz)}, ${timeIn(slot.start, tz)} Uhr`

  return (
    <section className="book" id="buchen">
      <div className="wrap">
        <h2 className="section-title">Termin buchen</h2>
        <p className="section-sub">In vier Schritten, ohne Konto. Du bekommst sofort eine Bestätigung.</p>

        <div className="book__grid">
          <ol className="steps">
            <Step n={1} title="Leistung" value={service && `${service.name}, ${euro(service.priceCents)}`}
                  state={stepState(1)} onEdit={() => setEditing(1)} refs={stepRefs}>
              <ServicePicker services={shop.services} selected={service} onSelect={choose(setService, 1)} />
            </Step>

            <Step n={2} title="Barber" value={barber === 'any' ? 'Egal, wer gerade frei ist' : barberObj?.name}
                  state={stepState(2)} onEdit={() => setEditing(2)} refs={stepRefs}>
              <BarberPicker barbers={shop.barbers} selected={barber} onSelect={choose(setBarber, 2)} />
            </Step>

            <Step n={3} title="Tag und Uhrzeit" value={slotLabel} state={stepState(3)} onEdit={() => setEditing(3)} refs={stepRefs}>
              {service && barber !== null && (
                <TimePicker tz={tz} service={service} barberId={barberId} date={date} setDate={setDate}
                            slot={slot} onSelect={choose(setSlot, 4)} />
              )}
            </Step>

            <Step n={4} title="Deine Daten" state={active === 4 ? 'active' : 'locked'} refs={stepRefs}>
              {slot && (
                <CustomerForm
                  onSubmit={async (form) => {
                    const appt = await api.book({ serviceId: service.id, barberId, start: slot.start, ...form })
                    setBooked(appt)
                    window.scrollTo({ top: document.getElementById('buchen').offsetTop - 8, behavior: 'smooth' })
                  }}
                  onSlotTaken={() => { setSlot(null); setEditing(null) }}
                  total={euro(service.priceCents)}
                />
              )}
            </Step>
          </ol>

          <aside className="summary" aria-label="Zusammenfassung">
            <Ticket
              title="Dein Termin"
              subtitle={shop.name}
              rows={[
                ['Leistung', service?.name],
                ['Dauer', service && duration(service.durationMinutes)],
                ['Barber', barber === 'any' ? (slot ? 'Wer frei ist' : 'Egal') : barberObj?.name],
                ['Datum', slot ? longDateOfInstant(slot.start, tz) : date && longDate(date)],
                ['Uhrzeit', slot && `${timeIn(slot.start, tz)} Uhr`],
              ]}
              total={service ? euro(service.priceCents) : '–'}
              note="Kostenlos stornieren bis 2 Stunden vorher."
            />
          </aside>
        </div>
      </div>
    </section>
  )
}

function Step({ n, title, value, state, onEdit, refs, children }) {
  return (
    <li className={`step step--${state}`} ref={(el) => (refs.current[n] = el)} aria-current={state === 'active' ? 'step' : undefined}>
      <div className="step__head">
        <span className="step__num" aria-hidden="true">{state === 'done' ? '✓' : n}</span>
        <div>
          <h3 className="step__title">{title}</h3>
          {state === 'done' && value && <p className="step__value">{value}</p>}
        </div>
        {state === 'done' && onEdit && <button className="link-btn" onClick={onEdit}>Ändern<span className="sr-only"> {title}</span></button>}
      </div>
      {state === 'active' && <div className="step__body">{children}</div>}
    </li>
  )
}

// ---- step 1 -----------------------------------------------------------------------------------

function ServicePicker({ services, selected, onSelect }) {
  const groups = useMemo(() => {
    const m = new Map()
    services.forEach((s) => m.set(s.category, [...(m.get(s.category) || []), s]))
    return [...m.entries()]
  }, [services])

  return groups.map(([category, items]) => (
    <div className="cat" key={category}>
      <h3>{category}</h3>
      <div className="options">
        {items.map((s) => (
          <button key={s.id} className="option" aria-pressed={selected?.id === s.id} onClick={() => onSelect(s)}>
            <span className="option__name">{s.name}</span>
            <span className="option__price">{euro(s.priceCents)}</span>
            <span className="option__meta">{duration(s.durationMinutes)}</span>
            {s.description && <span className="option__desc">{s.description}</span>}
          </button>
        ))}
      </div>
    </div>
  ))
}

// ---- step 2 -----------------------------------------------------------------------------------

function BarberPicker({ barbers, selected, onSelect }) {
  return (
    <div className="barbers">
      <button className="option barber" aria-pressed={selected === 'any'} onClick={() => onSelect('any')}>
        <span className="avatar avatar--any" aria-hidden="true">✦</span>
        <span>
          <span className="option__name">Egal, wer frei ist</span>
          <span className="option__meta" style={{ display: 'block' }}>Die meisten freien Termine</span>
        </span>
      </button>
      {barbers.map((b) => (
        <button key={b.id} className="option barber" aria-pressed={selected === b.id} onClick={() => onSelect(b.id)}>
          <span className="avatar" aria-hidden="true">{b.name[0]}</span>
          <span>
            <span className="option__name">{b.name}</span>
            <span className="option__meta" style={{ display: 'block' }}>{b.bio}</span>
          </span>
        </button>
      ))}
    </div>
  )
}

// ---- step 3 -----------------------------------------------------------------------------------

function TimePicker({ tz, service, barberId, date, setDate, slot, onSelect }) {
  const [days, setDays] = useState(null)
  const [slots, setSlots] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    let alive = true
    setDays(null)
    api.days(service.id, barberId, todayIn(tz), 14)
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
  }, [service.id, barberId, tz])

  useEffect(() => {
    if (!date) { setSlots(null); return }
    let alive = true
    setSlots(null)
    api.slots(service.id, barberId, date)
      .then((r) => alive && setSlots(r.slots))
      .catch((e) => alive && setError(e.message))
    return () => { alive = false }
  }, [date, service.id, barberId])

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
                  onClick={() => setDate(d.date)} aria-label={`${longDate(d.date)}, ${d.open ? `${d.freeSlots} freie Termine` : 'geschlossen'}`}>
            <span>{weekdayShort(d.date)}</span>
            <strong>{dayNumber(d.date)}</strong>
            <small>{!d.open ? 'zu' : d.freeSlots > 0 ? monthShort(d.date) : d.date === todayIn(tz) ? 'vorbei' : 'voll'}</small>
          </button>
        ))}
      </div>

      {!date && <p className="empty">In den nächsten 14 Tagen ist leider nichts mehr frei. Ruf am besten direkt im Shop an.</p>}
      {date && !slots && <div className="skeleton" style={{ height: 120, marginTop: 18 }} />}
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

// ---- step 4 -----------------------------------------------------------------------------------

function CustomerForm({ onSubmit, onSlotTaken, total }) {
  const [form, setForm] = useState({ firstName: '', lastName: '', phone: '', email: '', note: '', privacyAccepted: false })
  const [errors, setErrors] = useState({})
  const [message, setMessage] = useState(null)
  const [sending, setSending] = useState(false)

  const set = (k) => (e) => setForm({ ...form, [k]: e.target.type === 'checkbox' ? e.target.checked : e.target.value })

  const submit = async (e) => {
    e.preventDefault()
    setSending(true); setErrors({}); setMessage(null)
    try {
      await onSubmit(form)
    } catch (err) {
      setErrors(err.fieldErrors || {})
      setMessage(err.message)
      if (err.status === 409) onSlotTaken()
    } finally {
      setSending(false)
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
    <form className="form" onSubmit={submit} noValidate>
      {message && <p className="alert" role="alert">{message}</p>}
      <div className="form__row">
        {field('firstName', 'Vorname', { autoComplete: 'given-name', required: true })}
        {field('lastName', 'Nachname', { autoComplete: 'family-name', required: true })}
      </div>
      <div className="form__row">
        {field('phone', 'Telefonnummer', { type: 'tel', autoComplete: 'tel', inputMode: 'tel', placeholder: '0660 123 45 67', required: true })}
        {field('email', <>E-Mail <em>(optional, für die Bestätigung)</em></>, { type: 'email', autoComplete: 'email' })}
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
      <button className="btn btn--block" type="submit" disabled={sending}>
        {sending ? 'Wird gebucht …' : `Verbindlich buchen, ${total}`}
      </button>
    </form>
  )
}

// ---- done ------------------------------------------------------------------------------------

function Confirmation({ appt, onAgain }) {
  return (
    <section className="book" id="buchen">
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
