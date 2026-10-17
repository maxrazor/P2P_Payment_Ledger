package com.dfls.transfer_service.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.dfls.shared.dto.TransferRequest;
import com.dfls.shared.exception.InvalidAccountTransactionException;
import com.dfls.transfer_service.client.AccountClient;
import com.dfls.transfer_service.idempotency.Idempotent;
import com.dfls.transfer_service.model.Transaction;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class TransferFacadeService {

    private final AccountClient accountClient;
    private final TransferService transferService;

    public TransferFacadeService(AccountClient accountClient, 
                                 TransferService transferService) {
        this.accountClient = accountClient;
        this.transferService = transferService;
    }

    @Idempotent
    public Transaction handleTransfer(UUID authenticatedUserId, String idempotencyKey, TransferRequest request) {

        log.info("Facade business routing logic executed for key: {}", idempotencyKey);

        UUID accountId = accountClient.resolveAccount(request.destinationEmail(), request.currency());

        if (request.sourceAccountId().equals(accountId)) {
            throw new InvalidAccountTransactionException("Source and destination accounts must be different.");
        }
        return transferService.processTransfer(idempotencyKey, request, accountId);
    }
}
