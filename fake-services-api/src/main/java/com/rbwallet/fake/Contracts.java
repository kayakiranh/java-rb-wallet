package com.rbwallet.fake;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Fake servislerin HTTP DTO sözleşmeleri.
 *
 * <p>Java record'ları C# record DTO'lara benzer: immutable'dır, constructor/accessor ve değer
 * eşitliği üretir. Jakarta Validation annotation'ları ASP.NET DataAnnotations karşılığıdır.
 */
public final class Contracts {
  private Contracts() {}

  /** Banka/fatura sağlayıcısına gönderilen idempotent işlem isteği. */
  public record ExternalOperation(
      @NotNull UUID operationId,
      @NotNull BigDecimal amount,
      @NotBlank @Size(max = 50) String target) {}

  /** Dış işlemin kesin sonucu ve makinece okunabilir red nedeni. */
  public record ExternalResult(String status, String reason) {}

  /** Fake fatura kaydı. */
  public record Bill(String subscriberNumber, BigDecimal amount, boolean paid) {}

  /** Fraud değerlendirmesi için gönderilen tutar. */
  public record FraudRequest(@NotNull BigDecimal amount) {}

  /** Deterministik fraud kararı. */
  public record FraudResult(boolean approved, String reason) {}

  /** operationId + userId ile idempotent hale getirilen bildirim mesajı. */
  public record Notification(
      @NotNull UUID operationId, @NotNull UUID userId, @NotBlank @Size(max = 500) String message) {}

  /** Sonraki kaç bildirim çağrısının geçici olarak başarısız olacağını ayarlar. */
  public record FailureScenario(@Min(0) @Max(20) int count) {}

  /** Kalıcı provider sonucu ile HTTP cevap geciktirme davranışını birlikte taşır. */
  public record Execution(ExternalResult result, boolean delay) {}
}
