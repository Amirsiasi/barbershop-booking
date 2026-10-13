// Small fetch wrapper. All calls go to /api on the same origin (Vite proxy in dev, nginx in production).

export const SHOP_SLUG = import.meta.env.VITE_SHOP_SLUG || 'mr-artist'

export class ApiError extends Error {
  constructor(status, body) {
    // body without our ApiError format (proxy error, CORS rejection, ...) -> show the status code
    super(body?.message || `Etwas ist schiefgelaufen (Fehler ${status}). Bitte versuche es noch einmal.`)
    this.status = status
    this.fieldErrors = Object.fromEntries((body?.validationErrors || []).map((v) => [v.field, v.message]))
  }
}

async function request(path, { method = 'GET', body, auth } = {}) {
  const headers = {}
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (auth) headers['Authorization'] = 'Basic ' + auth

  let res
  try {
    res = await fetch(`/api${path}`, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) })
  } catch {
    throw new ApiError(0, { message: 'Keine Verbindung zum Server. Bitte prüfe deine Internetverbindung.' })
  }
  if (res.status === 204) return null
  const data = await res.json().catch(() => null)
  if (!res.ok) throw new ApiError(res.status, data)
  return data
}

const q = (params) =>
  '?' + new URLSearchParams(Object.entries(params).filter(([, v]) => v !== null && v !== undefined && v !== '')).toString()

export const api = {
  shop: () => request(`/shops/${SHOP_SLUG}`),
  days: (serviceId, barberId, from, days = 14) =>
    request(`/shops/${SHOP_SLUG}/availability/days` + q({ serviceId, barberId, from, days })),
  slots: (serviceId, barberId, date) => request(`/shops/${SHOP_SLUG}/availability` + q({ serviceId, barberId, date })),
  book: (payload) => request(`/shops/${SHOP_SLUG}/appointments`, { method: 'POST', body: payload }),
  appointment: (token) => request(`/appointments/${token}`),
  cancel: (token) => request(`/appointments/${token}/cancel`, { method: 'POST' }),

  admin: {
    me: (auth) => request('/admin/me', { auth }),
    day: (auth, date) => request('/admin/day' + q({ date }), { auth }),
    setStatus: (auth, id, status) => request(`/admin/appointments/${id}`, { method: 'PATCH', body: { status }, auth }),
    block: (auth, payload) => request('/admin/absences', { method: 'POST', body: payload, auth }),
    unblock: (auth, id) => request(`/admin/absences/${id}`, { method: 'DELETE', auth }),
    requests: (auth) => request('/admin/requests', { auth }),
    confirm: (auth, id) => request(`/admin/appointments/${id}/confirm`, { method: 'POST', auth }),
    reject: (auth, id) => request(`/admin/appointments/${id}/reject`, { method: 'POST', auth }),
    conversations: (auth) => request('/admin/chat/conversations', { auth }),
    messages: (auth, phone) => request('/admin/chat/messages?' + new URLSearchParams({ phone }), { auth }),
    simulate: (auth, msg) => request('/admin/chat/simulate', { method: 'POST', body: msg, auth }),
    services: (auth) => request('/admin/services', { auth }),
    createService: (auth, form) => request('/admin/services', { method: 'POST', body: form, auth }),
    updateService: (auth, id, form) => request(`/admin/services/${id}`, { method: 'PUT', body: form, auth }),
  },
}
