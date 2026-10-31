# .NET geliştiricisi için bu Java projesi

## Nereden başlayayım?

`WalletApplication.main` uygulamayı açar. Spring, `com.rbwallet.wallet` paketi altındaki `@Service`, `@Repository`, `@Component`, `@Configuration` sınıflarını tarayıp DI container'a kaydeder. Bir constructor varsa `@Autowired` yazmak gerekmez; bağımlılıkları otomatik verir. Bu yüzden `new WalletService(...)` controller içinde yapılmaz.

| .NET | Bu projedeki Java karşılığı |
|---|---|
| Program.cs / builder.Services | WalletApplication / AppConfig / @Bean |
| [ApiController] | @RestController |
| [Route] / [HttpPost] | @RequestMapping / @PostMapping |
| [FromBody] | @RequestBody |
| DataAnnotations | Jakarta Validation / @Valid |
| record DTO | Java record; erişim `request.amount()` |
| decimal | BigDecimal |
| Guid | UUID |
| DateTimeOffset | Instant (UTC) |
| Dapper | JdbcTemplate |
| EF migrations | Flyway SQL migration |
| SaveChanges transaction | TransactionTemplate / WalletStore.atomic |
| rowversion | bigint version + koşullu UPDATE |
| BackgroundService | @Scheduled |
| Exception middleware | @RestControllerAdvice |
| appsettings.json | application.yml + environment |
| xUnit + Moq | JUnit + Mockito |

## Neden record ve BigDecimal?

`record TopUp(UUID requestKey, BigDecimal amount, String bankAccount)` immutable bir veri taşıyıcısıdır. Java getter'ı `amount()` şeklindedir; `getAmount()` değildir. `var` yalnızca local değişken tip çıkarımı yapar, dynamic değildir.

Para için `new BigDecimal("150.10")` kullanın; `new BigDecimal(150.10)` floating-point hatası taşıyabilir. `a.add(b)` yeni nesne üretir; `a` değişmez. `a.compareTo(b)==0` sayısal eşitliktir. `equals`, scale'i de karşılaştırır: 150 ve 150.00 farklı sayılabilir. Bu ayrım fake idempotency kontrolünde özellikle önemlidir.

## SQL ve transaction akışı

`WalletStore.atomic(() -> { ... })` lambda içindeki SQL'leri tek transaction'da yürütür. Exception olursa tamamı rollback olur. Bu sınırın içine HTTP çağrısı koymayın: bağlantı ve kilitler gereksiz uzun tutulur.

Yüklemede önce Pending işlem kaydı commit edilir. Sonra fake bankaya gidilir. Sonuç gelirse ikinci kısa transaction bakiyeyi ve işlem durumunu birlikte günceller. Sonuç belirsizse Pending korunur; worker aynı dış işlem kimliğiyle tekrar kontrol eder.

Faturada aynı mantığa ek olarak tutar rezervasyonu vardır. `available = balance - reserved`. Transferde dış fraud kontrolünden sonra kısa SQL transaction'ında iki bakiye ve işlem kaydı atomik güncellenir. `UPDATE ... WHERE version=?` sıfır satır etkilerse başka istek kazanmıştır; mevcut transaction geri alınır.

Spring'in `@Transactional` anotasyonu proxy üzerinden çalışır; aynı nesnenin kendi metodunu çağırması sık yapılan bir tuzaktır. Bu örnekte transaction sınırları görünür olsun diye `TransactionTemplate` seçildi.

## Asenkronluk farkı

ASP.NET Core'daki `async/await` bu kodda birebir taklit edilmez. Spring MVC ve RestClient blocking I/O kullanır; bağlantı/read timeout'ları vardır. `@Scheduled` işler arka planda çalışır. `Thread.sleep(8000)` yalnızca fake sağlayıcının TIMEOUT simülasyonundadır, gerçek finansal serviste kullanılmaz. Java 25 hedeflenir; öğrenme kolaylığı için preview özellikleri veya gereksiz reactive katman yoktur.

## Güvenlik ve yaşam süreleri

Spring servisleri varsayılan olarak singleton'dır. Bu yüzden servis field'larında kullanıcıya ait değişken veri tutmayın. Kullanıcı bilgisi JWT `sub` claim'inden her istekte alınır. JdbcTemplate thread-safe kullanılabilir; bağlantı transaction bağlamına göre havuzdan alınır. FakeStore paylaşılan mutable state kullandığı için synchronized ve process dosya kilidi kullanır.

`try (var resource = ...)` C# `using` gibidir; `AutoCloseable.close()` otomatik çağrılır. `@PreDestroy` bean kapanırken cleanup yapar. `List.of`/`Map.of` immutable koleksiyonlar üretir. `Optional`, değerin bulunamayabileceğini gösterir; burada endpoint hataları için açık BusinessException tercih edildi.

Java parolaları BCrypt ile hash'ler. BCrypt 72 UTF-8 bayt sınırında validation yapılır. .NET hash/veri taşınması bu portun kapsamı değildir; Java ayrı veritabanı kullanır. API JSON alanları ve iş davranışları korunur.

## Önerilen okuma sırası

1. Contracts → AuthController → AuthService: request, validation, DI, JWT.
2. WalletController → WalletService.topUp → WalletStore: finansal durum geçişi.
3. WalletService.transfer / payBill: atomiklik ve rezervasyon.
4. FakeServicesClient → FakeStore: HTTP, timeout, kalıcılık, idempotency.
5. Workers → test sınıfları: recovery, outbox ve yarışlar.

## Sık karşılaşacağın hatalar

- `release version 25 not supported`: Maven Java 17/21 kullanıyordur; `mvn -version` ile JAVA_HOME'u kontrol et.
- `JWT_KEY ...`: değişkeni Wallet Run Configuration'a ekle; .env IDE tarafından okunmaz.
- Fake 401: iki uygulamadaki `FAKE_API_KEY` farklı veya eksiktir.
- SQL bağlantı hatası: önce DB oluştur, SQL Authentication ve portu kontrol et.
- Fatura zaten ödenmiş: aynı fake verisindeki global fatura ikinci kez ödenemez.
- 202: yeni requestKey ile yeniden para işlemi oluşturma; mevcut işlemi sorgula/resume et.
