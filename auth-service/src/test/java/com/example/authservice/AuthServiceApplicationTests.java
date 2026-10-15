package com.example.authservice;

import com.example.authservice.support.UsesTestDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        // TEST ONLY secret: standard Base64 of the 77-byte ASCII text
        // "auth-service TEST ONLY hs512 signing secret; never used in a real environment".
        "app.jwtSecret=YXV0aC1zZXJ2aWNlIFRFU1QgT05MWSBoczUxMiBzaWduaW5nIHNlY3JldDsgbmV2ZXIgdXNlZCBpbiBhIHJlYWwgZW52aXJvbm1lbnQ="
})
@UsesTestDatabase
class AuthServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
