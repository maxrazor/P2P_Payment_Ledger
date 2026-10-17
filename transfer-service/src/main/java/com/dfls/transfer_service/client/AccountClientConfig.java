package com.dfls.transfer_service.client;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class AccountClientConfig {

    @Bean
    public RestClient accountRestClient(
            @Value("${services.account-service.url}") String baseUrl,
            @Value("${gateway.secret}") String gatewaySecret,
            @Value("${app.security.internal-secret-token}") String internalSecretToken) {

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) Duration.ofSeconds(3).toMillis());
        requestFactory.setReadTimeout((int) Duration.ofSeconds(5).toMillis());

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader("X-Internal-Service", internalSecretToken)
                .defaultHeader("X-Gateway-Secret", gatewaySecret)
                .build();
    }
}