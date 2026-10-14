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
        <div className="info__route">
          <a className="btn" href={mapsUrl} target="_blank" rel="noreferrer">Route zum Barbershop</a>
        </div>
      </div>
    </section>
  )
}

const Icon = {
  instagram: (
    <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" strokeWidth="2.2"><rect x="3" y="3" width="18" height="18" rx="5" /><circle cx="12" cy="12" r="4.2" /><circle cx="17.4" cy="6.6" r="1" fill="currentColor" stroke="none" /></svg>
  ),
  whatsapp: (
    <svg viewBox="0 0 24 24" width="13" height="13" fill="currentColor"><path d="M12 2.2a9.7 9.7 0 0 0-8.4 14.6L2.3 21.7l5-1.3A9.7 9.7 0 1 0 12 2.2Zm0 17.7a8 8 0 0 1-4.1-1.1l-.3-.2-3 .8.8-2.9-.2-.3A8 8 0 1 1 12 19.9Zm4.4-6c-.2-.1-1.4-.7-1.7-.8-.2-.1-.4-.1-.5.1l-.8 1c-.1.2-.3.2-.5.1a6.6 6.6 0 0 1-3.3-2.9c-.2-.4.2-.4.7-1.3.1-.2 0-.3 0-.4l-.8-1.8c-.2-.5-.4-.4-.5-.4h-.5a.9.9 0 0 0-.6.3 2.7 2.7 0 0 0-.9 2c0 1.2.9 2.4 1 2.5.1.2 1.7 2.7 4.2 3.8 1.6.7 2.2.7 3 .6.5-.1 1.4-.6 1.6-1.1.2-.6.2-1 .1-1.1l-.5-.2Z" /></svg>
  ),
  mail: (
    <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinejoin="round"><rect x="3" y="5" width="18" height="14" rx="2" /><path d="m3.5 6 8.5 7 8.5-7" /></svg>
  ),
  phone: (
    <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinejoin="round"><path d="M5 3h4l2 5-2.5 1.5a11 11 0 0 0 6 6L16 13l5 2v4a2 2 0 0 1-2 2A18 18 0 0 1 3 5a2 2 0 0 1 2-2Z" /></svg>
  ),
}

/** "+436601234567" -> "+43 660 1234567" for reading; links use the plain digits. */
const prettyPhone = (p) => p.replace(/^\+43(\d{3})(\d+)$/, '+43 $1 $2')

/**
 * Footer: shop and address, how to reach us, social links. Links appear only when the owner entered them
 * in the admin area (Shop-Auftritt), so nothing on the site points to an empty profile.
 */
export function Footer({ shop }) {
  if (!shop) return null
  const socials = [
    shop.instagram && { key: 'ig', icon: Icon.instagram, label: 'Instagram', href: `https://www.instagram.com/${shop.instagram}/` },
    shop.whatsapp && { key: 'wa', icon: Icon.whatsapp, label: 'WhatsApp', href: `https://wa.me/${shop.whatsapp.replace(/\D/g, '')}` },
    shop.email && { key: 'mail', icon: Icon.mail, label: 'E-Mail', href: `mailto:${shop.email}` },
  ].filter(Boolean)
  const year = new Date().getFullYear()

  return (
    <footer className="ft">
      <div className="ft__frame">
        <div className="ft__cols">
          <div className="ft__col ft__brand">
            <span className="ft__mark" aria-hidden="true"><span className="pole" /></span>
            <div>
              <strong>{shop.name}</strong>
              <p>{shop.street}<br />{shop.zipCity}</p>
            </div>
          </div>

          <div className="ft__col">
            <strong>Kontakt</strong>
            <p>Fragen zu einem Termin? Schreib uns oder komm einfach vorbei.</p>
            <ul className="ft__plain">
              {shop.phone && <li><a href={`tel:${shop.phone.replace(/[^+\d]/g, '')}`}>{shop.phone}</a></li>}
              {shop.whatsapp && <li><a href={`https://wa.me/${shop.whatsapp.replace(/\D/g, '')}`} target="_blank" rel="noreferrer">{prettyPhone(shop.whatsapp)} (WhatsApp)</a></li>}
              {shop.email && <li><a href={`mailto:${shop.email}`}>{shop.email}</a></li>}
              {!shop.phone && !shop.whatsapp && !shop.email && <li><a href="/buchen">Termin online buchen</a></li>}
            </ul>
          </div>

          <div className="ft__col">
            <strong>Folge uns</strong>
            {socials.length > 0 ? (
              <ul className="ft__social">
                {socials.map((s) => (
                  <li key={s.key}>
                    <a href={s.href} target={s.key === 'mail' ? undefined : '_blank'} rel="noreferrer">
                      <span className="ft__icon" aria-hidden="true">{s.icon}</span>{s.label}
                    </a>
                  </li>
                ))}
              </ul>
            ) : <p>Bald auch auf Instagram.</p>}
          </div>
        </div>

        <div className="ft__band" aria-hidden="true" />

        <div className="ft__bottom">
          <span>
            ©{year} {shop.name}
            <span className="ft__dot" aria-hidden="true" />
            <a href="/impressum">Impressum</a>
            <span className="ft__dot" aria-hidden="true" />
            <a href="/datenschutz">Datenschutz</a>
          </span>
          <a href="/admin">Für das Team</a>
        </div>
      </div>
    </footer>
  )
}
