package com.dfls.identity_service.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.dfls.identity_service.model.Role;
import com.dfls.identity_service.model.SecurityUser;
import com.dfls.identity_service.model.User;
import com.dfls.identity_service.service.JwtService;

import java.util.UUID;
import javax.crypto.SecretKey;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private JwtService jwtService;
    private SecretKey signingKey;
    
    private static final String TEST_SECRET = "KEOV4/OPGCA17SThgUh5WDNUpqgazP0VZ7jWtyWc+o8=";
    private static final long TEST_EXPIRATION = 86400000;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(TEST_SECRET, TEST_EXPIRATION);
        byte[] keyBytes = Decoders.BASE64.decode(TEST_SECRET);
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    @Test
    void shouldContainExpectedClaims() {
        UUID id = UUID.randomUUID();
        User user = new User();
        user.setId(id);
        user.setEmail("user@ledger.com");
        user.setRole(Role.ROLE_MEMBER);
        SecurityUser securityUser = new SecurityUser(user);

        String token = jwtService.generateToken(securityUser);
        
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertThat(claims.getSubject()).isEqualTo(id.toString());
        assertThat(claims.get("role", String.class)).isEqualTo("ROLE_MEMBER");
        assertThat(claims.getExpiration()).isAfter(java.util.Date.from(java.time.Instant.now()));
    }
}