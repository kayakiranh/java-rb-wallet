package com.rbwallet.fake;

import static com.rbwallet.fake.Contracts.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * TR: synchronized, C# lock gibi bu instance içindeki kritik bölgeyi korur. Dosya kilidi ikinci
 * process'in aynı fake state dosyasını açmasını engeller. Önce disk yazılır sonra RAM değiştirilir;
 * yazma hatasında başarılı cevap verilmez. EN: Single-instance durable fake provider; never use
 * this as the real wallet ledger.
 */
@Component
public class FakeStore implements AutoCloseable {
  private final Path path;
  private final ObjectMapper json;
  private final FileChannel lockChannel;
  private final FileLock processLock;
  private State state;

  public record Saved(String kind, ExternalOperation request, ExternalResult result) {}

  public static class State {
    public int version = 1;
    public Map<String, Saved> operations = new HashMap<>();
    public Map<String, Notification> notifications = new HashMap<>();
    public int notificationFailures;
    public Map<String, Bill> bills =
        new HashMap<>(
            Map.of(
                "10001",
                new Bill("10001", new BigDecimal("150"), false),
                "10002",
                new Bill("10002", new BigDecimal("250"), false),
                "REJECT",
                new Bill("REJECT", new BigDecimal("150"), false),
                "TIMEOUT",
                new Bill("TIMEOUT", new BigDecimal("150"), false)));

    State copy() {
      State n = new State();
      n.version = version;
      n.operations = new HashMap<>(operations);
      n.notifications = new HashMap<>(notifications);
      n.notificationFailures = notificationFailures;
      n.bills = new HashMap<>(bills);
      return n;
    }
  }

  public FakeStore(@Value("${fake.storage-path}") String file, ObjectMapper json)
      throws IOException {
    this.path = Path.of(file).toAbsolutePath();
    this.json = json;
    Files.createDirectories(path.getParent());
    lockChannel =
        FileChannel.open(
            Path.of(path + ".lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
    FileLock acquired = null;
    try {
      acquired = lockChannel.tryLock();
      if (acquired == null)
        throw new IOException("Fake state başka process tarafından kullanılıyor.");
      state =
          Files.exists(path) ? json.readValue(Files.readAllBytes(path), State.class) : new State();
      if (state == null
          || state.version != 1
          || state.operations == null
          || state.bills == null
          || state.notifications == null)
        throw new IOException("Geçersiz fake state; otomatik sıfırlama yapılmadı.");
    } catch (IOException | RuntimeException ex) {
      if (acquired != null) acquired.release();
      lockChannel.close();
      throw ex;
    }
    processLock = acquired;
  }

  public synchronized Bill bill(String subscriber) {
    return state.bills.get(subscriber);
  }

  public synchronized ExternalResult operation(UUID id) {
    Saved saved = state.operations.get(id.toString());
    return saved == null ? null : saved.result();
  }

  public synchronized Execution execute(String kind, ExternalOperation request) throws IOException {
    String id = request.operationId().toString();
    Saved old = state.operations.get(id);
    if (old != null) {
      // TR: BigDecimal.equals 150 ile 150.00 için false verir; burada sayısal eşitlik gerekir.
      boolean same =
          old.kind().equals(kind)
              && old.request().amount().compareTo(request.amount()) == 0
              && old.request().target().equals(request.target());
      return new Execution(
          same ? old.result() : new ExternalResult("Rejected", "IdempotencyConflict"), false);
    }
    if (request.operationId().equals(new UUID(0, 0))
        || request.amount().signum() <= 0
        || request.amount().compareTo(new BigDecimal("1000000")) > 0
        || request.amount().stripTrailingZeros().scale() > 2)
      return new Execution(new ExternalResult("Rejected", "InvalidRequest"), false);
    State next = state.copy();
    ExternalResult result;
    if (request.target().equals("REJECT")) result = new ExternalResult("Rejected", "DemoRejected");
    else if (kind.equals("bank"))
      result =
          new ExternalResult(
              Set.of("DEMO-ACCOUNT", "TIMEOUT").contains(request.target())
                  ? "Completed"
                  : "Rejected",
              Set.of("DEMO-ACCOUNT", "TIMEOUT").contains(request.target())
                  ? null
                  : "AccountNotFound");
    else {
      Bill bill = next.bills.get(request.target());
      if (bill == null) result = new ExternalResult("Rejected", "BillNotFound");
      else if (bill.paid()) result = new ExternalResult("Rejected", "BillAlreadyPaid");
      else if (bill.amount().compareTo(request.amount()) != 0)
        result = new ExternalResult("Rejected", "AmountMismatch");
      else {
        next.bills.put(request.target(), new Bill(bill.subscriberNumber(), bill.amount(), true));
        result = new ExternalResult("Completed", null);
      }
    }
    next.operations.put(id, new Saved(kind, request, result));
    save(next);
    return new Execution(result, request.target().equals("TIMEOUT"));
  }

  public synchronized boolean notify(Notification request) throws IOException {
    String key = request.operationId() + ":" + request.userId();
    if (state.notifications.containsKey(key)) return true;
    State next = state.copy();
    if (next.notificationFailures > 0) {
      next.notificationFailures--;
      save(next);
      return false;
    }
    next.notifications.put(key, request);
    save(next);
    return true;
  }

  public synchronized List<Notification> messages() {
    return List.copyOf(state.notifications.values());
  }

  public synchronized void failNext(int count) throws IOException {
    if (count < 0 || count > 20) throw new IllegalArgumentException("count 0..20 olmalı");
    State next = state.copy();
    next.notificationFailures = count;
    save(next);
  }

  private void save(State next) throws IOException {
    Path temp = Path.of(path + ".tmp");
    try (var file =
        FileChannel.open(
            temp,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE)) {
      ByteBuffer bytes = ByteBuffer.wrap(json.writeValueAsBytes(next));
      while (bytes.hasRemaining()) file.write(bytes);
      file.force(true);
    }
    // TR: Aynı dosya sisteminde atomik rename; destek yoksa sessizce daha zayıf yazmaya geçilmez.
    Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    state = next;
  }

  @PreDestroy
  @Override
  public void close() throws IOException {
    if (processLock.isValid()) processLock.release();
    lockChannel.close();
  }
}
