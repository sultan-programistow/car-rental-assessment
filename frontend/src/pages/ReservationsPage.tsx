import { useCallback, useEffect, useState } from 'react'
import { api, ApiError } from '../api'
import { fmt, isFuture } from '../ui'
import type { Reservation } from '../types'

/** Newest-first list with cancel (future only - the backend refuses to
 * rewrite started/completed rentals, history is immutable by design). */
export default function ReservationsPage() {
  const [rows, setRows] = useState<Reservation[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [busyId, setBusyId] = useState<number | null>(null)

  const load = useCallback(() => {
    setRows(null)
    api
      .reservations()
      .then(setRows)
      .catch((e) => setError((e as ApiError).message))
  }, [])

  useEffect(() => {
    load()
  }, [load])

  async function cancel(r: Reservation) {
    if (!confirm(`Cancel reservation #${r.id} (${r.carType} for ${r.borrowedBy})?`)) return
    setBusyId(r.id)
    setError(null)
    try {
      await api.cancel(r.id)
      setNotice(`Reservation #${r.id} cancelled.`)
      load()
    } catch (e) {
      setError((e as ApiError).message)
    } finally {
      setBusyId(null)
    }
  }

  return (
    <section>
      <h2>Reservations</h2>
      {notice && <p className="notice ok">{notice}</p>}
      {error && (
        <p className="notice err">
          {error} <button onClick={load}>Refresh</button>
        </p>
      )}
      {rows?.length === 0 && <p className="muted">No reservations yet.</p>}
      {rows && rows.length > 0 && (
        <table>
          <thead>
            <tr>
              <th>#</th>
              <th>Car</th>
              <th>Type</th>
              <th>Pickup</th>
              <th>Return</th>
              <th>Borrowed by</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.id}>
                <td>{r.id}</td>
                <td>
                  <a href={`/cars/${r.carId}`}>
                    #{r.carId}
                  </a>
                </td>
                <td>{r.carType}</td>
                <td>{fmt(r.pickupAt)}</td>
                <td>{fmt(r.returnAt)}</td>
                <td>{r.borrowedBy}</td>
                <td>
                  {isFuture(r.pickupAt) ? (
                    <button
                      className="danger"
                      disabled={busyId === r.id}
                      onClick={() => void cancel(r)}
                    >
                      cancel
                    </button>
                  ) : (
                    <span className="muted">history</span>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  )
}
