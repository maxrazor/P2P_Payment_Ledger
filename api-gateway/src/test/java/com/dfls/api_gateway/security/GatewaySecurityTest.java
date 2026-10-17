package com.dfls.api_gateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class GatewaySecurityTest {

    @Autowired
    private WebTestClient webTestClient; // Inject WebTestClient instead

    @Test
    void shouldRejectRequestWithoutAuthentication() {
        webTestClient
                .post()
                .uri("/api/v1/transfers")
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }

    @Test
    void shouldRejectRequestWithInvalidToken() {
        webTestClient
                .post()
                .uri("/api/v1/transfers")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }
}