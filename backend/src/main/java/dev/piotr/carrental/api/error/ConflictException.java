package dev.piotr.carrental.api.error;

/**
 * RFC 9110 (section 15.5.10) conflict: the request can't be completed because
 * it collides with the CURRENT STATE of the resource. Our case: the requested
 * car/time window collides with rentals that other sessions may have created
 * a moment ago. Client should re-read state (reload availability) and retry.
 */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
