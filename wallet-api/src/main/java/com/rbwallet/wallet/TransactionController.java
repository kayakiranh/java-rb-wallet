package com.rbwallet.wallet;

import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {
  private final WalletService service;
  private final WalletStore db;

  public TransactionController(WalletService service, WalletStore db) {
    this.service = service;
    this.db = db;
  }

  @GetMapping
  public List<Contracts.TransactionResponse> history(
      @AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "1") int page) {
    return db.history(jwt.getSubject(), page);
  }

  @GetMapping("/by-request/{requestKey}")
  public Contracts.TransactionResponse find(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID requestKey) {
    return service.byRequest(jwt.getSubject(), requestKey.toString());
  }

  @PostMapping("/{id}/resume")
  public ResponseEntity<Contracts.TransactionResponse> resume(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return OperationResponse.of(service.resume(jwt.getSubject(), id.toString()));
  }
}
