package com.rbwallet.wallet;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

/** TR: Constructor injection, .NET constructor DI ile aynıdır; @Service otomatik kaydeder. */
@Service
public class AuthService {
  private final WalletStore db;
  private final PasswordEncoder passwords;
  private final JwtEncoder encoder;
  private final String issuer, audience;

  public AuthService(
      WalletStore db,
      PasswordEncoder passwords,
      JwtEncoder encoder,
      @Value("${wallet.issuer}") String issuer,
      @Value("${wallet.audience}") String audience) {
    this.db = db;
    this.passwords = passwords;
    this.encoder = encoder;
    this.issuer = issuer;
    this.audience = audience;
  }

  public Contracts.Token register(Contracts.Register request) {
    String email = request.email().trim().toLowerCase(Locale.ROOT), name = request.name().trim();
    if (name.length() < 2) throw new BusinessException("İsim en az iki karakter olmalı.");
    // TR: BCrypt 72 bayttan fazlasını desteklemez. Sessiz kesmek yerine açık validation yapıyoruz.
    if (request.password().getBytes(StandardCharsets.UTF_8).length > 72)
      throw new BusinessException("Parola UTF-8 olarak en fazla 72 bayt olabilir.");
    String hash = passwords.encode(request.password()); // CPU maliyeti SQL transaction dışında.
    String id = db.registerUser(name, email, hash);
    return token(id);
  }

  public Contracts.Token login(Contracts.Login request) {
    var user = db.credentials(request.email().trim().toLowerCase(Locale.ROOT));
    if (request.password().getBytes(StandardCharsets.UTF_8).length > 72
        || user == null
        || !passwords.matches(request.password(), user.passwordHash()))
      throw new BusinessException("E-posta veya parola hatalı.", 401);
    return token(user.id());
  }

  private Contracts.Token token(String userId) {
    Instant now = Instant.now(), expires = now.plus(30, ChronoUnit.MINUTES);
    var claims =
        JwtClaimsSet.builder()
            .issuer(issuer)
            .audience(List.of(audience))
            .subject(userId)
            .id(UUID.randomUUID().toString())
            .issuedAt(now)
            .expiresAt(expires)
            .build();
    return new Contracts.Token(
        encoder
            .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue(),
        expires);
  }
}
