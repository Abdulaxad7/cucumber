package org.apigateway.security;

import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.util.Base64;

public final class JwtSecretDecoder {

    public static final int MINIMUM_SECRET_BYTES = 64;

    private static final String SETTING_NAME = "JWT_SECRET";

    private JwtSecretDecoder() {
    }

    public static SecretKey decode(String base64Secret) {
        byte[] secret = decodeBase64(base64Secret);
        if (secret.length < MINIMUM_SECRET_BYTES) {
            throw new IllegalStateException(SETTING_NAME + " must decode to at least " + MINIMUM_SECRET_BYTES
                    + " bytes for HS512, but it decodes to " + secret.length + " bytes");
        }
        return Keys.hmacShaKeyFor(secret);
    }

    private static byte[] decodeBase64(String base64Secret) {
        try {
            return Base64.getDecoder().decode(base64Secret);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(SETTING_NAME + " must be standard Base64 (RFC 4648 section 4)");
        }
    }
}
