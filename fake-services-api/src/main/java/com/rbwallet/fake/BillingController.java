package com.rbwallet.fake;

import static com.rbwallet.fake.Contracts.*;

import jakarta.validation.Valid;
import java.io.IOException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Fatura sorgulama ve ödeme sağlayıcısının test double Controller'ı.
 *
 * <p>Fatura durumu FakeStore'da kalıcıdır; böylece ikinci ödeme gerçekten “zaten ödendi” sonucu
 * üretir. Spring {@code ResponseEntity}, ASP.NET Core {@code ActionResult<T>} karşılığıdır.
 */
@RestController
@RequestMapping("/api/billing")
public class BillingController {
  private final FakeStore store;

  public BillingController(FakeStore store) {
    this.store = store;
  }

  @GetMapping("/bills/{subscriberNumber}")
  /** Aboneye ait demo faturayı 200, bulunamazsa 404 ile döndürür. */
  public ResponseEntity<Bill> get(@PathVariable String subscriberNumber) {
    var bill = store.bill(subscriberNumber);
    return bill == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(bill);
  }

  @PostMapping("/payments")
  /** Ödemeyi operationId üzerinden idempotent işler. */
  public ExternalResult pay(@Valid @RequestBody ExternalOperation request)
      throws IOException, InterruptedException {
    var result = store.execute("bill", request);
    if (result.delay()) Thread.sleep(8000);
    return result.result();
  }
}
