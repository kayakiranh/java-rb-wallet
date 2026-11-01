package com.rbwallet.wallet;

import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/**
 * Oturum açmış kullanıcının outbox bildirim teslimatlarını okuyan HTTP adapter'ı.
 *
 * <p>Kullanıcı kimliği request parametresinden değil doğrulanmış JWT {@code sub} claim'inden gelir;
 * bu, başka kullanıcının kayıtlarını tahmin edilen bir id ile okuma riskini önler. .NET'teki
 * {@code User.FindFirstValue(ClaimTypes.NameIdentifier)} yaklaşımına denktir.
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
  private final WalletStore db;

  public NotificationController(WalletStore db) {
    this.db = db;
  }

  @GetMapping
  /** Yalnızca JWT sahibine ait teslimat kayıtlarını döndürür. */
  public List<Contracts.Delivery> list(@AuthenticationPrincipal Jwt jwt) {
    return db.notifications(jwt.getSubject());
  }
}
