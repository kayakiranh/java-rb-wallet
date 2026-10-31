package com.rbwallet.wallet;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bills")
public class BillController {
  private final WalletService service;

  public BillController(WalletService service) {
    this.service = service;
  }

  @GetMapping("/{subscriberNumber}")
  public Contracts.Bill get(@PathVariable String subscriberNumber) {
    return service.bill(subscriberNumber);
  }

  @PostMapping("/payments")
  public ResponseEntity<Contracts.TransactionResponse> pay(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Contracts.BillPayment request) {
    return OperationResponse.of(service.payBill(jwt.getSubject(), request));
  }
}
