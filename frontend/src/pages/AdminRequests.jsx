import { useEffect, useRef, useState } from 'react'
import { api } from '../lib/api.js'
import { euro, longDateOfInstant, timeIn } from '../lib/time.js'

/**
 * Admin area "Anfragen": open requests from WhatsApp with confirm / reject, and a WhatsApp simulator
 * that sends messages through exactly the same code path real WhatsApp messages will use.
 */
export default function AdminRequests({ auth, tz, onChange, isOwner = true }) {
  const [requests, setRequests] = useState(null)
  const [error, setError] = useState(null)
  const [busyId, setBusyId] = useState(null)
  const [chatVersion, setChatVersion] = useState(0) // bump to reload the conversation after a decision

  const load = () => api.admin.requests(auth).then((r) => { setRequests(r); onChange?.(r.length) }).catch((e) => setError(e.message))
  useEffect(() => {
    load()
    const id = setInterval(load, 20_000)
    return () => clearInterval(id)
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  const decide = async (r, action) => {
    setBusyId(r.id); setError(null)
    try {
      await (action === 'confirm' ? api.admin.confirm(auth, r.id) : api.admin.reject(auth, r.id))
      await load()
      setChatVersion((v) => v + 1)
    } catch (e) {
      setError(e.message)
    } finally {
      setBusyId(null)
    }
  }

  return (
    <main className="wrap requests-page" style={{ paddingBottom: 48 }}>
      <section>
        <div className="daynav"><h1>{isOwner ? 'Offene Anfragen' : 'Meine offenen Anfragen'}</h1></div>
        <p className="section-sub" style={{ marginTop: -6 }}>
          Anfragen per WhatsApp halten den Termin 2 Stunden frei. Ohne Bestätigung wird er automatisch wieder freigegeben.
        </p>
        {error && <p className="alert" role="alert">{error}</p>}
        {!requests && <div className="skeleton" style={{ height: 160 }} />}
        {requests && requests.length === 0 && (
          <p className="empty">Keine offenen Anfragen. Neue Anfragen erscheinen hier automatisch.</p>
        )}
        <ul className="request-list">
          {requests?.map((r) => (
            <li key={r.id} className="request-card">
              <div className="request-card__when">
                <strong>{timeIn(r.start, tz)}</strong>
                <span>{longDateOfInstant(r.start, tz)}</span>
              </div>
              <div className="request-card__who">
                <strong>{r.customerName}</strong>
                <a href={`tel:${r.phone}`}>{r.phone}</a>
                <span>{r.serviceName}, {euro(r.priceCents)}, bei {r.barberName}</span>
                {r.pendingUntil && <span className="request-card__hold">Reserviert bis {timeIn(r.pendingUntil, tz)} Uhr</span>}
              </div>
              <div className="request-card__actions">
                <button className="btn" disabled={busyId === r.id} onClick={() => decide(r, 'confirm')}>Bestätigen</button>
                <button className="btn btn--ghost" disabled={busyId === r.id} onClick={() => decide(r, 'reject')}>Ablehnen</button>
              </div>
            </li>
          ))}
        </ul>
      </section>

      {isOwner && <Simulator auth={auth} version={chatVersion} onNewRequest={load} />}
    </main>
  )
}

function Simulator({ auth, version, onNewRequest }) {
  const [phone, setPhone] = useState('+43 660 1234567')
  const [profileName, setProfileName] = useState('')
  const [text, setText] = useState('')
  const [messages, setMessages] = useState([])
  const [conversations, setConversations] = useState([])
  const [error, setError] = useState(null)
  const [sending, setSending] = useState(false)
  const bottom = useRef(null)

  const loadConversations = () => api.admin.conversations(auth).then(setConversations).catch(() => {})
  const loadMessages = (p = phone) => api.admin.messages(auth, p).then(setMessages).catch(() => setMessages([]))

  useEffect(() => { loadConversations(); loadMessages() }, [version]) // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => { bottom.current?.scrollIntoView({ block: 'nearest' }) }, [messages])

  const send = async (e) => {
    e.preventDefault()
    if (!text.trim()) return
    setSending(true); setError(null)
    try {
      setMessages(await api.admin.simulate(auth, { phone, profileName: profileName || null, text }))
      setText('')
      loadConversations()
      onNewRequest()
    } catch (err) {
      setError(Object.values(err.fieldErrors || {})[0] || err.message)
    } finally {
      setSending(false)
    }
  }

  const open = (p) => { setPhone(p); loadMessages(p) }

  return (
    <section className="simulator">
      <div className="daynav"><h1>WhatsApp-Simulator</h1></div>
      <p className="section-sub" style={{ marginTop: -6 }}>
        Schreib hier wie ein Kunde. Die Antworten kommen von genau dem Ablauf, der später echte WhatsApp-Nachrichten beantwortet.
      </p>

      <div className="simulator__grid">
        <div className="phone">
          <div className="phone__head">
            <span className="avatar" aria-hidden="true">✂</span>
            <div>
              <strong>Mr. Artist Barbershop</strong>
              <span>Nachrichten an {phone}</span>
            </div>
          </div>
          <div className="phone__chat" aria-live="polite">
            {messages.length === 0 && (
              <p className="phone__hint">Probier z. B. „Amir 11:30 15.10“ oder „morgen 17 Uhr bei Sami“.</p>
            )}
            {messages.map((m) => (
              <div key={m.id} className={`bubble bubble--${m.direction === 'IN' ? 'me' : 'shop'}`}>{m.text}</div>
            ))}
            <div ref={bottom} />
          </div>
          <form className="phone__input" onSubmit={send}>
            <input value={text} onChange={(e) => setText(e.target.value)} placeholder="Nachricht" aria-label="Nachricht" maxLength={1000} />
            <button className="btn" disabled={sending || !text.trim()}>Senden</button>
          </form>
        </div>

        <div className="simulator__side">
          {error && <p className="alert" role="alert">{error}</p>}
          <div className="field">
            <label htmlFor="sim-phone">Absender (Telefonnummer)</label>
            <input id="sim-phone" value={phone} onChange={(e) => setPhone(e.target.value)} onBlur={() => loadMessages()} />
          </div>
          <div className="field">
            <label htmlFor="sim-name">WhatsApp-Profilname <em>(optional)</em></label>
            <input id="sim-name" value={profileName} onChange={(e) => setProfileName(e.target.value)} placeholder="z. B. Amir" />
          </div>
          {conversations.length > 0 && (
            <>
              <h3 className="simulator__h">Unterhaltungen</h3>
              <ul className="conversations">
                {conversations.map((c) => (
                  <li key={c.phone}>
                    <button onClick={() => open(c.phone)} aria-current={c.phone.replace(/\s/g, '') === phone.replace(/\s/g, '') ? 'true' : undefined}>
                      <strong>{c.name || c.phone}</strong>
                      <span>{c.lastDirection === 'OUT' ? 'Shop: ' : ''}{c.lastText}</span>
                    </button>
                  </li>
                ))}
              </ul>
            </>
          )}
        </div>
      </div>
    </section>
  )
}
