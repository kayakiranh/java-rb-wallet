package com.rbwallet.fake;

import static com.rbwallet.fake.Contracts.*;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/fraud")
public class FraudController {
  @PostMapping("/check")
  public FraudResult check(@Valid @RequestBody FraudRequest request) {
    boolean approved =
        request.amount().signum() > 0
            && request.amount().compareTo(new java.math.BigDecimal("10000")) <= 0;
    return new FraudResult(approved, approved ? null : "Demo işlem limiti 10.000 TRY.");
  }
}
