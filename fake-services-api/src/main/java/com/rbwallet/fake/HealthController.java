package com.rbwallet.fake;

import org.springframework.web.bind.annotation.*;

@RestController
public class HealthController {
  @GetMapping("/health")
  public java.util.Map<String, String> live() {
    return java.util.Map.of("status", "healthy");
  }
}
