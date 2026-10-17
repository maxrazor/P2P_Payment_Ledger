package com.dfls.identity_service.kafka;

import com.dfls.shared.event.UserRegisteredEvent; // Defined in your shared-dto module

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
@Slf4j
public class IdentityKafkaProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String topic;

    public IdentityKafkaProducer(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${app.kafka.topics.user-registrations:user-registrations-topic}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public void sendUserRegisteredEvent(UserRegisteredEvent event) {
        
        log.debug("Initiating Kafka registration event send for user: {} to topic: {}", event.userId(), topic);

        kafkaTemplate.send(topic, event.userId().toString(), event)
            .whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("Successfully sent user registered event. User: {}, Topic: {}, Partition: {}, Offset: {}", 
                        event.userId(), 
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
                } else {
                    log.error("Failed to send user registered event for user: {} due to error: {}", event.userId(), ex.getMessage(), ex);
                }
            });
    }
}

@Component
@RequiredArgsConstructor
@Slf4j
class IdentityEventListener {

    private final IdentityKafkaProducer identityKafkaProducer;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleUserRegistered(UserRegisteredEvent event) {

        log.info("Database transaction successfully committed. Streaming event to Kafka for user: {}", event.userId());

        identityKafkaProducer.sendUserRegisteredEvent(event);
    }
}