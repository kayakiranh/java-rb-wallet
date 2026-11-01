package com.rbwallet.wallet;

import static com.rbwallet.wallet.Contracts.*;

import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;

/**
 * TR: HttpClient/Refit karşılığı. Tüm çağrılarda AppConfig'in X-Api-Key ve timeout ayarları
 * kullanılır.
 */
@Component
public class FakeServicesClient {
  private final RestClient http;

  public FakeServicesClient(RestClient http) {
    this.http = http;
  }

  /** Transfer/fatura tutarı için dış fraud kararını alır. */
  public FraudResult fraud(java.math.BigDecimal amount) {
    return Objects.requireNonNull(
        http.post()
            .uri("/api/fraud/check")
            .body(new FraudRequest(amount))
            .retrieve()
            .body(FraudResult.class));
  }

  /** Abone faturasını sorgular ve provider 404'ünü domain hatasına çevirir. */
  public Bill bill(String subscriber) {
    try {
      return Objects.requireNonNull(
          http.get()
              .uri("/api/billing/bills/{subscriber}", subscriber)
              .retrieve()
              .body(Bill.class));
    } catch (HttpClientErrorException.NotFound ex) {
      throw new BusinessException("Fatura bulunamadı.", 404);
    }
  }

  /** Timeout sonrası sağlayıcının operation id ile sakladığı kesin sonucu arar. */
  public ExternalResult find(String id) {
    try {
      return Objects.requireNonNull(
          http.get().uri("/api/operations/{id}", id).retrieve().body(ExternalResult.class));
    } catch (HttpClientErrorException.NotFound ex) {
      return null;
    }
  }

  /** İşlem türüne göre banka veya fatura endpointine aynı operation id ile komut yollar. */
  public ExternalResult execute(Operation t) {
    String path = t.kind().equals("TopUp") ? "/api/bank/top-ups" : "/api/billing/payments";
    return Objects.requireNonNull(
        http.post()
            .uri(path)
            .body(new ExternalOperation(t.id(), t.amount(), t.target()))
            .retrieve()
            .body(ExternalResult.class));
  }

  /** Outbox teslimatını idempotent bildirim sağlayıcısına gönderir. */
  public void notify(Delivery n) {
    http.post()
        .uri("/api/notification/messages")
        .body(new Notification(n.transactionId(), n.userId(), "Cüzdan işleminiz tamamlandı."))
        .retrieve()
        .toBodilessEntity();
  }
}
