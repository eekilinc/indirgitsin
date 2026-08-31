# Güvenlik
Güncel final sürümü kullanın. Güvenlik düzeltmeleri son final sürümüne hazırlanır; eski test APK'ları için destek taahhüdü yoktur.

Açıkları [özel güvenlik bildirimi](https://github.com/eekilinc/indirgitsin/security/advisories/new) ile bildirin. Tekrarlama adımları, etkilenen sürüm ve olası etki yeterlidir. Gerçek parola, token veya özel medya göndermeyin.

İmza anahtarları ve GitHub erişim token'ları APK'ya veya kaynak koduna eklenmez. Public sertifika parmak izi [release-certificate.sha256](docs/release-certificate.sha256) dosyasındadır. Her final APK'nın sertifikası ve SHA-256 özeti CI'da kontrol edilir.

PR doğrulamasında imza sırları kullanılmaz. Yayın işleri yalnızca yetkili tag/manual çalıştırmalarında imza kullanır; iş sonunda geçici anahtar kaldırılır. Güvenlik taraması bir güvenlik garantisi değildir.
