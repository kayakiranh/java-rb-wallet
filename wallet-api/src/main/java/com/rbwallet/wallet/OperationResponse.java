package com.rbwallet.wallet;

import org.springframework.http.ResponseEntity;

/** TR: Pending finansal sonuç henüz kesin değildir; istemciye 202 döner. */
final class OperationResponse {
  private OperationResponse() {}

  /** Kesin sonuçları 200, provider sonucu henüz bilinmeyen Pending işlemleri 202 ile döndürür. */
  static ResponseEntity<Contracts.TransactionResponse> of(Contracts.TransactionResponse result) {
    return ResponseEntity.status(result.status().equals("Pending") ? 202 : 200).body(result);
  }
}
