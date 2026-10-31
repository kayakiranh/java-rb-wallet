package com.rbwallet.fake;

import static com.rbwallet.fake.Contracts.*;

import jakarta.validation.Valid;
import java.io.IOException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** TR: Yalnızca geliştirme fake servisinde bildirim retry senaryosu üretir. */
@RestController
@RequestMapping("/api/scenarios")
public class ScenariosController {
  private final FakeStore store;

  public ScenariosController(FakeStore store) {
    this.store = store;
  }

  @PutMapping("/notification-failures")
  public ResponseEntity<Void> failures(@Valid @RequestBody FailureScenario request)
      throws IOException {
    store.failNext(request.count());
    return ResponseEntity.noContent().build();
  }
}
