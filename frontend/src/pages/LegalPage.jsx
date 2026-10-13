import { Link } from 'react-router-dom'
import { Footer } from '../components/ShopInfo.jsx'

/** Placeholder: Impressum and Datenschutzerklärung must be written by/for the shop owner before going live. */
export default function LegalPage({ shop, title }) {
  return (
    <>
      <div className="page-center" style={{ alignItems: 'start' }}>
        <div className="done" style={{ textAlign: 'left', width: '100%' }}>
          <Link to="/">{shop?.name || 'Zur Startseite'}</Link>
          <h1 className="section-title" style={{ marginTop: 20 }}>{title}</h1>
          <p className="alert alert--info">
            Dieser Text fehlt noch. Vor dem Livegang muss hier das {title} des Shops stehen
            (Inhaber, Anschrift, Kontakt, bei der Datenschutzerklärung auch: welche Daten gespeichert werden,
            wie lange, und wer sie verarbeitet, z. B. Hosting und E-Mail-Versand).
          </p>
        </div>
      </div>
      <Footer shop={shop} />
    </>
  )
}
