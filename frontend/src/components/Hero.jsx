import { useEffect, useState } from 'react'
import { hhmm, isoWeekday, minutesIn, todayIn } from '../lib/time.js'

const DAY_NAMES = ['', 'Montag', 'Dienstag', 'Mittwoch', 'Donnerstag', 'Freitag', 'Samstag', 'Sonntag']

/** "Jetzt geöffnet bis 19:00" or "Geschlossen, öffnet Montag um 10:00", computed in the shop's time zone. */
export function openStatus(shop, now = Date.now()) {
  const tz = shop.timeZone
  const today = isoWeekday(todayIn(tz))
  const nowMin = minutesIn(new Date(now).toISOString(), tz)
  const toMin = (t) => Number(t.slice(0, 2)) * 60 + Number(t.slice(3, 5))
  const byDay = Object.fromEntries(shop.openingHours.map((h) => [h.dayOfWeek, h]))

  const h = byDay[today]
  if (h && nowMin >= toMin(h.open) && nowMin < toMin(h.close)) {
    return { open: true, text: `Jetzt geöffnet bis ${hhmm(h.close)} Uhr` }
  }
  if (h && nowMin < toMin(h.open)) return { open: false, text: `Öffnet heute um ${hhmm(h.open)} Uhr` }
  for (let i = 1; i <= 7; i++) {
    const d = ((today - 1 + i) % 7) + 1
    if (byDay[d]) {
      const when = i === 1 ? 'morgen' : DAY_NAMES[d]
      return { open: false, text: `Geschlossen, öffnet ${when} um ${hhmm(byDay[d].open)} Uhr` }
    }
  }
  return { open: false, text: 'Derzeit geschlossen' }
}

export default function Hero({ shop }) {
  const [, tick] = useState(0)
  useEffect(() => {
    const id = setInterval(() => tick((n) => n + 1), 60_000)
    return () => clearInterval(id)
  }, [])

  const [first, ...rest] = shop.name.replace(/barbershop/i, '').trim().split(' ')
  const status = openStatus(shop)
  const mapsUrl = `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(`${shop.name}, ${shop.street}, ${shop.zipCity}`)}`

  return (
    <header className="hero">
      <div className="wrap hero__inner">
        <div className="pole hero__pole" aria-hidden="true" />
        <div className="hero__top">
          <span className={`status ${status.open ? '' : 'status--closed'}`}>{status.text}</span>
          <a href="#info">Öffnungszeiten</a>
        </div>
        <div>
          <h1>
            {first} {rest.join(' ')}
            <small>Barbershop in Wien-Hernals</small>
          </h1>
          <p className="hero__lead">{shop.tagline}</p>
          <div className="hero__actions">
            <a className="btn btn--light" href="#buchen">Termin buchen</a>
            <a className="hero__address" href={mapsUrl} target="_blank" rel="noreferrer" style={{ textDecoration: 'none' }}>
              <strong>{shop.street}</strong>
              {shop.zipCity}
            </a>
          </div>
        </div>
      </div>
    </header>
  )
}
