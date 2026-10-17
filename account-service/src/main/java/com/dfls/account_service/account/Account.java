package com.dfls.account_service.account;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "accounts",uniqueConstraints = {
        @UniqueConstraint(
            name = "uc_user_currency",
            columnNames = {"identifier_email", "currency"}
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
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "identifier_email", nullable = false)
    private String email; 
}