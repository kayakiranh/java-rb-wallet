package com.rbwallet.wallet;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * TR: .NET auth limiter karşılığı; IP başına dakikada 20 deneme. Proxy başlıklarına körü körüne
 * güvenilmez.
 */
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {
  /** Dakika kovası + sayaç; immutable record concurrent map güncellemelerini sadeleştirir. */
  private record Window(long minute, int count) {}

  private final ConcurrentHashMap<String, Window> requests = new ConcurrentHashMap<>();

  @Override
  /**
   * Yalnızca register/login isteklerine IP bazlı sabit pencere limiti uygular ve temel güvenlik
   * header'larını ekler. Demo için process-memory'dedir; çoklu pod production'da Redis/API Gateway
   * gibi ortak limiter gerekir.
   */
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (request.getMethod().equals("POST")
        && (request.getRequestURI().equals("/api/auth/register")
            || request.getRequestURI().equals("/api/auth/login"))) {
      long minute = System.currentTimeMillis() / 60000;
      requests.entrySet().removeIf(e -> e.getValue().minute() < minute);
      if (requests.size() > 10000 && !requests.containsKey(request.getRemoteAddr())) {
        response.setStatus(429);
        return;
      }
      Window w =
          requests.compute(
              request.getRemoteAddr(),
              (key, old) ->
                  old == null || old.minute() != minute
                      ? new Window(minute, 1)
                      : new Window(minute, old.count() + 1));
      if (w.count() > 20) {
        response.setStatus(429);
        response.setHeader("Retry-After", "60");
        return;
      }
    }
    if (request.getRequestURI().equals("/") || request.getRequestURI().equals("/index.html"))
      response.setHeader(
          "Content-Security-Policy",
          "default-src 'self'; script-src 'self'; style-src 'self'; object-src 'none'; base-uri"
              + " 'self'; frame-ancestors 'none'");
    response.setHeader("Cache-Control", "no-store");
    response.setHeader("Referrer-Policy", "no-referrer");
    chain.doFilter(request, response);
  }
}
