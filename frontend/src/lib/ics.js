// "Add to calendar" without any service: build a tiny .ics file in the browser.

const stamp = (iso) => new Date(iso).toISOString().replace(/[-:]/g, '').replace(/\.\d{3}/, '')
const esc = (s) => String(s).replace(/([,;\\])/g, '\\$1').replace(/\n/g, '\\n')

export function downloadIcs(appt) {
  const lines = [
    'BEGIN:VCALENDAR',
    'VERSION:2.0',
    'PRODID:-//BarberBook//DE',
    'BEGIN:VEVENT',
    `UID:${appt.manageToken}@barberbook`,
    `DTSTAMP:${stamp(new Date().toISOString())}`,
    `DTSTART:${stamp(appt.start)}`,
    `DTEND:${stamp(appt.end)}`,
    `SUMMARY:${esc(`${appt.serviceName} bei ${appt.barberName}`)}`,
    `LOCATION:${esc(`${appt.shopName}, ${appt.shopAddress}`)}`,
    `DESCRIPTION:${esc(`Termin verwalten: ${window.location.origin}/termin/${appt.manageToken}`)}`,
    'BEGIN:VALARM',
    'TRIGGER:-PT2H',
    'ACTION:DISPLAY',
    'DESCRIPTION:Termin beim Barber',
    'END:VALARM',
    'END:VEVENT',
    'END:VCALENDAR',
  ]
  const blob = new Blob([lines.join('\r\n')], { type: 'text/calendar;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = 'termin.ics'
  document.body.appendChild(a)
  a.click()
  a.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
