package com.rbwallet.wallet;

import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
  private final WalletStore db;

  public NotificationController(WalletStore db) {
    this.db = db;
  }

  @GetMapping
  public List<Contracts.Delivery> list(@AuthenticationPrincipal Jwt jwt) {
    return db.notifications(jwt.getSubject());
  }
}
