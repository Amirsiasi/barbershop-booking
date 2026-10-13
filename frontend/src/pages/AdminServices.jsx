import { useEffect, useMemo, useState } from 'react'
import { api } from '../lib/api.js'
import { duration, euro } from '../lib/time.js'
import Panel from '../components/Panel.jsx'

const DURATIONS = Array.from({ length: 48 }, (_, i) => (i + 1) * 5) // 5 … 240 min

/** "28" / "28,50" / "28.5" -> 2850 cents, or null if it is not a valid amount. */
export function parseEuro(text) {
  const t = String(text).trim().replace(/\s|€/g, '').replace(',', '.')
  if (!/^\d+(\.\d{1,2})?$/.test(t)) return null
  return Math.round(Number(t) * 100)
}

const centsToInput = (cents) => (cents % 100 ? (cents / 100).toFixed(2).replace('.', ',') : String(cents / 100))

/** Admin area "Leistungen & Preise": the shop's price list with edit, add and hide. */
export default function AdminServices({ auth }) {
  const [services, setServices] = useState(null)
  const [error, setError] = useState(null)
  const [editing, setEditing] = useState(null) // a service, or {} for a new one
  const [notice, setNotice] = useState(null)

  const load = () => api.admin.services(auth).then(setServices).catch((e) => setError(e.message))
  useEffect(() => { load() }, []) // eslint-disable-line react-hooks/exhaustive-deps

  const groups = useMemo(() => {
    const m = new Map()
    ;(services || []).forEach((s) => m.set(s.category, [...(m.get(s.category) || []), s]))
    return [...m.entries()]
  }, [services])

  const categories = groups.map(([c]) => c)

  const save = async (form, id) => {
    const saved = id ? await api.admin.updateService(auth, id, form) : await api.admin.createService(auth, form)
    setEditing(null)
    setNotice(`„${saved.name}“ gespeichert.`)
    setTimeout(() => setNotice(null), 4000)
    load()
  }

  return (
    <main className="wrap" style={{ paddingBottom: 48 }}>
      <div className="daynav">
        <h1>Leistungen &amp; Preise</h1>
        <button className="btn" onClick={() => setEditing({})}>Neue Leistung</button>
      </div>
      <p className="section-sub" style={{ marginTop: -6 }}>
        Änderungen gelten sofort für neue Buchungen. Bereits gebuchte Termine behalten ihren Preis und ihre Dauer.
      </p>

      {notice && <p className="alert alert--ok" role="status">{notice}</p>}
      {error && <p className="alert" role="alert">{error}</p>}
      {!services && !error && <div className="skeleton" style={{ height: 320 }} />}

      {services && groups.map(([category, items]) => (
        <section className="price-group" key={category}>
          <h2>{category}</h2>
          <ul className="price-list">
            {items.map((s) => (
              <li key={s.id} className={s.active ? '' : 'is-hidden'}>
                <div className="price-list__main">
                  <strong>{s.name}</strong>
                  {s.description && <span>{s.description}</span>}
                </div>
                <span className="price-list__duration">{duration(s.durationMinutes)}</span>
                <span className="price-list__price">{euro(s.priceCents)}</span>
                <span className={`badge ${s.active ? '' : 'badge--CANCELLED'}`}>{s.active ? 'Online buchbar' : 'Ausgeblendet'}</span>
                <button className="btn btn--ghost price-list__edit" onClick={() => setEditing(s)}>
                  Bearbeiten<span className="sr-only"> {s.name}</span>
                </button>
              </li>
            ))}
          </ul>
        </section>
      ))}

      {editing && (
        <ServicePanel service={editing} categories={categories} onClose={() => setEditing(null)} onSave={save} />
      )}
    </main>
  )
}

function ServicePanel({ service, categories, onClose, onSave }) {
  const isNew = !service.id
  const [form, setForm] = useState({
    name: service.name || '',
    category: service.category || categories[0] || '',
    description: service.description || '',
    durationMinutes: service.durationMinutes || 30,
    price: service.priceCents !== undefined ? centsToInput(service.priceCents) : '',
    active: service.active ?? true,
  })
  const [errors, setErrors] = useState({})
  const [message, setMessage] = useState(null)
  const [busy, setBusy] = useState(false)

  const set = (k) => (e) => setForm({ ...form, [k]: e.target.type === 'checkbox' ? e.target.checked : e.target.value })

  const submit = async (e) => {
    e.preventDefault()
    setErrors({}); setMessage(null)
    const priceCents = parseEuro(form.price)
    if (priceCents === null) {
      setErrors({ priceCents: 'Bitte einen Betrag wie 28 oder 28,50 eingeben.' })
      return
    }
    setBusy(true)
    try {
      await onSave({
        name: form.name, category: form.category, description: form.description,
        durationMinutes: Number(form.durationMinutes), priceCents, active: form.active,
        sortOrder: service.sortOrder,
      }, service.id)
    } catch (err) {
      setErrors(err.fieldErrors || {})
      setMessage(err.message)
    } finally {
      setBusy(false)
    }
  }

  const err = (k) => errors[k] && <p className="field__error">{errors[k]}</p>
  const priceChanged = !isNew && parseEuro(form.price) !== null && parseEuro(form.price) !== service.priceCents

  return (
    <Panel title={isNew ? 'Neue Leistung' : 'Leistung bearbeiten'} onClose={onClose}>
      <form className="form" onSubmit={submit} noValidate style={{ marginTop: 16 }}>
        {message && <p className="alert" role="alert">{message}</p>}

        <div className={`field ${errors.name ? 'field--error' : ''}`}>
          <label htmlFor="s-name">Name</label>
          <input id="s-name" value={form.name} onChange={set('name')} maxLength={120} placeholder="z. B. Skin Fade" />
          {err('name')}
        </div>

        <div className={`field ${errors.category ? 'field--error' : ''}`}>
          <label htmlFor="s-cat">Kategorie</label>
          <input id="s-cat" list="s-cats" value={form.category} onChange={set('category')} maxLength={80} />
          <datalist id="s-cats">{categories.map((c) => <option key={c} value={c} />)}</datalist>
          {err('category')}
        </div>

        <div className="form__row">
          <div className={`field ${errors.priceCents ? 'field--error' : ''}`}>
            <label htmlFor="s-price">Preis in €</label>
            <input id="s-price" value={form.price} onChange={set('price')} inputMode="decimal" placeholder="28" autoFocus={!isNew} />
            {err('priceCents')}
          </div>
          <div className={`field ${errors.durationMinutes ? 'field--error' : ''}`}>
            <label htmlFor="s-dur">Dauer</label>
            <select id="s-dur" value={form.durationMinutes} onChange={set('durationMinutes')}>
              {DURATIONS.map((d) => <option key={d} value={d}>{duration(d)}</option>)}
            </select>
            {err('durationMinutes')}
          </div>
        </div>

        {priceChanged && (
          <p className="alert alert--info">
            Neuer Preis: {euro(parseEuro(form.price))} statt {euro(service.priceCents)}. Gilt nur für neue Buchungen.
          </p>
        )}

        <div className={`field ${errors.description ? 'field--error' : ''}`}>
          <label htmlFor="s-desc">Beschreibung <em>(optional, sehen die Kunden)</em></label>
          <textarea id="s-desc" value={form.description} onChange={set('description')} maxLength={300} />
          {err('description')}
        </div>

        <label className="check">
          <input type="checkbox" checked={form.active} onChange={set('active')} />
          <span>Online buchbar. Ohne Häkchen ist die Leistung für Kunden ausgeblendet, bestehende Termine bleiben.</span>
        </label>

        <button className="btn btn--block" disabled={busy}>{busy ? 'Speichert …' : 'Speichern'}</button>
      </form>
    </Panel>
  )
}
