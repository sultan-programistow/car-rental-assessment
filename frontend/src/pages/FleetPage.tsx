import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, ApiError } from '../api'
import { fmt } from '../ui'
import { CAR_TYPES, type Car, type CarType } from '../types'

/** Fleet admin: list with status, expandable rows, add & remove car.
 * Remove is a HARD delete: the DB cascades the car's rentals away - the
 * dialog says so explicitly, the manager knows. */
export default function FleetPage() {
  const [cars, setCars] = useState<Car[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [newType, setNewType] = useState<CarType>('SEDAN')
  const [busy, setBusy] = useState(false)

  const load = useCallback(() => {
    setCars(null)
    api
      .cars()
      .then(setCars)
      .catch((e) => setError((e as ApiError).message))
  }, [])

  useEffect(() => {
    load()
  }, [load])

  async function add(e: React.FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      const car = await api.addCar(newType)
      setNotice(`Car #${car.id} (${car.type}) added to the fleet.`)
      load()
    } catch (err) {
      setError((err as ApiError).message)
    } finally {
      setBusy(false)
    }
  }

  async function remove(car: Car) {
    if (!confirm(`Remove car #${car.id} (${car.type})?\n\nAll of its rental history will be deleted (cascade).`))
      return
    setBusy(true)
    setError(null)
    try {
      await api.deleteCar(car.id)
      setNotice(`Car #${car.id} removed (history with it).`)
      load()
    } catch (err) {
      setError((err as ApiError).message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <section>
      <h2>Fleet</h2>
      {notice && <p className="notice ok">{notice}</p>}
      {error && (
        <p className="notice err">
          {error} <button onClick={load}>Refresh</button>
        </p>
      )}
      <form onSubmit={add} className="card row">
        <label>
          New car type
          <select value={newType} onChange={(e) => setNewType(e.target.value as CarType)}>
            {CAR_TYPES.map((t) => (
              <option key={t}>{t}</option>
            ))}
          </select>
        </label>
        <button disabled={busy}>Add car</button>
      </form>
      {cars && cars.length === 0 && <p className="muted">Fleet is empty.</p>}
      {cars && cars.length > 0 && (
        <table>
          <thead>
            <tr>
              <th>Car</th>
              <th>Type</th>
              <th>Status</th>
              <th>Current / next rental</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {cars.map((car) => {
              let status = <span className="badge free">AVAILABLE</span>
              let rental = '—'
              if (car.nextPickupAt) {
                if (new Date(car.nextPickupAt).getTime() <= Date.now()) {
                  status = <span className="badge busy">RENTED</span>
                  rental = `now → ${fmt(car.nextReturnAt)} (${car.nextBorrowedBy})`
                } else {
                  status = <span className="badge booked">BOOKED</span>
                  rental = `${fmt(car.nextPickupAt)} → ${fmt(car.nextReturnAt)} (${car.nextBorrowedBy})`
                }
              }
              return (
                <tr key={car.id}>
                  <td>
                    <Link to={`/cars/${car.id}`}>#{car.id}</Link>
                  </td>
                  <td>{car.type}</td>
                  <td>{status}</td>
                  <td className="muted">{rental}</td>
                  <td>
                    <button className="danger" disabled={busy} onClick={() => void remove(car)}>
                      remove
                    </button>
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      )}
      <p className="muted small">
        Click a car's id to see its full rental history. Removing a car is hard: history cascades
        away with it.
      </p>
    </section>
  )
}
