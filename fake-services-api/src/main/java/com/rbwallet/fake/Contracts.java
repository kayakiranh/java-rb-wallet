package com.rbwallet.fake;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

public final class Contracts {
  private Contracts() {}

  public record ExternalOperation(
      @NotNull UUID operationId,
      @NotNull BigDecimal amount,
      @NotBlank @Size(max = 50) String target) {}

  public record ExternalResult(String status, String reason) {}

  public record Bill(String subscriberNumber, BigDecimal amount, boolean paid) {}

  public record FraudRequest(@NotNull BigDecimal amount) {}

  public record FraudResult(boolean approved, String reason) {}

  public record Notification(
      @NotNull UUID operationId, @NotNull UUID userId, @NotBlank @Size(max = 500) String message) {}

  public record FailureScenario(@Min(0) @Max(20) int count) {}

  public record Execution(ExternalResult result, boolean delay) {}
}
