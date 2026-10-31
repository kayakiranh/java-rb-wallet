package com.rbwallet.wallet;

import static com.rbwallet.wallet.Contracts.*;

import java.util.UUID;
import org.slf4j.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

/**
 * TR: Controller HTTP'yi, Service iş akışını, Store SQL'i yönetir. Gereksiz generic repository
 * yoktur. EN: Application service. No database transaction stays open across a provider HTTP call.
 */
@Service
public class WalletService {
  private static final Logger log = LoggerFactory.getLogger(WalletService.class);
  private final WalletStore db;
  private final FakeServicesClient fake;

  public WalletService(WalletStore db, FakeServicesClient fake) {
    this.db = db;
    this.fake = fake;
  }

  public WalletResponse wallet(String userId) {
    return db.wallet(userId).response();
  }

  public TransactionResponse topUp(String userId, TopUp request) {
    MoneyRules.validate(request.requestKey(), request.amount());
    Operation t =
        db.atomic(
            () -> {
              Wallet w = db.wallet(userId);
              Operation old =
                  db.existing(
                      w.id(),
                      request.requestKey().toString(),
                      "TopUp",
                      request.bankAccount(),
                      request.amount());
              return old != null
                  ? old
                  : db.insert(
                      w,
                      request.requestKey().toString(),
                      "TopUp",
                      request.amount(),
                      request.bankAccount(),
                      null,
                      "Pending",
                      null);
            });
    return complete(t).response(null);
  }

  public TransactionResponse transfer(String userId, Transfer request) {
    MoneyRules.validate(request.requestKey(), request.amount());
    Wallet initial = db.wallet(userId);
    Operation old =
        db.existing(
            initial.id(),
            request.requestKey().toString(),
            "Transfer",
            request.recipientWalletNumber(),
            request.amount());
    if (old != null) return old.response(null);
    MoneyRules.funds(initial, request.amount());
    // TR: Dış çağrı SQL transaction başlamadan yapılır; sonra bakiye tekrar kontrol edilir.
    FraudResult fraud = fake.fraud(request.amount());
    return db.atomic(
        () -> {
          Wallet w = db.wallet(userId);
          Operation replay =
              db.existing(
                  w.id(),
                  request.requestKey().toString(),
                  "Transfer",
                  request.recipientWalletNumber(),
                  request.amount());
          if (replay != null) return replay.response(null);
          Wallet recipient = db.recipient(request.recipientWalletNumber());
          if (recipient.id().equals(w.id()))
            throw new BusinessException("Kendi cüzdanınıza transfer yapamazsınız.");
          MoneyRules.funds(w, request.amount());
          Operation t =
              db.insert(
                  w,
                  request.requestKey().toString(),
                  "Transfer",
                  request.amount(),
                  recipient.number(),
                  recipient.id(),
                  fraud.approved() ? "Completed" : "Rejected",
                  fraud.reason());
          if (fraud.approved()) {
            db.balance(w, w.balance().subtract(request.amount()), w.reserved());
            db.balance(recipient, recipient.balance().add(request.amount()), recipient.reserved());
            db.outbox(w.userId(), t.id());
            db.outbox(recipient.userId(), t.id());
          }
          return t.response(null);
        });
  }

  public Bill bill(String subscriber) {
    if (subscriber == null || subscriber.isBlank() || subscriber.length() > 50)
      throw new BusinessException("Geçersiz abone numarası.");
    return fake.bill(subscriber);
  }

  public TransactionResponse payBill(String userId, BillPayment request) {
    if (request.requestKey().equals(new UUID(0, 0)))
      throw new BusinessException("RequestKey boş olamaz.");
    Wallet initial = db.wallet(userId);
    Operation old =
        db.existing(
            initial.id(),
            request.requestKey().toString(),
            "BillPayment",
            request.subscriberNumber(),
            null);
    if (old != null) return complete(old).response(null);
    Bill bill = bill(request.subscriberNumber());
    if (bill.paid()) throw new BusinessException("Fatura zaten ödenmiş.", 409);
    MoneyRules.validate(request.requestKey(), bill.amount());
    MoneyRules.funds(initial, bill.amount());
    FraudResult fraud = fake.fraud(bill.amount());
    Operation t =
        db.atomic(
            () -> {
              Wallet w = db.wallet(userId);
              Operation replay =
                  db.existing(
                      w.id(),
                      request.requestKey().toString(),
                      "BillPayment",
                      request.subscriberNumber(),
                      null);
              if (replay != null) return replay;
              MoneyRules.funds(w, bill.amount());
              if (fraud.approved()) db.balance(w, w.balance(), w.reserved().add(bill.amount()));
              return db.insert(
                  w,
                  request.requestKey().toString(),
                  "BillPayment",
                  bill.amount(),
                  request.subscriberNumber(),
                  null,
                  fraud.approved() ? "Pending" : "Rejected",
                  fraud.reason());
            });
    return complete(t).response(null);
  }

  public TransactionResponse resume(String userId, String id) {
    Operation t = db.operation(id);
    if (!db.wallet(userId).id().equals(t.walletId()))
      throw new BusinessException("İşlem bulunamadı.", 404);
    return complete(t).response(null);
  }

  public TransactionResponse byRequest(String userId, String key) {
    Wallet w = db.wallet(userId);
    return db.byRequest(w.id(), key).response(w.id());
  }

  /**
   * TR: Belirsiz sonuç ret değildir. Aynı dış işlem kimliğiyle recovery yapılır; yeni para isteği
   * üretilmez.
   */
  Operation complete(Operation t) {
    if (!t.status().equals("Pending")) return t;
    ExternalResult result;
    try {
      result = fake.find(t.id());
      if (result == null) result = fake.execute(t);
    } catch (RestClientException | NullPointerException ex) {
      log.warn("Provider result unknown. operationId={}", t.id());
      db.defer(t.id());
      return db.operation(t.id());
    }
    if (!"Completed".equals(result.status()) && !"Rejected".equals(result.status())) {
      db.defer(t.id());
      return db.operation(t.id());
    }
    return db.finish(t.id(), result);
  }
}
