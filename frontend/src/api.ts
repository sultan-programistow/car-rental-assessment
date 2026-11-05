import axios from 'axios'
import type { AvailabilityRow, Car, CarType, Reservation } from './types'

/**
 * One axios client for the whole app. The response interceptor normalizes
 * every failure into ApiError{status,message} so pages render exactly one
 * thing: the backend's {"message": ...} text (409 conflict wording comes
 * from the server - the arbiter of the race - not from the UI).
 *
 * Base URL: same-origin '/api' in dev (Vite proxy) AND in a single-origin
 * deployment; cross-origin deployments set VITE_API_URL to the backend's
 * origin (the backend then opt-ins that origin via CORS).
 */
const client = axios.create({ baseURL: `${(import.meta.env.VITE_API_URL as string | undefined) ?? ''}/api` })

export class ApiError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

client.interceptors.response.use(
  (r) => r,
  (error) => {
    const status: number = error.response?.status ?? 0
    const message: string =
      error.response?.data?.message ??
      (status ? `Request failed with status ${status}` : 'Backend unreachable - is it running?')
    return Promise.reject(new ApiError(status, message))
  },
)

const unwrap = <T,>(p: Promise<{ data: T }>) => p.then((r) => r.data)

export const api = {
  availability: (from: string | null, days: number): Promise<AvailabilityRow[]> =>
    unwrap(
      client.get('/car-types/availability', {
        params: { from: from || undefined, days },
      }),
    ),

  reserve: (body: {
    carType: CarType
    pickupAt: string
    durationDays: number
    borrowedBy: string
  }): Promise<Reservation> => unwrap(client.post('/reservations', body)),

  reservations: (): Promise<Reservation[]> => unwrap(client.get('/reservations')),

  cancel: (id: number): Promise<void> => unwrap(client.delete(`/reservations/${id}`)),

  cars: (): Promise<Car[]> => unwrap(client.get('/cars')),

  addCar: (type: CarType): Promise<Car> => unwrap(client.post('/cars', { type })),

  deleteCar: (id: number): Promise<void> => unwrap(client.delete(`/cars/${id}`)),

  history: (carId: number | string): Promise<Reservation[]> =>
    unwrap(client.get(`/cars/${carId}/history`)),
}
