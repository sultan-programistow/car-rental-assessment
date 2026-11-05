/** Types mirroring the backend API contract. Timestamps arrive as
 * ISO-8601 instants ("2027-01-10T09:00:00Z") - the backend owns the moment,
 * we own the presentation (render in browser-local time). */

export type CarType = 'SEDAN' | 'SUV' | 'VAN'

export const CAR_TYPES: CarType[] = ['SEDAN', 'SUV', 'VAN']

export interface AvailabilityRow {
  type: CarType
  fleetSize: number
  available: number
}

export interface Reservation {
  id: number
  carId: number
  carType: CarType
  pickupAt: string
  returnAt: string
  borrowedBy: string
}

export interface Car {
  id: number
  type: CarType
  createdAt: string
  nextPickupAt: string | null
  nextReturnAt: string | null
  nextBorrowedBy: string | null
}
