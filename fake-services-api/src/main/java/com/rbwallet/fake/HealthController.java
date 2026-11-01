package com.rbwallet.fake;

import org.springframework.web.bind.annotation.*;

/** Fake servis prosesinin liveness endpoint'i; ASP.NET Core basit health endpoint karşılığıdır. */
@RestController
public class HealthController {
  @GetMapping("/health")
  /** Proses HTTP isteğine cevap verebiliyorsa healthy döndürür. */
  public java.util.Map<String, String> live() {
    return java.util.Map.of("status", "healthy");
  }
}
