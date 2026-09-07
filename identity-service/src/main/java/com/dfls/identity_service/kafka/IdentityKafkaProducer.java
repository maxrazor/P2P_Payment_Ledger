package com.dfls.identity_service.kafka;

import com.dfls.shared.event.UserRegisteredEvent; // Defined in your shared-dto module

import lombok.extern.slf4j.Slf4j;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
@Slf4j
public class IdentityKafkaProducer {

    //TODO: Put TOPIC to environmental variables

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private static final String TOPIC = "user-registrations-topic";

    public IdentityKafkaProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendUserRegisteredEvent(UUID userId, String email) {
        UserRegisteredEvent event = new UserRegisteredEvent(userId, email);

        log.debug("Initiating Kafka registration event send for user: {}", userId);

        kafkaTemplate.send(TOPIC, userId.toString(), event)
        .whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("Successfully sent user registered event. User: {}, Topic: {}, Partition: {}, Offset: {}", 
                    userId, 
                    result.getRecordMetadata().topic(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
            } else {
                log.error("Failed to send user registered event for user: {} due to error: {}", userId, ex.getMessage(), ex);
            }
        });
    }
}