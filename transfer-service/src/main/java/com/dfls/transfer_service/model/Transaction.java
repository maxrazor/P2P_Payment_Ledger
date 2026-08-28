package com.dfls.transfer_service.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transactions")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Transaction {

    @Id
    @Builder.Default // 2. Tells the builder to use this default if not specified
    private UUID id = UUID.randomUUID(); 

    @Column(name = "idempotency_key", unique = true, nullable = false)
    private String idempotencyKey; // 🛡️ Used by Redis to prevent accidental double-charging

    @Column(name = "source_account_id", nullable = false)
    private UUID sourceAccountId; // The sender wallet reference

    @Column(name = "destination_account_id", nullable = false)
    private UUID destinationAccountId; // The receiver wallet reference

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency; // e.g., "USD"
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus status; // PENDING, SUCCESS, FAILED (Managed via an Enum)

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

}