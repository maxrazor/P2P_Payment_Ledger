package com.dfls.identity_service.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
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
        return path.equals("/actuator/") || path.startsWith("/public/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) 
            throws ServletException, IOException {
        
        String requestSecret = request.getHeader(HEADER_GATEWAY_SECRET);

        if (!isAuthorized(requestSecret)) {

            log.warn("Unauthorized direct access attempt detected from IP: {}", request.getRemoteAddr());
            
            handlerExceptionResolver.resolveException(
                request, response, null, new InsufficientAuthenticationException("Direct access not allowed."));
            return;
        }

        String userId = request.getHeader("X-Authenticated-User-Id"); 
        String roleStr = request.getHeader("X-User-Role");

        if (StringUtils.hasText(userId)) {
            List<GrantedAuthority> authorities = new ArrayList<>();
            if (StringUtils.hasText(roleStr)) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + roleStr.trim().toUpperCase()));
            }

            UsernamePasswordAuthenticationToken authentication = 
                    new UsernamePasswordAuthenticationToken(userId, null, authorities);
            
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }

    private boolean isAuthorized(String requestSecret) {
        if (gatewaySecretBytes.length == 0 || !StringUtils.hasText(requestSecret)) {
            return false;
        }
        
        byte[] requestSecretBytes = requestSecret.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(gatewaySecretBytes, requestSecretBytes);
    }
}
        
