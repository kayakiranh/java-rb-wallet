package com.rbwallet.wallet;

import org.slf4j.*;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientException;

/**
 * TR: @RestControllerAdvice, merkezi exception middleware benzeri davranır; stack trace dışarı
 * verilmez.
 */
@RestControllerAdvice
public class ApiExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  /** Beklenen domain/iş kuralı hatasını güvenli ProblemDetails cevabına çevirir. */
  @ExceptionHandler(BusinessException.class)
  ResponseEntity<ProblemDetail> business(BusinessException ex) {
    return problem(ex.status(), ex.getMessage());
  }

  /** Optimistic concurrency ve unique constraint yarışlarını HTTP 409 olarak bildirir. */
  @ExceptionHandler({ConcurrencyFailureException.class, DataIntegrityViolationException.class})
  ResponseEntity<ProblemDetail> conflict(Exception ex) {
    return problem(409, "Eşzamanlı işlem veya mevcut kayıt. Aynı RequestKey ile tekrar deneyin.");
  }

  /** Jakarta Validation alan hatalarını ASP.NET ValidationProblem benzeri bir gövdede toplar. */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ProblemDetail> validation(MethodArgumentNotValidException ex) {
    var p =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "İstek alanlarını kontrol edin.");
    p.setTitle("Geçersiz istek.");
    p.setProperty(
        "errors",
        ex.getBindingResult().getFieldErrors().stream()
            .collect(
                java.util.stream.Collectors.groupingBy(
                    e -> e.getField(),
                    java.util.stream.Collectors.mapping(
                        e -> e.getDefaultMessage(), java.util.stream.Collectors.toList()))));
    return ResponseEntity.badRequest().body(p);
  }

  /** Bozuk JSON veya route tipi hatasında framework ayrıntısını dışarı sızdırmaz. */
  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<ProblemDetail> malformed(Exception ex) {
    return problem(400, "Geçersiz istek biçimi.");
  }

  /** Dış sağlayıcı iletişim hatasını geçici servis hatası olarak HTTP 503'e eşler. */
  @ExceptionHandler(RestClientException.class)
  ResponseEntity<ProblemDetail> provider(Exception ex) {
    return problem(503, "Dış servis kullanılamıyor. Daha sonra tekrar deneyin.");
  }

  /** Beklenmeyen hatayı server loguna tip bilgisiyle yazar, stack trace'i istemciye vermez. */
  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> unexpected(Exception ex) {
    log.error("Request failed. type={}", ex.getClass().getSimpleName());
    return problem(500, "İşlem tamamlanamadı.");
  }

  /** Spring ProblemDetail ile RFC 9457 uyumlu standart hata cevabı üretir. */
  private ResponseEntity<ProblemDetail> problem(int status, String message) {
    var p = ProblemDetail.forStatus(status);
    p.setTitle(message);
    return ResponseEntity.status(status).body(p);
  }
}
