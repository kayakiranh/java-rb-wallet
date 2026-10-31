package com.rbwallet.fake;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/** TR: Gerçek Spring security filtresi ve JSON endpointleri ortak anahtarla test edilir. */
@SpringBootTest(
    properties = {
      "fake.api-key=test-shared-api-key-at-least-24-chars",
      "fake.storage-path=target/http-test-${random.uuid}/state.json"
    })
@AutoConfigureMockMvc
class FakeHttpTest {
  private static final String KEY = "test-shared-api-key-at-least-24-chars";
  @Autowired MockMvc mvc;

  @Test
  void apiKeyAndProviderContract() throws Exception {
    mvc.perform(get("/health")).andExpect(status().isOk());
    mvc.perform(get("/api/notification/messages")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/notification/messages").header("X-Api-Key", "wrong"))
        .andExpect(status().isUnauthorized());
    String id = UUID.randomUUID().toString();
    String body = "{\"operationId\":\"" + id + "\",\"amount\":1000,\"target\":\"DEMO-ACCOUNT\"}";
    mvc.perform(
            post("/api/bank/top-ups")
                .header("X-Api-Key", KEY)
                .contentType("application/json")
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("Completed"));
    mvc.perform(get("/api/operations/" + id).header("X-Api-Key", KEY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("Completed"));
    mvc.perform(get("/api/billing/bills/10001").header("X-Api-Key", KEY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.amount").value(150));
    mvc.perform(
            put("/api/scenarios/notification-failures")
                .header("X-Api-Key", KEY)
                .contentType("application/json")
                .content("{\"count\":21}"))
        .andExpect(status().isBadRequest());
  }
}
