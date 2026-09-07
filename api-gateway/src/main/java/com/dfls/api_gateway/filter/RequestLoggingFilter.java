package com.dfls.api_gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Component
@Order(-1000)
@Slf4j
public class RequestLoggingFilter implements GlobalFilter {

    //TODO: See if this needs improvements

    @Override
    public Mono<Void> filter(
            ServerWebExchange exchange,
            GatewayFilterChain chain) {

        long start = System.currentTimeMillis();

        ServerHttpRequest request = exchange.getRequest();

        log.info(
                "Request received method={} path={}",
                request.getMethod(),
                request.getURI().getPath()
        );

        return chain.filter(exchange)
                .doFinally(signal -> {

                    long duration =
                            System.currentTimeMillis() - start;

                    log.info(
                            "Request completed method={} path={} status={} durationMs={}",
                            request.getMethod(),
                            request.getURI().getPath(),
                            exchange.getResponse().getStatusCode(),
                            duration
                    );
                });
    }
}