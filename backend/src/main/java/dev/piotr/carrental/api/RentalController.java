package dev.piotr.carrental.api;

import dev.piotr.carrental.api.dto.AvailabilityDto;
import dev.piotr.carrental.api.dto.ReserveCarRequest;
import dev.piotr.carrental.api.dto.ReservationDto;
import dev.piotr.carrental.service.RentalService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api")
public class RentalController {

    private final RentalService rental;

    public RentalController(RentalService rental) {
        this.rental = rental;
    }

    /**
     * '?' handling for the picker. from is optional (defaults to now); days
     * is required and validated in the service (keeps ONE place, the service,
     * authoritative for business rules and easy to unit test).
     */
    @GetMapping("/car-types/availability")
    public List<AvailabilityDto> availability(@RequestParam(required = false) LocalDateTime from,
                                              @RequestParam Integer days) {
        return rental.availability(from, days);
    }

    @PostMapping("/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationDto reserve(@Valid @RequestBody ReserveCarRequest request) {
        return rental.reserve(request);
    }

    @GetMapping("/reservations")
    public List<ReservationDto> reservations() {
        return rental.listReservations();
    }

    @DeleteMapping("/reservations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable Long id) {
        rental.cancel(id);
    }
}
