# Lisans ve kaynak dağıtımı

Hak sahibi GPL seçimini 31 Ağustos 2026 tarihinde onayladı. İndir Gitsin ana uygulamasının lisansı **GPL-3.0-only**; tam metin kökteki [LICENSE](../LICENSE) dosyasındadır. Copyright (C) 2026 İndir Gitsin contributors. Garanti verilmez; dağıtım ve değişiklik koşulları lisans metnindedir.

Üçüncü taraf kodlar kendi lisans ve telif bildirimlerini korur:

- `cipher/`: sabitlenmiş GPL v3 alt modülü, kaynak ZIP'ine dahil.
- NewPipeExtractor: GPL v3 ailesi; sürümü ve kaynak adresi uygulama içi bildirimlerde.
- LAME 3.100: **LGPL-2.0-or-later**; değiştirilmemiş encoder kaynakları `app/src/main/cpp/lame/` içinde. Resmî proje aynasının `e1f244ae762bc876913002abb22141a3abb2f4b8` revizyonu. `UPSTREAM.json` her dosyanın SHA-256 değerini, `COPYING` lisans metnini içerir.
- AndroidX, Kotlin, OkHttp ve diğer bağımlılıkların bildirimleri Ayarlar → Lisans bölümündedir.

LAME ayrı `libindirgitsin_mp3.so` kitaplığı olarak uygulamayla derlenir. JNI bağlayıcısı, CMake/config dosyaları ve Kotlin dönüşüm kodu ana uygulamanın GPL v3 lisansındadır. LAME kaynaklarında değişiklik yapılmadı; frontend, MPGLIB decoder ve assembly derlenmez. FFmpeg yalnızca CI test verisini üretir; uygulamaya dahil değildir.

Kaynak ZIP'i sürümün izlenen uygulama dosyalarını, vendored LAME kaynaklarını, derleme tariflerini ve sabit cipher revizyonunu birlikte içerir. NDK/CMake sürümleri Gradle'da sabittir. Değiştirilmiş LAME veya uygulama ile `./gradlew :app:assembleDebug` yeniden derlenebilir; kullanıcı kendi anahtarıyla release de üretebilir. Dağıtımın özel imza anahtarı kaynak paketine konulmaz. Derleme ve imzalama adımları [RELEASING.md](RELEASING.md) içindedir.

Bu lisans seçimi içerik indirme izni, YouTube şartlarına uyum veya Play Store kabulü sağlamaz. Mağaza için açık konular [yayın hazırlığı değerlendirmesinde](PUBLISHING_READINESS.md) ayrı tutulur.
