package org.apigateway;

import org.apigateway.support.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "jwt.secret=" + TestTokens.SECRET)
class ApiGatewayApplicationTests {

    @Test
    void contextLoads() {
    }

}
