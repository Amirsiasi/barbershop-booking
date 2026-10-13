import { useEffect, useState } from 'react'
import { Route, Routes } from 'react-router-dom'
import { api } from './lib/api.js'
import BookingPage from './pages/BookingPage.jsx'
import ManagePage from './pages/ManagePage.jsx'
import AdminPage from './pages/AdminPage.jsx'
import LegalPage from './pages/LegalPage.jsx'

export default function App() {
  const [shop, setShop] = useState(null)
  const [error, setError] = useState(null)

  const load = () => {
    setError(null)
    api.shop().then(setShop).catch(setError)
  }
  useEffect(load, [])

  useEffect(() => {
    if (shop) document.title = `${shop.name} – Termin buchen`
  }, [shop])

  if (error) {
    return (
      <div className="page-center">
        <div style={{ maxWidth: 420, textAlign: 'center' }}>
          <p className="alert">{error.message}</p>
          <button className="btn" style={{ marginTop: 16 }} onClick={load}>Erneut laden</button>
        </div>
      </div>
    )
  }

  return (
    <Routes>
      <Route path="/" element={<BookingPage shop={shop} />} />
      <Route path="/termin/:token" element={<ManagePage shop={shop} />} />
      <Route path="/admin" element={<AdminPage shop={shop} />} />
      <Route path="/impressum" element={<LegalPage shop={shop} title="Impressum" />} />
      <Route path="/datenschutz" element={<LegalPage shop={shop} title="Datenschutzerklärung" />} />
      <Route path="*" element={<BookingPage shop={shop} />} />
    </Routes>
  )
}
