package com.rbwallet.fake;

import static com.rbwallet.fake.Contracts.*;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/operations")
public class OperationsController {
  private final FakeStore store;

  public OperationsController(FakeStore store) {
    this.store = store;
  }

  @GetMapping("/{id}")
  public ResponseEntity<ExternalResult> get(@PathVariable java.util.UUID id) {
    var result = store.operation(id);
    return result == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(result);
  }
}
