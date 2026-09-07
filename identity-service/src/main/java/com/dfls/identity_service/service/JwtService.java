package com.dfls.identity_service.service;

import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.dfls.identity_service.model.SecurityUser;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    private final long JWT_EXPIRATION;
    private final SecretKey signingKey;

    public JwtService(
            @Value("${jwt.secret}") String JWT_SECRET,
            @Value("${jwt.expiration}") long JWT_EXPIRATION) {
        this.JWT_EXPIRATION = JWT_EXPIRATION;
        byte[] keyBytes = Decoders.BASE64.decode(JWT_SECRET);
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(SecurityUser userDetails) {
        return Jwts.builder()
                .claim("role", userDetails.getRole())
                .subject(userDetails.getId().toString())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + JWT_EXPIRATION))
                .signWith(signingKey)
                .compact();
    }
}
