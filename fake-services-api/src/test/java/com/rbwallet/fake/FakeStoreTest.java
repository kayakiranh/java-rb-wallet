package com.rbwallet.fake;

import static com.rbwallet.fake.Contracts.*;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

/** TR: Her test ayrı geçici dizin kullanır; gerçek demo verisi silinmez. */
class FakeStoreTest {
  @TempDir Path dir;

  private FakeStore open() throws Exception {
    return new FakeStore(dir.resolve("state.json").toString(), new ObjectMapper());
  }

  @Test
  void restartPreservesPaidBillAndScaleIndependentReplay() throws Exception {
    var request = new ExternalOperation(UUID.randomUUID(), new BigDecimal("150"), "10001");
    try (var first = open()) {
      assertEquals("Completed", first.execute("bill", request).result().status());
    }
    try (var second = open()) {
      assertTrue(second.bill("10001").paid());
      assertEquals(
          "Completed",
          second
              .execute(
                  "bill",
                  new ExternalOperation(request.operationId(), new BigDecimal("150.00"), "10001"))
              .result()
              .status());
      assertEquals(
          "Rejected",
          second
              .execute(
                  "bill", new ExternalOperation(UUID.randomUUID(), new BigDecimal("150"), "10001"))
              .result()
              .status());
    }
  }

  @Test
  void timeoutCommitsBeforeDelayingAndReplayDoesNotDelay() throws Exception {
    try (var store = open()) {
      var r = new ExternalOperation(UUID.randomUUID(), new BigDecimal("100"), "TIMEOUT");
      assertTrue(store.execute("bank", r).delay());
      assertFalse(store.execute("bank", r).delay());
      assertEquals("Completed", store.operation(r.operationId()).status());
    }
  }

  @Test
  void conflictingReferenceCannotChangeAmount() throws Exception {
    try (var store = open()) {
      UUID id = UUID.randomUUID();
      store.execute("bank", new ExternalOperation(id, new BigDecimal("100"), "DEMO-ACCOUNT"));
      assertEquals(
          "IdempotencyConflict",
          store
              .execute("bank", new ExternalOperation(id, new BigDecimal("200"), "DEMO-ACCOUNT"))
              .result()
              .reason());
    }
  }

  @Test
  void notificationFailureAndDeduplicationSurviveRestart() throws Exception {
    var message = new Notification(UUID.randomUUID(), UUID.randomUUID(), "Test");
    try (var store = open()) {
      store.failNext(1);
      assertFalse(store.notify(message));
    }
    try (var store = open()) {
      assertTrue(store.notify(message));
      assertTrue(store.notify(message));
      assertTrue(
          store.notify(new Notification(message.operationId(), UUID.randomUUID(), "Other user")));
      assertEquals(2, store.messages().size());
    }
    try (var store = open()) {
      assertEquals(2, store.messages().size());
    }
  }

  @Test
  void concurrentBillHasOnlyOneWinner() throws Exception {
    try (var store = open();
        var pool = Executors.newFixedThreadPool(4)) {
      var jobs = new ArrayList<Callable<String>>();
      for (int i = 0; i < 20; i++)
        jobs.add(
            () ->
                store
                    .execute(
                        "bill",
                        new ExternalOperation(UUID.randomUUID(), new BigDecimal("150"), "10001"))
                    .result()
                    .status());
      int completed = 0;
      for (var job : pool.invokeAll(jobs)) if (job.get().equals("Completed")) completed++;
      assertEquals(1, completed);
    }
  }

  @Test
  void corruptionAndSecondInstanceAreRejected() throws Exception {
    try (var store = open()) {
      assertThrows(Exception.class, this::open);
    }
    Files.writeString(dir.resolve("state.json"), "broken");
    assertThrows(Exception.class, this::open);
  }
}
