package com.dfls.account_service.kafka;

import com.dfls.account_service.account.AccountService;
import com.dfls.shared.event.UserRegisteredEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class UserRegistrationConsumer {

    private final AccountService accountService;

    public UserRegistrationConsumer(AccountService accountService) {
        this.accountService = accountService;
    }

    @KafkaListener(topics = "user-registrations-topic", groupId = "account-service-group")
    public void consumeUserRegistration(UserRegisteredEvent event) {
        System.out.println("📬 Kafka Event Received! Creating account wallet for User ID: " + event.userId());
        
        // Call your service to provision the empty USD ledger envelope wallet
        accountService.createDefaultWallet(event.userId(), "USD");
    }
}