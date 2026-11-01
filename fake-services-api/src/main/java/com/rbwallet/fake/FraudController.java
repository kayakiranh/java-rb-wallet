package com.rbwallet.fake;

import static com.rbwallet.fake.Contracts.*;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * Deterministik fraud test double'ı.
 *
 * <p>Strategy'nin en basit hali olarak tutar limitine göre karar verir; amaç gerçek fraud motoru
 * yazmak değil Wallet entegrasyonunun approve/reject dallarını tekrarlanabilir test etmektir.
 */
@RestController
@RequestMapping("/api/fraud")
public class FraudController {
  @PostMapping("/check")
  /** 0'dan büyük ve 10.000 TRY'yi aşmayan demo işlemleri onaylar. */
  public FraudResult check(@Valid @RequestBody FraudRequest request) {
    boolean approved =
        request.amount().signum() > 0
            && request.amount().compareTo(new java.math.BigDecimal("10000")) <= 0;
    return new FraudResult(approved, approved ? null : "Demo işlem limiti 10.000 TRY.");
  }
}
