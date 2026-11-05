package dev.piotr.carrental.service;

import dev.piotr.carrental.api.dto.CarDto;
import dev.piotr.carrental.api.dto.CreateCarRequest;
import dev.piotr.carrental.api.dto.ReservationDto;
import dev.piotr.carrental.api.error.NotFoundException;
import dev.piotr.carrental.domain.Car;
import dev.piotr.carrental.repository.CarRepository;
import dev.piotr.carrental.repository.ReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fleet management plus the per-car view for the admin UI. Deleting a car
 * cascades its rentals away in the DATABASE (FK ON DELETE CASCADE) - a
 * documented, deliberate decision: this is a mechanical "remove" button for
 * an assessment; a real product would have an archive.
 */
@Service
public class CarService {

    private final CarRepository cars;
    private final ReservationRepository reservations;
    private final Clock clock;

    public CarService(CarRepository cars, ReservationRepository reservations, Clock clock) {
        this.cars = cars;
        this.reservations = reservations;
        this.clock = clock;
    }

    /** Two queries total (not per-car N+1): cars + all open rentals, joined in memory. */
    public List<CarDto> listCars() {
        List<Car> fleet = cars.findAllByOrderByTypeAscIdAsc();
        Instant now = clock.instant();
        Map<Long, dev.piotr.carrental.domain.Reservation> nextByCar = new LinkedHashMap<>();
        for (var rental : reservations.findByReturnAtAfter(now)) {
            nextByCar.putIfAbsent(rental.getCar().getId(), rental); // earliest pickup first
        }
        return fleet.stream()
                .map(car -> {
                    var next = nextByCar.get(car.getId());
                    return new CarDto(
                            car.getId(), car.getType(), car.getCreatedAt(),
                            next == null ? null : next.getPickupAt(),
                            next == null ? null : next.getReturnAt(),
                            next == null ? null : next.getBorrowedBy());
                })
                .toList();
    }

    @Transactional
    public CarDto createCar(CreateCarRequest request) {
        Car saved = cars.save(new Car(request.type()));
        return new CarDto(saved.getId(), saved.getType(), saved.getCreatedAt(), null, null, null);
    }

    /** DB-level ON DELETE CASCADE wipes the car's rental history with it. */
    @Transactional
    public void deleteCar(Long id) {
        Car car = cars.findById(id)
                .orElseThrow(() -> new NotFoundException("Car %d does not exist".formatted(id)));
        cars.delete(car);
    }

    public List<ReservationDto> history(Long carId) {
        cars.findById(carId)
                .orElseThrow(() -> new NotFoundException("Car %d does not exist".formatted(carId)));
        return reservations.findByCarIdOrderByPickupAtAsc(carId).stream()
                .map(ReservationDto::from)
                .toList();
    }
}
