package com.dfls.transfer_service.controller;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.dfls.shared.dto.TransferRequest;
import com.dfls.transfer_service.model.Transaction;
import com.dfls.transfer_service.service.TransferFacadeService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1")
public class TransferController {

    private TransferFacadeService transferFacadeService;

    public TransferController(TransferFacadeService transferFacadeService){
        this.transferFacadeService = transferFacadeService;
    }

    @PostMapping("/transfers")
    public ResponseEntity<Void> initiateTransfer(@RequestHeader("X-Authenticated-User-Id") UUID userId,
                                                 @RequestHeader("X-Idempotency-Key") String idempotencyKey,
                                                 @RequestBody TransferRequest request)
    {
        
        log.info("Transfer request received with Idempotency Key: {}", idempotencyKey);

        Transaction savedTransaction = transferFacadeService.handleTransfer(userId, idempotencyKey, request);

        log.info("Transfer commited with transaction ID: {}", savedTransaction.getId());

            URI location = ServletUriComponentsBuilder
                    .fromCurrentRequest()
                    .path("/{id}")
                    .buildAndExpand(savedTransaction.getId())
                    .toUri();

        return ResponseEntity.created(location).build(); 
    }
}
