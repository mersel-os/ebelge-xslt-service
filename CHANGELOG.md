# Changelog

Bu proje [Semantic Versioning](https://semver.org/) kurallarını takip eder.

## [1.2.0] - 2026-02-21

### Breaking Changes

#### `ublTrMainSchematronType` Parametresi Kaldırıldı

`/v1/validate` endpoint'indeki `ublTrMainSchematronType` form alanı kaldırıldı. UBL-TR Schematron belge tipi artık genel `parameters` alanı üzerinden gönderilmelidir:

```
Eski: ublTrMainSchematronType=efatura
Yeni: parameters=[{"key":"type","value":"efatura"}]
```

- **API**: `ublTrMainSchematronType` multipart form alanı artık kabul edilmiyor. `type` değeri `parameters` JSON array'i içinde `{"key":"type","value":"..."}` olarak gönderilmelidir.
- **.NET Client SDK**: `ValidationRequest.UblTrMainSchematronType` property'si kaldırıldı. Yerine `Parameters = new[] { new SchematronParameter("type", "efatura") }` kullanılmalıdır.
- `type` parametresi gönderilmezse Schematron XSLT'deki default değer (`efatura`) kullanılır.

> **Not**: `type` parametresi yalnızca `UBL_TR_MAIN` Schematron tipi için geçerlidir. GİB'in UBL-TR Main Schematron'u bu değer üzerinden fatura profillerini kontrol eder. Kabul edilen değerler: `efatura` (e-Fatura) ve `earchive` (e-Arşiv).

### Eklenen

#### Schematron Parametre Desteği — Dinamik Değişken Geçirme

Schematron doğrulama sırasında XSLT'ye özel parametre (`xsl:param`) geçirebilme özelliği eklendi. Custom Schematron kurallarında `$parametre_adi` şeklinde tanımlanan değişkenler artık doğrulama isteği sırasında doldurulabilir.

- **Validate API**: `/v1/validate` endpoint'ine `parameters` alanı eklendi. JSON array formatında key/value çiftleri kabul eder (örn: `[{"key":"sessionBuyerIdentification","value":"1234567890"}]`).
- **Otomatik parametre tanıma**: Custom rule test ifadelerindeki `$variableName` referansları otomatik tespit edilir. `<sch:let>` tanımları Schematron XML'e enjekte edilir ve ISO pipeline sonrası `<xsl:param>`'a dönüştürülür.
- **Tüm Schematron tipleri desteklenir**: UBL-TR Main, e-Arşiv, e-Defter (Yevmiye, Kebir, Berat, Rapor), Envanter — herhangi bir XSLT parametresi gönderilebilir.
- **Web UI**: Doğrulama formuna "Schematron Parametreleri" bölümü eklendi. Açılır/kapanır panel ile dinamik key/value satırları eklenip çıkarılabilir.
- **Güvenlik**: Parametre sayısı 50 ile sınırlıdır. Geçersiz JSON sessizce yok sayılır.

#### Dinamik Hata Mesajları — `{{xpath}}` Placeholder Desteği

Custom Schematron kural mesajlarında `{{xpath_ifadesi}}` placeholder syntax'i ile XML'deki değerleri doğrudan hata mesajına yerleştirebilme özelliği eklendi.

- Mesaj alanında `{{cbc:ID}}`, `{{$parametre}}` gibi XPath ifadeleri kullanılabilir.
- Placeholder'lar derleme sırasında `<sch:value-of select="..."/>` elementlerine dönüştürülür.
- Tek mesajda birden fazla placeholder desteklenir.
- Placeholder içermeyen mesajlar etkilenmez (geriye uyumlu).

**Örnek mesaj:**
```
Satıcı VKN ({{cac:AccountingSupplierParty/cac:Party/cac:PartyIdentification/cbc:ID}}) oturumdaki firma ({{$sessionSupplierIdentification}}) ile eşleşmiyor.
```

**Runtime çıktısı:**
> Satıcı VKN (**9876543210**) oturumdaki firma (**1234567890**) ile eşleşmiyor.

<details>
<summary><strong>Kullanım Örneği: Oturum Bazlı Fatura Sahipliği Kontrolü</strong></summary>

Faturayı gönderen kullanıcının (session) VKN/TCKN'si ile XML'deki satıcı bilgisinin eşleşip eşleşmediğini doğrulama aşamasında kontrol edebilirsiniz. Böylece yanlış firmaya ait faturalar iş katmanına hiç ulaşmadan reddedilebilir.

**1. Admin panelinden global custom rule ekleyin:**

| Alan | Değer |
|------|-------|
| **Schematron Tipi** | `UBL_TR_MAIN` |
| **Context** | `inv:Invoice` |
| **Test** | `cac:AccountingSupplierParty/cac:Party/cac:PartyIdentification/cbc:ID[@schemeID='VKN' or @schemeID='TCKN'] = $sessionSupplierIdentification` |
| **Mesaj** | `Satıcı VKN ({{cac:AccountingSupplierParty/cac:Party/cac:PartyIdentification/cbc:ID[@schemeID='VKN' or @schemeID='TCKN']}}) oturumdaki firma ({{$sessionSupplierIdentification}}) ile eşleşmiyor.` |
| **Flag** | `error` |

Rule kaydedildiğinde:
- `$sessionSupplierIdentification` parametresi otomatik olarak tanınır ve Schematron XML'e `<sch:let>` tanımı enjekte edilir.
- `{{...}}` placeholder'ları `<sch:value-of>` elementlerine dönüştürülür.

**2. Doğrulama isteğinde parametreyi geçirin:**

```
parameters=[{"key":"sessionSupplierIdentification","value":"1234567890"}]
```

**3. Sonuç:** XML'deki `AccountingSupplierParty` VKN/TCKN'si `1234567890` ile eşleşmezse, hata mesajında **her iki değer de** görünür:

> Satıcı VKN (**9876543210**) oturumdaki firma (**1234567890**) ile eşleşmiyor.

Bu yaklaşımla ihtiyacınız olan herhangi bir iş kuralını Schematron'a custom rule olarak ekleyip, gerekli parametreleri runtime'da geçirmeniz yeterlidir. Kod değişikliği gerektirmez.

</details>

#### Derlenmiş Dosya Önizleme

Auto-generated (pipeline çıktısı) dosyalar için VS Code tarzı readonly önizleme özelliği eklendi:

- Dosya tıklanarak Monaco Editor ile syntax-highlighted görüntüleme.
- XML/XSL/XSD desteği, minimap, code folding, satır numaraları.
- Kopyala butonu ile içerik panoya aktarılabilir.
- API: `GET /v1/admin/auto-generated/content?path=...`

#### .NET Client SDK Güncellemesi

- `ValidationRequest` modeline `Parameters` özelliği eklendi (`IReadOnlyCollection<SchematronParameter>`).
- `SchematronParameter` modeli eklendi (`Key`, `Value` çifti).
- Client, parametreleri JSON array olarak serialize ederek multipart form'a ekler.

---

## [1.1.0] - 2026-02-18

### Eklenen

#### Doğrulama Profilleri — XSD Override & Özel Schematron Kuralları

Doğrulama davranışını profil bazında özelleştirme altyapısı eklendi. Artık her profil için:

- **XSD Override**: Belge tipine göre XSD element kısıtlamalarını (`minOccurs`, `maxOccurs`) değiştirebilirsiniz. Override edilen XSD'ler çalışma zamanında derlenir ve cache'lenir.
- **Özel Schematron Kuralları (İkili Katman)**:
  - **Global kurallar**: Tüm profillerde otomatik olarak aktif olan, Schematron tipine göre tanımlanan kurallar. Örneğin "Fatura numarası GIB ile başlayamaz" gibi genel iş kuralları.
  - **Profil bazlı kurallar**: Yalnızca ilgili profil seçildiğinde aktif olan, global kurallara ek olarak uygulanan kurallar.
- Kurallar ISO Schematron pipeline'a (Dispatcher → Abstract → Message) enjekte edilir ve assert ID'si üzerinden suppression desteği sağlar.
- Admin API: `PUT /v1/admin/schematron-rules` (global), profil bazlı kurallar profil kayıt endpoint'i ile yönetilir.
- Web UI: Schematron kural yönetim ekranı (Kurallar tab'ı), profil editöründe kural ekleme/düzenleme, collapse/expand desteği.

> **Ekran görüntüleri**: [docs/screenshots/README.md](docs/screenshots/README.md)

#### Asset Versioning — GİB Paket Güncelleme Geçmişi & Onay Mekanizması

GİB'den indirilen doğrulama dosyalarının (Schematron, XSD vb.) güncelleme geçmişini takip eden ve değişiklikleri onay mekanizması ile kontrol altına alan bir versioning sistemi eklendi:

- **Staging & Onay Akışı**: GİB sync artık dosyaları doğrudan live dizine yazmaz. Önce staging'e indirir, diff oluşturur ve admin onayı bekler. Onay verilmeden değişiklik uygulanmaz.
- **Dosya Bazlı Diff**: Her versiyon için `_before` ve `_after` snapshot'ları saklanır. Eklenen, silinen ve değişen dosyalar açıkça gösterilir.
- **Karakter Bazlı Inline Diff**: Değişen satırlarda yalnızca farklılaşan karakter/kelimeler vurgulanır (prefix/suffix analizi).
- **Side-by-Side & Unified Görünüm**: Diff görüntüleme iki modda sunulur; tam ekran modal ile geniş ekran deneyimi.
- **Suppression Etki Analizi**: Schematron dosyalarındaki kural ID değişiklikleri tespit edilir ve mevcut suppression'ları kırabilecek değişiklikler için uyarı gösterilir (Levenshtein mesafesi ile benzerlik analizi).
- **Versiyon Geçmişi**: Tüm onaylanan/reddedilen versiyonlar zaman damgalı ID ile (yyyy-MM-dd-HH-mm-ss) saklanır ve web arayüzünden incelenebilir.
- API: `POST /v1/admin/packages/sync-preview`, `GET/POST/DELETE /v1/admin/asset-versions/...`
- Kütüphane: `java-diff-utils` eklendi.

> **Ekran görüntüleri**: [docs/screenshots/README.md](docs/screenshots/README.md)

#### Varsayılan XSLT Şablon Yönetimi

Her belge tipi (e-Fatura, e-Arşiv, e-İrsaliye, e-İrsaliye Yanıt, e-MM, e-SMM) için varsayılan XSLT şablonlarını web arayüzünden yönetme özelliği eklendi:

- **Listeleme**: Tüm belge tipleri için şablon durumu (mevcut/yok), dosya boyutu ve son değiştirilme tarihi.
- **Görüntüleme**: Tam ekran modal içinde salt okunur XSLT içeriği.
- **Düzenleme**: Tam ekran editör ile inline XSLT düzenleme ve kaydetme.
- **Yükleme**: Dosya seçici ile `.xslt`/`.xsl`/`.xml` dosyası yükleme.
- **Silme**: Onay diyaloğu ile şablon silme.
- Her işlem sonrası otomatik `assetRegistry.reload()` tetiklenir (derleme + cache yenileme).
- API: `GET/PUT/DELETE /v1/admin/default-xslt/{transformType}`
- Admin sayfasında "XSLT Şablonları" tab'ı olarak eklendi.

> **Ekran görüntüleri**: [docs/screenshots/README.md](docs/screenshots/README.md)

### Değişen

- Admin sayfası tab bazlı yapıya dönüştürüldü (GİB Sync, XSLT Şablonları, Kurallar, Paketler).
- Profil kaydetme ve global kural kaydetme sonrası otomatik `assetRegistry.reload()` eklendi.
- GİB sync akışı staging/onay mekanizması ile değiştirildi (eskisi: direkt live yazma).
- Schematron XSLT pipeline'ında assert ID'si korunacak şekilde düzenleme yapıldı (`iso-schematron-message.xsl`).
- Dark mode'da badge ve diff satırı renkleri düzeltildi.

---

## [1.0.0] - 2026-02-08

### Eklenen

- XML Schema (XSD) doğrulama — 6 belge tipi (Invoice, DespatchAdvice, ReceiptAdvice, CreditNote, FreelancerVoucher, EArchiveReport)
- Schematron doğrulama — 8 tip (UBL-TR Main, EArchive Report, E-Defter Yevmiye/Kebir/Berat + DS varyantları)
- XSLT dönüşüm — 7 belge tipi (Invoice, ArchiveInvoice, DespatchAdvice, ReceiptAdvice, EMM, ECheck)
- Kullanıcı tarafından yüklenen özel XSLT desteği
- Filigran (watermark) desteği
- Schematron derleme pipeline'ı (ISO Schematron → XSLT)
- Prometheus metrikleri + Grafana dashboard
- Docker multi-stage build
- GitHub Actions CI/CD (build, test, Docker image push, GitHub Release)
- Scalar API dokümantasyonu
- Saxon HE sağlık kontrolü
