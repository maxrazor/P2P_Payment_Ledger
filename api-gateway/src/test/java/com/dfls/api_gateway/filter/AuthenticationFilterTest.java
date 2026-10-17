package com.dfls.api_gateway.filter;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import com.dfls.api_gateway.exception.GlobalErrorWebExceptionHandler;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class AuthenticationFilterTest {

    @Mock
    private GatewayFilterChain filterChain;

    private AuthenticationFilter filterFactory;
    private GatewayFilter gatewayFilter;
    private final GlobalErrorWebExceptionHandler exceptionHandler = new GlobalErrorWebExceptionHandler();
    
    long expirationTime = System.currentTimeMillis() + 3600000;

    private final String testJwtSecret = "KEOV4/OPGCA17SThgUh5WDNUpqgazP0VZ7jWtyWc+o8=";
    private final String testGatewaySecret = "$2a$12$rCmDdT9GlGwITpiElVRaPu7UR8rvtV2YcdZg/eOZwJ1mqDm9Bci3m";
    private SecretKey signingKey;

    @BeforeEach
    void setUp() {
        filterFactory = new AuthenticationFilter(testGatewaySecret, testJwtSecret);

        AuthenticationFilter.Config config = new AuthenticationFilter.Config();
        gatewayFilter = filterFactory.apply(config);

        Mockito.lenient().when(filterChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        byte[] keyBytes = Decoders.BASE64.decode(testJwtSecret);
        signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    @Test
    void shouldAuthenticateValidJwt() {
        UUID userId = UUID.randomUUID();
        String token = createToken(userId, "USER", expirationTime); 

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/test")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Mono<Void> result = gatewayFilter.filter(exchange, filterChain);

        StepVerifier.create(result).verifyComplete();
        verify(filterChain).filter(any(ServerWebExchange.class));
    }

    @Test
    void shouldRejectRequestWithoutToken() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Mono<Void> result = gatewayFilter.filter(exchange, filterChain);

        StepVerifier.create(result)
                .expectErrorMatches(throwable -> throwable instanceof org.springframework.web.server.ResponseStatusException ex
                        && ex.getStatusCode() == HttpStatus.UNAUTHORIZED
                        && "Missing authentication token".equals(ex.getReason()))
                .verify();

        verify(filterChain, never()).filter(any(ServerWebExchange.class));
    }

    @Test
    void shouldRejectInvalidToken() {

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/test")
                .header(HttpHeaders.AUTHORIZATION, "Bearer eyJhbGciOiJIUzI1NiJ9.bbbbb.ccccc")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Mono<Void> result = gatewayFilter.filter(exchange, filterChain);

        StepVerifier.create(result)
            .expectErrorSatisfies(throwable -> {
                assertThat(throwable).isInstanceOf(io.jsonwebtoken.security.SignatureException.class);
                
                Mono<Void> handlerOutput = exceptionHandler.handle(exchange, throwable);
                StepVerifier.create(handlerOutput).verifyComplete();
                
                assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            })
            .verify();

        verify(filterChain, never()).filter(any(ServerWebExchange.class));
    }

    @Test
    void shouldAuthenticateUsingAccessTokenCookie() {

        UUID userId = UUID.randomUUID();
        String token = createToken(userId, "USER", expirationTime);

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/test")
                .cookie(new org.springframework.http.HttpCookie("access_token", token))
                .build();

        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Mono<Void> result = gatewayFilter.filter(exchange, filterChain);

        StepVerifier.create(result).verifyComplete();
        verify(filterChain, times(1)).filter(any(ServerWebExchange.class));

    }

    @Test
    void shouldReplaceClientSuppliedAuthenticatedUserId() {

        UUID realUserId = UUID.randomUUID();
        UUID forgedUserId = UUID.randomUUID();

        String token = createToken(realUserId, "USER", expirationTime);

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/test")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header("X-Authenticated-User-Id", forgedUserId.toString()) 
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Mono<Void> result = gatewayFilter.filter(exchange, filterChain);

        StepVerifier.create(result).verifyComplete();

        org.mockito.ArgumentCaptor<ServerWebExchange> exchangeCaptor = 
                org.mockito.ArgumentCaptor.forClass(ServerWebExchange.class);
        
        verify(filterChain, times(1)).filter(exchangeCaptor.capture());
        
        ServerWebExchange mutatedExchange = exchangeCaptor.getValue();
        String finalUserIdHeader = mutatedExchange.getRequest().getHeaders().getFirst("X-Authenticated-User-Id");

        assertThat(finalUserIdHeader)
                .isNotNull()
                .isEqualTo(realUserId.toString())
                .isNotEqualTo(forgedUserId.toString());

    }

    @Test
    void shouldRemoveClientSuppliedInternalHeaders() {

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/test")
                .header("X-Authenticated-User-Id", "malicious-hacker-999")
                .header("X-User-Role", "ADMIN")
                .header("X-Gateway-Secret", "stolen-secret-key")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Mono<Void> result = gatewayFilter.filter(exchange, filterChain);

        StepVerifier.create(result)
                .expectErrorSatisfies(throwable -> {
                    assertThat(throwable).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
                    
                    org.springframework.web.server.ResponseStatusException rse = 
                            (org.springframework.web.server.ResponseStatusException) throwable;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(rse.getReason()).isEqualTo("Missing authentication token");

                    Mono<Void> handlerOutput = exceptionHandler.handle(exchange, throwable);
                    StepVerifier.create(handlerOutput).verifyComplete();
                    
                    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                })
                .verify();

        verify(filterChain, never()).filter(any(ServerWebExchange.class));

    }

    @Test
    void shouldPropagateAdminRole() {

        UUID userId = UUID.randomUUID();
        String testGatewaySecret = "$2a$12$rCmDdT9GlGwITpiElVRaPu7UR8rvtV2YcdZg/eOZwJ1mqDm9Bci3m";

        String token = createToken(userId, "ROLE_ADMIN", expirationTime);

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/test")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Mono<Void> result = gatewayFilter.filter(exchange, filterChain);

        StepVerifier.create(result).verifyComplete();

        org.mockito.ArgumentCaptor<ServerWebExchange> exchangeCaptor = 
                org.mockito.ArgumentCaptor.forClass(ServerWebExchange.class);
        
        verify(filterChain, times(1)).filter(exchangeCaptor.capture());
        
        ServerWebExchange mutatedExchange = exchangeCaptor.getValue();
        HttpHeaders finalHeaders = mutatedExchange.getRequest().getHeaders();

        assertThat(finalHeaders.getFirst("X-Authenticated-User-Id")).isEqualTo(userId.toString());
        assertThat(finalHeaders.getFirst("X-Gateway-Secret")).isEqualTo(testGatewaySecret);
        assertThat(finalHeaders.getFirst("X-User-Role")).isEqualTo("ADMIN");

    }

    @Test
    void shouldNotPropagateUserRoleAsAdmin() {

        UUID userId = UUID.randomUUID();

        String token = createToken(userId, "ROLE_USER", expirationTime);

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/test")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Mono<Void> result = gatewayFilter.filter(exchange, filterChain);

        StepVerifier.create(result)
                .verifyComplete();

        org.mockito.ArgumentCaptor<ServerWebExchange> exchangeCaptor = 
                org.mockito.ArgumentCaptor.forClass(ServerWebExchange.class);
        
        verify(filterChain, times(1)).filter(exchangeCaptor.capture());
        
        ServerWebExchange mutatedExchange = exchangeCaptor.getValue();
        HttpHeaders finalHeaders = mutatedExchange.getRequest().getHeaders();

        assertThat(finalHeaders.getFirst("X-Authenticated-User-Id")).isEqualTo(userId.toString());
        
        assertThat(finalHeaders.getFirst("X-User-Role"))
                .isEqualTo("USER")
                .isNotEqualTo("ADMIN");

    }

    @Test
    void shouldRejectExpiredToken() {

        UUID userId = UUID.randomUUID();

        long tenMinutesAgo = System.currentTimeMillis() - (10 * 60 * 1000);
        
        String token = createToken(userId, "ROLE_USER", tenMinutesAgo);

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/test")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Mono<Void> result = gatewayFilter.filter(exchange, filterChain);

        StepVerifier.create(result)
                .expectErrorSatisfies(throwable -> {
                    assertThat(throwable).isInstanceOf(io.jsonwebtoken.ExpiredJwtException.class);
                    
                    Mono<Void> handlerOutput = exceptionHandler.handle(exchange, throwable);
                    StepVerifier.create(handlerOutput).verifyComplete();
                    
                    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    
                    String responseBody = exchange.getResponse().getBodyAsString().block(java.time.Duration.ofSeconds(2));
                    assertThat(responseBody)
                            .contains("\"status\":401")
                            .contains("\"error\":\"TOKEN_EXPIRED\"")
                            .contains("\"message\":\"Your session has expired. Please log in again.\"");
                })
                .verify();

        verify(filterChain, never()).filter(any(ServerWebExchange.class));

    }

    private String createToken(UUID userId, String role, long time) {
        return Jwts.builder()
                .subject(userId.toString())
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(time))
                .signWith(signingKey)
                .compact();
    }
}