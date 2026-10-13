package com.example.authservice.exception;

public class LastOwnerException extends ApiException {

    public LastOwnerException() {
        super(ErrorCode.LAST_OWNER, "The last owner can't leave the organization. Make another member an owner first.");
    }
}
