package com.rbwallet.wallet;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/**
 * Fatura sorgulama ve ödeme HTTP adapter'ı.
 *
 * <p>Controller yalnızca HTTP/JWT/validation işini yapar; ödeme orkestrasyonu
 * {@link WalletService}'te kalır. Bu Thin Controller yaklaşımı .NET'teki ince API Controller ile
 * aynıdır. {@code @Valid}, ASP.NET Core DataAnnotations model validation karşılığıdır.
 */
@RestController
@RequestMapping("/api/bills")
public class BillController {
  private final WalletService service;

  public BillController(WalletService service) {
    this.service = service;
  }

  @GetMapping("/{subscriberNumber}")
  /** Dış fatura sağlayıcısından güncel borcu sorgular; kullanıcı bakiyesini değiştirmez. */
  public Contracts.Bill get(@PathVariable String subscriberNumber) {
    return service.bill(subscriberNumber);
  }

  @PostMapping("/payments")
  /** JWT subject ile sahibi belirlenen cüzdandan idempotent fatura ödeme akışını başlatır. */
  public ResponseEntity<Contracts.TransactionResponse> pay(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Contracts.BillPayment request) {
    return OperationResponse.of(service.payBill(jwt.getSubject(), request));
  }
}
