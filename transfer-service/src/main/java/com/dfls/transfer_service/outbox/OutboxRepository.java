package com.dfls.transfer_service.outbox;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    List<OutboxEvent> findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus status);

    @Query(value = """
        SELECT * FROM outbox_events
        WHERE status = 'PENDING'
        OR (status = 'PROCESSING'
        AND locked_until < NOW())
        ORDER BY created_at ASC
        LIMIT :batchSize
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<OutboxEvent> findEventsToClaim(@Param("batchSize") int batchSize);

}
