package com.rbwallet.wallet;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * TR: record, C# positional record gibi immutable DTO'dur. Para için double değil BigDecimal
 * kullanılır.
 */
public final class Contracts {
  private Contracts() {}

  /** Kayıt isteği DTO'su; Java record C# positional record gibi immutable veri taşır. */
  public record Register(
      @NotBlank @Size(min = 2, max = 100) String name,
      @NotBlank @Email @Size(max = 254) String email,
      @NotBlank @Size(min = 12, max = 128) String password) {}

  /** Login isteği; parola yalnızca request boyunca taşınır ve loglanmaz. */
  public record Login(
      @NotBlank @Email @Size(max = 254) String email, @NotBlank @Size(max = 128) String password) {}

  /**
   * Bankadan yükleme komutu. requestKey, istemci retry'larında aynı işlemin tekrar oluşmasını
   * önleyen Idempotency Key desenidir.
   */
  public record TopUp(
      @NotNull UUID requestKey,
      @NotNull BigDecimal amount,
      @NotBlank @Size(max = 50) String bankAccount) {}

  /** Cüzdanlar arası transfer komutu; alıcı kullanıcı id'si yerine dışarı açık cüzdan numarasıdır. */
  public record Transfer(
      @NotNull UUID requestKey,
      @NotNull BigDecimal amount,
      @NotBlank @Size(max = 32) String recipientWalletNumber) {}

  /** Fatura ödeme komutu; tutar istemciden değil güvenilen fatura sağlayıcısından alınır. */
  public record BillPayment(
      @NotNull UUID requestKey, @NotBlank @Size(max = 50) String subscriberNumber) {}

  /** JWT access token ve UTC bitiş zamanını döndüren response DTO'su. */
  public record Token(String accessToken, Instant expiresAt) {}

  /**
   * Veritabanı cüzdan modeli. C# class/record karşılığıdır; JPA entity kullanılmadığı için SQL row
   * doğrudan JdbcTemplate RowMapper ile bu immutable tipe çevrilir.
   */
  public record Wallet(
      String id,
      String userId,
      String number,
      BigDecimal balance,
      BigDecimal reserved,
      long version) {
    /** İç persistence modelini API'ye özel ve daha dar response modeline dönüştürür. */
    public WalletResponse response() {
      return new WalletResponse(number, balance, reserved, balance.subtract(reserved), "TRY");
    }
  }

  /** API'ye dönen bakiye görünümü; kullanılabilir tutar balance-reserved değeridir. */
  public record WalletResponse(
      String number,
      BigDecimal balance,
      BigDecimal reserved,
      BigDecimal available,
      String currency) {}

  /**
   * Finansal işlemin persistence modeli. kind/status alanları demo kapsamını sade tutmak için String;
   * daha büyük sistemde enum/state machine ile sınırlandırılabilir.
   */
  public record Operation(
      String id,
      String walletId,
      String recipientWalletId,
      String requestKey,
      String kind,
      String status,
      BigDecimal amount,
      String target,
      String failureReason,
      Instant createdAt,
      long version) {
    /** İşlemi izleyici cüzdana göre Incoming/Outgoing API cevabına dönüştürür. */
    public TransactionResponse response(String viewerWalletId) {
      String direction =
          kind.equals("TopUp")
                  || (viewerWalletId != null && viewerWalletId.equals(recipientWalletId))
              ? "Incoming"
              : "Outgoing";
      return new TransactionResponse(
          id, requestKey, kind, status, amount, target, failureReason, createdAt, direction);
    }
  }

  /** İstemciye gösterilen işlem özeti; iç walletId ve version gibi alanları sızdırmaz. */
  public record TransactionResponse(
      String id,
      String requestKey,
      String kind,
      String status,
      BigDecimal amount,
      String target,
      String failureReason,
      Instant createdAt,
      String direction) {}

  /** Dış sağlayıcıdan alınan fatura görünümü. */
  public record Bill(String subscriberNumber, BigDecimal amount, boolean paid) {}

  /** Wallet'ın banka/fatura fake sağlayıcısına gönderdiği ortak işlem sözleşmesi. */
  public record ExternalOperation(String operationId, BigDecimal amount, String target) {}

  /** Sağlayıcının kesin Completed/Rejected sonucu. */
  public record ExternalResult(String status, String reason) {}

  /** Fraud karar servisine gönderilen parasal risk girdisi. */
  public record FraudRequest(BigDecimal amount) {}

  /** Fraud servisinin karar ve açıklaması. */
  public record FraudResult(boolean approved, String reason) {}

  /** Outbox worker'ın bildirim sağlayıcısına gönderdiği mesaj. */
  public record Notification(String operationId, String userId, String message) {}

  /** Bildirim outbox kaydı ve retry/delivery metadata'sı. */
  public record Delivery(
      String id,
      String transactionId,
      String userId,
      int attempts,
      Instant deliveredAt,
      Instant nextAttemptAt,
      String lastError) {}
}
