package com.rbwallet.fake;

import static com.rbwallet.fake.Contracts.*;

import jakarta.validation.Valid;
import java.io.IOException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Outbox worker'ın çağırdığı idempotent bildirim sağlayıcısı fake'i.
 *
 * <p>{@code operationId:userId} birleşimi duplicate teslimatı engeller. Bu davranış, en az bir kez
 * teslimat yapan Outbox Pattern'de consumer idempotency gereksinimini görünür kılar.
 */
@RestController
@RequestMapping("/api/notification")
public class NotificationController {
  private final FakeStore store;

  public NotificationController(FakeStore store) {
    this.store = store;
  }

  @PostMapping("/messages")
  /** Mesajı bir kez kaydeder; ayarlanmış geçici hata varsa 503 üretir. */
  public ResponseEntity<?> send(@Valid @RequestBody Notification request) throws IOException {
    if (request.operationId().equals(new java.util.UUID(0, 0))
        || request.userId().equals(new java.util.UUID(0, 0)))
      return ResponseEntity.badRequest().build();
    return store.notify(request)
        ? ResponseEntity.ok(java.util.Map.of("status", "Recorded"))
        : ResponseEntity.status(503).build();
  }

  @GetMapping("/messages")
  /** Testlerin teslim edilen mesajları doğrulaması için kayıtları listeler. */
  public java.util.List<Notification> list() {
    return store.messages();
  }
}
