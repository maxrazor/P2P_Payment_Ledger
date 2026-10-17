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
import com.dfls.account_service.ledger.ValidationResult;
import com.dfls.account_service.outbox.OutboxService;
import com.dfls.account_service.processed.ProcessedEvent;
import com.dfls.account_service.processed.ProcessedEventRepository;
import com.dfls.shared.dto.TransferInitiatedKafkaEvent;
import com.dfls.shared.event.EventType;
import com.dfls.shared.event.TransferInitiatedPayload;
import com.dfls.shared.exception.AccountNotFoundException;

import io.opentelemetry.api.trace.Span;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransferProcessor {

    private final AccountRepository accountRepository;
    private final LedgerRepository ledgerRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final OutboxService outboxService;
    private final LedgerValidator ledgerValidator;
    private final LedgerService ledgerService;

    @Transactional
    public void process(TransferInitiatedKafkaEvent kafkaEvent) {

        TransferInitiatedPayload event = kafkaEvent.payload();

        log.info(
                "Processing transfer event eventId={} transferId={} senderAccountId={} receiverAccountId={} amount={} currency={}",
                kafkaEvent.eventId(),
                event.transferId(),
                event.senderAccountId(),
                event.receiverAccountId(),
                event.amount(),
                event.currency()
        );

        if (processedEventRepository.existsByEventId(kafkaEvent.eventId())) {
            log.warn(
                "Duplicate transfer event skipped eventId={} transferId={}",
                kafkaEvent.eventId(),
                event.transferId()
            );
            return; 
        }

        String traceId = Span.current()
        .getSpanContext()
        .getTraceId();

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

        ValidationResult validation = ledgerValidator.validateTransfer(sender, receiver, event);
        if (!validation.isValid()) {
            rejectTransfer(kafkaEvent, sender.getUserId(), receiver.getUserId(), traceId, event, validation.rejectionReason()); 
            return;
        }

        BigDecimal balance = ledgerRepository.sumLedgerBalance(sender.getId(), event.currency());
        if (balance.compareTo(event.amount()) < 0) {
            rejectTransfer(kafkaEvent, sender.getUserId(), receiver.getUserId(), traceId, event, "Insufficient funds");
            return;
        }

        ledgerService.createDebit(sender.getId(), event.transferId(), event.amount(), event.currency(), "Transfer DEBIT");
        ledgerService.createCredit(receiver.getId(), event.transferId(), event.amount(), event.currency(), "Transfer CREDIT");

        outboxService.createTransferResult(kafkaEvent, sender.getUserId(), receiver.getUserId(), traceId, EventType.TRANSFER_COMPLETED, "Success");

        processedEventRepository.save(new ProcessedEvent(kafkaEvent.eventId())
        );

        log.info(
            "Transfer processing completed successfully " +
            "transferId={} eventId={}",
            event.transferId(),
            kafkaEvent.eventId()
        );
    }

    private void rejectTransfer(TransferInitiatedKafkaEvent kafkaEvent, UUID senderUserId, UUID receiverUserId, String traceId, TransferInitiatedPayload event, String reason) {
        log.info(
            "Transfer rejected: transferId={} senderAccountId={} amount={} reason={}",
            event.transferId(),
            event.senderAccountId(),
            event.amount(),
            reason
        );
        outboxService.createTransferResult(kafkaEvent, senderUserId, receiverUserId, traceId, EventType.TRANSFER_REJECTED, reason);
        processedEventRepository.save(new ProcessedEvent(kafkaEvent.eventId()));
    }
}
