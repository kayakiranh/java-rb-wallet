package com.rbwallet.fake;

import static com.rbwallet.fake.Contracts.*;

import jakarta.validation.Valid;
import java.io.IOException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bank")
public class BankController {
  private final FakeStore store;

  public BankController(FakeStore store) {
    this.store = store;
  }

  @PostMapping("/top-ups")
  public ExternalResult topUp(@Valid @RequestBody ExternalOperation request)
      throws IOException, InterruptedException {
    var result = store.execute("bank", request);
    // TR: Önce işlem diske yazılır. Sonra gecikme, cevabın kaybolmasını simüle eder.
    if (result.delay()) Thread.sleep(8000);
    return result.result();
  }
}
