package com.rbwallet.wallet;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/**
 * Cüzdan bakiyesi ve bankadan yükleme endpointlerini sunan ince HTTP adapter'ı.
 *
 * <p>{@code @AuthenticationPrincipal Jwt}, ASP.NET Core Controller'daki {@code User} principal
 * karşılığıdır. Kimlik request body'den alınmadığı için kullanıcı başka cüzdan adına işlem yapamaz.
 */
@RestController
@RequestMapping("/api/wallet")
public class WalletController {
  private final WalletService service;

  public WalletController(WalletService service) {
    this.service = service;
  }

  /** TR: JWT sub, kullanıcı kimliğidir. İstek gövdesinden kullanıcı kimliği alınmaz. */
  @GetMapping
  public Contracts.WalletResponse get(@AuthenticationPrincipal Jwt jwt) {
    return service.wallet(jwt.getSubject());
  }

  @PostMapping("/top-ups")
  /** Banka hesabından idempotent yükleme akışını başlatır; Pending sonuç için HTTP 202 dönebilir. */
  public ResponseEntity<Contracts.TransactionResponse> topUp(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Contracts.TopUp request) {
    return OperationResponse.of(service.topUp(jwt.getSubject(), request));
  }
}
