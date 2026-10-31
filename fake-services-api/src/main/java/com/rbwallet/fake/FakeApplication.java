package com.rbwallet.fake;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** TR: Banka/fraud/fatura demo sağlayıcısı ayrı process ve portta çalışır. */
@SpringBootApplication(
    exclude =
        org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration
            .class)
public class FakeApplication {
  public static void main(String[] args) {
    SpringApplication.run(FakeApplication.class, args);
  }
}
