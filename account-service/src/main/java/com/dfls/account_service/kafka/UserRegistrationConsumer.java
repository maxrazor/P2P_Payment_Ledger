package com.dfls.account_service.kafka;

import com.dfls.account_service.account.AccountService;
import com.dfls.shared.event.UserRegisteredEvent;
import com.dfls.shared.exception.DuplicateAccountException;

import lombok.extern.slf4j.Slf4j;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class UserRegistrationConsumer {

    private final AccountService accountService;

    public UserRegistrationConsumer(AccountService accountService) {
        this.accountService = accountService;
    }

    @KafkaListener(
            topics = "user-registrations-topic", 
            groupId = "account-service-group"
    )
    public void consumeUserRegistration(UserRegisteredEvent event) {

        log.info("Received registration event for email {} at time: {}", event.email(), event.registeredAt());

        try {
            accountService.createDefaultWallet(event.userId(), "USD", event.email());
            log.info("Successfully provisioned default wallet account record for userId: {}", event.userId());

        } catch (DuplicateAccountException ex) {
            log.info("Duplicate registration event caught: default wallet already exists for userId: {}. Ignoring event.", event.userId());
        }
    }
}