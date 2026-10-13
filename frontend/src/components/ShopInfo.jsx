import { hhmm, isoWeekday, todayIn } from '../lib/time.js'

const DAYS = ['Montag', 'Dienstag', 'Mittwoch', 'Donnerstag', 'Freitag', 'Samstag', 'Sonntag']

export default function ShopInfo({ shop }) {
  const today = isoWeekday(todayIn(shop.timeZone))
  const byDay = Object.fromEntries(shop.openingHours.map((h) => [h.dayOfWeek, h]))
  const mapsUrl = `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(`${shop.name}, ${shop.street}, ${shop.zipCity}`)}`

  return (
    <section className="info" id="info">
      <div className="wrap info__grid">
        <div>
          <h2>Öffnungszeiten</h2>
          <table className="hours">
            <tbody>
              {DAYS.map((name, i) => {
                const h = byDay[i + 1]
                return (
                  <tr key={name} className={today === i + 1 ? 'is-today' : ''}>
                    <td>{name}{today === i + 1 && <span className="sr-only"> (heute)</span>}</td>
                    <td>{h ? `${hhmm(h.open)} – ${hhmm(h.close)}` : 'geschlossen'}</td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
        <div>
          <h2>Gut zu wissen</h2>
          <ul className="highlights">
            {shop.highlights.map((h) => <li key={h}>{h}</li>)}
          </ul>
        </div>
        <div>
          <h2>So findest du uns</h2>
          <p style={{ margin: '0 0 16px' }}>
            {shop.name}<br />
            {shop.street}<br />
            {shop.zipCity}
            {shop.phone && <><br /><a href={`tel:${shop.phone}`}>{shop.phone}</a></>}
          </p>
          <a className="btn btn--ghost" href={mapsUrl} target="_blank" rel="noreferrer">Route in Google Maps</a>
        </div>
      </div>
    </section>
  )
}

export function Footer({ shop }) {
  return (
    <footer>
      <div className="wrap">
        <span>© {new Date().getFullYear()} {shop?.name}</span>
        <span>
          <a href="/impressum">Impressum</a> &nbsp; <a href="/datenschutz">Datenschutz</a> &nbsp; <a href="/admin">Für das Team</a>
        </span>
      </div>
    </footer>
  )
}
