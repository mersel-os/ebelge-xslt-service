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
├── default_transformers/              # Varsayılan HTML dönüşüm XSLT'leri
│   ├── eInvoice_Base.xslt
│   ├── eArchive_Base.xslt
│   ├── eDespatch_Base.xslt
│   ├── eDespatch_Answer_Base.xslt
│   └── eMM_Base.xslt
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

## Önemli Notlar

- `custom-assets/` dizini `.gitignore`'da tanımlıdır — commit edilmez
- Override edilmeyen dosyalar otomatik olarak dahili (bundled) versiyondan yüklenir
- Servis startup'ta hangi dosyaların override edildiğini loglar
- Asset dosyaları GIB paket sync ile otomatik indirilebilir veya manuel olarak ilgili dizinlere kopyalanabilir
- Doğrulama profilleri hot-reload destekler — dosya değişikliği algılandığında otomatik yüklenir
