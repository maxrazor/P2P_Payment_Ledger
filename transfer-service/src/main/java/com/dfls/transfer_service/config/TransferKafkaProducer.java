package com.dfls.transfer_service.config;

import java.util.UUID;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.dfls.shared.event.UserRegisteredEvent;

@Service
public class TransferKafkaProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private static final String TOPIC = "transfer-status-topic";

    public TransferKafkaProducer(KafkaTemplate<String, Object> kafkaTemplate) {
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
