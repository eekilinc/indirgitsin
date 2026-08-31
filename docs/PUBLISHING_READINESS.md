# Yayın hazırlığı değerlendirmesi
İnceleme tarihi: **31 Ağustos 2026**. Çalışan GitHub finali: **1.1.1**. Bu daldaki 1.2 hazırlığı Play Store'a gönderilmedi.

**Mevcut ürüne “Play Store'da sorunsuz yayımlanır” denemez.** Teknik derleme başarısı, telif/platform şartları ve mağaza politikası birbirinden ayrı değerlendirilmelidir. Bu inceleme hukuki görüş veya Google onayı değildir.

| Başlık | Durum / gerekli iş |
|---|---|
| İçerik hakları | Kritik. Uygulama YouTube içeriklerini cihazda saklıyor. Play, izinsiz telifli kopyalamayı teşvik eden uygulamalara izin vermiyor. İzinli kullanım uyarısı tek başına işlevin ve tanıtımın uyumunu kanıtlamaz. |
| YouTube şartları | İndirme kullanımı hizmetin açık iznine veya YouTube ve ilgili hak sahibinin yazılı iznine bağlı sınırlamalara tabi. Haklar ve kullanım senaryosu ayrıca incelenmeli. |
| Ana proje lisansı | LICENSE yok. GPL bileşenleri nedeniyle lisans ve kaynak dağıtım yükümlülükleri çözülmeli. [Ayrıntı](LICENSE_STATUS.md). |
| Hedef API | 1.1.1 target 34. Bu dal compile/target 36'ya geçirilmiş durumda; Android 16 cihaz testi CI kapsamına eklendi. |
| 16 KB sayfalar | 1.1.1 APK'sındaki tüm native kütüphanelerin ELF ve ZIP hizalaması uygun çıktı. Yeni final için otomatik kontrol eklendi. Bu statik kontrol, gerçek 16 KB cihaz testinin yerini tutmaz. |
| Kimlik / imza | Kalıcı stable paket ve sertifika korunuyor. Play App Signing planı ve mağaza anahtarı henüz seçilmedi. |
| AAB | İmzalı AAB üretimi hazırlandı; Play Console'a yüklenmedi ve mağaza tarafından doğrulanmadı. |
| Gizlilik | TR/EN uygulama içi politika eklendi. Kalıcı public URL, geliştirici iletişim kanalı ve üçüncü taraf hizmetleri kapsayan Data safety beyanı başvuru öncesi gözden geçirilmeli. |
| İzinler / arka plan | MediaStore ve dataSync ön plan hizmeti var. Play Console hizmet kullanım beyanı/demosu, uzun indirmelerde Android kotaları ve üretici güç tasarrufu testleri eksik. |
| Mağaza hazırlığı | Hesap doğrulama, gerekli test süreci, içerik derecelendirmesi, hedef kitle, ekran görüntüleri ve mağaza açıklaması henüz yapılmadı. |
| Erişilebilirlik | Yeni giriş alanında etiketler ve dokunma hedefleri var. TalkBack, büyük yazı, tablet/katlanabilir ekran ve tüm tema durumları için tam kabul testi yapılmadı. |
| Performans | R8/kaynak küçültme, sınırlı eşzamanlı aktarım, küçük akış tamponları, update önbelleği ve yarım dosya temizliği var. Gerçek telefon yük/batarya/ANR ölçümü tamamlanmış değil. |

## Güncel resmi kurallar
- [Google Play fikri mülkiyet politikası](https://support.google.com/googleplay/android-developer/answer/9888072?hl=en): izinsiz telifli içerik indirmeyi teşvik etme riski.
- [YouTube hizmet şartları — Permissions and Restrictions](https://www.youtube.com/static?template=terms): indirme ve otomatik erişim sınırlamaları.
- [Hedef API gereksinimi](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en): 31 Ağustos 2026'dan itibaren yeni mobil uygulamalar ve güncellemeler API 36+ hedeflemeli. Olası ek süre otomatik kabul edilmez.
- [16 KB sayfa desteği](https://developer.android.com/guide/practices/page-sizes): API 35+ hedefleyen uygulamalarda 64 bit uyumluluk gerekiyor; güncel sayfa güncellemeler için 1 Şubat 2027 tarihini belirtiyor.
- [Kullanıcı verileri](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en) ve [Data safety](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en): politika uygulama içinde ve public URL'de erişilebilir olmalı; SDK davranışları da beyana dahil.
- [Android 16 davranışları](https://developer.android.com/about/versions/16/behavior-changes-16): kenardan kenara yerleşim ve geri gezinme uyumluluğu.
- [WorkManager sürüm notları](https://developer.android.com/jetpack/androidx/releases/work): 2.10.5 güncellemesi Android 15 ön plan hizmeti/zaman aşımı düzeltmelerini içerir.

## Performans sonucunun sınırı
Aktarımlar iki işle sınırlandırılmıştır; bir iş içinde video/ses paraleldir. Dosyanın tamamı RAM'e alınmaz. MediaMuxer örnekleri yeniden kodlamadan kopyalar; tamponu 2 MB'dan başlar ve gerektiğinde en fazla 64 MB'a büyür. Bu, uygulamanın toplam RAM'inin 64 MB olduğu anlamına gelmez: WebView, UI, codec ve ağ bileşenleri ayrıca bellek kullanır.

Ölçüm betiği `tools/capture-device.py` beş soğuk süreç açılışı, boşta PSS ve ekran görüntüsü toplar. Aynı runner'daki eski/yeni değerler karşılaştırılabilir; emülatör, ağ durumu ve örnek sayısı nedeniyle fiziksel cihaz garantisi değildir. Android vitals verisi, uzun süreli düşük bellek testi, farklı üretici/batarya testleri ve gerçek 16 KB cihaz doğrulaması henüz yok.

MP3 dönüştürme ve HLS/canlı kayıt tamamlanmış sayılmaz. Yeni codec/segment motorları, lisans incelemesi ve uçtan uca cihaz testleri gerekir. Desteklenmeyen özellikler arayüzde varmış gibi gösterilmez.

## Statik analizde kalan işler
1.2 adayının Android lint raporunda hata yok; **50 uyarı ve 2 öneri** var. Bunlar ağırlıklı olarak bağımlılık sürümü önerileri, yerel sayı biçimlendirmesi, kullanılmayan kaynaklar ve KTX/Compose iyileştirmeleri. Bu nedenle “sıfır uyarı” veya tüm optimizasyonların tamamlandığı iddia edilmez.

API 36 mevcut Play hedefini karşılar; lint'in en yeni Android sürümünü öneren `OldTargetApi` uyarısı ayrı konudur. YouTube alan adı projeye ait olmadığı için `AppLinkWarning` uyarısını gidermek amacıyla sahte alan doğrulaması eklenmedi. Bağlantı yapıştırma/paylaşma yolu korunuyor. Bağımlılıklar sürüm numarasını büyütmek amacıyla topluca yükseltilmedi; her güncelleme ayrıca derleme ve cihaz doğrulaması gerektirir.

## İmzalı adayın ölçülmüş sonucu
[CI 113](https://github.com/eekilinc/indirgitsin/actions/runs/33383131109) tüm doğrulamalardan geçti. APK 16,81 MB'dan **4,15 MB**'a indi; aynı Android 14 emülatöründe beş soğuk açılışın ortancası **801 → 609 ms**, tek boşta PSS örneği **54.617 → 33.449 KiB** oldu. İmzalı APK üzerinde altı test ve 1.1.1 üzerine kurulum başarılıdır. [Ayrıntılı kanıt ve sınırlar](VALIDATION.md).

Bu sonuç indirme hızı, gerçek telefon belleği veya pil tüketimi garantisi değildir. 16 KB kontrolü statiktir; gerçek 16 KB ortam testi henüz yoktur. Public final ve mağaza kararı yukarıdaki açık koşullara bağlı kalır.

## Mağaza kararı
Önce içerik hakları ve ürünün kullanım şekli, ardından proje lisansı çözülmeli. Sonra Play Console beyanları, gerçek cihaz testleri ve gerekiyorsa mağaza için ayrı dağıtım tasarımı hazırlanmalı. Mevcut YouTube işlevi bu çalışma kapsamında sessizce kaldırılmadı; **Play Store yayın işlemi yapılmadı**.
