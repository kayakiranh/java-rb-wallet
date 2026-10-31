package com.rbwallet.wallet;

import static com.rbwallet.wallet.Contracts.*;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * TR: JdbcTemplate, Dapper'a benzer: SQL açıkça görünür, bağlantı havuzunu Spring yönetir.
 * TransactionTemplate, EF SaveChanges'in atomik sınırını burada açıkça belirler. EN: SQL
 * persistence; optimistic versions prevent double credits and overspending.
 */
@Repository
public class WalletStore {
  private final JdbcTemplate jdbc;
  private final TransactionTemplate transactions;

  public WalletStore(JdbcTemplate jdbc, TransactionTemplate transactions) {
    this.jdbc = jdbc;
    this.transactions = transactions;
  }

  public <T> T atomic(Supplier<T> work) {
    return transactions.execute(status -> work.get());
  }

  public record UserCredentials(String id, String passwordHash) {}

  /** TR: Auth SQL de Store içinde kalır; proxy nesnesinin field'larına dışarıdan erişilmez. */
  public UserCredentials credentials(String email) {
    return jdbc
        .query(
            "SELECT id,password_hash FROM users WHERE email=?",
            (r, n) -> new UserCredentials(r.getString("id"), r.getString("password_hash")),
            email)
        .stream()
        .findFirst()
        .orElse(null);
  }

  public String registerUser(String name, String email, String hash) {
    return atomic(
        () -> {
          if (credentials(email) != null)
            throw new BusinessException("E-posta zaten kayıtlı.", 409);
          String id = UUID.randomUUID().toString();
          jdbc.update(
              "INSERT INTO users(id,name,email,password_hash) VALUES(?,?,?,?)",
              id,
              name,
              email,
              hash);
          jdbc.update(
              "INSERT INTO wallets(id,user_id,number) VALUES(?,?,?)",
              UUID.randomUUID().toString(),
              id,
              UUID.randomUUID().toString().replace("-", ""));
          return id;
        });
  }

  public boolean ready() {
    return Integer.valueOf(1).equals(jdbc.queryForObject("SELECT 1", Integer.class));
  }

  private static final RowMapper<Wallet> WALLET =
      (r, n) ->
          new Wallet(
              r.getString("id"),
              r.getString("user_id"),
              r.getString("number"),
              r.getBigDecimal("balance"),
              r.getBigDecimal("reserved"),
              r.getLong("version"));
  private static final RowMapper<Operation> OP =
      (r, n) ->
          new Operation(
              r.getString("id"),
              r.getString("wallet_id"),
              r.getString("recipient_wallet_id"),
              r.getString("request_key"),
              r.getString("kind"),
              r.getString("status"),
              r.getBigDecimal("amount"),
              r.getString("target"),
              r.getString("failure_reason"),
              Instant.ofEpochMilli(r.getLong("created_at")),
              r.getLong("version"));

  private static Instant nullableTime(ResultSet r, String column) throws SQLException {
    long v = r.getLong(column);
    return r.wasNull() ? null : Instant.ofEpochMilli(v);
  }

  private static final RowMapper<Delivery> DELIVERY =
      (r, n) ->
          new Delivery(
              r.getString("id"),
              r.getString("transaction_id"),
              r.getString("user_id"),
              r.getInt("attempts"),
              nullableTime(r, "delivered_at"),
              Instant.ofEpochMilli(r.getLong("next_attempt_at")),
              r.getString("last_error"));

  public Wallet wallet(String userId) {
    return jdbc.query("SELECT * FROM wallets WHERE user_id=?", WALLET, userId).stream()
        .findFirst()
        .orElseThrow(() -> new BusinessException("Cüzdan bulunamadı.", 404));
  }

  public Wallet walletById(String id) {
    return jdbc.queryForObject("SELECT * FROM wallets WHERE id=?", WALLET, id);
  }

  public Wallet recipient(String number) {
    return jdbc.query("SELECT * FROM wallets WHERE number=?", WALLET, number).stream()
        .findFirst()
        .orElseThrow(() -> new BusinessException("Alıcı cüzdan bulunamadı.", 404));
  }

  public Operation existing(
      String walletId, String key, String kind, String target, BigDecimal amount) {
    Operation t =
        jdbc
            .query(
                "SELECT * FROM wallet_transactions WHERE wallet_id=? AND request_key=?",
                OP,
                walletId,
                key)
            .stream()
            .findFirst()
            .orElse(null);
    if (t != null
        && (!t.kind().equals(kind)
            || !t.target().equals(target)
            || (amount != null && t.amount().compareTo(amount) != 0)))
      throw new BusinessException("RequestKey farklı bir istek için kullanılmış.", 409);
    return t;
  }

  public Operation operation(String id) {
    return jdbc.query("SELECT * FROM wallet_transactions WHERE id=?", OP, id).stream()
        .findFirst()
        .orElseThrow(() -> new BusinessException("İşlem bulunamadı.", 404));
  }

  public Operation byRequest(String walletId, String key) {
    return jdbc
        .query(
            "SELECT * FROM wallet_transactions WHERE wallet_id=? AND request_key=?",
            OP,
            walletId,
            key)
        .stream()
        .findFirst()
        .orElseThrow(() -> new BusinessException("İşlem bulunamadı.", 404));
  }

  public Operation insert(
      Wallet w,
      String key,
      String kind,
      BigDecimal amount,
      String target,
      String recipient,
      String status,
      String reason) {
    String id = UUID.randomUUID().toString();
    long now = System.currentTimeMillis();
    jdbc.update(
        "INSERT INTO"
            + " wallet_transactions(id,wallet_id,recipient_wallet_id,request_key,kind,status,amount,target,failure_reason,created_at,next_attempt_at)"
            + " VALUES(?,?,?,?,?,?,?,?,?,?,?)",
        id,
        w.id(),
        recipient,
        key,
        kind,
        status,
        amount,
        target,
        reason,
        now,
        now + 15000);
    return operation(id);
  }

  /**
   * TR: WHERE version=? bir compare-and-swap işlemidir. Yarış varsa tüm SQL transaction geri
   * alınır.
   */
  public void balance(Wallet w, BigDecimal balance, BigDecimal reserved) {
    int changed =
        jdbc.update(
            "UPDATE wallets SET balance=?,reserved=?,version=version+1 WHERE id=? AND version=?",
            balance,
            reserved,
            w.id(),
            w.version());
    if (changed != 1)
      throw new BusinessException("Eşzamanlı işlem. Aynı RequestKey ile tekrar deneyin.", 409);
  }

  public void outbox(String userId, String transactionId) {
    jdbc.update(
        "INSERT INTO notifications(id,transaction_id,user_id,next_attempt_at) VALUES(?,?,?,?)",
        UUID.randomUUID().toString(),
        transactionId,
        userId,
        System.currentTimeMillis());
  }

  public Operation finish(String id, ExternalResult result) {
    return atomic(
        () -> {
          Operation t = operation(id);
          if (!t.status().equals("Pending")) return t;
          // TR: İşlem durumunu ve bakiyeyi aynı SQL transaction'ında değiştir; ikinci tamamlayıcı
          // kazanamaz.
          int changed =
              jdbc.update(
                  "UPDATE wallet_transactions SET status=?,failure_reason=?,version=version+1 WHERE"
                      + " id=? AND status='Pending' AND version=?",
                  result.status(),
                  result.reason(),
                  id,
                  t.version());
          if (changed != 1)
            throw new BusinessException("İşlem başka bir istek tarafından güncellendi.", 409);
          Wallet w = walletById(t.walletId());
          BigDecimal reserved =
              t.kind().equals("BillPayment") ? w.reserved().subtract(t.amount()) : w.reserved();
          BigDecimal balance = w.balance();
          if (result.status().equals("Completed"))
            balance =
                t.kind().equals("TopUp") ? balance.add(t.amount()) : balance.subtract(t.amount());
          balance(w, balance, reserved);
          if (result.status().equals("Completed")) outbox(w.userId(), id);
          return operation(id);
        });
  }

  public void defer(String id) {
    atomic(
        () -> {
          Integer attempts =
              jdbc.queryForObject(
                  "SELECT recovery_attempts FROM wallet_transactions WHERE id=?",
                  Integer.class,
                  id);
          jdbc.update(
              "UPDATE wallet_transactions SET"
                  + " recovery_attempts=recovery_attempts+1,next_attempt_at=?,version=version+1"
                  + " WHERE id=? AND status='Pending'",
              System.currentTimeMillis() + MoneyRules.retrySeconds(attempts + 1) * 1000,
              id);
          return null;
        });
  }

  public List<Operation> pending() {
    return jdbc.query(
        "SELECT * FROM wallet_transactions WHERE status='Pending' AND next_attempt_at<=? ORDER BY"
            + " next_attempt_at OFFSET 0 ROWS FETCH NEXT 25 ROWS ONLY",
        OP,
        System.currentTimeMillis());
  }

  public List<TransactionResponse> history(String userId, int page) {
    if (page < 1 || page > 10000) throw new BusinessException("Geçersiz sayfa.");
    Wallet w = wallet(userId);
    return jdbc
        .query(
            "SELECT * FROM wallet_transactions WHERE wallet_id=? OR recipient_wallet_id=? ORDER BY"
                + " created_at DESC,id DESC OFFSET ? ROWS FETCH NEXT 50 ROWS ONLY",
            OP,
            w.id(),
            w.id(),
            (page - 1) * 50)
        .stream()
        .map(t -> t.response(w.id()))
        .toList();
  }

  public List<Delivery> dueNotifications() {
    return jdbc.query(
        "SELECT * FROM notifications WHERE delivered_at IS NULL AND next_attempt_at<=? ORDER BY"
            + " next_attempt_at OFFSET 0 ROWS FETCH NEXT 25 ROWS ONLY",
        DELIVERY,
        System.currentTimeMillis());
  }

  public List<Delivery> notifications(String userId) {
    return jdbc.query(
        "SELECT * FROM notifications WHERE user_id=? ORDER BY next_attempt_at DESC OFFSET 0 ROWS"
            + " FETCH NEXT 50 ROWS ONLY",
        DELIVERY,
        userId);
  }

  public void delivered(Delivery n, boolean success) {
    // TR: Başka worker teslim ettiyse geciken başarısız cevap teslim bilgisini silemez.
    jdbc.update(
        "UPDATE notifications SET attempts=attempts+1,delivered_at=?,next_attempt_at=?,last_error=?"
            + " WHERE id=? AND delivered_at IS NULL",
        success ? System.currentTimeMillis() : null,
        System.currentTimeMillis() + MoneyRules.retrySeconds(n.attempts() + 1) * 1000,
        success ? null : "ProviderUnavailable",
        n.id());
  }
}
