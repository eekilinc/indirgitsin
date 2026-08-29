# İndir Gitsin — YouTube & YouTube Music İndirici

<p align="center">
  <img src="https://img.shields.io/github/v/release/eekilinc/indirgitsin?label=versiyon&color=FF0000" />
  <img src="https://img.shields.io/github/actions/workflow/status/eekilinc/indirgitsin/release.yml?label=build" />
  <img src="https://img.shields.io/github/license/eekilinc/indirgitsin" />
  <img src="https://img.shields.io/badge/platform-Android%2024%2B-3DDC84" />
  <img src="https://img.shields.io/badge/Kotlin-2.1-7F52FF" />
  <img src="https://img.shields.io/badge/Jetpack%20Compose-Material3-4285F4" />
</p>

<p align="center"><b>YouTube ve YouTube Music linklerinden video ve sesleri cihazına tek dokunuşla indir.</b><br/>Gizlilik odaklı • Reklamsız • Hızlı • Premium hissi</p>

<p align="center">
  <a href="https://github.com/eekilinc/indirgitsin/releases/latest"><b>⬇️ Son APK'yı İndir</b></a> •
  <a href="#-kurulum">Kurulum</a> •
  <a href="#-nasıl-çalışır">Nasıl Çalışır</a> •
  <a href="#-yasal-uyarı">Yasal</a>
</p>

---

## ✨ Öne Çıkanlar

| Özellik | Açıklama |
|---|---|
| 🔗 **Tek dokunuş** | Panodaki link otomatik algılanır, **Paylaş → İndir Gitsin** ile doğrudan açılır |
| 🎬 **YouTube + Music + Shorts** | `youtu.be`, `youtube.com`, `m.youtube.com`, `music.youtube.com` tam destek |
| 🎥 **Tüm kaliteler** | 144p → 4K (DASH muxed + video-only) • HLS/Canlı destek |
| 🎧 **Ses** | M4A / WEBM / **MP3** (her seste otomatik MP3 seçeneği) |
| 📥 **Arkaplanda indirme** | Sistem **DownloadManager** • Bildirimli, duraklat/iptal edilebilir, `/Download/IndirGitsin` |
| 📂 **İndirilenler** | Canlı progress + bitince otomatik listeye eklenir • Oynat / Paylaş / Sil |
| 🕘 **Kitaplık & Geçmiş** | Çözülen videolar yerelde (Room) • Tek tıkla tekrar getir |
| 🎨 **Premium UI** | Jetpack Compose + Material 3 • **Açık / Koyu / Sistem** tema • YouTube/Spotify esintisi |
| 🔄 **Otomatik güncelleme** | Açılışta GitHub Releases kontrolü + Ayarlar → **Denetle** |
| 🔒 **Gizlilik** | Hesap yok, iz yok, geçmiş sadece cihazında |

## 📸 Ekran Görüntüleri

| Ana Sayfa | Kaliteler | İndirilenler |
|---|---|---|
| Link yapıştır → anında önizleme | Video / Ses sekmeleri • MP3 dahil | Canlı progress + Oynat/Paylaş/Sil |
| <img width="260" src="https://via.placeholder.com/260x500?text=Ana+Sayfa" /> | <img width="260" src="https://via.placeholder.com/260x500?text=Kaliteler" /> | <img width="260" src="https://via.placeholder.com/260x500?text=Indirilenler" /> |

> `app/src/main/java/com/indirgitsin/app/ui/screen/HomeScreen.kt` içinde Compose Preview ile APK’sız önizleme yapabilirsin.

## 🧠 Nasıl Çalışır

1. **NewPipeExtractor `v0.26.5`** (SABR workaround’lu) ile watch page doğrudan parse edilir — Piped/Cobalt’a bağımlı değil.
2. `DownloaderImpl` (`Downloader.execute(Request)` doğru implementasyon) ile `n` parametresi ve imza çözülür.
3. Seçilen `StreamOption` → `DownloadManager` → `/Download/IndirGitsin/<başlık>_<kalite>.mp4/m4a/mp3` → bildirimden takip.
4. `MediaStore.Downloads` + `DownloadManager.Query` ile canlı + tamamlananlar listelenir.
5. Versiyon `GITHUB_REF_NAME`/`GITHUB_RUN_NUMBER`’dan otomatik (`1.0.X` = GitHub tag ile senkron).

## 🛠 Teknoloji

| Katman | Araç |
|---|---|
| Dil / UI | **Kotlin 2.1**, Jetpack Compose, Material 3, Navigation Compose |
| Mimari | MVVM, `HomeViewModel` + `UiState`, Coroutines/Flow |
| Veri | NewPipeExtractor 0.26.5, OkHttp 4.12, Coil, Room 2.6, DataStore 1.0 |
| Derleme | AGP 8.13.2, Gradle 8.13, `targetSdk 34`, `minSdk 24` |
| Cipher | `com.zemer:cipher` (composite build, Timber) — PoToken/`n` gerektiğinde |

```
app/src/main/java/com/indirgitsin/app/
  MainActivity.kt                 // SEND/VIEW intent + Clipboard + update popup + bottom nav
  HomeViewModel.kt                // extract + reset + history
  data/extractor/                 // NewPipeHelper + DownloaderImpl + YoutubeExtractor (fallback)
  data/downloader/FileDownloader  // DownloadManager (birincil)
  data/history/AppDatabase        // Room geçmiş
  data/SettingsStore              // DataStore (tema, kalite, klasör)
  ui/screen/HomeScreen            // Premium hero + search + video card (X ile temizle)
  ui/screen/DownloadsScreen       // Canlı progress + iptal + oynat/paylaş/sil
  ui/theme/                       // YT kırmızısı, açık/koyu palet
  util/UpdateChecker              // GitHub releases/latest kontrolü
```

## 📲 Kurulum

### Son APK (önerilen)
**Releases** → en üstteki `İndir Gitsin v1.0.X` → `app-debug.apk` veya `app-release.apk` → telefonunda kur (Bilinmeyen kaynaklara izin ver).

### Kaynaktan derle
```bash
git clone https://github.com/eekilinc/indirgitsin.git
cd indirgitsin
# Android Studio Koala+ / JDK 17 ile aç ve Run
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
# adb install app/build/outputs/apk/debug/app-debug.apk
```

`local.properties` içindeki `sdk.dir` otomatik oluşur. İlk derlemede `cipher` submodule’i `includeBuild` ile gelir.

## ▶️ Kullanım

1. YouTube’da videoyu **Paylaş → İndir Gitsin** de veya linki kopyala → uygulamada **Yapıştır**.
2. Kart belirince **İndir** → kalite seç (Video / Ses sekmeleri) → indirme başlar.
3. **İndirilenler** sekmesinden canlı progress’i izle, **X** ile iptal et, bitince **Oynat** (VLC/MX/YouTube Music’e gönder) / **Paylaş** / **Sil**.
4. Kartın sağ üstündeki **X** ile anında temizleyip yeni link yapıştır.

## ⚙️ Ayarlar

- **İndirme Konumu:** `/Download/IndirGitsin` (varsayılan) → **Değiştir** ile alt klasör adını değiştir (örn. `Muzikler`), **Aç** ile klasörü göster.
- **Tema:** Açık / Koyu / Sistem
- **Varsayılan Kalite / Ses Formatı:** DataStore’a kalıcı
- **Güncelleme:** Açılışta otomatik popup + **Denetle** manuel butonu → GitHub Releases’e gider.
- **Hakkında:** Program açıklaması + otomatik versiyon (`PackageManager`) + **GitHub** siyah butonu → `github.com/eekilinc/indirgitsin`

## 🔄 Otomatik Release

`.github/workflows/release.yml` her `main` push’unda:
1. `assembleDebug` + `assembleRelease`
2. Artifact yükler
3. `v1.0.<run_number>` tag’i ile **GitHub Release** oluşturur

`app/build.gradle.kts` versiyonu aynı tag’den türediği için uygulama içi versiyon ↔ GitHub tag birebir eşleşir.

Manuel tag:
```bash
git tag v1.0.0
git push origin v1.0.0
```

## 🔐 İzinler

`INTERNET`, `POST_NOTIFICATIONS` (indirme bildirimi), `READ_MEDIA_VIDEO/AUDIO` (İndirilenler listesi). Dosyalar sadece `/Download/IndirGitsin`’e yazılır.

## ⚖️ Yasal Uyarı

Yalnızca **kendi içeriklerin veya indirme izni olan videolar** için kullan. YouTube Hizmet Şartlarına ve telif haklarına uy. Geliştirici telif ihlalinden sorumlu değildir.

## 🗺 Yol Haritası

- [x] NewPipeExtractor + DownloadManager (SABR fix)
- [x] Koyu/Açık tema + otomatik güncelleme + IndirGitsin klasörü
- [ ] Çalma listesi (playlist) toplu indirme + kuyruk
- [ ] Uygulama içi ExoPlayer önizleme (ses/video)
- [ ] Bildirimden doğrudan aç/oynat

Önerin var mı? **Issues → New issue** açman yeterli.

## 🤝 Katkı

PR’lar memnuniyetle karşılanır. Lütfen `main`’e PR açmadan önce `./gradlew assembleDebug`’in geçtiğinden emin ol.

## 📄 Lisans

MIT — `LICENSE` dosyasına bak. `github.com/eekilinc/indirgitsin`
