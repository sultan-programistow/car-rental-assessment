package dev.piotr.carrental.api;

import dev.piotr.carrental.api.dto.CarDto;
import dev.piotr.carrental.api.dto.CreateCarRequest;
import dev.piotr.carrental.api.dto.ReservationDto;
import dev.piotr.carrental.service.CarService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/cars")
public class CarController {

    private final CarService cars;

    public CarController(CarService cars) {
        this.cars = cars;
    }

    @GetMapping
    public List<CarDto> fleet() {
        return cars.listCars();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CarDto addCar(@Valid @RequestBody CreateCarRequest request) {
        return cars.createCar(request);
    }

    /** Hard delete: DB cascade removes the car's rentals too (documented). */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCar(@PathVariable Long id) {
        cars.deleteCar(id);
    }

    @GetMapping("/{id}/history")
    public List<ReservationDto> history(@PathVariable Long id) {
        return cars.history(id);
    }
}
