package com.rbwallet.wallet;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestClient;

/**
 * TR: @Configuration, IServiceCollection kayıtlarının karşılığıdır. @Bean nesneyi DI container'a
 * verir.
 */
@Configuration
public class AppConfig {
  @Bean
  PasswordEncoder passwords() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  SecretKeySpec signingKey(@Value("${wallet.jwt-key}") String key) {
    byte[] bytes = key.getBytes(StandardCharsets.UTF_8);
    if (bytes.length < 32) throw new IllegalStateException("JWT_KEY en az 32 UTF-8 baytı olmalı.");
    return new SecretKeySpec(bytes, "HmacSHA256");
  }

  @Bean
  JwtEncoder jwtEncoder(SecretKeySpec key) {
    return new NimbusJwtEncoder(new ImmutableSecret<>(key));
  }

  @Bean
  JwtDecoder jwtDecoder(
      SecretKeySpec key,
      @Value("${wallet.issuer}") String issuer,
      @Value("${wallet.audience}") String audience) {
    var decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    OAuth2TokenValidator<Jwt> audienceValidator =
        jwt ->
            jwt.getAudience().contains(audience)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "Invalid audience", null));
    // TR: İmza tek başına yeterli değildir; issuer, audience ve token süresi de doğrulanır.
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(issuer), audienceValidator));
    return decoder;
  }

  @Bean
  SecurityFilterChain security(HttpSecurity http) throws Exception {
    return http.csrf(csrf -> csrf.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login")
                    .permitAll()
                    .requestMatchers("/api/**")
                    .authenticated()
                    .anyRequest()
                    .permitAll())
        .oauth2ResourceServer(o -> o.jwt(j -> {}))
        .build();
  }

  @Bean
  RestClient fakeHttp(
      @Value("${wallet.fake-url}") String baseUrl, @Value("${wallet.fake-key}") String key) {
    URI uri = URI.create(baseUrl);
    if (!java.util.Set.of("http", "https").contains(uri.getScheme())
        || uri.getHost() == null
        || (uri.getPath() != null && !uri.getPath().isEmpty() && !uri.getPath().equals("/"))
        || uri.getQuery() != null
        || uri.getFragment() != null
        || uri.getUserInfo() != null)
      throw new IllegalStateException("FAKE_BASE_URL HTTP(S) servis kökü olmalı.");
    if (key.isBlank() || key.length() < 24)
      throw new IllegalStateException(
          "FAKE_API_KEY iki serviste aynı ve en az 24 karakter olmalı.");
    var factory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
    factory.setReadTimeout(Duration.ofSeconds(5));
    return RestClient.builder()
        .baseUrl(uri.toString())
        .defaultHeader("X-Api-Key", key)
        .requestFactory(factory)
        .build();
  }

  @Bean
  OpenAPI openApi() {
    return new OpenAPI()
        .components(
            new Components()
                .addSecuritySchemes(
                    "Bearer",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
        .addSecurityItem(new SecurityRequirement().addList("Bearer"));
  }
}
