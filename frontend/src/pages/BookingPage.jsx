import { useMemo } from 'react'
import { Link } from 'react-router-dom'
import { duration, euro } from '../lib/time.js'
import Hero from '../components/Hero.jsx'
import ShopInfo, { Footer } from '../components/ShopInfo.jsx'
import { ShopBadge } from './BookingFlow.jsx'

/** Start page: who we are, the price list (every line leads straight into the booking) and opening hours. */
export default function BookingPage({ shop }) {
  if (!shop) return <Loading />
  return (
    <>
      <Hero shop={shop} />
      <PriceList shop={shop} />
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

function PriceList({ shop }) {
  const groups = useMemo(() => {
    const m = new Map()
    shop.services.forEach((s) => m.set(s.category, [...(m.get(s.category) || []), s]))
    return [...m.entries()]
  }, [shop.services])

  return (
    <section className="book" id="buchen">
      <div className="wrap pricelist">
        <div>
          <h2 className="section-title">Leistungen & Preise</h2>
          <p className="section-sub">Tipp auf eine Leistung, um direkt zu buchen. Mehrere Leistungen in einem Termin sind kein Problem.</p>
          {groups.map(([cat, items]) => (
            <div key={cat} className="pricelist__group">
              <h3>{cat}</h3>
              <ul>
                {items.map((s) => (
                  <li key={s.id}>
                    <Link to={`/buchen?leistung=${s.id}`} className="pricelist__row">
                      <span>
                        <strong>{s.name}</strong>
                        <small>{duration(s.durationMinutes)}{s.description ? ` · ${s.description}` : ''}</small>
                      </span>
                      <b>{euro(s.priceCents)}</b>
                      <span className="pricelist__go" aria-hidden="true">›</span>
                    </Link>
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>
        <aside className="pricelist__aside">
          <div className="bf-card">
            <ShopBadge shop={shop} />
            <div className="bf-card__action">
              <Link className="btn btn--block" to="/buchen">Termin buchen</Link>
              <p className="pricelist__hint">Ohne Konto, sofort bestätigt. Kostenlos stornieren bis 2 Stunden vorher.</p>
            </div>
          </div>
        </aside>
      </div>
    </section>
  )
}
