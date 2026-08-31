# Katkıda bulunma
Önce mevcut issue'ları kontrol edin. Büyük özellikleri uygulamadan önce kapsamını bir issue'da anlatın. Güvenlik açıklarını [SECURITY.md](SECURITY.md) üzerinden özel bildirin.

```sh
git clone --recurse-submodules https://github.com/eekilinc/indirgitsin.git
cd indirgitsin
./gradlew :app:testDebugUnitTest :app:lintDebug :cipher:library:testDebugUnitTest
node --test cipher/tools/tools.test.mjs
python3 -m unittest discover -s tools -p 'test_*.py'
```

JDK 17, Android SDK 36 ve Gradle'ın istediği build tools gerekir. Android cihaz testleri için `:app:connectedDebugAndroidTest` çalıştırın. Yeni davranış için anlamlı regresyon testi ekleyin; PR açıklamasında neyin değiştiğini ve nasıl doğrulandığını belirtin.

- Sesli video aktarımını, zaman damgalarını ve MediaStore atomik kayıt davranışını koruyun.
- MP3 olarak yeniden adlandırılmış M4A veya doğrulanmamış sessiz video sunmayın.
- CDN adresi, token, keystore, yerel build çıktısı ve kişisel bilgileri commit etmeyin.
- Alt modülü güncellerseniz ilgili commit'i ve lisans değişikliklerini açıkça belirtin.
- Ana proje lisansı henüz seçilmedi: [lisans durumu](docs/LICENSE_STATUS.md). Public erişim, otomatik olarak sınırsız yeniden kullanım izni değildir.
