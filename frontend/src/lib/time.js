// Date helpers. The API speaks UTC instants; everything shown is converted to the SHOP's time zone,
// so a customer abroad still sees the local Vienna time of the appointment.

export const euro = (cents) =>
  new Intl.NumberFormat('de-AT', { style: 'currency', currency: 'EUR', minimumFractionDigits: cents % 100 ? 2 : 0 }).format(cents / 100)

export const duration = (min) => (min < 60 ? `${min} Min.` : `${Math.floor(min / 60)} Std.${min % 60 ? ` ${min % 60} Min.` : ''}`)

/** "2026-10-14" for an instant, in the given zone. */
export const isoDateIn = (instant, tz) =>
  new Intl.DateTimeFormat('en-CA', { timeZone: tz, year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date(instant))

export const todayIn = (tz) => isoDateIn(Date.now(), tz)

/** Add days to an ISO date string without time zone pitfalls. */
export const addDays = (iso, n) => {
  const d = new Date(iso + 'T12:00:00Z')
  d.setUTCDate(d.getUTCDate() + n)
  return d.toISOString().slice(0, 10)
}

const dateParts = (iso) => new Date(iso + 'T12:00:00Z')

export const weekdayShort = (iso) => new Intl.DateTimeFormat('de-AT', { weekday: 'short', timeZone: 'UTC' }).format(dateParts(iso)).replace('.', '')
export const dayNumber = (iso) => Number(iso.slice(8, 10))
export const monthShort = (iso) => new Intl.DateTimeFormat('de-AT', { month: 'short', timeZone: 'UTC' }).format(dateParts(iso))
export const longDate = (iso) =>
  new Intl.DateTimeFormat('de-AT', { weekday: 'long', day: 'numeric', month: 'long', timeZone: 'UTC' }).format(dateParts(iso))

export const timeIn = (instant, tz) =>
  new Intl.DateTimeFormat('de-AT', { hour: '2-digit', minute: '2-digit', timeZone: tz }).format(new Date(instant))

export const longDateOfInstant = (instant, tz) =>
  new Intl.DateTimeFormat('de-AT', { weekday: 'long', day: 'numeric', month: 'long', timeZone: tz }).format(new Date(instant))

/** Hour (0-23) of an instant in the zone, used to group slots into morning / afternoon / evening. */
export const hourIn = (instant, tz) =>
  Number(new Intl.DateTimeFormat('en-GB', { hour: '2-digit', hourCycle: 'h23', timeZone: tz }).format(new Date(instant)))

/** Minutes since midnight in the zone (for positioning blocks in the admin calendar). */
export const minutesIn = (instant, tz) => {
  const [h, m] = new Intl.DateTimeFormat('en-GB', { hour: '2-digit', minute: '2-digit', hourCycle: 'h23', timeZone: tz })
    .format(new Date(instant))
    .split(':')
    .map(Number)
  return h * 60 + m
}

/** ISO weekday 1-7 of a date string. */
export const isoWeekday = (iso) => ((dateParts(iso).getUTCDay() + 6) % 7) + 1

/** UTC instant for a wall-clock time on a date in a zone, e.g. ("2026-10-14", "13:00", "Europe/Vienna"). */
export const instantFor = (iso, hhmm, tz) => {
  const guess = new Date(`${iso}T${hhmm}:00Z`)
  // shift by the zone's offset at that moment (twice, to settle around DST changes)
  let t = guess.getTime()
  for (let i = 0; i < 2; i++) {
    const shown = minutesIn(new Date(t).toISOString(), tz)
    const wanted = Number(hhmm.slice(0, 2)) * 60 + Number(hhmm.slice(3, 5))
    let diff = shown - wanted
    if (diff > 720) diff -= 1440
    if (diff < -720) diff += 1440
    t -= diff * 60000
  }
  return new Date(t).toISOString()
}

export const hhmm = (t) => (t ? t.slice(0, 5) : '')
