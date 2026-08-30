# Doğrulama ve kalan cihaz kontrolleri

Bu değişikliğin ana kabul ölçütü: indirilen video dosyasında okunabilir ve çözülebilir bir görüntü izi
ile ses izi bulunmalı; ses yoksa işlem başarılı gösterilmemeli.

## Bu çalışma ortamında çalıştırılan kontroller

- Üretim Kotlin sınıflarını kullanan 24 regresyon kontrolü geçti.
- cipher/tools/tools.test.mjs içindeki 26 test geçti.
- Kotlin PSI ile 38 Kotlin dosyasının sözdizimi kontrol edildi.
- Gradle 8.13, help --offline göreviyle değiştirilmiş yapılandırmayı başarıyla yükledi.
- checkReleaseSigning görevi, eksik anahtar durumunda beklendiği gibi yayını engelledi.
- Android SDK tanımlı değil. SDK indirme izni verilmediği için SDK kurulmadı.
  assembleDebug, Android lint, Android/JUnit görevleri ve cihaz testi burada doğrulanmadı.
- Bu liste yerel kontrolleri kapsar. GitHub Actions, gönderilen commit üzerinde Android derleme ve cihaz kontrollerini ayrıca çalıştırır.

Kotlin sözdizimi kontrolü, Android API tip kontrolünün veya gerçek APK derlemesinin yerine geçmez.
Bu makinedeki önbellek ile SDK gerektirmeyen kontroller tools/check-core.ps1 üzerinden tekrar çalıştırılabilir.

## SDK olan ortamda

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :cipher:library:testDebugUnitTest :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest
```

MediaFileMuxerInstrumentedTest:
- Cihazın MediaCodec bileşeniyle gerçek AVC görüntü ve AAC ses dosyalarını ayrı ayrı üretir.
- MediaFileMuxer ile birleştirir.
- Her iki izin örnek zaman damgalarını kaynak dosyalarla karşılaştırır.
- Sonuçtaki hem görüntünün hem sesin MediaCodec ile çözülebildiğini kontrol eder.
- Ses içermeyen bir video dosyasının başarı doğrulamasından geçmediğini kontrol eder.

CI yapılandırması bu cihaz testlerini API 29 ve 34 emülatörlerinde çalıştırır ve release işini sonuçlarına bağlar.
Yayınlanan ön sürümün açıklamasındaki Build bağlantısı, o APK'ya ait test sonuçlarını gösterir.

## Gerçek cihaz kabul listesi

| Senaryo | Beklenen |
|---|---|
| 360p video-only + AAC | Tek dosyada ses ve görüntü; 360p için istisna yok |
| 720p önceden birleştirilmiş video | Gereksiz ikinci ses indirilmez |
| 1080p AVC + AAC | İki aktarım, birleştirme, tek sesli MP4 |
| WebM VP9 + Opus, API 29+ | Gerçek WebM dosyasında iki iz |
| WebM Opus, API 24–28 | Birleştirilemeyen seçenek sunulmaz |
| MP4 video var, uyumlu ses yok | Sessiz video indirilebilir seçenek olarak gösterilmez |
| Ağ kesintisi / HTTP 403 | Sınırlı yeniden deneme, güncel bağlantı, anlaşılır hata |
| Bildirim izni reddi | Uygulama içi ilerleme ve iptal çalışır |
| İndirme sırasında uygulamadan çıkma | Foreground worker işini sürdürebilir |
| Süreç ölümü / yeniden açma | Kalıcı iş tekrar çalışır; gerekirse aktarım baştan başlar |
| Birleştirme sırasında iptal | Yarım sonuç tamamlandı diye listelenmez |
| Aynı video iki kere indirme | Mevcut dosya silinmez; farklı iş adı kullanılır |
| Önbelleği temizleme | Aktif indirme parçaları silinmez |
| Android 7–9 depolama izni reddi | Kullanıcıya izin gereksinimi bildirilir |
| Uzun playlist | Devam sayfaları okunur ve seçilenlerin tümü kuyruğa alınır |
| A bağlantısından hemen sonra B | A'nın geç gelen sonucu B'yi ezmez |
| v1.0.5 / 1.0.5 | Güncelleme uyarısı çıkmaz |
| Sil / uygulama içi oynat / paylaş | Doğru URI; silinen dosya yeniden listelenmez |

## Yayın engelleri

Kalıcı release için mevcut dağıtım keystore'u ve GitHub secrets gerekiyor; yeni dağıtım anahtarı üretilmedi.
Main/master push'u tüm kontroller geçince debug anahtarıyla imzalanmış test APK'sını GitHub ön sürümü olarak yayımlar.
Test uygulaması `.preview` paket kimliğiyle mevcut uygulamanın yanına kurulur; önceki uygulamayı kaldırmak gerekmez.
CI debug anahtarı kalıcı olmadığı için sonraki ön sürümde yalnızca test uygulamasını yeniden kurmak gerekebilir.
Kalıcı yayın etiket push'u ile tetiklenir. İmza veya doğrulama eksikse kalıcı yayın durur.

## Bilinen sınırlar

- MP3 kodlama ve HLS/canlı kayıt yok.
- Her kesintide bayt düzeyinde resume yok; iş tekrar başlayabilir.
- YouTube erişim/PoToken kısıtları tüm videolar için başarı garantisi vermez.
- Cihazın codec ve işletim sistemi desteği gerçek dosyayla kontrol edilmelidir.
