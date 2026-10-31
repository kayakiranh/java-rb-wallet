package com.rbwallet.wallet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * TR: .NET Program.cs karşılığı; component scan DI servislerini bulur. EN: Application composition
 * root.
 */
@SpringBootApplication
@EnableScheduling
public class WalletApplication {
  public static void main(String[] args) {
    SpringApplication.run(WalletApplication.class, args);
  }
}
