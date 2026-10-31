# Java RB Wallet — Java 25

RB Wallet'ın Java 25 / Spring Boot portu. Kayıt/giriş, otomatik TRY cüzdanı, bankadan yükleme, fraud kontrollü transfer, fatura sorgulama/ödeme, geçmiş, kalıcı fake servis, recovery, bildirim outbox ve aynı HTML/CSS/JavaScript arayüzü içerir.

## Hızlı başlangıç — Docker

Docker Engine/Desktop + Compose gerekir; SQL Server için x64 ortam kullanın.

1. `.env.example` dosyasını `.env` olarak kopyalayın.
2. SQL parolasını, en az 32 UTF-8 baytlık `JWT_KEY` değerini ve en az 24 karakterlik `FAKE_API_KEY` değerini farklı rastgele değerlerle değiştirin.
3. Repo kökünde `docker compose up --build -d` çalıştırın.

| Uygulama | Adres |
|---|---|
| Cüzdan ekranı | http://localhost:5080 |
| Wallet Swagger | http://localhost:5080/swagger |
| Fake Swagger | http://localhost:5081/swagger |
| SQL Server | localhost,1433 — RBWallet / sa |

Compose boşsa RBWallet veritabanını oluşturur; Flyway tabloları/migration geçmişini yönetir. SQL ve fake verileri volume'de korunur. `docker compose down` verileri korur. `down -v` verileri siler; yalnızca disposable test ortamında kullanın. API'ler root olmayan kullanıcıyla çalışır, portlar localhost'a bağlanır. Bu bir geliştirme demosudur.

## IDE / terminal ile çalıştırma

**JDK 25 ve Maven 3.9+** yükleyin. IntelliJ IDEA'da kökteki `pom.xml` dosyasını açın; Project SDK ve Maven Runner JDK değerlerini 25 seçin. İki uygulama birbirinden bağımsızdır. Uygulama için Node.js gerekmez.

Erişilebilir SQL Server'da önce boş `RBWallet` veritabanını oluşturun. Bu Java portu .NET EF tablolarını yerinde dönüştürmez: Java için ayrı boş veritabanı/volume kullanın. Aynı DB adı ve porttaki .NET Compose ile aynı anda başlatmayın.

Her uygulamanın Run Configuration → Environment Variables alanına ayarları girin:

| Değişken | Wallet API | Fake Services API |
|---|---|---|
| `JWT_KEY` | En az 32 UTF-8 bayt | Gerekmez |
| `FAKE_API_KEY` | Ortak anahtar | Aynı ortak anahtar |
| `WALLET_DB_URL` | `jdbc:sqlserver://localhost:1433;databaseName=RBWallet;encrypt=true;trustServerCertificate=true` | Gerekmez |
| `WALLET_DB_USER` | `sa` veya kendi SQL kullanıcınız | Gerekmez |
| `MSSQL_SA_PASSWORD` | SQL kullanıcınızın parolası | Gerekmez |
| `FAKE_BASE_URL` | `http://localhost:5081/` | Gerekmez |
| `SPRING_PROFILES_ACTIVE` | İsteğe bağlı | `dev` |
| `FAKE_STORAGE_PATH` | Gerekmez | İsteğe bağlı, varsayılan `data/fake-state.json` |

`.env` dosyasını yalnızca Compose otomatik okur; IDE/Maven kendiliğinden okumaz. Java'da .NET User Secrets yerine IDE environment ayarlarını kullanıyoruz. Secrets dosyalarını commit etmeyin.

Önce build:

```sh
mvn -B verify
```

İki ayrı terminalde (ilgili environment değerlerini ayarladıktan sonra):

```sh
java -jar fake-services-api/target/fake-services-api-1.0.0-SNAPSHOT.jar
java -jar wallet-api/target/wallet-api-1.0.0-SNAPSHOT.jar
```

Veya IDE'de `FakeApplication.main` ve `WalletApplication.main` çalıştırın. Fake API yalnızca `dev`/`test` profilinde açılır. Windows Authentication yerine örneklerde SQL Authentication kullanılır; Windows integrated JDBC ayrıca Microsoft'un native auth kurulumunu gerektirir.

## İlk finansal akış

`docs/RB-Wallet-Happy-Path.postman_collection.json` dosyasını Postman'e import edin; `fakeApiKey` değişkenine ortak anahtarı girin. Temiz demo verisinde Collection Runner ile sıralı çalıştırın.

1. Alice ve Bob kayıt olur; her biri için TRY cüzdanı oluşur.
2. Alice'e `bankAccount: DEMO-ACCOUNT` ile 1.000 TRY yüklenir.
3. Bob'a 200 TRY transfer edilir.
4. `10001` abonesinin 150 TRY faturası ödenir.
5. Alice **650 TRY**, Bob **200 TRY** olmalıdır.

JWT, Bob'un cüzdan numarası ve UUID `requestKey` alanları otomatik aktarılır. Aynı isteğin tekrarında aynı anahtar + gövde kullanılır; farklı gövde 409 döndürür. Faturalar fake veri deposunda globaldir: `10001` bir kez ödenebilir; yeni kullanıcı açmak faturayı sıfırlamaz. Collection yeniden koşusu ayrı temiz demo verisi gerektirir.

Wallet isteklerinde `Authorization: Bearer <accessToken>`, Wallet → Fake çağrılarında `X-Api-Key` kullanılır. Fake API bir kullanıcı JWT'si istemez. Compose içindeki servis adresi `http://fake:5081/`, IDE için `http://localhost:5081/` olur.

## Timeout / recovery / bildirim

| Senaryo | Girdi | Davranış |
|---|---|---|
| Başarılı yükleme | DEMO-ACCOUNT | Completed |
| Banka veya fatura reddi | REJECT | Rejected |
| Sonucu kaybolan işlem | TIMEOUT | Fake önce kaydeder, ilk cevabı 8 sn geciktirir |
| Transfer fraud reddi | 10.000 TRY üzeri | Yeterli bakiye varsa Rejected |
| Faturalar | 10001 / 10002 | 150 / 250 TRY |

Wallet HTTP read timeout'u 5 saniyedir. Kesinleşmeyen işlem **202 Pending** döner; bu başarıyla tamamlandı anlamına gelmez. Fatura tutarı rezerve kalır ve harcanamaz. Recovery 5 saniyede bir kontrol eder, ilk deneme işlemden en az 15 saniye sonradır. Retry beklemesi 10 saniyeden 640 saniyeye kadar artar. Aynı dış işlem kimliği tekrar kullanılır.

`POST /api/transactions/{id}/resume` işlemi kontrol eder; `GET /api/transactions/by-request/{requestKey}` sadece isteği başlatan kullanıcının kaydını verir. Bildirimler finansal kayıtla aynı transaction'da outbox'a yazılır, 2 saniyelik worker ile teslim edilir. Provider işlem + kullanıcı bazında tekrarları engeller. `PUT /api/scenarios/notification-failures` ve `{"count":1}` sonraki bildirime geçici 503 üretir.

Arayüz token'ı bellekte tutar. Son para isteği sekme depolamasında korunur; sayfa yenilenip tekrar giriş yapıldığında aynı anahtarla sonuç kontrol edilebilir. API gövdeleri/JWT/parolalar loglanmaz.

## Testler ve sınırlar

```sh
mvn -B verify
# Çalışan, temiz Compose ortamı ve FAKE_API_KEY gerekir:
python3 tests/smoke.py
# Fake + Wallet restart da test edilsin:
TEST_RESTART=1 python3 tests/smoke.py
```

Maven testleri gerçek SQL transaction'larını H2 test veritabanında çalıştırır; HTTP/JWT için MockMvc, uzak sağlayıcı için mock kullanır. Fake kalıcılık/concurrency testleri ayrı geçici dosyalarda çalışır. H2, SQL Server doğrulamasının yerine geçmez. GitHub Actions ayrıca gerçek SQL Server + iki Java API ile HTTP smoke ve tarayıcı testlerini çalıştırır. İki smoke komutunu aynı veri üzerinde art arda çalıştırmayın.

`python3 -m pip install playwright==1.58.0`, `python3 -m playwright install chromium`, `python3 tests/ui.py` ile tarayıcı kontrolü yapılabilir. Python/Playwright yalnızca test aracıdır.

Transfer iki bakiye + işlem + outbox'ı tek transaction'da günceller. `version` alanları compare-and-swap ile yarışları algılar; 409 sonrası aynı anahtarla retry yapılabilir. Dış HTTP çağrıları sırasında SQL transaction açık tutulmaz. Fake JSON deposu tek instance içindir, bozuk dosyayı otomatik sıfırlamaz ve dosya kilidiyle ikinci process'i reddeder. Bu demo çift taraflı muhasebe defteri veya gerçek banka entegrasyonu değildir.

Kampanya, otomatik ödeme, KYC, para çekme ve çoklu para birimi referans deponun mevcut temel kapsamında yoktur; bu portta da yoktur. Yeni endpointler varmış gibi gösterilmez.

## Java öğrenmek için

Önce [Java ve .NET rehberini](docs/java-for-dotnet.md), sonra `AuthController → AuthService → WalletStore` sırasını okuyun. Kodda kritik transaction, DI, record, para ve recovery kararları Türkçe yorumlarla açıklanmıştır.

Kaynak davranış referansı: `kayakiranh/rb-wallet`, commit `1ed46342a3c698883cd4bed34f64a11631267807`.
Spring Boot 3.5.x Java 25 uyumu: https://docs.spring.io/spring-boot/3.5/system-requirements.html
