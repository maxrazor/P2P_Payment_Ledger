package com.dfls.account_service.outbox;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID>{
    List<OutboxEvent> findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus status);

    @Query(value = """
        SELECT * FROM outbox_events
        WHERE status = 'PENDING'
        OR (status = 'PROCESSING' AND locked_until < :now)
        ORDER BY created_at ASC
        LIMIT :batchSize
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<OutboxEvent> findEventsToClaim(@Param("batchSize") int batchSize, @Param("now") Instant now);

    @Modifying
    @Transactional
    @Query("UPDATE OutboxEvent e SET e.status = :status, e.publishedAt = :now WHERE e.id IN :ids")
    void batchUpdateStatus(@Param("ids") Collection<UUID> ids, @Param("status") OutboxStatus status, @Param("now") Instant now);

    @Modifying
    @Transactional
    @Query("UPDATE OutboxEvent e SET e.status = :status, e.lastError = :error WHERE e.id IN :ids")
    void batchUpdateStatusWithError(@Param("ids") Collection<UUID> ids, @Param("status") OutboxStatus status, @Param("error") String error);

    
}
