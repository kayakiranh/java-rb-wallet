# Java RB Wallet — Java 25

RB Wallet'ın Java 25 / Spring Boot portu. Kayıt/giriş, otomatik TRY cüzdanı, bankadan yükleme, fraud kontrollü transfer, fatura sorgulama/ödeme, geçmiş, kalıcı fake servis, recovery, bildirim outbox ve aynı HTML/CSS/JavaScript arayüzü içerir.

## Yerel Oracle AI Database Free ile başlangıç

JDK 25, Maven 3.9+ ve çalışan Oracle AI Database Free gerekir. Varsayılan bağlantı `localhost:1521/FREEPDB1` ve uygulama kullanıcısı `RBWALLET` olarak ayarlanmıştır. JDBC Thin kullanıldığı için uygulamanın Oracle Instant Client/native DLL bağımlılığı yoktur.

### 1. Uygulama kullanıcısını bir kez oluştur

SQL Developer'da **SYSTEM** kullanıcısıyla yeni bağlantı aç:

| Alan | Değer |
|---|---|
| Host | localhost |
| Port | 1521 |
| Connection type | Basic |
| Service name | FREEPDB1 (SID seçme) |
| Username | SYSTEM |
| Password | Oracle kurulumunda belirlediğin yönetici parolası |

[docs/oracle/create-dev-user.sql](docs/oracle/create-dev-user.sql) dosyasında parola yer tutucusunu kendi uygulama parolanla değiştir, bu bağlantının worksheet'inde **F5 / Run Script** ile bir kez çalıştır. Script `RBWALLET` kullanıcısını 100 MB kota ve yalnızca gerekli CREATE SESSION / CREATE TABLE yetkileriyle oluşturur. Uygulama SYSTEM veya SYS ile çalıştırılmaz. Mevcut kullanıcı/veri silinmez.

Oracle'da kullanıcı aynı zamanda şema sahibidir. SQL Server'daki gibi ayrı `CREATE DATABASE RBWallet` komutu çalıştırılmaz. Tabloları uygulama ilk açılışında Flyway oluşturur. Eski SQL Server veritabanı/hash/verileri bu yeni şemaya otomatik taşınmaz; önceki DDL yalnızca `docs/reference/sqlserver` altında referans olarak korunmuştur.

### 2. IntelliJ IDEA ayarlarını gir

Kökteki `pom.xml` dosyasını aç. Project SDK ve Maven Runner JDK değerlerini **25** seç. İki Run Configuration oluştur: `WalletApplication` ve `FakeApplication`.

Run Configuration → **Environment Variables**:

| Değişken | WalletApplication | FakeApplication |
|---|---|---|
| `WALLET_DB_URL` | `jdbc:oracle:thin:@//localhost:1521/FREEPDB1` (varsayılan) | Gerekmez |
| `WALLET_DB_USER` | `RBWALLET` (varsayılan) | Gerekmez |
| `WALLET_DB_PASSWORD` | Scriptte belirlediğin uygulama parolası | Gerekmez |
| `JWT_KEY` | En az 32 UTF-8 baytlık rastgele anahtar | Gerekmez |
| `FAKE_API_KEY` | En az 24 karakterlik ortak anahtar | **Aynı** ortak anahtar |
| `SPRING_PROFILES_ACTIVE` | Gerekmez | `dev` |

`.env` dosyasını IDE ve Maven kendiliğinden okumaz; environment alanlarını doldur. Şifreleri `application.yml` veya Git'e yazma. Servis adı/port kuruluma göre farklıysa yalnızca `WALLET_DB_URL` değerini değiştir.

### 3. Build ve çalıştır

```sh
mvn -B verify
```

Önce `FakeApplication.main`, sonra `WalletApplication.main` çalıştır. Alternatif olarak yukarıdaki environment değerleri tanımlı iki terminalde:

```sh
java -jar fake-services-api/target/fake-services-api-1.0.0-SNAPSHOT.jar
java -jar wallet-api/target/wallet-api-1.0.0-SNAPSHOT.jar
```

| Uygulama | Adres |
|---|---|
| Cüzdan ekranı | http://localhost:5080 |
| Wallet Swagger | http://localhost:5080/swagger |
| Fake Swagger | http://localhost:5081/swagger |
| Oracle bağlantı kontrolü | http://localhost:5080/health/ready |

Readiness, `SELECT 1 FROM dual` ile gerçek Oracle bağlantısını kontrol eder. Free sürümün kaynak sınırları için havuz en fazla 5 bağlantıdır. Migration'lar `db/migration/oracle` konumunda; para NUMBER(18,2), long değerler NUMBER(19), metinler VARCHAR2/NVARCHAR2 olarak saklanır. `wallet_number` kolon adı Oracle NUMBER anahtar sözcüğüyle çakışmayı önler; JSON alanı hâlâ `number` olarak kalır.

## İsteğe bağlı: API'leri Docker ile çalıştır

Oracle bilgisayarında kurulu kalır. `.env.example` dosyasını `.env` olarak kopyalayıp RBWALLET parolasını ve anahtarları değiştir. `docker compose up --build -d` yalnızca iki Java API'sini başlatır; ikinci Oracle kurmaz. Container → bilgisayar bağlantısı `host.docker.internal:1521/FREEPDB1` kullanır. Oracle listener'ın container bağlantısına açık olması gerekir. IDE yöntemi için Docker gerekmez.

Fake veri `fake-data` volume'ünde kalır; Oracle verisi kendi yerel kurulumundadır. `docker compose down` verileri korur. İki uygulamayı aynı anda IDE ve Docker'da aynı portlarda başlatma.

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
# Çalışan izole test Compose ortamı ve FAKE_API_KEY gerekir:
python3 tests/smoke.py
# Fake + Wallet restart da test edilsin:
TEST_RESTART=1 python3 tests/smoke.py
```

Maven testleri gerçek SQL transaction'larını H2 Oracle modunda test veritabanında çalıştırır; HTTP/JWT için MockMvc, uzak sağlayıcı için mock kullanır. Fake kalıcılık/concurrency testleri ayrı geçici dosyalarda çalışır. H2, Oracle Free doğrulamasının yerine geçmez. GitHub Actions ayrıca gerçek Oracle Free + iki Java API ile HTTP smoke ve tarayıcı testlerini çalıştırır. İki smoke komutunu aynı veri üzerinde art arda çalıştırmayın.

CI, `compose.yaml` ve `compose.oracle-test.yaml` dosyalarını birlikte kullanır; ayrı Oracle Free container'ına bağlanır. Bilgisayarındaki kurulu Oracle testler tarafından kullanılmaz. Yerel test container'ını çalıştırmak için ayrıca `ORACLE_ADMIN_PASSWORD` tanımlayıp `docker compose -f compose.yaml -f compose.oracle-test.yaml up --build -d` kullan. Container restart smoke için `COMPOSE_FILE=compose.yaml:compose.oracle-test.yaml TEST_RESTART=1 python3 tests/smoke.py` (Linux/macOS/CI) kullanılır; bu testleri disposable verilerde çalıştır.

`python3 -m pip install playwright==1.58.0`, `python3 -m playwright install chromium`, `python3 tests/ui.py` ile tarayıcı kontrolü yapılabilir. Python/Playwright yalnızca test aracıdır.

Transfer iki bakiye + işlem + outbox'ı tek transaction'da günceller. `version` alanları compare-and-swap ile yarışları algılar; 409 sonrası aynı anahtarla retry yapılabilir. Dış HTTP çağrıları sırasında SQL transaction açık tutulmaz. Fake JSON deposu tek instance içindir, bozuk dosyayı otomatik sıfırlamaz ve dosya kilidiyle ikinci process'i reddeder. Bu demo çift taraflı muhasebe defteri veya gerçek banka entegrasyonu değildir.

Kampanya, otomatik ödeme, KYC, para çekme ve çoklu para birimi referans deponun mevcut temel kapsamında yoktur; bu portta da yoktur. Yeni endpointler varmış gibi gösterilmez.

## Java öğrenmek için

Önce [Java ve .NET rehberini](docs/java-for-dotnet.md), sonra `AuthController → AuthService → WalletStore` sırasını okuyun. Kodda kritik transaction, DI, record, para ve recovery kararları Türkçe yorumlarla açıklanmıştır.

Kaynak davranış referansı: `kayakiranh/rb-wallet`, commit `1ed46342a3c698883cd4bed34f64a11631267807`.
Spring Boot 3.5.x Java 25 uyumu: https://docs.spring.io/spring-boot/3.5/system-requirements.html
