import { duration, euro, longDateOfInstant, timeIn } from '../lib/time.js'

/** The appointment rendered like a little paper ticket. Used in the summary, the confirmation and the manage page. */
export default function Ticket({ title, subtitle, rows, total, note }) {
  return (
    <div className="ticket">
      <div className="ticket__head">
        <div className="pole" aria-hidden="true" />
        <div>
          <h3>{title}</h3>
          {subtitle && <p>{subtitle}</p>}
        </div>
      </div>
      <div className="ticket__body">
        <dl>
          {rows.map(([k, v]) => (
            <div key={k}>
              <dt>{k}</dt>
              <dd>{v || '–'}</dd>
            </div>
          ))}
        </dl>
        {total !== undefined && (
          <div className="ticket__total">
            <span>Bezahlung im Shop</span>
            <strong>{total}</strong>
          </div>
        )}
        {note && <p className="ticket__note">{note}</p>}
      </div>
    </div>
  )
}

/** Rows for an appointment returned by the API. */
export function appointmentRows(a) {
  const tz = a.timeZone
  return [
    ['Datum', longDateOfInstant(a.start, tz)],
    ['Uhrzeit', `${timeIn(a.start, tz)} – ${timeIn(a.end, tz)} Uhr`],
    ['Leistung', a.serviceName],
    ['Dauer', duration(a.durationMinutes)],
    ['Barber', a.barberName],
    ['Adresse', a.shopAddress],
  ]
}

export const appointmentTotal = (a) => euro(a.priceCents)
