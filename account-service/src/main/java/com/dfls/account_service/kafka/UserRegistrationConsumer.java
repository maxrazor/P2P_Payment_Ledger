package com.dfls.account_service.kafka;

import com.dfls.account_service.account.AccountService;
import com.dfls.shared.event.UserRegisteredEvent;

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

    @KafkaListener(topics = "user-registrations-topic", groupId = "account-service-group")
    public void consumeUserRegistration(UserRegisteredEvent event) {

        log.info(
            "Received registration event for email {} at time: {}",
            event.email(),
            event.registeredAt()
        );
        
        // Call your service to provision the empty USD ledger envelope wallet
        accountService.createDefaultWallet(event.userId(), "USD", event.email());

    }
}