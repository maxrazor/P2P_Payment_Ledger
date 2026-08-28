package com.dfls.identity_service.config;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class GatewayHeaderFilter extends OncePerRequestFilter {

    @Value("${gateway.secret}")
    private String gatewaySecret;

    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                    HttpServletResponse response, 
                                    FilterChain filterChain) throws ServletException, IOException {
        
        String requestSecret = request.getHeader("X-Gateway-Secret");                             
        if (gatewaySecret == null || !gatewaySecret.equals(requestSecret)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Forbidden\", \"message\": \"Direct access not allowed.\"}");
            return; // Short-circuit the request chain
        }

        // Token matches, continue to the next filter
        filterChain.doFilter(request, response);
    }
}