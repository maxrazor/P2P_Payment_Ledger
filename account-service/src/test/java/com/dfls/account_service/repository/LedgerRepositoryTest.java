package com.dfls.account_service.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.dfls.account_service.ledger.EntryType;
import com.dfls.account_service.ledger.LedgerEntry;
import com.dfls.account_service.ledger.LedgerRepository;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class LedgerRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private LedgerRepository ledgerRepository;

    @Test
    void shouldCalculateBalance() {
        UUID accountId = UUID.randomUUID();

        LedgerEntry entry_1 = LedgerEntry.builder()
                                        .accountId(accountId)
                                        .transactionId(UUID.randomUUID())
                                        .entryType(EntryType.CREDIT)
                                        .amount(new BigDecimal("500"))
                                        .currency("USD")
                                        .createdAt(Instant.now())
                                        .description("None")
                                        .build();

        LedgerEntry entry_2 = LedgerEntry.builder()
                                        .accountId(accountId)
                                        .transactionId(UUID.randomUUID())
                                        .entryType(EntryType.DEBIT)
                                        .amount(new BigDecimal("-300"))
                                        .currency("USD")
                                        .createdAt(Instant.now())
                                        .description("None")
                                        .build();

        entityManager.persistAndFlush(entry_1);
        entityManager.persistAndFlush(entry_2);
        entityManager.clear();

        assertEquals(0, ledgerRepository.sumLedgerBalance(accountId, "USD").compareTo(new BigDecimal("200")));

    }

    @Test
    void shouldReturnZeroWhenNoEntriesExist() {
        UUID accountId = UUID.randomUUID();

        assertEquals(0, ledgerRepository.sumLedgerBalance(accountId, "USD").compareTo(new BigDecimal("0")));

    }

    @Test
    void shouldReturnStatementOrderedByCreatedAt() {
        UUID accountId = UUID.randomUUID();
        Instant now = Instant.now();

        LedgerEntry entry_1 = LedgerEntry.builder()
                                        .accountId(accountId)
                                        .transactionId(UUID.randomUUID())
                                        .entryType(EntryType.CREDIT)
                                        .amount(new BigDecimal("500"))
                                        .currency("USD")
                                        .createdAt(now.minusSeconds(10))
                                        .description("None")
                                        .build();

        LedgerEntry entry_2 = LedgerEntry.builder()
                                        .accountId(accountId)
                                        .transactionId(UUID.randomUUID())
                                        .entryType(EntryType.DEBIT)
                                        .amount(new BigDecimal("-300"))
                                        .currency("USD")
                                        .createdAt(now)
                                        .description("None")
                                        .build();

        entityManager.persistAndFlush(entry_1);
        entityManager.persistAndFlush(entry_2);
        entityManager.clear();

        Pageable pageable = PageRequest.of(0, 2);
        
        Page<LedgerEntry> result = ledgerRepository.findByAccountIdOrderByCreatedAtDesc(accountId, pageable);

        List<LedgerEntry> content = result.getContent();
        assertEquals(2, content.size(), "Should return both entries");
        
        assertEquals(entry_2.getTransactionId(), content.get(0).getTransactionId(), "Newest entry should be first");
        assertEquals(entry_1.getTransactionId(), content.get(1).getTransactionId(), "Older entry should be second");

    }
}
