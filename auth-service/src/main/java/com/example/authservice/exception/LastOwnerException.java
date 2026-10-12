package com.example.authservice.exception;

/** 409 {@code LAST_OWNER}: the only owner can't leave; make someone else an owner first (D11). */
public class LastOwnerException extends ApiException {

    public LastOwnerException() {
        super(ErrorCode.LAST_OWNER, "The last owner can't leave the organization. Make another member an owner first.");
    }
}
