package com.rbwallet.wallet;

import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/**
 * Finansal işlem geçmişi ve recovery endpointlerini sunan ince Controller.
 *
 * <p>{@code requestKey} idempotency anahtarıdır; timeout sonrası yeni işlem üretmek yerine mevcut
 * işlemi bulmaya yarar. {@code resume} ise aynı dış işlem kimliğiyle sonucu tekrar uzlaştırır. Bu,
 * .NET API'de Controller + application service kullanımıyla aynıdır.
 */
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
  /** Kullanıcının gelen/giden işlemlerini sayfalı olarak listeler. */
  public List<Contracts.TransactionResponse> history(
      @AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "1") int page) {
    return db.history(jwt.getSubject(), page);
  }

  @GetMapping("/by-request/{requestKey}")
  /** İstemcinin aynı requestKey ile oluşturduğu işlemin güncel durumunu döndürür. */
  public Contracts.TransactionResponse find(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID requestKey) {
    return service.byRequest(jwt.getSubject(), requestKey.toString());
  }

  @PostMapping("/{id}/resume")
  /** Pending işlemi provider kaydıyla uzlaştırır; yeni para hareketi oluşturmaz. */
  public ResponseEntity<Contracts.TransactionResponse> resume(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return OperationResponse.of(service.resume(jwt.getSubject(), id.toString()));
  }
}
