package com.dfls.identity_service.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic userRegistrationTopic() {
        return TopicBuilder.name("user-registrations-topic")
                .partitions(3)       // Scales parallel performance processing
                .replicas(1)         // Set higher in multi-node production clouds
                .build();
    }
}