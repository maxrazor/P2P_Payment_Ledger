package com.dfls.account_service.ledger;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ledger_entries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // Sequential long ID—critical for chronological audit auditing

    @Column(name = "account_id", nullable = false)
    private UUID accountId; // Which wallet this entry belongs to

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId; // Links this ledger line back to the high-level transfer record

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 6)
    private EntryType entryType; // "DEBIT" or "CREDIT"

    /**
     * 🛡️ FINTECH STANDARD: Never use 'double' or 'float' for money due to rounding bugs!
     * 18 digits total, 4 decimal places allows tracking fractions of a cent securely.
     * Rule: DEBIT entries are stored as NEGATIVE numbers. CREDIT entries as POSITIVE.
     */
    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal amount; 

    @Column(nullable = false, length = 3)
    private String currency; // Default: "USD"

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "description", nullable = false)
    private String description;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }
}