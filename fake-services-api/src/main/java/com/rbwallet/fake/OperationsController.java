package com.rbwallet.fake;

import static com.rbwallet.fake.Contracts.*;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Timeout/recovery sırasında provider tarafındaki kesin sonucu sorgulatan REST adapter'ı.
 *
 * <p>Wallet yeni ödeme göndermeden önce operationId ile bu endpoint'i kontrol eder. Böylece
 * belirsiz cevap doğrudan red sayılmaz ve çift para hareketi riski azaltılır.
 */
@RestController
@RequestMapping("/api/operations")
public class OperationsController {
  private final FakeStore store;

  public OperationsController(FakeStore store) {
    this.store = store;
  }

  @GetMapping("/{id}")
  /** Provider'ın daha önce işlediği sonucu döndürür; kayıt yoksa 404 verir. */
  public ResponseEntity<ExternalResult> get(@PathVariable java.util.UUID id) {
    var result = store.operation(id);
    return result == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(result);
  }
}
