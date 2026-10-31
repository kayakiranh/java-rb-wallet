package com.rbwallet.wallet;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class HealthController {
  private final WalletStore db;

  public HealthController(WalletStore db) {
    this.db = db;
  }

  @GetMapping("/health")
  public Map<String, String> live() {
    return Map.of("status", "healthy");
  }

  @GetMapping("/health/ready")
  public ResponseEntity<Map<String, String>> ready() {
    try {
      if (!db.ready()) throw new IllegalStateException("Database unavailable");
      return ResponseEntity.ok(Map.of("status", "ready"));
    } catch (RuntimeException ex) {
      return ResponseEntity.status(503).body(Map.of("status", "unavailable"));
    }
  }
}
