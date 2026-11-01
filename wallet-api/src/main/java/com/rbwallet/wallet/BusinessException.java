package com.rbwallet.wallet;

/** TR: İş kuralı ihlali ile altyapı hatasını ayırır; HTTP cevabına Advice çevirir. */
public class BusinessException extends RuntimeException {
  private final int status;

  /** Varsayılan olarak HTTP 400'e eşlenecek iş kuralı hatası oluşturur. */
  public BusinessException(String message) {
    this(message, 400);
  }

  /** İş kuralı mesajını beklenen HTTP status ile taşır; stack trace API cevabına verilmez. */
  public BusinessException(String message, int status) {
    super(message);
    this.status = status;
  }

  /** Global advice'ın kullanacağı HTTP status değerini döndürür. */
  public int status() {
    return status;
  }
}
