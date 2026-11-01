package com.rbwallet.wallet;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Liveness ve readiness endpointlerini ayırır.
 *
 * <p>Liveness sadece JVM/Spring prosesinin cevap verdiğini, readiness ise Oracle bağlantısının
 * gerçekten kullanılabildiğini gösterir. .NET HealthChecks'teki ayrı live/ready tag yaklaşımına
 * denktir; Kubernetes/OpenShift readiness başarısızken pod'a trafik göndermez.
 */
@RestController
public class HealthController {
  private final WalletStore db;

  public HealthController(WalletStore db) {
    this.db = db;
  }

  @GetMapping("/health")
  /** Uygulama prosesinin ayakta olduğunu bildirir; dış bağımlılık kontrol etmez. */
  public Map<String, String> live() {
    return Map.of("status", "healthy");
  }

  @GetMapping("/health/ready")
  /** Oracle'a SELECT 1 FROM dual çalıştırarak trafik almaya hazır olup olmadığını bildirir. */
  public ResponseEntity<Map<String, String>> ready() {
    try {
      if (!db.ready()) throw new IllegalStateException("Database unavailable");
      return ResponseEntity.ok(Map.of("status", "ready"));
    } catch (RuntimeException ex) {
      return ResponseEntity.status(503).body(Map.of("status", "unavailable"));
    }
  }
}
