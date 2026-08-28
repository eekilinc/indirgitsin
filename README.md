# İndir Gitsin — YouTube & YouTube Music İndirici

Modern, kullanıcı dostu Android uygulaması. Linki kopyala veya YouTube'dan **Paylaş → İndir Gitsin** de, kaliteyi seç ve indir.

## Özellikler
- 🔗 **Link yapıştır**: Panodaki YouTube linki otomatik algılanır
- 📤 **Paylaş entegrasyonu**: YouTube / YouTube Music uygulamasından `Paylaş` deyince listede görünür
- 🎬 **YouTube + YouTube Music** desteği (youtu.be, youtube.com, music.youtube.com, shorts)
- 🎥 **Kalite seçimi**: Video (144p-1080p) ve Ses (m4a/mp3) seçenekleri — Bottom Sheet
- 🎨 **Modern UI**: Jetpack Compose + Material 3, Dark/Light tema, animasyonlu kartlar
- 📥 **Sistem DownloadManager** ile arka planda indirme, bildirimli

## Teknoloji
- **Kotlin 2.0 + Jetpack Compose + Material3**
- MVVM, Coroutines, Navigation Compose
- **NewPipeExtractor** (YouTube veri çekme — API key gerektirmez)
- OkHttp, Coil, DataStore
- `minSdk 24` / `targetSdk 34`

## Proje Yapısı
```
app/src/main/java/com/indirgitsin/app/
  MainActivity.kt          // Intent (SEND/VIEW) + Clipboard + UI host
  HomeViewModel.kt
  data/extractor/          // NewPipeExtractor wrapper
  data/downloader/         // DownloadManager wrapper
  data/model/              // VideoInfo, StreamOption, UiState
  ui/screen/HomeScreen.kt  // Modern ana ekran + BottomSheet
  ui/theme/                // Material3 tema
```

## APK olmadan Test
### 1) Compose Preview
Android Studio'da `HomeScreen.kt` dosyasını aç → sağ üst **Split/Design** → Preview'leri gör. APK build etmeden UI'ı test edebilirsin.

### 2) Unit Test
```bash
./gradlew testDebugUnitTest
```

### 3) Emülatör / Fiziksel Cihaz
Android Studio → Device Manager → Emulator oluştur → **Run > app**. Kod değişince anında **Apply Changes**.

```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
# adb install app/build/outputs/apk/debug/app-debug.apk
```

## GitHub Push → Otomatik Release
`.github/workflows/release.yml` her `main` push'unda:
1. `assembleDebug` + `assembleRelease` build eder
2. APK'ları artifact olarak yükler
3. Otomatik **GitHub Release** oluşturur (`v1.0.<run_number>` tag'i)

Manuel release için tag push'la:
```bash
git tag v1.0.0
git push origin v1.0.0
```

Release dosyaları: `Actions` sekmesi → workflow → `indir-gitsin-debug-apk` artifact veya `Releases` sayfasından indir.

## Kurulum
1. Repo'yu klonla ve Android Studio ile aç (Koala+ önerilir, JDK 17)
2. `local.properties` içinde `sdk.dir` otomatik oluşur
3. Run

## Yasal Uyarı
Bu uygulama yalnızca **kendi içeriklerin veya indirme izni olan videolar** için kullanılmalıdır. YouTube Hizmet Şartlarına ve telif haklarına uyunuz. Geliştirici telif ihlalinden sorumlu değildir.

## Lisans
MIT
