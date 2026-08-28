package com.dfls.identity_service.service;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import javax.crypto.SecretKey;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import com.dfls.identity_service.model.SecurityUser;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class JwtService {
    
    private final String JWT_SECRET = "KEOV4/OPGCA17SThgUh5WDNUpqgazP0VZ7jWtyWc+o8=";
    private final long JWT_EXPIRATION = 86400000;
    
    //TODO: Put JWT info into environmental variables for release

    // Generate token for user
    public String generateToken(SecurityUser userDetails) {
        return generateToken(new HashMap<>(), userDetails);
    }
    
    public String generateToken(Map<String, Object> extraClaims, SecurityUser userDetails) {
        return Jwts.builder()
                .claims(extraClaims) // Sets the custom claims map safely
                .subject((userDetails).getId().toString()) // Sets 'sub'
                .issuedAt(new Date(System.currentTimeMillis())) // Sets 'iat'
                .expiration(new Date(System.currentTimeMillis() + JWT_EXPIRATION)) // Sets 'exp'
                .signWith(getSigningKey()) // SignatureAlgorithm is automatically determined by the key type
                .compact();
    }
    
    
    // Extract username from token
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }
    
    // Validate token
    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername())) && !isTokenExpired(token);
    }
    
    // Check if token expired
    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }
    
    // Get signing key
    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(JWT_SECRET);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private <T> T extractClaim(String token, Function<Claims, T> claimResolver) {
        final Claims claims = extractAllClaims(token);
        return claimResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}

