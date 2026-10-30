# Dizilla (Android WebView sarmalayıcı)

Kişisel kullanım için, adresi ayarlardan değiştirilebilen WebView uygulaması.
Reklam/takip engelleyici, tam ekran video, PiP (telefon) ve Chromecast (telefon) içerir.
Android TV / TV box (ör. Raspberry Pi 5 + LineageOS Android TV) için kumanda desteği vardır.

## Derleme

1. Android Studio'nun güncel sürümünü açın (AGP 9.1.1 / Gradle 9.3.1 gerekir) ve projeyi açın.
2. `Build > Build APK(s)` veya komut satırında: `./gradlew assembleDebug`
3. Çıktı: `app/build/outputs/apk/debug/app-debug.apk`

Özel `debug.keystore` yoksa Android'in varsayılan debug anahtarıyla imzalanır.
Release için `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD` ortam değişkenlerini ve
anahtar takma adı `upload` olan bir keystore kullanın; yoksa imzasız release üretilir.

## Raspberry Pi / Android TV'ye kurulum

- Pi'de geliştirici seçeneklerini ve ADB'yi açın, ardından: `adb connect <pi-ip>:5555` ve `adb install -r app-debug.apk`
- USB bellekle de kurulabilir (Bilinmeyen kaynaklar izni açık olmalı).

## Kumanda (TV modu)

TV modu otomatik algılanır (Ayarlar'dan Açık/Kapalı yapılabilir). TV modunda:

| Tuş | İşlev |
|---|---|
| Yön tuşları | Sanal imleci hareket ettirir (basılı tutunca hızlanır, kenarda sayfa kayar) |
| OK / Enter | Tıklama (basılı tutma = uzun dokunma) |
| Menü / Info | İmleç modu ↔ odak modu |
| Geri | Tam ekran videodan çık → sayfada geri → "Çıkmak için tekrar basın" |
| Tam ekran videoda ◀ ▶ | 10 sn geri / ileri |
| Tam ekran videoda OK | Oynat / duraklat |
| Tam ekran videoda ▲ ▼ | Ses |
| Medya tuşları | Oynat, duraklat, ileri/geri sar |
| Kırmızı (PROG_RED) | Tam ekran aç/kapat |

TV modunda Cast, PiP ve aşağı çekip yenileme kapalıdır; masaüstü görünüm kullanılır.

## Hata ayıklama (Logcat)

`adb logcat -s DizillaApp WebViewInfo NetLog PlayerInfo AdBlock DeviceMode DRM`

- `WebViewInfo`: WebView paketi/sürümü ve kullanılan kullanıcı ajanı
- `DizillaApp`: sayfa yönlendirmeleri, HTTP/SSL/WebView hataları ve ana belge istek özeti
- `NetLog`: her ağ isteği (engellendi/geçti)
- `PlayerInfo`: sayfadaki iframe ve video kaynakları
- URL sorgu parametreleri ve çerez değerleri tanılama kayıtlarına yazılmaz; yalnızca çerez bulunup bulunmadığı kaydedilir.

## APK'yı GitHub'da derletme (bilgisayarda Android Studio olmadan)

1. Projeyi GitHub deponuza yükleyin (`.github/workflows/build-apk.yml` dahil).
2. Depoda **Actions > Build APK > Run workflow** ile çalıştırın (main'e push de tetikler).
3. İş bitince sayfanın altındaki **Artifacts** bölümünden `Dizilla-debug-apk` dosyasını indirin, içinden `app-debug.apk` çıkar.

## İmzalı release APK

İmzalı release APK'yı [buradan indirebilirsiniz](./releases/app-release.apk).
