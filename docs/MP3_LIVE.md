# MP3 ve canlı kayıt — 1.2 adayı

## MP3
Video bağlantısını açıp **Ses** sekmesinden MP3 128/192/320 kbps seçin. Kaynak AAC/Opus ses indirilir, Android codec'i PCM'e çözer, LAME gerçek MPEG Layer III kareleri üretir. Dosya uzantısı değiştirilerek MP3 taklidi yapılmaz. Mono/stereo desteklenir; çok kanallı ve şifreli ses reddedilir. Dönüşüm kayıplıdır ve işlemci/batarya kullanır; 320 kbps düşük kaliteli kaynağı iyileştirmez. Kaynak sesi aynen saklamak için M4A/WebM seçin.

## Canlı yayın
Desteklenen YouTube canlı bağlantısında **Canlı kayıt** görünür. 5/15/30/60 dakika seçilir. Uygulamadan veya bildirimden **Durdur ve kaydet**, tamamlanan parçaları yeniden kodlamadan tek MP4 yapar. **İptal** kaydı siler. En fazla 60 dakika veya 2 GiB; disk alanı azaldığında daha erken sonlanabilir. Geçmiş yayın arşivini değil en yeni tamamlanan parçayı kaydeder; birkaç saniye gecikme normaldir. Durdurma sırasında süren ağ isteğinin zaman aşımı 30 saniyeye kadar beklenebilir.

Yalnızca şifresiz, birleşik AVC/AAC HLS akışları hedeflenir. Ayrı ses listeleri, DASH, DRM ve şifreli HLS desteklenmez. Mikrofon veya ekran kaydı yapılmaz; yeni mikrofon/kamera izni istenmez. Yayın biçimi veya parça sürekliliği değişirse mevcut kesintisiz bölüm kaydedilir.

Ağ veya süreç kesintisinde tamamlanan parçalar özel uygulama alanında tutulur. İndirmeler → **Yeniden dene**, kayıtlı bölümü internet gerektirmeden sonlandırır. Gelecekteki yayın parçaları eski kayda eklenmez. Android’in uygulamayı zorla durdurması kaydı durdurur. Etkin olmayan parçalar mevcut yedi günlük temizliğe tabidir.

## Doğrulama
Birim testleri HLS URL/byte-range/DRM/süre doğrulamasını, master listede birleşik akış seçimini, son canlı parçadan başlamayı, manuel durmayı ve kesintiden kurtarmayı kapsar. Sentetik AVC/AAC TS ve fMP4 dosyaları CI'da FFmpeg ile üretilir. Cihaz testleri bu kayıtların ses/görüntüsünü yeniden çözer, süre ve senkronu denetler. MP3 testleri mono/stereo kaynakların 128/192/320 kbps çıktılarını gerçek codec ile çözer; hatalı çıktıların silindiğini kontrol eder. Bunlar gerçek YouTube erişimi testi değildir.

Telefonda kabul: kısa bir izinli videoyu 192 kbps MP3 olarak kaydedip oynatın; izinli canlı yayını en az bir dakika kaydedip durdurun; çıkan MP4'te ses/görüntü ve süreyi kontrol edin. İkinci canlı kayıtta ağı kesin, hata sonrası yeniden deneyip önceki bölümün kurtarıldığını doğrulayın. Son olarak iptal edilen kaydın kitaplığa eklenmediğini kontrol edin. Bu aday için fiziksel cihaz kabulü henüz yapılmadı.
