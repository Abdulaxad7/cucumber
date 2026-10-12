package com.example.authservice.exception;

/** 409 {@code SEAT_LIMIT_REACHED}: the organization has no free seat (D11). */
public class SeatLimitReachedException extends ApiException {

    public SeatLimitReachedException() {
        super(ErrorCode.SEAT_LIMIT_REACHED, "The organization has no free seat.");
    }
}
