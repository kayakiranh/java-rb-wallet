package com.rbwallet.wallet;

import org.slf4j.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * TR: @Scheduled, BackgroundService/PeriodicTimer karşılığıdır. fixedDelay önceki iş bittikten
 * sonra bekler.
 */
@Component
public class Workers {
  private static final Logger log = LoggerFactory.getLogger(Workers.class);
  private final WalletStore db;
  private final WalletService service;
  private final FakeServicesClient fake;

  public Workers(WalletStore db, WalletService service, FakeServicesClient fake) {
    this.db = db;
    this.service = service;
    this.fake = fake;
  }

  @Scheduled(
      fixedDelayString = "${wallet.recovery-delay-ms:5000}",
      initialDelayString = "${wallet.recovery-delay-ms:5000}")
  /**
   * Zamanı gelmiş Pending işlemleri aynı operation id ile uzlaştırır. fixedDelay seçimi, uzun süren
   * turun bir sonraki turla üst üste binmesini azaltır.
   */
  public void recover() {
    for (var t : db.pending())
      try {
        service.complete(t);
      } catch (RuntimeException ex) {
        log.warn(
            "Recovery will retry. operationId={} type={}", t.id(), ex.getClass().getSimpleName());
      }
  }

  @Scheduled(
      fixedDelayString = "${wallet.notification-delay-ms:2000}",
      initialDelayString = "${wallet.notification-delay-ms:2000}")
  /** Outbox mesajlarını en az bir kez teslim eder; consumer idempotency duplicate'i güvenli kılar. */
  public void notifications() {
    for (var n : db.dueNotifications()) {
      boolean success;
      try {
        fake.notify(n);
        success = true;
      } catch (RuntimeException ex) {
        success = false;
        log.warn("Notification will retry. messageId={}", n.id());
      }
      // TR: Finansal transaction'a dokunulmaz. HTTP başarılı ama DB kaydı başarısızsa provider
      // tekrarları engeller.
      try {
        db.delivered(n, success);
      } catch (RuntimeException ex) {
        log.warn("Outbox save failed. messageId={}", n.id());
      }
    }
  }
}
