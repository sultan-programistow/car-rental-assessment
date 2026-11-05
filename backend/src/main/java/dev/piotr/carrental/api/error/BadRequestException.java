package dev.piotr.carrental.api.error;

/** Client error tied to the request itself (bad input, infeasible value). */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
