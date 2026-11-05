import { Link, NavLink, Route, Routes } from 'react-router-dom'
import BookingPage from './pages/BookingPage'
import ReservationsPage from './pages/ReservationsPage'
import FleetPage from './pages/FleetPage'
import CarHistoryPage from './pages/CarHistoryPage'
import './App.css'

/** Internal tool for the rental-company manager: no public customer flow,
 * no auth (deliberate scope cut of the assignment). */
export default function App() {
  return (
    <div className="layout">
      <header>
        <h1>🚗 Car Rental — Manager Console</h1>
        <nav>
          <NavLink to="/" end>
            Book a car
          </NavLink>
          <NavLink to="/reservations">Reservations</NavLink>
          <NavLink to="/cars">Fleet</NavLink>
        </nav>
      </header>
      <main>
        <Routes>
          <Route path="/" element={<BookingPage />} />
          <Route path="/reservations" element={<ReservationsPage />} />
          <Route path="/cars" element={<FleetPage />} />
          <Route path="/cars/:carId" element={<CarHistoryPage />} />
          <Route
            path="*"
            element={
              <p>
                Page not found. <Link to="/">Back to booking</Link>
              </p>
            }
          />
        </Routes>
      </main>
      <footer>
        Timestamps are stored as precise instants; you see them in your own local time.
      </footer>
    </div>
  )
}
