package com.dfls.account_service.processed;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {

    boolean existsByEventId(UUID eventId);
}