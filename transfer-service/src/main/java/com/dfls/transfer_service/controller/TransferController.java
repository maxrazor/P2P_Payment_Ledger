package com.dfls.transfer_service.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dfls.shared.dto.TransferRequest;
import com.dfls.transfer_service.service.TransferService;

@RestController
@RequestMapping("/api/v1")
public class TransferController {

    private TransferService transferService;

    public TransferController(TransferService transferService){
        this.transferService = transferService;
    }

    @PostMapping("/transfers")
    public ResponseEntity<Void> initiateTransfer(@RequestHeader("X-Authenticated-User-Id") UUID userId,
                                                 @RequestHeader("X-Idempotency-Key") String idempotencyKey,
                                                 @RequestBody TransferRequest request)
    {
        transferService.processTransfer(userId, idempotencyKey, request);
        return ResponseEntity.ok().build();
    }

}
