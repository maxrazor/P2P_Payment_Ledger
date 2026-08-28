package com.dfls.api_gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

@Component("AuthenticationFilter")
public class AuthenticationFilter extends AbstractGatewayFilterFactory<AuthenticationFilter.Config> {

    private final String JWT_SECRET = "KEOV4/OPGCA17SThgUh5WDNUpqgazP0VZ7jWtyWc+o8=";
    private static final Logger log = LoggerFactory.getLogger(AuthenticationFilter.class);

    //TODO: Use environmental variable for key in the final release

    public AuthenticationFilter() {
        super(Config.class);
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(JWT_SECRET);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();

            log.debug("Inside the filter");

            // CAVEAT CHECK: Strip any malicious pre-existing internal headers passed by the client
            request = request.mutate()
                    .headers(httpHeaders -> {
                        httpHeaders.remove("X-Authenticated-User-Id");
                    }).build();

            // Verify Authorization header presence
            if (!request.getHeaders().containsHeader(HttpHeaders.AUTHORIZATION)) {
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            String token = authHeader.substring(7);

            log.debug("Token found");

            try {
                // Cryptographically parse token on the edge
                Claims claims = Jwts.parser()
                        .verifyWith(getSigningKey())
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();

                // Inject the true, verified values into downstream headers
                String userId = claims.getSubject();

                request = request.mutate()
                        .header("X-Authenticated-User-Id", userId)
                        .build();

            } catch (Exception e) {
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            ServerWebExchange mutatedExchange = exchange.mutate().request(request).build();

            if (log.isDebugEnabled()) {
                log.debug("Sending Request Downstream - URI: {}, Headers: {}", 
                    mutatedExchange.getRequest().getURI(), 
                    mutatedExchange.getRequest().getHeaders());
            }

            return chain.filter(mutatedExchange);
        };
    }

    public static class Config {}
}