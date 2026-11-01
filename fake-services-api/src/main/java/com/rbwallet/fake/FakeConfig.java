package com.rbwallet.fake;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.security.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Fake API güvenlik ve OpenAPI yapılandırması.
 *
 * <p>Kullanıcı JWT'si yerine servisler arası ortak X-Api-Key kullanılır. Filter, .NET custom
 * authentication middleware/handler karşılığıdır. Fake'in dev/test dışında başlamaması production'a
 * yanlışlıkla demo sağlayıcı bağlama riskini azaltır.
 */
@Configuration
public class FakeConfig {
  /** Stateless güvenlik zincirini kurar ve API key'i sabit süreli karşılaştırmayla doğrular. */
  @Bean
  SecurityFilterChain security(
      HttpSecurity http, @Value("${fake.api-key}") String key, Environment env) throws Exception {
    if (!env.matchesProfiles("dev", "test"))
      throw new IllegalStateException("FakeServices yalnızca dev/test ortamında çalışır.");
    if (key.isBlank() || key.length() < 24)
      throw new IllegalStateException("FAKE_API_KEY en az 24 karakter olmalı.");
    var filter =
        new OncePerRequestFilter() {
          @Override
          protected void doFilterInternal(
              HttpServletRequest req, HttpServletResponse res, FilterChain chain)
              throws ServletException, IOException {
            if (req.getRequestURI().startsWith("/api/")) {
              String supplied = req.getHeader("X-Api-Key");
              if (supplied == null
                  || !MessageDigest.isEqual(
                      supplied.getBytes(StandardCharsets.UTF_8),
                      key.getBytes(StandardCharsets.UTF_8))) {
                res.setStatus(401);
                return;
              }
            }
            chain.doFilter(req, res);
          }
        };
    return http.csrf(c -> c.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(a -> a.anyRequest().permitAll())
        .addFilterBefore(filter, AnonymousAuthenticationFilter.class)
        .build();
  }

  /** Swagger/OpenAPI ekranına X-Api-Key şemasını ekler. */
  @Bean
  OpenAPI openApi() {
    return new OpenAPI()
        .components(
            new Components()
                .addSecuritySchemes(
                    "ApiKey",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name("X-Api-Key")))
        .addSecurityItem(new SecurityRequirement().addList("ApiKey"));
  }
}
