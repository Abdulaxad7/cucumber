package com.example.authservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class OrgRoleCeilingException extends RuntimeException {
    public OrgRoleCeilingException(String message) {
        super(message);
    }
}
