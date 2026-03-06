# Changelog

Bu proje [Semantic Versioning](https://semver.org/) kurallarını takip eder.

## [1.3.0] - 2026-03-06

### Eklenen

#### HTML Güvenlik Katmanı — No-Exfiltration Sandbox

XSLT dönüşümden çıkan HTML içeriğine otomatik güvenlik sanitization uygulanır. Temel felsefe: **scriptler çalışsın ama dışarıya veri çıkaramasın**. QR kod, barkod, canvas gibi yerel işlemler serbest; cookie, fetch, location gibi exfiltration API'leri engellenir.

Sanitization iki katmanlı savunma sağlar:

1. **HtmlSanitizer (Jsoup)** — HTML DOM üzerinde tehlikeli elementleri ve exfiltration içeren scriptleri temizler
2. **Dinamik CSP** — İzin verilen scriptlerin SHA-256 hash'leri ile `Content-Security-Policy` header'ı oluşturulur; ağ erişimi ve form submit CSP seviyesinde engellenir

##### Kaldırılan Tehlikeli Elementler

HTML çıktısından aşağıdaki elementler ve attribute'lar otomatik olarak kaldırılır:

| Element / Attribute | Açıklama | Saldırı Vektörü |
|---------------------|----------|-----------------|
| `<iframe>` | Gömülü frame | Clickjacking, cross-origin saldırılar |
| `<object>` | Gömülü nesne (Flash, Java vb.) | Zararlı plugin çalıştırma |
| `<embed>` | Gömülü içerik | Zararlı plugin çalıştırma |
| `<applet>` | Java applet | Uzaktan kod çalıştırma |
| `<base>` | Base URL değiştirme | Base URL hijacking ile tüm linkleri ele geçirme |
| `<meta http-equiv="refresh">` | Otomatik yönlendirme | Open redirect, phishing |
| `<link rel="import">` | HTML import | Harici zararlı HTML yükleme |
| `<link rel="preload" as="script">` | Script ön yükleme | Harici zararlı script yükleme |
| `<link rel="modulepreload">` | Modül ön yükleme | Harici zararlı modül yükleme |
| `on*` attribute'lar | Event handler'lar (onclick, onerror, onload, onmouseover vb.) | Inline JavaScript çalıştırma |
| `javascript:` URL | href/src/action'da JavaScript URL | Tıklama ile kod çalıştırma |
| `vbscript:` URL | href/src/action'da VBScript URL | Tıklama ile kod çalıştırma |

##### Korunan (Kaldırılmayan) Elementler

| Element | Açıklama |
|---------|----------|
| `<img src="data:image/...">` | Base64 görseller (QR kod çıktısı) |
| `<img src="https://...">` | Harici görseller (logo, imza vb.) |
| `<style>` | CSS stilleri |
| `<link rel="stylesheet">` | Harici CSS dosyaları |
| Normal `<a href="https://...">` | HTTPS linkleri |

##### Script Exfiltration Analizi

Her `<script>` tag'ının içeriği aşağıdaki exfiltration pattern listesine karşı taranır. **Herhangi biri** tespit edilirse script **kaldırılır** ve ihlal response header'ında raporlanır.

| # | Pattern | Violation Mesajı | Saldırı Senaryosu |
|---|---------|-----------------|---------------------|
| 1 | `document.cookie` | `cookie access` | Cookie çalarak session hijacking |
| 2 | `document.domain` | `domain manipulation` | Same-origin policy bypass |
| 3 | `localStorage` | `localStorage access` | Token/session verisi çalma |
| 4 | `sessionStorage` | `sessionStorage access` | Geçici oturum verisi çalma |
| 5 | `window.location` | `redirect/exfiltration via location` | URL'ye veri ekleyerek redirect ile çalma |
| 6 | `location.href =` | `redirect/exfiltration via location.href` | Redirect ile veri sızdırma |
| 7 | `location.replace(` | `redirect/exfiltration via location.replace` | History'siz redirect ile veri sızdırma |
| 8 | `location.assign(` | `redirect/exfiltration via location.assign` | Redirect ile veri sızdırma |
| 9 | `window.open(` | `window.open exfiltration` | Yeni pencere açarak veri gönderme |
| 10 | `XMLHttpRequest` | `XHR network call` | Ağ üzerinden veri gönderme |
| 11 | `fetch(` | `fetch API network call` | Ağ üzerinden veri gönderme |
| 12 | `navigator.sendBeacon(` | `sendBeacon exfiltration` | Sayfa kapanırken veri gönderme |
| 13 | `new WebSocket(` | `WebSocket connection` | Kalıcı bağlantı ile veri akışı |
| 14 | `postMessage(` | `cross-origin messaging` | Cross-origin pencereler arası veri gönderme |
| 15 | `eval(` | `dynamic code execution (eval)` | Dinamik kod çalıştırma (diğer kontrolleri bypass) |
| 16 | `new Function(` | `dynamic code execution (Function constructor)` | Dinamik kod çalıştırma (diğer kontrolleri bypass) |
| 17 | `import(` | `dynamic module import` | Harici modül yükleme |
| 18 | `alert(` | `UI blocking dialog (alert)` | Kullanıcıyı engelleyen dialog açma |
| 19 | `confirm(` | `UI blocking dialog (confirm)` | Kullanıcıyı engelleyen onay dialogu açma |
| 20 | `prompt(` | `UI blocking dialog (prompt)` | Kullanıcıdan veri isteyen dialog açma |
| 21 | `<script src="...">` | `Harici script kaynağı engellendi: {url}` | Dışarıdan zararlı script yükleme |

> **Not**: Pattern taraması case-insensitive regex ile yapılır. Obfuscation girişimlerinin çoğunu yakalar; ancak CSP ikinci savunma katmanı olarak `connect-src 'none'` ile ağ erişimini tamamen keser.

##### İzin Verilen Script API'leri

Aşağıdaki API'ler exfiltration riski taşımadığı için **engellenmez**:

| API | Kullanım Alanı |
|-----|----------------|
| `getContext`, `toDataURL`, `drawImage` | Canvas API — QR kod/barkod üretimi |
| `getElementById`, `querySelector`, `getElementsBy*` | DOM okuma |
| `createElement`, `appendChild`, `textContent`, `setAttribute` | DOM yazma |
| `innerHTML` | QR kütüphaneleri container'a yazmak için kullanır |
| `document.write` / `document.writeln` | Eski XSLT şablonlarının çıktı yöntemi |
| `Math`, `String`, `Array`, `Date` | Hesaplama ve formatlama |

##### Dinamik CSP Header

Transform yanıtlarında `Content-Security-Policy` header'ı dinamik olarak oluşturulur:

```
Content-Security-Policy:
  default-src 'none';
  script-src 'sha256-{hash1}' 'sha256-{hash2}';
  style-src 'self' 'unsafe-inline';
  img-src 'self' data: https:;
  font-src 'self' data:;
  connect-src 'none';
  form-action 'none';
  frame-src 'none';
  object-src 'none'
```

| Direktif | Değer | Etki |
|----------|-------|------|
| `script-src` | `'sha256-...'` | Sadece hash'i eşleşen scriptler çalışır |
| `connect-src` | `'none'` | fetch, XHR, WebSocket tamamen engel |
| `form-action` | `'none'` | Form submit ile veri gönderme engel |
| `frame-src` | `'none'` | iframe açma engel |
| `object-src` | `'none'` | Plugin yükleme engel |
| `img-src` | `'self' data: https:` | Base64 ve harici görseller serbest |

> Script yoksa `script-src 'none'` olur. `'unsafe-inline'` **asla** kullanılmaz.

##### Yeni Response Header'ları

| Header | Tip | Açıklama |
|--------|-----|----------|
| `X-Xslt-Scripts-Removed` | integer | Güvenlik nedeniyle kaldırılan script sayısı. `0` ise hiçbir script engellenmemiş. |
| `X-Xslt-Security-Violations` | string | Tespit edilen ihlaller (virgülle ayrılmış). Header yoksa ihlal yok. Maks 1000 karakter. |

**Temiz yanıt örneği** (ihlal yok):
```
HTTP/1.1 200 OK
Content-Type: text/html; charset=utf-8
X-Xslt-Scripts-Removed: 0
Content-Security-Policy: default-src 'none'; script-src 'sha256-K7gN...'; ...
```

**İhlal tespit edilen yanıt örneği**:
```
HTTP/1.1 200 OK
Content-Type: text/html; charset=utf-8
X-Xslt-Scripts-Removed: 2
X-Xslt-Security-Violations: Script exfiltration API içeriyor: cookie access, Script exfiltration API içeriyor: fetch API network call
Content-Security-Policy: default-src 'none'; script-src 'sha256-K7gN...'; ...
```

##### .NET Client SDK

`TransformResponse` modeline eklenen property'ler:

| Property | Tip | Açıklama |
|----------|-----|----------|
| `ScriptsRemoved` | `int` | Kaldırılan script sayısı |
| `SecurityViolations` | `IReadOnlyList<string>` | İhlal detay listesi |
| `HasSecurityViolations` | `bool` | Hızlı kontrol: `SecurityViolations.Count > 0` |

```csharp
var result = await xsltClient.TransformAsync(request, ct);

if (result.HasSecurityViolations)
{
    logger.LogWarning(
        "XSLT güvenlik ihlalleri ({Count} script kaldırıldı): {Violations}",
        result.ScriptsRemoved,
        string.Join("; ", result.SecurityViolations));
}
```

### Değişen

- `SecurityHeaderConfig`: Transform endpoint (`POST /v1/transform`) artık kendi dinamik CSP'sini oluşturur; genel filter bu endpoint'i atlar.
- `application.yml`: Varsayılan CSP'de `img-src` genişletildi (`data:` + `https:` eklendi).

### Teknik Detaylar

- Yeni dependency: [Jsoup 1.18.3](https://jsoup.org/) (HTML parsing ve DOM manipülasyonu)
- Yeni sınıflar: `HtmlSanitizer` (infrastructure), `SanitizationResult` (application/models)
- Değişen sınıflar: `SaxonXsltTransformer`, `TransformResult`, `TransformController`, `SecurityHeaderConfig`, `XsltHeaders`
- 27 yeni test (toplam 75+ test)

---

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
