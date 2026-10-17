package com.dfls.api_gateway.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class GatewayFallbackControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void shouldReturnTransferServiceUnavailable() {

        webTestClient
                .get()
                .uri("/fallback/transfer")
                .exchange()
                .expectStatus()
                .isEqualTo(503)
                .expectBody()
                .jsonPath("$.error")
                .isEqualTo("TRANSFER_SERVICE_UNAVAILABLE");
    }

    @Test
    void shouldReturnAccountServiceUnavailable() {

        webTestClient
                .get()
                .uri("/fallback/account")
                .exchange()
                .expectStatus()
                .isEqualTo(503)
                .expectBody()
                .jsonPath("$.error")
                .isEqualTo("ACCOUNT_SERVICE_UNAVAILABLE");
    }
}