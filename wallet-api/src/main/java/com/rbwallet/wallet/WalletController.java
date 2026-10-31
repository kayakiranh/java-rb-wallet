package com.rbwallet.wallet;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

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
  public ResponseEntity<Contracts.TransactionResponse> topUp(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Contracts.TopUp request) {
    return OperationResponse.of(service.topUp(jwt.getSubject(), request));
  }
}
