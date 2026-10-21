package com.example.authservice.exception;

public class SeatLimitReachedException extends ApiException {

    public SeatLimitReachedException() {
        super(ErrorCode.SEAT_LIMIT_REACHED, "The organization has no free seat.");
    }
}
