package com.dfls.api_gateway.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;

import java.util.Date;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@EnableWireMock({
    @ConfigureWireMock(
        name = "downstream-service", 
        port = 0,
        baseUrlProperties = { "TRANSFER_SERVICE_URL" } 
    )
})
class GatewaySecurityIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Value("${jwt.secret}")
    private String jwtSecret;

    private String validToken;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        byte[] keyBytes = io.jsonwebtoken.io.Decoders.BASE64.decode(jwtSecret);
        var signingKey = Keys.hmacShaKeyFor(keyBytes);

        validToken = Jwts.builder()
                .subject(userId.toString())
                .claim("role", "ROLE_USER")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(signingKey)
                .compact();
    }

    @Test
    void shouldAllowValidTokenAndForwardToService() {
        stubFor(post(urlEqualTo("/api/v1/transfers"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"SUCCESS\"}")));

        webTestClient
                .post()
                .uri("/api/v1/transfers")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("SUCCESS");

        verify(postRequestedFor(urlEqualTo("/api/v1/transfers"))
                .withHeader("X-Authenticated-User-Id", equalTo(userId.toString()))
                .withHeader("X-User-Role", equalTo("USER")));
    }

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