import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { openStatus } from './Hero.jsx'

/**
 * The opening scene, driven by scrolling (one orchestrated moment, everything after it stays calm):
 *
 *   1. black screen, the name "Mr. Artist" huge, a barber mirror (arch) in the middle
 *   2. while scrolling, the two words slide apart and the mirror opens until it fills the screen
 *   3. the mirror shows the shop photo in black and white - or moving barber-pole stripes until the
 *      owner uploads a photo in the admin area
 *   4. a pair of scissors cuts along a dashed line at the bottom: that line is the scroll progress
 *
 * The scroll position is written into one CSS variable (--p, 0..1); CSS does all the drawing.
 * With "reduce motion" the scene is shown still, in its open state.
 */
export default function ScrollHero({ shop }) {
  const section = useRef(null)
  const [reduced] = useState(() => window.matchMedia?.('(prefers-reduced-motion: reduce)').matches)
  const [, tick] = useState(0)

  useEffect(() => {
    const id = setInterval(() => tick((n) => n + 1), 60_000) // keep "open now" correct
    return () => clearInterval(id)
  }, [])

  useEffect(() => {
    const el = section.current
    if (!el) return
    if (reduced) { el.style.setProperty('--p', '1'); return }
    let frame = 0
    const update = () => {
      frame = 0
      const r = el.getBoundingClientRect()
      const total = r.height - window.innerHeight
      const p = total > 0 ? Math.min(1, Math.max(0, -r.top / total)) : 1
      el.style.setProperty('--p', p.toFixed(4))
      el.style.setProperty('--s', Math.abs(Math.sin(p * Math.PI * 16)).toFixed(3)) // scissors snip while cutting
      el.classList.toggle('is-open', p > 0.82)
    }
    const onScroll = () => { if (!frame) frame = requestAnimationFrame(update) }
    update()
    window.addEventListener('scroll', onScroll, { passive: true })
    window.addEventListener('resize', onScroll)
    return () => {
      window.removeEventListener('scroll', onScroll)
      window.removeEventListener('resize', onScroll)
      cancelAnimationFrame(frame)
    }
  }, [reduced])

  const status = openStatus(shop)
  const [first, ...rest] = shop.name.replace(/barbershop/i, '').trim().split(' ')
  const mapsUrl = `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(`${shop.name}, ${shop.street}, ${shop.zipCity}`)}`

  return (
    <header ref={section} className={`sh ${reduced ? 'is-still is-open' : ''}`} style={{ '--p': 0 }}>
      <div className="sh__stage">
        <div className="sh__bar">
          <span className={`status ${status.open ? '' : 'status--closed'}`}>{status.text}</span>
          <Link className="sh__book" to="/buchen">Termin buchen</Link>
        </div>

        <div className={`sh__mirror ${shop.photoUrl ? '' : 'sh__mirror--stripes'}`} aria-hidden={!shop.photoUrl}>
          {shop.photoUrl
            ? <img src={shop.photoUrl} alt={`${shop.name} in Wien-Hernals`} />
            : <div className="sh__stripes" />}
          <div className="sh__grain" aria-hidden="true" />
        </div>

        <h1 className="sh__title">
          <span className="sh__word sh__word--l"><span>{first}</span></span>
          <span className="sh__word sh__word--r"><span>{rest.join(' ')}</span></span>
          <span className="sr-only"> Barbershop in Wien-Hernals</span>
        </h1>

        <div className="sh__end">
          <p className="sh__lead">{shop.tagline}</p>
          <div className="sh__actions">
            <Link className="btn btn--light" to="/buchen">Termin buchen</Link>
            <a className="sh__addr" href={mapsUrl} target="_blank" rel="noreferrer">{shop.street}, {shop.zipCity}</a>
          </div>
        </div>

        <div className="sh__cut" aria-hidden="true">
          <span className="sh__cut-hint">Scrollen</span>
          <span className="sh__cut-line" />
          <svg className="sh__scissors" viewBox="0 0 48 32" width="40" height="28">
            <g fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round">
              <circle cx="8" cy="7" r="5.5" />
              <circle cx="8" cy="25" r="5.5" />
              <path className="sh__blade sh__blade--a" d="M12.5 10 L44 21" />
              <path className="sh__blade sh__blade--b" d="M12.5 22 L44 11" />
            </g>
          </svg>
        </div>
      </div>
    </header>
  )
}
