# Doğrulama ve kalan cihaz kontrolleri

Bu değişikliğin ana kabul ölçütü: indirilen video dosyasında okunabilir ve çözülebilir bir görüntü izi
ile ses izi bulunmalı; ses yoksa işlem başarılı gösterilmemeli.

## Bu çalışma ortamında çalıştırılan kontroller

- Üretim Kotlin sınıflarını kullanan 30 regresyon kontrolü geçti.
- cipher/tools/tools.test.mjs içindeki 26 test geçti.
- Kotlin PSI ile kaynak sözdizimi kontrol edildi; bu kontrol Android tip kontrolü değildir.
- Gradle 8.13, help --offline göreviyle değiştirilmiş yapılandırmayı başarıyla yükledi.
- checkReleaseSigning görevi, eksik anahtar durumunda beklendiği gibi yayını engelledi.
- Android SDK tanımlı değil. SDK indirme izni verilmediği için SDK kurulmadı.
  assembleDebug, Android lint, Android/JUnit görevleri ve cihaz testi burada doğrulanmadı.
- Bu liste yerel kontrolleri kapsar. GitHub Actions, gönderilen commit üzerinde Android derleme ve cihaz kontrollerini ayrıca çalıştırır.

Kotlin sözdizimi kontrolü, Android API tip kontrolünün veya gerçek APK derlemesinin yerine geçmez.
Bu makinedeki önbellek ile SDK gerektirmeyen kontroller tools/check-core.ps1 üzerinden tekrar çalıştırılabilir.

## Önceki sürümün gerçek cihaz sonucu

Kullanıcı 1.0.94-preview ve ardından 1.1.1 final sürümlerinde sesli video indirmenin telefonda çalıştığını ve hızlı olduğunu
31 Ağustos 2026'da doğruladı. Bu bildirim tüm kalite/cihaz/ağ senaryolarının test edildiği anlamına gelmez.
1.2 adayında yeniden kodlamadan birleştirme motoru korunur; aktarıma devam ve arayüz değişiklikleri ayrıca test edilir.

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
StartupInstrumentedTest ana ekranın açılıp RESUMED durumuna geldiğini ve küçültülmüş APK'da Rhino'nun
yansıma yoluyla yüklenip JavaScript/JSON çalıştırabildiğini doğrular.
Kuyruk testi, etkin işin parçalarının temizlikte korunduğunu ve süresi dolan etkin olmayan parçaların silindiğini de kontrol eder.
HomeScreenInstrumentedTest, gerçek Activity üzerinde bağlantı girişi/temizleme ve düğmenin etkinleşmesini doğrular.
Bu altı cihaz testi kontrollü medya kullanır; gerçek YouTube indirmesini veya uzun süreli ağ/batarya koşullarını doğrulamaz.

CI yapılandırması bu cihaz testlerini API 29, 34 ve 36 emülatörlerinde çalıştırır ve release işini sonuçlarına bağlar.
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
| Başarısız indirmede yeniden dene | Güncel bağlantı çözülür; doğrulanmış parçalar korunur |
| Dosya sıralama | En yeni, ad, boyut sıraları arama/filtre ile birlikte çalışır |
| Public GitHub veya bağlantı hatası | Giriş gerekmeden kontrol; hata halinde güncel iddiası yok |

## Final yayın kapıları

1.1.0 çalışmasında debug API 29/34 ve imzalı release API 34 cihaz testleri geçti.
Yayın, yeni apksigner çıktısındaki `V2 Signer:` etiketi eski `Signer #1` biçimiyle
eşleşmediği için durdu; gerçek sertifika beklenen kalıcı anahtarla aynıydı.
1.1.1 her iki biçimi doğrular, farklı/eksik sertifikayı reddeder; beş araç testi bu denetimi korur.
Cihaz testinin kaldırdığı release APK ekran görüntüsü kontrolünden önce tekrar kurulur;
açılış çıktısında `Status: ok` bulunmadan yayın sürdürülmez.

Kalıcı anahtar GitHub Actions secrets üzerinden sağlanır; paket kimliği `.stable` olur.
Eksik imza, hatalı sertifika veya başarısız doğrulama final yayınını durdurur.
Debug cihaz testleri API 29/34/36 üzerinde; ek imzalı release cihaz testi API 34 üzerinde çalışır.
APK ve kaynak ZIP için SHA256SUMS.txt yayımlanır. Kaynak arşivi yalnızca Git'in izlediği dosyalardan,
cipher alt modülünün sabitlenmiş sürümü dahil edilerek üretilir; yerel özel anahtar dahil edilmez.

## Bilinen sınırlar

- CI 113 MP3 ve canlı kayıt öncesini ölçer. Yeni özelliklerin kapsamı ve kabul testleri [MP3_LIVE.md](MP3_LIVE.md) içindedir; CI 113 değerleri yeni native encoder boyutunu veya yükünü temsil etmez.
- Doğrulanmış tam HTTP parçalarından devam vardır. Kesilen son parça veya doğrulanamayan kaynak baştan alınır.
- YouTube erişim/PoToken kısıtları tüm videolar için başarı garantisi vermez.
- Cihazın codec ve işletim sistemi desteği gerçek dosyayla kontrol edilmelidir.

## 1.2 geliştirme doğrulaması
- MediaTransferTest: ağ kesintisi, değişen ETag, Range yok sayılması, doğrulayıcı eksikliği, tamamlanmış parçanın kullanılması, yarım parçanın kesilmesi, bozuk metadata ve farklı URL kimliği; ayrıca kaynakta bildirilen boyuta uymayan tam/kısmi yanıtların reddedilmesi. Toplam 10 aktarım testi.
- HomeScreenInstrumentedTest: gerçek Activity üzerinde bağlantı girişi ve düğmenin etkinleşmesi; YouTube'a ağ isteği başlatmaz.
- İmzalı aday: R8/kaynak küçültme, APK/AAB üretimi, 16 KB ELF/ZIP denetimi, Android 14'te 1.1.1 üzerine kurulum.
- Açılış/PSS ölçümü: tools/capture-device.py; eski ve yeni APK aynı CI emülatöründe. Gerçek telefon performansının yerine geçmez.
- Yarım indirme temizliğinde etkin işler ve ortak Download dosyaları korunmalı.
- Yeni hedef API nedeniyle Android 16'da kenar boşlukları, klavye ve geri düğmesi ayrıca kontrol edilmeli.

Bu dalın kesin sonucu, commit'e ait GitHub Actions raporudur. Birleştirme motoru korunmuştur; yeni aktarım/devam davranışının gerçek YouTube ve ağ kesintisi kabul testi telefonda ayrıca yapılmalıdır.

## Doğrulanmış imzalı aday — 1.2.0-dev.113

[CI 113](https://github.com/eekilinc/indirgitsin/actions/runs/33383131109), `8ab6253ba8f0367c0269747f3f554fbb3c0607f9` commit'inde başarılıdır. Sonraki belge güncellemeleri bu APK'nın kodunu değiştirmez. [Ham ölçümler ve dosya özetleri](measurements/candidate-113.json).

- Uygulama JUnit: debug ve release varyantlarında 40'ar test; cipher JUnit: 62 test; JavaScript araçları: 26 test; sertifika araçları: 5 test başarılı.
- Debug API 29/34/36: her birinde 6 test, sıfır hata ve sıfır atlanan test. Kalıcı imzalı, küçültülmüş release API 34: 6 test, sıfır hata ve sıfır atlanan test.
- Android lint: sıfır hata; 50 uyarı ve 2 öneri var. Uyarılar çözülmüş gibi gösterilmez.
- 1.1.1 üzerine kurulum, açılış, kalıcı sertifika, APK/kaynak SHA-256 ve dört ABI'nin statik ELF/ZIP 16 KB hizalaması doğrulandı. Kaynak ZIP'i 158 izlenen dosyayla test edilmiş commit ve sabitlenmiş cipher kaynağına birebir uyuyor.
- Testi başlatamayan `Trace`, geçersiz final metot ve taşınmış `ViewTreeLifecycleOwner` sorunları ortak API korumalarıyla giderildi. Kuyruk testi terminal durumun yazılmasını bekler. Testler veya uygulama küçültmesi kapatılmadı.

| Ölçüm | 1.1.1 | 1.2.0-dev.113 |
|---|---:|---:|
| APK boyutu (bayt) | 16.814.252 | 4.147.488 |
| Ortanca soğuk açılış (5 örnek) | 801 ms | 609 ms |
| Boşta toplam PSS (KiB; tek örnek) | 54.617 | 33.449 |

Boyut %75,33 azaldı. Açılış ve bellek aynı Android 14 CI emülatöründe ölçüldü; cihaz, pil, ağ ve uzun indirme performansı hakkında garanti değildir. Yeni adayda gerçek telefon kabul listesi hâlâ uygulanmalıdır. Son public final 1.1.1'dir; bu doğrulama Play Store yayını anlamına gelmez.
