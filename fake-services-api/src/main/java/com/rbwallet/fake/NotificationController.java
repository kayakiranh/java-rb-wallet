package com.rbwallet.fake;

import static com.rbwallet.fake.Contracts.*;

import jakarta.validation.Valid;
import java.io.IOException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notification")
public class NotificationController {
  private final FakeStore store;

  public NotificationController(FakeStore store) {
    this.store = store;
  }

  @PostMapping("/messages")
  public ResponseEntity<?> send(@Valid @RequestBody Notification request) throws IOException {
    if (request.operationId().equals(new java.util.UUID(0, 0))
        || request.userId().equals(new java.util.UUID(0, 0)))
      return ResponseEntity.badRequest().build();
    return store.notify(request)
        ? ResponseEntity.ok(java.util.Map.of("status", "Recorded"))
        : ResponseEntity.status(503).build();
  }

  @GetMapping("/messages")
  public java.util.List<Notification> list() {
    return store.messages();
  }
}
