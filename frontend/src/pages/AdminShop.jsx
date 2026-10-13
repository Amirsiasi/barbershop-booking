import { useEffect, useRef, useState } from 'react'
import { api } from '../lib/api.js'
import { ShopBadge } from './BookingFlow.jsx'

const MAX_EDGE = 1600 // px - plenty for the booking card, keeps uploads small

/** Shrinks the picture in the browser (phone photos are often 5-10 MB) and returns a JPEG blob. */
async function shrink(file) {
  const url = URL.createObjectURL(file)
  try {
    const img = await new Promise((resolve, reject) => {
      const i = new Image()
      i.onload = () => resolve(i)
      i.onerror = () => reject(new Error('Dieses Bild kann nicht gelesen werden. Bitte nimm ein JPG oder PNG.'))
      i.src = url
    })
    const scale = Math.min(1, MAX_EDGE / Math.max(img.naturalWidth, img.naturalHeight))
    const canvas = document.createElement('canvas')
    canvas.width = Math.round(img.naturalWidth * scale)
    canvas.height = Math.round(img.naturalHeight * scale)
    const ctx = canvas.getContext('2d')
    ctx.fillStyle = '#ffffff' // transparent PNGs get a white background instead of black
    ctx.fillRect(0, 0, canvas.width, canvas.height)
    ctx.drawImage(img, 0, 0, canvas.width, canvas.height)
    return await new Promise((resolve) => canvas.toBlob(resolve, 'image/jpeg', 0.85))
  } finally {
    URL.revokeObjectURL(url)
  }
}

const ratingToInput = (r) => (r == null ? '' : Number(r).toFixed(1).replace('.', ','))

/** Admin area "Shop": photo and rating shown in the summary card of the booking page. Owner only. */
export default function AdminShop({ auth, shop }) {
  const [settings, setSettings] = useState(null)
  const [rating, setRating] = useState('')
  const [count, setCount] = useState('')
  const [error, setError] = useState(null)
  const [notice, setNotice] = useState(null)
  const [busy, setBusy] = useState(false)
  const fileInput = useRef(null)

  const apply = (s) => {
    setSettings(s)
    setRating(ratingToInput(s.rating))
    setCount(s.reviewCount == null ? '' : String(s.reviewCount))
  }
  useEffect(() => { api.admin.shopSettings(auth).then(apply).catch((e) => setError(e.message)) }, []) // eslint-disable-line react-hooks/exhaustive-deps

  const flash = (text) => { setNotice(text); setTimeout(() => setNotice(null), 4000) }

  const run = async (fn, ok) => {
    setBusy(true); setError(null)
    try {
      apply(await fn())
      flash(ok)
    } catch (e) {
      setError(Object.values(e.fieldErrors || {})[0] || e.message)
    } finally {
      setBusy(false)
    }
  }

  const upload = (e) => {
    const file = e.target.files?.[0]
    e.target.value = ''
    if (!file) return
    run(async () => api.admin.uploadPhoto(auth, await shrink(file)), 'Foto gespeichert.')
  }

  const saveRating = (e) => {
    e.preventDefault()
    const r = rating.trim() === '' ? null : Number(rating.trim().replace(',', '.'))
    const c = count.trim() === '' ? null : Number(count.trim().replace(/\./g, ''))
    if (r !== null && (Number.isNaN(r) || r < 1 || r > 5)) { setError('Die Bewertung liegt zwischen 1,0 und 5,0.'); return }
    if (c !== null && (!Number.isInteger(c) || c < 0)) { setError('Bitte gib die Anzahl als ganze Zahl ein.'); return }
    run(() => api.admin.saveRating(auth, r === null ? null : Math.round(r * 10) / 10, c), 'Bewertung gespeichert.')
  }

  const preview = settings && { ...shop, rating: settings.rating, reviewCount: settings.reviewCount, photoUrl: settings.photoUrl }

  return (
    <main className="wrap" style={{ paddingBottom: 48 }}>
      <div className="daynav"><h1>Shop-Auftritt</h1></div>
      <p className="section-sub" style={{ marginTop: -6 }}>
        Foto und Bewertung erscheinen auf der Buchungsseite neben der Auswahl. Änderungen sind nach spätestens einer halben Minute online.
      </p>
      {notice && <p className="alert alert--ok" role="status">{notice}</p>}
      {error && <p className="alert" role="alert">{error}</p>}
      {!settings && !error && <div className="skeleton" style={{ height: 320 }} />}

      {settings && (
        <div className="shop-admin">
          <section>
            <h2 className="shop-admin__h">So sieht es aus</h2>
            <div className="bf-card shop-admin__preview"><ShopBadge shop={preview} /></div>
          </section>

          <div className="shop-admin__forms">
            <section className="shop-admin__box">
              <h2 className="shop-admin__h">Foto</h2>
              <p className="shop-admin__hint">Am besten ein helles Querformat-Foto vom Shop. JPG, PNG oder WebP, wird automatisch verkleinert.</p>
              <input ref={fileInput} type="file" accept="image/jpeg,image/png,image/webp" onChange={upload} hidden />
              <div className="shop-admin__actions">
                <button className="btn" disabled={busy} onClick={() => fileInput.current.click()}>
                  {busy ? 'Wird hochgeladen …' : settings.photoUrl ? 'Anderes Foto wählen' : 'Foto hochladen'}
                </button>
                {settings.photoUrl && (
                  <button className="btn btn--ghost" disabled={busy}
                          onClick={() => window.confirm('Foto wirklich entfernen?') && run(() => api.admin.deletePhoto(auth), 'Foto entfernt.')}>
                    Entfernen
                  </button>
                )}
              </div>
            </section>

            <form className="shop-admin__box" onSubmit={saveRating}>
              <h2 className="shop-admin__h">Bewertung</h2>
              <p className="shop-admin__hint">Zum Beispiel aus Google übernehmen. Feld leer lassen, wenn keine Bewertung angezeigt werden soll.</p>
              <div className="form__row">
                <div className="field">
                  <label htmlFor="rating">Sterne (1,0 bis 5,0)</label>
                  <input id="rating" inputMode="decimal" value={rating} onChange={(e) => setRating(e.target.value)} placeholder="4,8" />
                </div>
                <div className="field">
                  <label htmlFor="reviews">Anzahl Bewertungen</label>
                  <input id="reviews" inputMode="numeric" value={count} onChange={(e) => setCount(e.target.value)} placeholder="127" />
                </div>
              </div>
              <div className="shop-admin__actions"><button className="btn" disabled={busy}>Speichern</button></div>
            </form>
          </div>
        </div>
      )}
    </main>
  )
}
