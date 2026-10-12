package org.apigateway.security;

import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.util.Base64;

/**
 * Turns the {@code JWT_SECRET} value into the HS512 signing key (D4). Error messages name the setting
 * but never contain the value or any part of it.
 */
public final class JwtSecretDecoder {

    /** 512 bits, the minimum key size for HS512. */
    public static final int MINIMUM_SECRET_BYTES = 64;

    private static final String SETTING_NAME = "JWT_SECRET";

    private JwtSecretDecoder() {
    }

    /**
     * @param base64Secret standard Base64 (RFC 4648 section 4) of at least {@value #MINIMUM_SECRET_BYTES} bytes
     * @throws IllegalStateException if the value is not standard Base64 or decodes to too few bytes
     */
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
            // The decoder's message quotes the offending character of the secret, so it is not chained.
            throw new IllegalStateException(SETTING_NAME + " must be standard Base64 (RFC 4648 section 4)");
        }
    }
}
