package com.dfls.transfer_service.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dfls.transfer_service.model.Transaction;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

}
