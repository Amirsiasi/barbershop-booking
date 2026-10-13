import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api } from '../lib/api.js'
import { downloadIcs } from '../lib/ics.js'
import { longDateOfInstant, timeIn } from '../lib/time.js'
import Ticket, { appointmentRows, appointmentTotal } from '../components/Ticket.jsx'
import { Footer } from '../components/ShopInfo.jsx'

/** Opened from the link in the confirmation: view, save to calendar or cancel - no account needed. */
export default function ManagePage({ shop }) {
  const { token } = useParams()
  const [appt, setAppt] = useState(null)
  const [error, setError] = useState(null)
  const [confirming, setConfirming] = useState(false)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    api.appointment(token).then(setAppt).catch(setError)
  }, [token])

  const cancel = async () => {
    setBusy(true); setError(null)
    try {
      setAppt(await api.cancel(token))
      setConfirming(false)
    } catch (e) {
      setError(e)
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <div className="page-center" style={{ alignItems: 'start' }}>
        <div className="done" style={{ width: '100%' }}>
          <Link to="/" style={{ display: 'inline-block', marginBottom: 20 }}>{shop?.name || 'Zur Startseite'}</Link>

          {!appt && !error && <div className="skeleton" style={{ height: 360 }} />}
          {!appt && error && <p className="alert">{error.status === 404 ? 'Diesen Termin gibt es nicht. Prüfe bitte den Link aus deiner Bestätigung.' : error.message}</p>}

          {appt && (
            <>
              <h1 className="section-title">
                {appt.status === 'CANCELLED' ? 'Termin abgesagt' : `Hallo ${appt.customerFirstName}`}
              </h1>
              <p className="section-sub">
                {appt.status === 'CANCELLED'
                  ? 'Danke, dass du Bescheid gegeben hast. Der Termin ist wieder frei.'
                  : appt.status === 'CONFIRMED' ? 'Hier ist dein Termin.' : 'Dieser Termin liegt in der Vergangenheit.'}
              </p>

              <div style={appt.status === 'CANCELLED' ? { opacity: 0.55 } : undefined}>
                <Ticket title={`${timeIn(appt.start, appt.timeZone)} Uhr`} subtitle={appt.shopName}
                        rows={appointmentRows(appt)} total={appointmentTotal(appt)} />
              </div>

              {error && <p className="alert" role="alert">{error.message}</p>}

              {appt.status === 'CONFIRMED' && !confirming && (
                <div className="done__actions">
                  <button className="btn" onClick={() => downloadIcs(appt)}>In Kalender speichern</button>
                  {appt.cancellable && <button className="btn btn--ghost" onClick={() => setConfirming(true)}>Termin absagen</button>}
                </div>
              )}

              {appt.status === 'CONFIRMED' && !appt.cancellable && (
                <p className="alert alert--info" style={{ marginTop: 16 }}>
                  Online absagen geht nur bis {timeIn(appt.cancelDeadline, appt.timeZone)} Uhr am {longDateOfInstant(appt.cancelDeadline, appt.timeZone)}.
                  {shop?.phone ? <> Bitte ruf uns an: <a href={`tel:${shop.phone}`}>{shop.phone}</a></> : ' Bitte melde dich direkt im Shop.'}
                </p>
              )}

              {confirming && (
                <div className="alert alert--info" style={{ display: 'grid', gap: 12, marginTop: 4 }}>
                  <span>Willst du den Termin wirklich absagen? Das kann nicht rückgängig gemacht werden.</span>
                  <div className="done__actions">
                    <button className="btn btn--danger" onClick={cancel} disabled={busy}>{busy ? 'Wird abgesagt …' : 'Ja, absagen'}</button>
                    <button className="btn btn--ghost" onClick={() => setConfirming(false)}>Termin behalten</button>
                  </div>
                </div>
              )}

              {appt.status !== 'CONFIRMED' && (
                <div className="done__actions"><Link className="btn" to="/#buchen">Neuen Termin buchen</Link></div>
              )}
            </>
          )}
        </div>
      </div>
      <Footer shop={shop} />
    </>
  )
}
