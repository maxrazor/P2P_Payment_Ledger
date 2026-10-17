package com.dfls.api_gateway.filter;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

@Component("AuthenticationFilter")
@Slf4j
public class AuthenticationFilter extends AbstractGatewayFilterFactory<AuthenticationFilter.Config> {

    private final String GATEWAY_SECRET;
    private final SecretKey signingKey;

    public AuthenticationFilter(
            @Value("${gateway.secret}") String GATEWAY_SECRET,
            @Value("${jwt.secret}") String jwtSecret
    ) {
        super(Config.class);
        this.GATEWAY_SECRET = GATEWAY_SECRET;
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> Mono.defer(() -> {
            ServerHttpRequest request = exchange.getRequest();

            log.debug("Inside the filter");

            ServerHttpRequest sanitizedRequest = request.mutate()
                    .headers(httpHeaders -> {
                        httpHeaders.remove("X-Authenticated-User-Id");
                        httpHeaders.remove("X-User-Role");
                        httpHeaders.remove("X-Gateway-Secret");
                        httpHeaders.remove("X-Internal-Service");
                    }).build();

            String token = extractToken(sanitizedRequest);

            if (token == null) {
                return Mono.error(new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Missing authentication token"
                ));
            }

            log.debug("Token found, validating...");

            return Mono.fromSupplier(() -> Jwts.parser()
                        .verifyWith(signingKey)
                        .build()
                        .parseSignedClaims(token)
                        .getPayload())

                .flatMap(claims -> {
                    String userId = claims.getSubject();
                    String role = claims.get("role", String.class);

                    ServerHttpRequest.Builder requestBuilder = sanitizedRequest.mutate()
                            .header("X-Authenticated-User-Id", userId)
                            .header("X-Gateway-Secret", GATEWAY_SECRET);

                    if (role != null) {
                        if (role.toUpperCase().startsWith("ROLE_")) {
                            role = role.substring(5);
                        }
                        requestBuilder.header("X-User-Role", role.toUpperCase());
                    }

                    ServerWebExchange mutatedExchange = exchange.mutate()
                            .request(requestBuilder.build())
                            .build();

                    log.info("Authenticated request for User-ID: {} with Role: {} forwarded to URI: {}", 
                        userId, role != null ? role : "NONE", mutatedExchange.getRequest().getURI());

                    return chain.filter(mutatedExchange);
                });
        });
    }

    private String extractToken(ServerHttpRequest request) {

        String authHeader = request.getHeaders()
                .getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        HttpCookie cookie = request.getCookies().getFirst("access_token");

        if (cookie != null) {
            return cookie.getValue();
        }

        return null;
    }

    public static class Config {}
}


            

            


