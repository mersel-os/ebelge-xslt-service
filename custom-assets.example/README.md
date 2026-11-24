# Özel Asset Yapılandırması (Custom Assets)

Bu dizin, XSLT Service'in asset dosyalarını dışarıdan override etmek için örnek yapıyı gösterir.

## Kullanım

1. Bu dizini `custom-assets/` olarak kopyalayın:
   ```bash
   cp -r custom-assets.example custom-assets
   ```

2. Override etmek istediğiniz dosyaları ilgili dizine yerleştirin.
   **Yalnızca override etmek istediğiniz dosyaları koyun** — eksik dosyalar otomatik olarak bundled (dahili) versiyondan yüklenir.

3. Servisi başlatırken environment variable ile yolu belirtin:
   ```bash
   XSLT_ASSETS_EXTERNAL_PATH=./custom-assets java -jar app.jar
   ```

   Veya Docker ile:
   ```bash
   docker run -v $(pwd)/custom-assets:/opt/xslt-assets:ro \
     -e XSLT_ASSETS_EXTERNAL_PATH=/opt/xslt-assets \
     mersel-xslt-service
   ```

## Dizin Yapısı

```
custom-assets/
├── validation-profiles.yml            # Doğrulama profilleri (bastırma kuralları)
├── custom-document-types.yml          # Özel belge türleri (kök → XSD + opsiyonel Schematron)
├── custom/                            # Özel belge türlerinin XSD'leri (GİB sync dokunmaz)
│   └── sample/                        # Uydurma örnek belge türü (SAMPLE_VOUCHER)
│       ├── common/                    # Göreli yolla import edilen ortak tipler
│       ├── maindoc/                   # Sample-Voucher-1.0.xsd
│       └── ornek/                     # Uydurma verili örnek belge
├── watermark.css                      # Filigran görünümü (opsiyonel, örnek: watermark.css.example)
├── default_transformers/              # Varsayılan HTML dönüşüm XSLT'leri
│   ├── eInvoice_Base.xslt
│   ├── eArchive_Base.xslt
│   ├── eDespatch_Base.xslt
│   ├── eDespatch_Answer_Base.xslt
│   ├── eMM_Base.xslt
│   ├── eSMM_Base.xslt
│   ├── eAdisyon_Base.xslt
│   ├── eDovizAlim_Base.xslt
│   ├── eDovizSatim_Base.xslt
│   ├── eDekont_Base.xslt
│   └── eGiderPusulasi_Base.xslt
└── validator/
    ├── ubl-tr-package/                # UBL-TR paket dosyaları
    │   ├── schematron/                # GİB kaynak Schematron XML'leri
    │   └── schema/                    # UBL-TR XSD dosyaları
    │       ├── common/                # Ortak UBL XSD'ler
    │       └── maindoc/               # Belge tipi XSD'ler
    ├── earchive/                      # e-Arşiv dosyaları
    │   ├── schematron/                # earsiv_schematron.xsl
    │   └── schema/                    # e-Arşiv XSD dosyaları
    └── eledger/                       # e-Defter dosyaları
        ├── schematron/                # E-Defter Schematron (.sch) dosyaları
        └── schema/                    # E-Defter XSD dosyaları
```

## Yaygın Kullanım Senaryoları

### Schematron Kurallarını Özelleştirme
GİB'in Schematron kurallarını kendi ihtiyaçlarınıza göre özelleştirmek için:
```bash
cp bundled/validator/ubl-tr-package/schematron/UBL-TR_Main_Schematron.xml \
   custom-assets/validator/ubl-tr-package/schematron/UBL-TR_Main_Schematron.xml
# Dosyayı düzenleyin...
```

### Varsayılan XSLT Şablonunu Değiştirme
Kendi fatura görünümünüzü kullanmak için:
```bash
cp bundled/default_transformers/eInvoice_Base.xslt \
   custom-assets/default_transformers/eInvoice_Base.xslt
# Dosyayı düzenleyin...
```

### Doğrulama Profili Ekleme
Kendi bastırma profillerinizi tanımlamak için:
```bash
cp custom-assets.example/validation-profiles.yml custom-assets/validation-profiles.yml
# Dosyayı düzenleyin — yeni profiller ekleyin veya mevcut olanları override edin
```

Profili doğrulama isteğinde kullanmak için:
```bash
curl -F "source=@fatura.xml" -F "profile=my-company" \
     -F "schemaValidationType=INVOICE" \
     -F "schematronValidationType=UBLTR_MAIN" \
     localhost:8080/v1/validate
```

### Özel Belge Türü Ekleme
Servisin yerleşik olarak tanımadığı bir kök elementi doğrulatmak için XSD setini `custom/`
altına koyun ve `custom-document-types.yml` içinde tanımlayın (alanlar örnek dosyada açıklanmıştır):
```bash
mkdir -p custom-assets/custom/sample
cp -r /yol/sema-seti/* custom-assets/custom/sample/
cp custom-assets.example/custom-document-types.yml custom-assets/custom-document-types.yml
# document-types listesine tanımı ekleyin
```

**Örnek:** `custom-document-types.yml` içindeki `SAMPLE_VOUCHER` tanımı ve `custom/sample/`
altındaki XSD'ler mekanizmayı göstermek için yazılmış uydurma bir belge türüdür; herhangi bir resmi
standarda karşılık gelmez. Ana XSD ortak tipleri `../common/` altından göreli yolla import eder;
Schematron olarak `UBLTR_MAIN` uygulanır (bu kökte yalnız `//@currencyID` gibi genel kurallar tetiklenir).
`SCREENSHOTS.md`'deki özel belge türü görüntüleri bu örnekle aynı şemayla alınmıştır.
Bu dizini asset dizini (ya da seed) olarak verdiğinizde örnek belge doğrudan doğrulanır:
```bash
curl -F "source=@custom-assets.example/custom/sample/ornek/Sample-Voucher.xml" \
     localhost:8080/v1/validate
# → detectedDocumentType: SAMPLE_VOUCHER, validSchema: true, validSchematron: true
```
Schematron adımı için UBL-TR paketi (GİB sync) yüklü olmalıdır; istemiyorsanız tanımdaki
`schematron` satırını silin. Kendi belge türünüzü eklerken örnek tanımı ve `custom/sample/` dizinini silin.

### Filigran Görünümünü Değiştirme
`watermarkText` ile eklenen filigranın rengini, boyutunu ya da açısını değiştirmek için yorumlu
örnek dosyayı kopyalayıp düzenleyin:
```bash
cp custom-assets.example/watermark.css.example custom-assets/watermark.css
# Dosyayı düzenleyin — yalnız değiştirmek istediğiniz kuralları bırakın
```
Örnek dosya `.example` uzantılı olduğu için dizini olduğu gibi kopyalamak filigranı değiştirmez;
etkinleştirmek için adı `watermark.css` olmalıdır. Dosya dahili stilden sonra eklenir; servis
çalışırken eklenen ya da değiştirilen dosya dosya izleyiciyle veya `POST /v1/admin/assets/reload`
ile uygulanır. Seçiciler, tarifler ve kurallar: [Filigran Kullanım Kılavuzu](../docs/filigran.md).

## Önemli Notlar

- `custom-assets/` dizini `.gitignore`'da tanımlıdır — commit edilmez
- Override edilmeyen dosyalar otomatik olarak dahili (bundled) versiyondan yüklenir
- Servis startup'ta hangi dosyaların override edildiğini loglar
- Asset dosyaları GIB paket sync ile otomatik indirilebilir veya manuel olarak ilgili dizinlere kopyalanabilir
- e-Döviz, e-Dekont ve e-Gider Pusulası belgeleri `CreditNote` olarak
  `validator/ubl-tr-package/` altındaki XSD ve Schematron ile doğrulanır
- e-Dekont varsayılan XSLT'si paket sync sırasında resmi örnek XML'in içinden çıkarılır ve `eDekont_Base.xslt` adıyla kaydedilir
- e-Adisyon varsayılan XSLT'si GİB paketinden gelmez; `eAdisyon_Base.xslt` adıyla manuel yüklenmelidir
- Doğrulama profilleri ve `watermark.css` hot-reload destekler — dosya değişikliği algılandığında otomatik yüklenir
