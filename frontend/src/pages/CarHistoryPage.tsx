import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api, ApiError } from '../api'
import { fmt } from '../ui'
import type { Reservation } from '../types'

/** Per-car borrow history (the page that justifies immutable history). */
export default function CarHistoryPage() {
  const { carId } = useParams()
  const [rows, setRows] = useState<Reservation[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    api
      .history(carId!)
      .then((r) => {
        if (!cancelled) setRows(r)
      })
      .catch((e) => {
        if (!cancelled) setError((e as ApiError).message)
      })
    return () => {
      cancelled = true
    }
  }, [carId])

  return (
    <section>
      <h2>
        Car #{carId} — history{' '}
      </h2>
      <p>
        <Link to="/cars">← back to fleet</Link>
      </p>
      {error && <p className="notice err">{error}</p>}
      {rows?.length === 0 && <p className="muted">This car has never been rented.</p>}
      {rows && rows.length > 0 && (
        <table>
          <thead>
            <tr>
              <th>#</th>
              <th>Type</th>
              <th>Pickup</th>
              <th>Return</th>
              <th>Borrowed by</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.id}>
                <td>{r.id}</td>
                <td>{r.carType}</td>
                <td>{fmt(r.pickupAt)}</td>
                <td>{fmt(r.returnAt)}</td>
                <td>{r.borrowedBy}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  )
}
