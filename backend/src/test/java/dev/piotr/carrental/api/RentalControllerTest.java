package dev.piotr.carrental.api;

import dev.piotr.carrental.api.dto.ReserveCarRequest;
import dev.piotr.carrental.api.error.ApiExceptionHandler;
import dev.piotr.carrental.api.error.BadRequestException;
import dev.piotr.carrental.api.error.ConflictException;
import dev.piotr.carrental.api.error.NotFoundException;
import dev.piotr.carrental.service.RentalService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP contract in a minimal slice: status-code semantics (201/400/409/404)
 * and that the uniform {"message": ...} body arrives. Business rules themselves
 * live in RentalServiceTest - this one only proves the edge wiring.
 */
@WebMvcTest(RentalController.class)
class RentalControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    RentalService rental;

    /*
     * Jackson requires a watched pattern "yyyy-MM-dd'T'HH:mm:ss"; ISO with
     * seconds works everywhere for LocalDateTime.
     */
    private static final String TS = "2026-12-01T09:00:00";

    private static final String BODY = """
            {"carType": "SEDAN",
             "pickupAt": "%s",
             "durationDays": 3,
             "borrowedBy": "tom@example.com"}
            """.formatted(TS);

    @Test
    void reserve_returns201() throws Exception {
        mvc.perform(post("/api/reservations").contentType(APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated());

        ArgumentCaptor<ReserveCarRequest> captor = ArgumentCaptor.forClass(ReserveCarRequest.class);
        verify(rental).reserve(captor.capture());
        assertThat(captor.getValue().pickupAt()).isEqualTo(LocalDateTime.of(2026, 12, 1, 9, 0));
    }

    @Test
    void invalidBody_is400() throws Exception {
        String bad = """
                {"carType": "SEDAN", "pickupAt": "%s", "durationDays": 3, "borrowedBy": "not-an-email"}
                """.formatted(TS);
        mvc.perform(post("/api/reservations").contentType(APPLICATION_JSON).content(bad))
                .andExpect(status().isBadRequest());
    }

    @Test
    void conflict_is409_withMessage() throws Exception {
        when(rental.reserve(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new ConflictException("No SEDAN available for the requested time window"));

        mvc.perform(post("/api/reservations").contentType(APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .contains("No SEDAN available"));
    }

    @Test
    void serviceValidationError_is400() throws Exception {
        when(rental.reserve(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new BadRequestException("pickupAt cannot be in the past"));

        mvc.perform(post("/api/reservations").contentType(APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cancel_unknownId_is404() throws Exception {
        org.mockito.Mockito.doThrow(new NotFoundException("Reservation 7 does not exist"))
                .when(rental).cancel(7L);

        mvc.perform(delete("/api/reservations/7"))
                .andExpect(status().isNotFound());
    }
}
