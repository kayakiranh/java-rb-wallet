package com.rbwallet.wallet;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/**
 * Cüzdandan cüzdana transfer use-case'inin HTTP giriş adapter'ı.
 *
 * <p>Controller fraud, bakiye veya transaction ayrıntısını bilmez; bunları {@link WalletService}
 * yönetir. Bu Separation of Concerns, ASP.NET Core'da business service'i Controller'a constructor
 * injection ile vermekle aynı fikirdir.
 */
@RestController
@RequestMapping("/api/transfers")
public class TransferController {
  private final WalletService service;

  public TransferController(WalletService service) {
    this.service = service;
  }

  @PostMapping
  /** JWT sahibinden alıcı cüzdana idempotent ve fraud kontrollü transfer başlatır. */
  public ResponseEntity<Contracts.TransactionResponse> transfer(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Contracts.Transfer request) {
    return OperationResponse.of(service.transfer(jwt.getSubject(), request));
  }
}
