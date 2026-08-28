package com.dfls.account_service.account;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface AccountRepository extends JpaRepository<Account, UUID>{

    //TODO: Double check the necessity of those

    List<Account> findByUserId(UUID userId);

    Optional<Account> findById(UUID accountId);

    boolean existsByUserIdAndCurrency(UUID userId, String currency);
    
    Optional<AccountIdView> findProjectedByUserId(UUID userId, String currency);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") UUID id);

}
