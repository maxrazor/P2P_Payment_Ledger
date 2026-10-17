package com.dfls.account_service.ledger;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerRepository extends JpaRepository<LedgerEntry, Long>{

    @Query("SELECT COALESCE(SUM(l.amount), 0) FROM LedgerEntry l " +
           "WHERE l.accountId = :accountId AND l.currency = :currency")
    BigDecimal sumLedgerBalance(@Param("accountId") UUID accountId,
                                @Param("currency") String currency);

    Page<LedgerEntry> findByAccountIdOrderByCreatedAtDesc(UUID accountId, Pageable pageable);

}
