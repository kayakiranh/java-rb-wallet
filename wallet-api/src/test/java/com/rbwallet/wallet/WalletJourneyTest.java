package com.rbwallet.wallet;

import static com.rbwallet.wallet.Contracts.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * TR: H2 üzerinde gerçek SQL/transaction ve JWT filtresi çalışır. Yalnızca uzak sağlayıcı mock'tur.
 * EN: H2 Oracle-mode tests do not replace the separate Oracle Free + real HTTP smoke test.
 */
@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:journey;MODE=Oracle;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "wallet.jwt-key=test-signing-key-at-least-32-bytes-long",
      "wallet.fake-key=test-shared-key-at-least-24-chars",
      "wallet.recovery-delay-ms=99999999",
      "wallet.notification-delay-ms=99999999"
    })
@AutoConfigureMockMvc
class WalletJourneyTest {
  @Autowired WalletService service;
  @Autowired WalletStore db;
  @Autowired AuthService auth;
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @MockitoBean FakeServicesClient fake;

  @BeforeEach
  void provider() {
    when(fake.fraud(any()))
        .thenAnswer(
            i ->
                new FraudResult(
                    ((BigDecimal) i.getArgument(0)).compareTo(new BigDecimal("10000")) <= 0, null));
    when(fake.find(anyString())).thenReturn(null);
    when(fake.execute(any())).thenReturn(new ExternalResult("Completed", null));
    when(fake.bill("10001")).thenReturn(new Bill("10001", new BigDecimal("150"), false));
  }

  private String user() {
    String email = UUID.randomUUID() + "@example.test";
    auth.register(new Register("Demo User", email, "DemoPassword123!"));
    return db.credentials(email).id();
  }

  @Test
  void happyPathReplayConflictAndRecipientHistory() {
    String alice = user(), bob = user();
    assertEquals(32, service.wallet(alice).number().length());
    UUID key = UUID.randomUUID();
    var top = new TopUp(key, new BigDecimal("1000"), "DEMO-ACCOUNT");
    assertEquals("Completed", service.topUp(alice, top).status());
    service.topUp(alice, top);
    assertEquals(
        409,
        assertThrows(
                BusinessException.class,
                () -> service.topUp(alice, new TopUp(key, new BigDecimal("2000"), "DEMO-ACCOUNT")))
            .status());
    var transfer = new Transfer(UUID.randomUUID(), new BigDecimal("200"), db.wallet(bob).number());
    service.transfer(alice, transfer);
    service.transfer(alice, transfer);
    var bill = new BillPayment(UUID.randomUUID(), "10001");
    service.payBill(alice, bill);
    service.payBill(alice, bill);
    assertEquals(0, new BigDecimal("650").compareTo(service.wallet(alice).balance()));
    assertEquals(0, new BigDecimal("200").compareTo(service.wallet(bob).balance()));
    assertEquals("Incoming", db.history(bob, 1).getFirst().direction());
    assertEquals(3, db.history(alice, 1).size());
    assertEquals(
        404,
        assertThrows(BusinessException.class, () -> service.byRequest(bob, key.toString()))
            .status());
  }

  @Test
  void timeoutReservesFundsAndRecoveryCreditsOnlyOnce() {
    String alice = user(), bob = user();
    assertEquals(32, service.wallet(alice).number().length());
    service.topUp(alice, new TopUp(UUID.randomUUID(), new BigDecimal("1000"), "DEMO-ACCOUNT"));
    when(fake.execute(any()))
        .thenThrow(new org.springframework.web.client.ResourceAccessException("timeout"));
    var pending = service.payBill(alice, new BillPayment(UUID.randomUUID(), "10001"));
    assertEquals("Pending", pending.status());
    assertEquals(0, new BigDecimal("150").compareTo(service.wallet(alice).reserved()));
    assertThrows(
        BusinessException.class,
        () ->
            service.transfer(
                alice,
                new Transfer(UUID.randomUUID(), new BigDecimal("851"), db.wallet(bob).number())));
    assertEquals(
        404,
        assertThrows(BusinessException.class, () -> service.resume(bob, pending.id())).status());
    when(fake.find(pending.id())).thenReturn(new ExternalResult("Completed", null));
    service.resume(alice, pending.id());
    service.resume(alice, pending.id());
    assertEquals(0, new BigDecimal("850").compareTo(service.wallet(alice).balance()));
    assertEquals(0, BigDecimal.ZERO.compareTo(service.wallet(alice).reserved()));
  }

  @Test
  void rejectedBillReleasesReservation() {
    String alice = user();
    service.topUp(alice, new TopUp(UUID.randomUUID(), new BigDecimal("1000"), "DEMO-ACCOUNT"));
    when(fake.execute(any())).thenReturn(new ExternalResult("Rejected", "DemoRejected"));
    assertEquals(
        "Rejected", service.payBill(alice, new BillPayment(UUID.randomUUID(), "10001")).status());
    assertEquals(0, new BigDecimal("1000").compareTo(service.wallet(alice).balance()));
    assertEquals(0, service.wallet(alice).reserved().signum());
  }

  @Test
  void concurrentTransfersCannotOverspend() throws Exception {
    String alice = user(), bob = user();
    assertEquals(32, service.wallet(alice).number().length());
    service.topUp(alice, new TopUp(UUID.randomUUID(), new BigDecimal("1000"), "DEMO-ACCOUNT"));
    String recipient = db.wallet(bob).number();
    try (var pool = Executors.newFixedThreadPool(2)) {
      var jobs = new ArrayList<Future<?>>();
      for (int i = 0; i < 2; i++)
        jobs.add(
            pool.submit(
                () -> {
                  var request = new Transfer(UUID.randomUUID(), new BigDecimal("600"), recipient);
                  try {
                    service.transfer(alice, request);
                  } catch (RuntimeException conflict) {
                    /* Kaybeden işlem bakiye değiştiremez. */
                  }
                }));
      for (var job : jobs) job.get();
    }
    assertEquals(0, new BigDecimal("400").compareTo(service.wallet(alice).balance()));
    assertEquals(0, new BigDecimal("600").compareTo(service.wallet(bob).balance()));
  }

  @Test
  void httpRegistrationJwtValidationAndTopUp() throws Exception {
    // TR: Oracle'a uygun DUAL sorgusu test DB'sinde de gerçekten çalışmalı.
    mvc.perform(get("/health/ready")).andExpect(status().isOk());
    mvc.perform(get("/api/wallet")).andExpect(status().isUnauthorized());
    String body =
        json.writeValueAsString(
            new Register("HTTP User", UUID.randomUUID() + "@example.test", "DemoPassword123!"));
    String response =
        mvc.perform(post("/api/auth/register").contentType("application/json").content(body))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String token = json.readTree(response).get("accessToken").asText();
    mvc.perform(get("/api/wallet").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.balance").value(0));
    mvc.perform(
            post("/api/wallet/top-ups")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        new TopUp(UUID.randomUUID(), new BigDecimal("1000"), "DEMO-ACCOUNT"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("Completed"));
    mvc.perform(
            post("/api/auth/register")
                .contentType("application/json")
                .content("{\"name\":\"A\",\"email\":\"bad\",\"password\":\"short\"}"))
        .andExpect(status().isBadRequest());
  }

  @Autowired org.springframework.security.oauth2.jwt.JwtEncoder jwtEncoder;

  @Test
  void invalidAudienceAndExpiredTokensAreRejected() throws Exception {
    for (boolean expired : List.of(false, true)) {
      var now = java.time.Instant.now();
      var claims =
          org.springframework.security.oauth2.jwt.JwtClaimsSet.builder()
              .issuer("rb-wallet")
              .subject(UUID.randomUUID().toString())
              .audience(List.of(expired ? "rb-wallet-client" : "another-client"))
              .issuedAt(now.minusSeconds(600))
              .expiresAt(expired ? now.minusSeconds(300) : now.plusSeconds(300))
              .build();
      var header =
          org.springframework.security.oauth2.jwt.JwsHeader.with(
                  org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256)
              .build();
      String token =
          jwtEncoder
              .encode(
                  org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(header, claims))
              .getTokenValue();
      mvc.perform(get("/api/wallet").header("Authorization", "Bearer " + token))
          .andExpect(status().isUnauthorized());
    }
  }

  @Test
  void moneyAndBackoffBoundaries() {
    for (String amount : List.of("0", "-1", "1.001", "1000001"))
      assertThrows(
          BusinessException.class,
          () -> MoneyRules.validate(UUID.randomUUID(), new BigDecimal(amount)));
    assertEquals(10, MoneyRules.retrySeconds(0));
    assertEquals(640, MoneyRules.retrySeconds(100));
  }
}
