import { useCallback, useEffect, useState } from 'react'
import { api, ApiError } from '../api'
import { fmt, tomorrowAt10 } from '../ui'
import { CAR_TYPES, type AvailabilityRow, type CarType } from '../types'

/** All types start as "unknown", rendered as dash until the load resolves. */
type Availability = Record<CarType, AvailabilityRow | null>

/**
 * The picker page: choose type + pickup + days, see live availability per
 * type, create the reservation. On a 409 (someone booked the last car while
 * the manager was filling the form) we never trust the UI: show the server's
 * message and lead the user back to a fresh availability read.
 */
export default function BookingPage() {
  const [carType, setCarType] = useState<CarType>('SEDAN')
  const [pickupAt, setPickupAt] = useState(tomorrowAt10())
  const [days, setDays] = useState(3)
  const [borrowedBy, setBorrowedBy] = useState('')

  const [availability, setAvailability] = useState<Availability | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const loadAvailability = useCallback(async () => {
    setError(null)
    setAvailability(null)
    try {
      const rows = await api.availability(pickupAt || null, days)
      const next = { SEDAN: null, SUV: null, VAN: null } as Availability
      for (const row of rows) next[row.type] = row
      setAvailability(next)
    } catch (e) {
      setError((e as ApiError).message)
    }
  }, [pickupAt, days])

  useEffect(() => {
    void loadAvailability()
  }, [loadAvailability])

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    setNotice(null)
    try {
      const r = await api.reserve({
        carType,
        pickupAt,
        durationDays: days,
        borrowedBy,
      })
      setNotice(`Booked ${r.carType} #${r.carId} for ${r.borrowedBy}: ${fmt(r.pickupAt)} → ${fmt(r.returnAt)}`)
      await loadAvailability()
    } catch (err) {
      const e = err as ApiError
      setError(e.status === 409 ? `${e.message} Availability refreshed below - pick again.` : e.message)
      if (e.status === 409) await loadAvailability()
    } finally {
      setBusy(false)
    }
  }

  const selected = availability?.[carType] ?? null
  const noneAvailable = selected?.available === 0

  return (
    <section>
      <h2>Book a car</h2>
      {notice && <p className="notice ok">{notice}</p>}
      {error && (
        <p className="notice err">
          {error} <button onClick={() => void loadAvailability()}>Refresh</button>
        </p>
      )}
      <form onSubmit={submit} className="card">
        <label>
          Car type
          <select value={carType} onChange={(e) => setCarType(e.target.value as CarType)}>
            {CAR_TYPES.map((t) => (
              <option key={t} value={t}>
                {t}
              </option>
            ))}
          </select>
        </label>
        <label>
          Pickup (your local time)
          <input
            type="datetime-local"
            value={pickupAt}
            onChange={(e) => setPickupAt(e.target.value)}
            required
          />
        </label>
        <label>
          Days
          <input
            type="number"
            min={1}
            value={days}
            onChange={(e) => setDays(Number(e.target.value))}
            required
          />
        </label>
        <label>
          Borrower email
          <input
            type="email"
            placeholder="customer@example.com"
            value={borrowedBy}
            onChange={(e) => setBorrowedBy(e.target.value)}
            required
          />
        </label>
        <button type="submit" disabled={busy || noneAvailable}>
          {noneAvailable ? `${carType} fully booked for that window` : busy ? 'Booking…' : 'Reserve'}
        </button>
      </form>
      <h3>Availability for the chosen window</h3>
      {availability ? (
        <table>
          <thead>
            <tr>
              <th>Type</th>
              <th>Available / fleet</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {CAR_TYPES.map((t) => {
              const a = availability[t]
              return (
                <tr key={t} className={a?.available === 0 ? 'exhausted' : ''}>
                  <td>{t}</td>
                  <td>{a ? `${a.available} / ${a.fleetSize}` : 'no cars of this type'}</td>
                  <td>
                    <button
                      onClick={() => setCarType(t)}
                      disabled={busy || a?.available === 0}
                    >
                      pick
                    </button>
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      ) : (
        <p className="muted">loading…</p>
      )}
    </section>
  )
}
