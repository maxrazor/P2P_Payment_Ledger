package com.dfls.account_service.transfer;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dfls.account_service.account.Account;
import com.dfls.account_service.account.AccountRepository;
import com.dfls.account_service.ledger.LedgerRepository;
import com.dfls.account_service.ledger.LedgerService;
import com.dfls.account_service.ledger.LedgerValidator;
import com.dfls.account_service.outbox.OutboxService;
import com.dfls.account_service.processed.ProcessedEvent;
import com.dfls.account_service.processed.ProcessedEventRepository;
import com.dfls.shared.exception.AccountNotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TransferProcessor {

    private final AccountRepository accountRepository;
    private final LedgerRepository ledgerRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final OutboxService outboxService;
    private final LedgerValidator ledgerValidator;
    private final LedgerService ledgerService;

    @Transactional
    public void process(TransferRequestedEvent event) {

        //TODO: Add proper exception for idempotency

        if (processedEventRepository.existsByEventId(event.eventId())) {
            return;
        }

        //Lock accounts in deterministic order
        UUID firstId = event.senderAccountId()
                .compareTo(event.receiverAccountId()) < 0
                ? event.senderAccountId()
                : event.receiverAccountId();

        UUID secondId = firstId.equals(event.senderAccountId())
                ? event.receiverAccountId()
                : event.senderAccountId();

        Account first = accountRepository.findByIdForUpdate(firstId)
                .orElseThrow(AccountNotFoundException::new);

        Account second = accountRepository.findByIdForUpdate(secondId)
                .orElseThrow(AccountNotFoundException::new);

        Account sender = first.getId().equals(event.senderAccountId())
                ? first
                : second;

        Account receiver = first.getId().equals(event.receiverAccountId())
                ? first
                : second;

        if (sender.getId().equals(receiver.getId())) {
            throw new IllegalArgumentException(
                    "Sender and receiver cannot be the same account"
            );
        }
    
        ledgerValidator.validateTransfer(sender, receiver, event);

        BigDecimal balance = ledgerRepository.sumLedgerBalance(sender.getId(), event.currency());

        if (balance.compareTo(event.amount()) < 0) {
            outboxService.createTransferCompleted(event, null, null, null);(event, "Insufficient funds");
            processedEventRepository.save(
                    new ProcessedEvent(event.eventId())
            );
            return;
        }

        ledgerService.createDebit(sender.getId(), event.transferId(), event.amount(), event.currency());
        ledgerService.createCredit(receiver.getId(), event.transferId(), event.amount(), event.currency());

        outboxService.createTransferCompleted(event);

        processedEventRepository.save(
                new ProcessedEvent(event.eventId())
        );
    }
}
