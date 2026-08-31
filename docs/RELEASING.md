# Derleme ve yayın
Android Studio veya JDK 17 + Android SDK 36 gerekir. Yerel SDK kurulumu zorunlu değildir; CI derlemeleri GitHub Actions'ta çalışır.

```sh
git clone --recurse-submodules https://github.com/eekilinc/indirgitsin.git
cd indirgitsin
./gradlew :app:assembleDebug
```

Debug çıktı: `app/build/outputs/apk/debug/app-debug.apk`. Test paketi `com.indirgitsin.app.preview`; kalıcı final paketi **com.indirgitsin.app.stable**. Final ve test ayrı kurulur. Test APK'larının debug imzası değişebilir; final imzası değiştirilmemelidir.

## Doğrulama
PR/main ve yetkili manuel çalıştırmalarda birim testleri, lint, debug derlemesi ve Android 10/14/16 cihaz testleri çalışır. Cihaz raporları AAC/AVC birleştirme/çözme, kuyruk/tekrar deneme, açılış ve bağlantı girişini kapsar. Emülatörün açılış/bellek kanıtları artifact olarak saklanır.

Main push'u test ön sürümü üretir. Yetkili workflow_dispatch çalıştırması kalıcı anahtarla aday APK/AAB üretip test eder; tag yoksa final yayımlamaz. AAB üretmek Play Store'a yüklemek değildir.

## Kalıcı imza
GitHub Actions secrets:

- ANDROID_KEYSTORE_BASE64
- ANDROID_KEYSTORE_PASSWORD
- ANDROID_KEY_ALIAS
- ANDROID_KEY_PASSWORD

Yerel karşılıkları RELEASE_STORE_FILE, RELEASE_STORE_PASSWORD, RELEASE_KEY_ALIAS, RELEASE_KEY_PASSWORD değişkenleridir. Anahtar/parolalar kaynak koduna veya APK'ya girmez. Eksik imza ile final derlemesi engellenir. CI sertifikayı [public parmak izi](release-certificate.sha256) ile karşılaştırır.

Mevcut özel anahtarın güvenli yedeğini tutun. **Paket kimliği ve kalıcı anahtar değişirse mevcut final kullanıcıları normal güncelleme yapamaz.** Play App Signing'e geçilecekse GitHub/Play dağıtımları arasındaki sertifika stratejisi ayrıca planlanmalıdır.

## Final
Uygulama sürümü `vX.Y.Z` etiketinden, Android versionCode CI run numarasından üretilir. Tüm kontroller geçtikten sonra etiketli iş APK, SHA256SUMS.txt ve sabitlenmiş cipher kaynaklarını içeren ZIP'i GitHub Release'e ekler.

Optimize final ayrıca Android 14'te cihaz testinden geçirilir. 1.1.1 APK'nın sabit SHA-256 değeri doğrulanır; eski sürüm kurulur, yeni aday onun üzerine yüklenir. Aynı emülatörde iki sürümün 5 açılış örneği ve boşta PSS bellek görüntüsü kaydedilir. Gerçek telefon hızı/batarya ölçümü değildir.

R8 mapping dosyası, AAB ve ayrıntılı raporlar doğrulama artifact'inde bulunur. Play Console'a otomatik yükleme yoktur. [Lisans durumu](LICENSE_STATUS.md) ve [yayın hazırlığı](PUBLISHING_READINESS.md) çözülmeden mağaza uygunluğu varsayılmamalıdır.
