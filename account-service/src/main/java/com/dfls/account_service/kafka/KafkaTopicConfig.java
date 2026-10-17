package com.dfls.account_service.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    private final String topicName;

    public KafkaTopicConfig(
            @Value("${app.kafka.topics.transfer-results:transfer-results-topic}") String topicName) {
        this.topicName = topicName;
    }

    @Bean
    public NewTopic userRegistrationTopic() {
        return TopicBuilder.name(topicName)
                .partitions(3)      
                .replicas(1)  
                .build();
    }
}