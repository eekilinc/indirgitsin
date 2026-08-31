# Doğrulama ve kalan cihaz kontrolleri

Bu değişikliğin ana kabul ölçütü: indirilen video dosyasında okunabilir ve çözülebilir bir görüntü izi
ile ses izi bulunmalı; ses yoksa işlem başarılı gösterilmemeli.

## Bu çalışma ortamında çalıştırılan kontroller

- Üretim Kotlin sınıflarını kullanan 30 regresyon kontrolü geçti.
- cipher/tools/tools.test.mjs içindeki 26 test geçti.
- Kotlin PSI ile 41 Kotlin dosyasının sözdizimi kontrol edildi.
- Gradle 8.13, help --offline göreviyle değiştirilmiş yapılandırmayı başarıyla yükledi.
- checkReleaseSigning görevi, eksik anahtar durumunda beklendiği gibi yayını engelledi.
- Android SDK tanımlı değil. SDK indirme izni verilmediği için SDK kurulmadı.
  assembleDebug, Android lint, Android/JUnit görevleri ve cihaz testi burada doğrulanmadı.
- Bu liste yerel kontrolleri kapsar. GitHub Actions, gönderilen commit üzerinde Android derleme ve cihaz kontrollerini ayrıca çalıştırır.

Kotlin sözdizimi kontrolü, Android API tip kontrolünün veya gerçek APK derlemesinin yerine geçmez.
Bu makinedeki önbellek ile SDK gerektirmeyen kontroller tools/check-core.ps1 üzerinden tekrar çalıştırılabilir.

## Önceki sürümün gerçek cihaz sonucu

Kullanıcı 1.0.94-preview sürümünde sesli video indirmenin telefonda çalıştığını ve hızlı olduğunu
31 Ağustos 2026'da doğruladı. Bu bildirim tüm kalite/cihaz/ağ senaryolarının test edildiği anlamına gelmez.
1.1.0 finalde birleştirme/aktarım motoru korunur; kuyruk ve arayüz değişiklikleri ayrıca test edilir.

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

DownloadQueueInstrumentedTest gerçek WorkManager kuyruğunda yinelenen isteği, tarifesiz ağ kısıtını,
kontrollü hatadan yeniden denemeyi, güncel ağ ayarını ve iptal sonrası yeniden eklemeyi doğrular.
Aktarım kontrollü bir test worker'ıyla hata verir; dış YouTube ağına bağımlı değildir.
StartupInstrumentedTest ana ekranın açılıp RESUMED durumuna geldiğini doğrular.

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
| Aynı video/kaliteye aktifken tekrar basma | İkinci iş oluşturulmaz; tamamlandıktan sonra yeni indirmeye izin verilir |
| Önbelleği temizleme | Aktif indirme parçaları silinmez |
| Android 7–9 depolama izni reddi | Kullanıcıya izin gereksinimi bildirilir |
| Uzun playlist | Devam sayfaları okunur ve seçilenlerin tümü kuyruğa alınır |
| A bağlantısından hemen sonra B | A'nın geç gelen sonucu B'yi ezmez |
| v1.0.5 / 1.0.5 | Güncelleme uyarısı çıkmaz |
| Sil / uygulama içi oynat / paylaş | Doğru URI; silmek için onay gerekir; iptal dosyayı korur |
| Hız / tahmini kalan | İki aktarımın toplamını gösterir; bilinmeyen boyutta süre uydurulmaz |
| Tarifesiz ağ açık, mobil veri tarifeli | İş bekler; uygun ağ gelince başlar |
| Başarısız indirmede yeniden dene | Güncel bağlantı çözülür, baştan kuyruğa alınır |
| Dosya sıralama | En yeni, ad, boyut sıraları arama/filtre ile birlikte çalışır |
| Özel GitHub veya bağlantı hatası | Güncel iddiası yerine erişim açıklaması ve GitHub bağlantısı |

## Final yayın kapıları

Kalıcı anahtar GitHub Actions secrets üzerinden sağlanır; paket kimliği `.stable` olur.
Eksik imza, hatalı sertifika veya başarısız doğrulama final yayınını durdurur.
Debug cihaz testleri API 29/34 üzerinde; ek imzalı release cihaz testi API 34 üzerinde çalışır.
APK ve kaynak ZIP için SHA256SUMS.txt yayımlanır. Kaynak arşivi yalnızca Git'in izlediği dosyalardan,
cipher alt modülünün sabitlenmiş sürümü dahil edilerek üretilir; yerel özel anahtar dahil edilmez.

## Bilinen sınırlar

- MP3 kodlama ve HLS/canlı kayıt yok.
- Her kesintide bayt düzeyinde resume yok; iş tekrar başlayabilir.
- YouTube erişim/PoToken kısıtları tüm videolar için başarı garantisi vermez.
- Cihazın codec ve işletim sistemi desteği gerçek dosyayla kontrol edilmelidir.
