package com.dfls.account_service.account;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "accounts",uniqueConstraints = {
        @UniqueConstraint(
            name = "uc_user_currency", // Optional: naming your constraint
            columnNames = {"email", "currency"}
        )
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id; // The unique Account/Wallet ID (e.g., printed on a card)

    @Column(name = "user_id", nullable = false)
    private UUID userId; // Loose logical ID reference linking back to the Identity Service

    @Column(name = "currency", nullable = false, length = 3)
    private String currency; // Default: "USD"

    @Column(name = "identifier_email", nullable = false)
    private String email; 
}