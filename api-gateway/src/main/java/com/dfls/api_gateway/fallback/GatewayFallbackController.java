package com.dfls.api_gateway.fallback;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/fallback")
public class GatewayFallbackController {

    @RequestMapping("/account")
    public ResponseEntity<Map<String, Object>> accountFallback() {
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "error", "ACCOUNT_SERVICE_UNAVAILABLE",
                        "message", "Account service is temporarily unavailable"
                ));
    }

    @RequestMapping("/transfer")
    public ResponseEntity<Map<String, Object>> transferFallback() {
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "error", "TRANSFER_SERVICE_UNAVAILABLE",
                        "message", "Transfer service is temporarily unavailable"
                ));
    }
}