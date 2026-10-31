package com.rbwallet.wallet;

/** TR: İş kuralı ihlali ile altyapı hatasını ayırır; HTTP cevabına Advice çevirir. */
public class BusinessException extends RuntimeException {
  private final int status;

  public BusinessException(String message) {
    this(message, 400);
  }

  public BusinessException(String message, int status) {
    super(message);
    this.status = status;
  }

  public int status() {
    return status;
  }
}
