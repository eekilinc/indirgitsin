# İndir Gitsin

Kotlin ve Jetpack Compose ile yazılmış Android YouTube video/ses indiricisi.
Android 7.0 (API 24) ve üzeri; compileSdk 35, targetSdk 34.

## İndirme akışı

1. YouTube bağlantısı NewPipeExtractor ile çözümlenir. Gerekirse YouTube web yanıtı ve zemer-cipher kullanılır.
2. Akışın video-only olup olmadığı açıkça belirlenir; çözünürlükten tahmin edilmez.
3. Ayrı video ve ses gerekiyorsa uyumlu iki dosya paralel indirilir:
   - MP4: AVC/HEVC + AAC; AV1 için API 34+ gerekir.
   - WebM: VP8/VP9 + Vorbis; Opus birleştirmek için API 29+ gerekir.
4. Android MediaMuxer özgün codec bilgilerini ve zaman damgalarını koruyarak birleştirir.
5. Sonuçta ses ve görüntü izleri kontrol edilir. Ses yoksa indirme başarılı sayılmaz.
6. Doğrulanmış dosya Download/IndirGitsin veya seçilen alt klasöre kaydedilir.

Ses akışları gerçek M4A/WebM formatlarıyla sunulur. Uygulama MP3 dönüştürücü içermez;
M4A/WebM dosyalarını MP3 olarak yeniden adlandırmaz. HLS/canlı yayın kaydı desteklenmez.
Desteklenen kalite, videonun sunduğu akışlara ve cihazın codec desteğine bağlıdır.

## Kuyruk ve indirme ekranı

İndirmeler WorkManager ile kalıcı olarak kuyruğa alınır. Aynı anda en fazla iki indirme işi,
her iş içinde video ve ses olmak üzere iki akış çalışır. Bildirimden veya İndirilenler ekranından iptal edilebilir.

Uygulama süreci kapanırsa WorkManager işi yeniden çalıştırabilir. Yarım dosyadan bayt düzeyinde
devam etme henüz yoktur: yeniden denemede bağlantı tekrar çözülür ve aktarım baştan başlar.
Kullanıcının Android ayarlarından zorla durdurması, cihaz/ağ kısıtları ve işletim sistemi
arka plan sınırlamaları indirmeyi erteleyebilir.

Android 10+ için MediaStore ve IS_PENDING kullanılır; tamamlanmamış dosyalar normal
medya listesine çıkarılmaz. Android 7–9 için depolama izni gerekir. Yeni dosyalar,
önceki indirmelerin üzerine yazmamak için iş kimliği içeren benzersiz ad alır.
Eski DownloadManager indirmeleri ekranda takip edilmeye devam edilir.

Çalma listelerinin devam sayfaları okunur; seçili videolar tek seferde kalıcı kuyruğa eklenir.
Sonsuz radyo/çok büyük listeler için 100 sayfa sınırı vardır.
Varsayılan kalite, toplu indirmelerde seçimi ve tek video ekranında sıralamayı etkiler.
Varsayılan ses formatı ses seçeneklerinin sırasını belirler.

## Mimari

- UI: Compose, Material 3, Navigation Compose
- Durum: HomeViewModel + StateFlow; yeni arama eski isteği iptal eder
- Ağ: NewPipeExtractor, OkHttp, zemer-cipher (cipher/ Git submodule)
- İndirme: DownloadWorker, MediaTransfer, MediaFileMuxer, DownloadStorage
- Veri: Room geçmişi, DataStore ayarları, WorkManager iş kayıtları
- Oynatma: Media3 ExoPlayer; uygulama arka plana geçince duraklatılır

## Derleme

JDK 17 ve Android SDK (platform 35 ve AGP'nin istediği build-tools) gerekir.

```sh
git clone --recurse-submodules https://github.com/eekilinc/indirgitsin.git
cd indirgitsin
# Android Studio ile SDK konumunu ayarlayın veya local.properties içine sdk.dir yazın.
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest :app:lintDebug :cipher:library:testDebugUnitTest
node --test cipher/tools/tools.test.mjs
```

Debug APK: app/build/outputs/apk/debug/app-debug.apk.
Debug APK yalnızca geliştirme/test içindir; kalıcı dağıtım için kullanılmamalıdır.
`com.indirgitsin.app.preview` kimliği ve “İndir Gitsin Test” adıyla mevcut uygulamanın yanına kurulur.

Bağlı cihaz veya emülatörde gerçek AAC/AVC örneklerini birleştiren testi çalıştırmak için:

```sh
./gradlew :app:connectedDebugAndroidTest
```

Manuel doğrulama listesi: [docs/VALIDATION.md](docs/VALIDATION.md).

## İmzalı yayın

Pull request ve main/master push'larında test, lint ve debug derlemesi çalışır.
Main/master için tüm kontrollerden sonra test APK'sı `v1.0.<run>-preview` GitHub ön sürümünde yayımlanır.
Yayımlanan dosya doğrulama işinde oluşturulup imzası kontrol edilen APK'nın aynısıdır; SHA-256 özeti de eklenir.
Kalıcı dağıtım için imzalı GitHub Release, v*.*.* etiketi gönderildiğinde ve doğrulama işleri geçtiğinde oluşturulur.

GitHub Actions secrets:

- ANDROID_KEYSTORE_BASE64: mevcut dağıtım keystore dosyasının Base64 içeriği
- ANDROID_KEYSTORE_PASSWORD
- ANDROID_KEY_ALIAS
- ANDROID_KEY_PASSWORD

Yerel release derlemesinde aynı bilgiler RELEASE_STORE_FILE, RELEASE_STORE_PASSWORD,
RELEASE_KEY_ALIAS, RELEASE_KEY_PASSWORD ortam değişkenleriyle verilir.
Eksik imzayla release APK üretilmesi engellenir. Anahtar/password dosyalarını Git'e eklemeyin.

Mevcut kullanıcıların üzerine güncelleme kurabilmesi için önceki dağıtımın imza anahtarı korunmalıdır.
Bu değişiklik yeni imza anahtarı üretmez ve GitHub secrets ayarlamaz.
Daha önce farklı debug anahtarlarıyla dağıtılmış APK'ların güncelleme sorunu otomatik giderilemez.

## Gizlilik ve sınırlamalar

Video kimlikleri halka açık Cobalt/Piped/Invidious servislerine gönderilmez.
YouTube ve thumbnail sunucularına; sürüm kontrolü ve cipher yapılandırması için GitHub'a ağ erişimi yapılır.
Geçmiş ve ayarlar cihazda saklanır; uygulamanın yedekleme kuralları bunları bulut ve cihaz aktarımından hariç tutar.
İndirilen dosyalar ortak Downloads alanındadır; erişim hakkı olan diğer uygulamalar bunları görebilir.

YouTube tarafındaki değişiklikler, erişim kısıtları veya codec desteği nedeniyle bazı videolar indirilemeyebilir.
Yalnızca indirme hakkınız olan içerikleri kullanın.

## Lisans bilgisi

Kök depoda bir LICENSE dosyası bulunmuyor; önceki README'deki MIT ifadesi bu nedenle kaldırıldı.
cipher/ alt modülü GPL-3.0 lisansını içeriyor; NewPipeExtractor da kendi lisans koşullarına tabidir.
Bu güncelleme projeyi yeniden lisanslamaz. Dağıtım öncesinde proje lisansı ve üçüncü taraf bildirimleri tamamlanmalıdır.
