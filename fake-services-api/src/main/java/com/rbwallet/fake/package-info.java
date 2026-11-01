/**
 * Geliştirme ve test için banka, fatura, fraud ve bildirim sağlayıcılarını taklit eden ayrı Spring
 * Boot uygulaması.
 *
 * <p><b>Desen ve gerekçe:</b> Test Double/Fake Service deseniyle gerçek dış sistemlerden bağımsız,
 * tekrar üretilebilir happy-path, red ve timeout senaryoları sağlanır. Ayrı process ve portta
 * çalışması gerçek servisler arası HTTP sınırını korur. {@code FakeStore}, idempotency kayıtlarını
 * JSON dosyasında kalıcı tutar; bu yapı yalnızca tek instance geliştirme/test içindir ve gerçek
 * finansal ledger yerine kullanılamaz.
 *
 * <p><b>.NET karşılığı:</b> Ayrı bir ASP.NET Core fake API/TestServer projesine benzer.
 * {@code synchronized} C# {@code lock}, {@code AutoCloseable + @PreDestroy} ise
 * {@code IDisposable/IHostedLifecycleService} cleanup yaklaşımına karşılık gelir.
 */
package com.rbwallet.fake;
