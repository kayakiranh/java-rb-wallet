package com.rbwallet.wallet;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * TR: BigDecimal karşılaştırmasında equals scale'e de bakar; parasal karşılaştırmada compareTo
 * kullanılır.
 */
public final class MoneyRules {
  private MoneyRules() {}

  /** UUID, pozitif üst limit ve iki ondalık hane kurallarını merkezi olarak uygular. */
  public static void validate(UUID key, BigDecimal amount) {
    if (key == null || key.equals(new UUID(0, 0)))
      throw new BusinessException("RequestKey boş olamaz.");
    if (amount == null
        || amount.signum() <= 0
        || amount.compareTo(new BigDecimal("1000000")) > 0
        || amount.stripTrailingZeros().scale() > 2)
      throw new BusinessException(
          "Tutar 0 ile 1.000.000 arasında ve en fazla iki ondalık haneli olmalı.");
  }

  /** Rezerve tutarı düşerek gerçekten harcanabilir bakiyeyi kontrol eder. */
  public static void funds(Contracts.Wallet wallet, BigDecimal amount) {
    if (wallet.balance().subtract(wallet.reserved()).compareTo(amount) < 0)
      throw new BusinessException("Yetersiz kullanılabilir bakiye.");
  }

  /** 10–640 saniye aralığında exponential backoff üretir. */
  public static long retrySeconds(int attempts) {
    return 10L << Math.min(6, Math.max(0, attempts - 1));
  }
}
