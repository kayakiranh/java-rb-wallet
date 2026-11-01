package com.rbwallet.fake;

import static com.rbwallet.fake.Contracts.*;

import jakarta.validation.Valid;
import java.io.IOException;
import org.springframework.web.bind.annotation.*;

/**
 * Bankadan cüzdana para yükleme sağlayıcısını taklit eden REST adapter'ı.
 *
 * <p>Aynı operationId ile gelen çağrılar FakeStore tarafından idempotent karşılanır. TIMEOUT
 * senaryosunda sonuç önce kalıcılaştırılıp cevap geciktirilir; böylece gerçek dünyadaki “işlendi ama
 * cevap kayboldu” durumu test edilir. .NET'teki ayrı fake ASP.NET Core Controller'a denktir.
 */
@RestController
@RequestMapping("/api/bank")
public class BankController {
  private final FakeStore store;

  public BankController(FakeStore store) {
    this.store = store;
  }

  @PostMapping("/top-ups")
  /** Banka yüklemesini bir kez işler ve seçili demo senaryosunu uygular. */
  public ExternalResult topUp(@Valid @RequestBody ExternalOperation request)
      throws IOException, InterruptedException {
    var result = store.execute("bank", request);
    // TR: Önce işlem diske yazılır. Sonra gecikme, cevabın kaybolmasını simüle eder.
    if (result.delay()) Thread.sleep(8000);
    return result.result();
  }
}
