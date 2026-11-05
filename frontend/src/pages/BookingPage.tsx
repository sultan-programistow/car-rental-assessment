import { useCallback, useEffect, useState } from 'react'
import { api, ApiError } from '../api'
import { fmt, tomorrowAt10 } from '../ui'
import { CAR_TYPES, type AvailabilityRow, type CarType } from '../types'

/** All types start as "unknown", rendered as dash until the load resolves. */
type Availability = Record<CarType, AvailabilityRow | null>

/**
 * The picker page: fill in pickup + days + borrower, see live availability
 * per type, and reserve a type straight from ITS row in the table - no
 * separate type select, no separate CTA. The window is global to all
 * types, so "which type" and "reserve" are naturally one action.
 *
 * Reservation buttons live OUTSIDE the <form> element (they sit in the
 * table) but belong to it via form="book-form": clicking one triggers the
 * form's native validation (required pickup/email, days>=1) and submits,
 * with the row's type carried as the button's submitter VALUE - the
 * actual payload authority stays in one submit() handler.
 *
 * On a 409 (someone booked the last car while the manager was filling the
 * form) we never trust the UI: show the server's message and lead the
 * user back to a fresh availability read.
 */
export default function BookingPage() {
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

  async function submit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault()
    // Which row's button started the submission decides the type.
    const submitter = (e.nativeEvent as SubmitEvent).submitter as
      | (HTMLButtonElement & { value: CarType })
      | null
    const carType = submitter?.value
    if (!carType) return

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

  return (
    <section>
      <h2>Book a car</h2>
      <form id="book-form" onSubmit={submit} className="card">
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
        <p className="muted">Pick a type below to reserve it for the window above.</p>
      </form>
      {notice && <p className="notice ok">{notice}</p>}
      {error && (
        <p className="notice err">
          {error} <button onClick={() => void loadAvailability()}>Refresh</button>
        </p>
      )}
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
                      form="book-form"
                      type="submit"
                      name="carType"
                      value={t}
                      disabled={busy || a?.available === 0}
                    >
                      {busy ? 'Booking…' : 'Reserve'}
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
