package com.dfls.account_service.processed;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "processed_events")
@Getter
@Setter
@NoArgsConstructor
public class ProcessedEvent {

    @Id
    private UUID eventId;

    @Column(nullable = false)
    private Instant processedAt;

    public ProcessedEvent(UUID eventId) {
        this.eventId = eventId;
        this.processedAt = Instant.now();
    }
}