package com.dfls.transfer_service.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class GatewayHeaderFilter extends OncePerRequestFilter {

    private static final String HEADER_GATEWAY_SECRET = "X-Gateway-Secret";
    private final byte[] gatewaySecretBytes;
    private final HandlerExceptionResolver handlerExceptionResolver;

    // Use constructor injection for better testability and immutability
    public GatewayHeaderFilter(
            @Value("${gateway.secret}") String gatewaySecret,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver) {
        this.gatewaySecretBytes = StringUtils.hasText(gatewaySecret) 
                ? gatewaySecret.getBytes(StandardCharsets.UTF_8) 
                : new byte[0];
        this.handlerExceptionResolver = handlerExceptionResolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        // Optimize performance by skipping health checks or public static assets
        return path.equals("/actuator/health") || path.startsWith("/public/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) 
            throws ServletException, IOException {
        
        String requestSecret = request.getHeader(HEADER_GATEWAY_SECRET);

        if (!isAuthorized(requestSecret)) {
            log.warn("Unauthorized direct access attempt detected from IP: {}", request.getRemoteAddr());
            
            // Forward the custom exception directly to your global handler
            handlerExceptionResolver.resolveException(
                request, response, null, new InsufficientAuthenticationException("Direct access not allowed."));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isAuthorized(String requestSecret) {
        // Prevent NullPointerException and empty secret bypasses
        if (gatewaySecretBytes.length == 0 || !StringUtils.hasText(requestSecret)) {
            return false;
        }
        
        // Constant-time comparison using pre-allocated byte arrays
        byte[] requestSecretBytes = requestSecret.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(gatewaySecretBytes, requestSecretBytes);
    }
}