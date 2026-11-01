/**
 * RB Wallet ana uygulama paketi.
 *
 * <p><b>Kullanılan mimari:</b> Sade katmanlı mimari uygulanır. {@code *Controller} sınıfları HTTP
 * sözleşmesini, {@code WalletService}/{@code AuthService} use-case ve iş akışlarını,
 * {@code WalletStore} ise SQL erişimini yönetir. Bu ayrım Controller-Service-Repository desenidir;
 * amaç HTTP, iş kuralı ve veri erişimi değişikliklerinin birbirine yayılmasını azaltmaktır.
 *
 * <p><b>Spring teknolojileri:</b> Spring MVC REST endpointlerini, Spring Security JWT doğrulamasını,
 * JdbcTemplate açık SQL erişimini, TransactionTemplate atomik sınırları, Flyway ise şema
 * migration'larını sağlar. Finansal kodda dış HTTP çağrısı sırasında veritabanı transaction'ı açık
 * tutulmaz. Belirsiz sağlayıcı cevapları Pending + recovery worker ile çözülür; bildirimler Outbox
 * Pattern ile finansal kayıtla aynı transaction içinde kuyruğa alınır.
 *
 * <p><b>.NET karşılıkları:</b> {@code @RestController} = {@code [ApiController]}, {@code @Service} =
 * DI container'a servis kaydı, {@code JdbcTemplate} = Dapper'a yakın kullanım,
 * {@code TransactionTemplate} = açık transaction scope, {@code @RestControllerAdvice} = global
 * exception middleware/filter, {@code @Scheduled} = BackgroundService/PeriodicTimer,
 * {@code application.yml} = appsettings.json + environment variables. Spring bean'leri varsayılan
 * olarak singleton olduğu için request/kullanıcı state'i servis field'larında tutulmaz.
 */
package com.rbwallet.wallet;
