# Kapaklı ses ve tam ekran video

## Ses kapakları

Yeni ses indirmelerinde kaynak videonun küçük resmi, ses aktarımıyla paralel alınır. Görsel alınamazsa indirme yine tamamlanır; oynatıcı müzik simgesi gösterir. Başlık ve kanal/sanatçı bilgisi ses ekranında görünür.

- **MP3:** JPEG kapak, başlık ve sanatçı ID3v2.3 APIC/TIT2/TPE1 alanlarına yazılır. Dosya paylaşılırken kapak da dosyanın içinde gider. Diğer oynatıcıların etiketi gösterme biçimi değişebilir.
- **M4A/WebM:** Özgün ses yeniden kodlanmaz. Kapak uygulamanın özel alanında saklanır ve çevrimdışı gösterilir; bu biçimlere kapak gömülmez. Dosyayı başka uygulamaya paylaşmak, özel alandaki kapağı taşımaz.
- **Eski dosyalar:** Dosyada gömülü kapak varsa okunur. Yoksa müzik görünümü kullanılır; dosya adından tahmin yapılarak yanlış videonun kapağı eklenmez. Yeni indirme MP3 kapağını kalıcı olarak dosyaya ekler.

Kapaklar en fazla 640 piksel ve 512 KiB JPEG olarak saklanır. Ağ cevabı 2 MiB, toplam istek süresi 8 saniye ile sınırlıdır. Özel kapak deposu son kullanılan 256 kaydı tutar; resim verisi en fazla 128 MiB'dir. Uygulama içinden dosya silinince ilgili özel kapak da silinir. Genel önbelleği temizlemek kapakları silmez; uygulamayı kaldırmak veya verisini temizlemek M4A/WebM kapaklarını kaldırır. MP3 içine gömülen kapak bundan etkilenmez.

## Video oynatıcı

İndirilenler'den açılan medya, alt gezinme menüsü olmayan ayrı bir oynatıcı penceresinde gösterilir. **Tam ekran** düğmesi videoyu yataya alır, durum ve gezinme çubuklarını gizler. Kenardan kaydırarak sistem çubukları geçici olarak açılabilir. İlk geri tuşu tam ekrandan çıkar; sonraki geri tuşu oynatıcıyı kapatır. Android 16 büyük ekranlarda/çoklu pencerede yön isteğini yok sayabilir; oynatıcı mevcut pencereye uyum sağlar.

Ekran döndüğünde aynı oynatıcı ve konum korunur. Uygulama arka plana geçtiğinde oynatma duraklatılır; arka planda müzik hizmeti bu sürümün kapsamına dahil değildir. Video oynarken ekran açık tutulur, duraklatınca bu istek kaldırılır. Kulaklık bağlantısının kesilmesi ve ses odağı Android'in medya kurallarıyla yönetilir.

Oynat/duraklat, 10 saniye ileri/geri, kaydırarak konum seçme, 0,5–2× hız ve videoda sığdır/yakınlaştır/doldur vardır. Video sonuna gelince Oynat baştan başlatır. Dosya silinmiş veya desteklenmiyorsa boş ekran yerine hata ve yeniden deneme görünür.

## Kabul kontrolleri

Otomatik testler gerçek MP3'ün gömülü JPEG/Unicode etiketlerini Android medya okuyucusuyla açar ve sesini çözer. Oynatıcı testleri çevrimdışı kapak, kapaksız eski dosya, tam ekran/sistem çubukları, geri dönüş, konumun ekran döndürme/Activity yeniden yaratma sonrasında korunması ve eksik dosya hatasını kapsar. Cihaz ekran görüntüleri CI raporlarına eklenir.

Gerçek telefonda son kontrol: yeni MP3'ü uygulamada ve başka bir müzik oynatıcıda açın; M4A/WebM sesin çevrimdışı kapağını kontrol edin; bir videoyu tam ekrana alın, ileri sarın, ekranı döndürün ve geri tuşuyla çıkın. Otomatik emülatör testleri tüm üretici ve ekran biçimlerini kapsamaz.

Uygulama yaklaşımı: [Android immersive mode](https://developer.android.com/develop/ui/views/layout/immersive), [Media3 player events](https://developer.android.com/media/media3/exoplayer/listening-to-player-events).
