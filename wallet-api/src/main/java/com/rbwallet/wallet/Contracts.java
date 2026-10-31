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

  public record Register(
      @NotBlank @Size(min = 2, max = 100) String name,
      @NotBlank @Email @Size(max = 254) String email,
      @NotBlank @Size(min = 12, max = 128) String password) {}

  public record Login(
      @NotBlank @Email @Size(max = 254) String email, @NotBlank @Size(max = 128) String password) {}

  public record TopUp(
      @NotNull UUID requestKey,
      @NotNull BigDecimal amount,
      @NotBlank @Size(max = 50) String bankAccount) {}

  public record Transfer(
      @NotNull UUID requestKey,
      @NotNull BigDecimal amount,
      @NotBlank @Size(max = 32) String recipientWalletNumber) {}

  public record BillPayment(
      @NotNull UUID requestKey, @NotBlank @Size(max = 50) String subscriberNumber) {}

  public record Token(String accessToken, Instant expiresAt) {}

  public record Wallet(
      String id,
      String userId,
      String number,
      BigDecimal balance,
      BigDecimal reserved,
      long version) {
    public WalletResponse response() {
      return new WalletResponse(number, balance, reserved, balance.subtract(reserved), "TRY");
    }
  }

  public record WalletResponse(
      String number,
      BigDecimal balance,
      BigDecimal reserved,
      BigDecimal available,
      String currency) {}

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

  public record Bill(String subscriberNumber, BigDecimal amount, boolean paid) {}

  public record ExternalOperation(String operationId, BigDecimal amount, String target) {}

  public record ExternalResult(String status, String reason) {}

  public record FraudRequest(BigDecimal amount) {}

  public record FraudResult(boolean approved, String reason) {}

  public record Notification(String operationId, String userId, String message) {}

  public record Delivery(
      String id,
      String transactionId,
      String userId,
      int attempts,
      Instant deliveredAt,
      Instant nextAttemptAt,
      String lastError) {}
}
