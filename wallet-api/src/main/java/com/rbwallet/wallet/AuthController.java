package com.rbwallet.wallet;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * TR: @RestController = [ApiController]; @RequestBody JSON DTO; @Valid DataAnnotations doğrulaması
 * gibi.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private final AuthService service;

  public AuthController(AuthService service) {
    this.service = service;
  }

  /** Kullanıcı + TRY cüzdanı oluşturur ve ilk access token'ı döndürür. */
  @PostMapping("/register")
  public Contracts.Token register(@Valid @RequestBody Contracts.Register request) {
    return service.register(request);
  }

  /** BCrypt parola kontrolünden sonra yeni, kısa ömürlü access token üretir. */
  @PostMapping("/login")
  public Contracts.Token login(@Valid @RequestBody Contracts.Login request) {
    return service.login(request);
  }
}
