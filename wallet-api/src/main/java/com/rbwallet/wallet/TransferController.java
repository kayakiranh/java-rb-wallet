package com.rbwallet.wallet;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transfers")
public class TransferController {
  private final WalletService service;

  public TransferController(WalletService service) {
    this.service = service;
  }

  @PostMapping
  public ResponseEntity<Contracts.TransactionResponse> transfer(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Contracts.Transfer request) {
    return OperationResponse.of(service.transfer(jwt.getSubject(), request));
  }
}
