# Ekran Görüntüleri

## Yayınlanmamış

### Özel Belge Türü — Geçerli Belge

`custom-document-types.yml` ile tanımlanan `SAMPLE_VOUCHER` türü (`urn:example:sample:voucher-1` namespace'li `Voucher` kökü). Servisin yerleşik olarak tanımadığı kök, tanımla eşleşti; tanımdaki XSD (`Sample-Voucher-1.0.xsd`, ortak şemayı göreceli yolla içe aktarır) ve Schematron (`UBLTR_MAIN`) uygulandı.

![Özel Belge Türü — Geçerli](docs/screenshots/ozel-belge-turu-gecerli.png)

### Özel Belge Türü — XSD Hatası

Zorunlu `cmn:Party` elementi çıkarılmış belge. Hata, tanımdaki XSD'den satır ve sütun bilgisiyle döner.

![Özel Belge Türü — XSD Hatası](docs/screenshots/ozel-belge-turu-xsd-hatasi.png)

### Özel Belge Türü — Schematron Hatası

`currencyID="XYZ"` içeren belge. XSD geçerli; tanımdaki `UBLTR_MAIN` Schematron'unun genel `//@currencyID` kuralı özel türde de çalışır.

![Özel Belge Türü — Schematron Hatası](docs/screenshots/ozel-belge-turu-schematron-hatasi.png)

### Özel Belge Türü — Profil Bastırma Kapsamı

Profil düzenleyicide bastırma kuralının **Kapsam** listesi. Asset dizininde tanımlı özel türler
`GET /v1/admin/custom-document-types` ile alınır ve "Özel belge türleri" grubunda listelenir.
Görüntüdeki `ornek-ozel-tur` profili `GeneralCurrencyIDCheck` kuralını yalnız `SAMPLE_VOUCHER`
belgelerinde bastırıyor: `currencyID="XYZ"` içeren örnek bu profille `validSchematron=true` döner,
bastırılan hata `suppressionInfo` içinde görünür. Uzun tip adları alt çizgilerden kırılır.

![Özel Belge Türü — Kapsam](docs/screenshots/ozel-belge-turu-kapsam.png)

### Özel Belge Türü — XSD Override Tipi

XSD override **Schema Tipi** seçicisinde aynı türler. Aynı profilde `SAMPLE_VOUCHER` için
`cmn:Party` `minOccurs=0` yapıldı; profil kaydedilince override şeması hemen derlenir
(`auto-generated/schema-overrides/SAMPLE_VOUCHER_ornek-ozel-tur_override.xsd`) ve `Party`'si
olmayan örnek bu profille XSD'den geçer.

![Özel Belge Türü — XSD Override Tipi](docs/screenshots/ozel-belge-turu-xsd-override-tipi.png)

---

## v1.1.0

### Özel Schematron Kuralları

Global Schematron kuralları yönetim ekranı. Profil bağımsız, tüm doğrulamalarda otomatik aktif olan özel iş kuralları tanımlanabilir. Örneğin: "Fatura numarası GIB ile başlayamaz" kuralı `OzelKural-01` ID'si ile ekleniyor.

![Global Schematron Kuralları](docs/screenshots/v1.1.0-ek-sematron-kurali.png)

### Doğrulama Profilleri — Ek Kurallar

Profil editöründe "Ek Kurallar" tab'ı. Mevcut bir profili miras alarak (`extends`) profil bazlı Schematron kuralları eklenebilir. Kurallar global kurallarla birleştirilerek ISO Schematron pipeline'a enjekte edilir.

![Profil Editörü — Ek Kurallar](docs/screenshots/profil-ek-sematron.png)

### Doğrulama Profilleri — XSD Override

Profil editöründe "XSD Override" tab'ı. Belge tipine göre XSD element kısıtlamaları (minOccurs, maxOccurs) özelleştirilebilir. Örnekte INVOICE tipi için `cbc:LineCountNumeric` elementi opsiyonel yapılmış.

![Profil Editörü — XSD Override](docs/screenshots/xsd-override.png)

### Özel Kuralın Doğrulamada Çalışması

Eklenen `OzelKural-01` kuralının gerçek bir doğrulamada çalışması. XSD Schema geçerli, Schematron'dan 1 hata dönmüş: "Fatura numarası (cbc:ID) 'GIB' ile başlayamaz." Kural ID (`OzelKural-01`) suppression için kullanılabilir.

![Doğrulama Sonucu](docs/screenshots/validation-example.png)

---

### GİB Paket Sync & Versiyon Geçmişi

GİB Sync tab'ı — Paketlerin staging'e indirilmesi, versiyon geçmişi ve onay/reddetme akışı. Her versiyon zaman damgalı ID ile (ör: `2026-02-17-20-21-49`) saklanır. Dosya değişiklik sayısı ve süre bilgisi görüntülenir.

![GİB Sync & Versiyon Geçmişi](docs/screenshots/validation-asset-sync.png)

### Dosya Diff — Side by Side Görünüm

Onaylanan bir versiyon detayında dosya bazlı diff. Side-by-Side modunda eski ve yeni içerik yan yana gösterilir, karakter bazlı değişiklikler vurgulanır. Örnekte `UBL-TR_Codelist.xml` dosyasında "TEST" ifadesinin eklendiği görülüyor.

![Dosya Diff — Side by Side](docs/screenshots/gib-asset-diff.png)

---

### Varsayılan XSLT Şablonları

"XSLT Şablonları" tab'ı. Her belge tipi (e-Arşiv Fatura, e-İrsaliye, e-MM, e-SMM, e-Fatura, e-İrsaliye Yanıt) için varsayılan XSLT şablonunun durumu listelenir. Mevcut şablonlar için görüntüleme, düzenleme, değiştirme ve silme; eksik şablonlar için yükleme butonları sunulur.

![Varsayılan XSLT Şablonları](docs/screenshots/varsaliyan-xslt-templates.png)

---

### GİB Paket Tanımları & Derlenmiş Dosyalar

"Paketler" tab'ı. Sync edilebilecek GİB paketleri (UBL-TR Şematron, UBL-TR XSD, e-Arşiv, e-Defter) ve pipeline sonrası üretilen auto-generated dosyalar (Schematron XSLT, XSD Override, Özel Schematron Kuralları) listelenir.

![GİB Paket Tanımları](docs/screenshots/gib-paket-listesi.png)

---

### Grafana Dashboard

Prometheus metrikleri ile beslenen Grafana dashboard. Servis durumu, toplam doğrulama/dönüşüm sayısı, hata oranı, rate limit, JVM heap kullanımı, XSD cache, belge tipine göre doğrulama dağılımı ve Schematron tipi dağılımı izlenebilir.

![Grafana Dashboard](docs/screenshots/grafana-dashboard.png)

---

## Yayınlanmamış — Filigran ve Asset Reload

### Filigranlı Önizleme

Filigranlı dönüşümün web arayüzündeki önizlemesi. Diğer ekranlar (kaydırma, PDF sayfaları, `watermark.css` ile özel stil, geçersiz dosya): [Filigran Kullanım Kılavuzu → Ekran görüntüleri](docs/filigran.md#ekran-görüntüleri).

![Filigranlı önizleme](docs/screenshots/filigran-donusum-onizleme.png)

### Asset Reload

Yönetim → GİB Sync sekmesindeki *Asset Reload* kartı. Servisi yeniden başlatmadan asset'leri yükler; her bileşenin durumu, yüklenen sayısı ve hatası satırında görünür. Örnekte üç bileşende hata var: bozuk varsayılan XSLT (kısmi), kapanmayan tırnaklı `validation-profiles.yml` (önceki profiller korunuyor) ve `</style>` içeren `watermark.css`.

![Asset Reload kartında hatalar](docs/screenshots/admin-asset-reload-hata.png)

---

## Görsel Ekleme Rehberi

1. Ekran görüntüsünü alın (PNG veya JPG formatında)
2. Dosyayı `docs/screenshots/` klasörüne kopyalayın
3. Bu dosyaya markdown ile referans ekleyin:
   ```
   ![Açıklama](docs/screenshots/dosya-adi.png)
   ```
4. Commit & push

**Önerilen boyut**: Genişlik 1200-1600px, PNG formatı.
