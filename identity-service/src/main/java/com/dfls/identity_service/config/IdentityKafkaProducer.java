package com.dfls.identity_service.config;

import com.dfls.shared.event.UserRegisteredEvent; // Defined in your shared-dto module
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class IdentityKafkaProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private static final String TOPIC = "user-registrations-topic";

    public IdentityKafkaProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendUserRegisteredEvent(UUID userId, String email) {
        UserRegisteredEvent event = new UserRegisteredEvent(userId, email);
        System.out.println("Identity Kafka sends event!");
        
        // send(topic, uniquePartitionKey, payload)
        // Using the userId as a key ensures all events for this user go to the same partition order
        kafkaTemplate.send(TOPIC, userId.toString(), event)
        .whenComplete((result, ex) -> {
            if (ex == null) {
                System.out.println("Sent offset: " + result.getRecordMetadata().offset());
            } else {
                System.err.println("Failed to produce: " + ex.getMessage());
            }
        });
    }

}