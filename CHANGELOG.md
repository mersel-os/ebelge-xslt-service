# Changelog

Bu proje [Semantic Versioning](https://semver.org/) kurallarını takip eder.

## [Unreleased]

### Eklenen

- Özel belge türleri: servisin yerleşik olarak tanımadığı kök elementler kod değiştirmeden doğrulanabilir. Asset dizinindeki `custom-document-types.yml` her tanım için kök namespace + yerel ad, asset dizinine göreli ana XSD ve opsiyonel olarak yerleşik bir Schematron tipi alır. Yerleşik tespit önceliklidir; tanınmayan kök bir tanımla eşleşirse `/v1/validate` tanımın XSD'si ve Schematron'u ile doğrular, `detectedDocumentType` tanımın `id` değeridir. Ana XSD'nin göreceli `xs:import`/`xs:include`'ları kendi konumuna göre çözülür; profillerdeki `scope` ve `xsd-overrides` anahtarları tanım adıyla çalışır. Tanımlar `Custom Document Types` adıyla hot-reload'a katılır ve XSD derlemesinden önce yüklenir; hatalı tanımlar (geçersiz ad, yerleşik adla çakışma, aynı kök, kökü yerleşik tespitin zaten tanıdığı — ör. UBL-TR `Invoice` ya da e-Arşiv namespace'indeki herhangi bir kök — ve bu yüzden hiç uygulanmayacak tanım, asset dizini dışına çıkan yol, bilinmeyen alan veya Schematron tipi) raporlanıp atlanır. Dosya okunamazsa ya da yapısı hatalıysa (kök eşleme değil, `document-types` liste değil) reload `FAILED` döner ve önceki tanımlar korunur. Reload sırasında tanımlar XSD'lerden önce yayımlandığı için yeni eklenen ya da değişen bir tanım, XSD bileşeni bitmeden gelen istekte bir kez anında derlenir; doğrulama eski şemayla ya da "şema yüklenemedi" hatasıyla yapılmaz
- `GET /v1/admin/custom-document-types`: yüklü özel belge türlerini (ad, açıklama, kök, XSD, Schematron) döner. Yönetim arayüzündeki profil düzenleyici bu listeyi bastırma kuralı **Kapsam** seçicisinde ve XSD override **Schema Tipi** seçicisinde "Özel belge türleri" grubu olarak gösterir; profilde kayıtlı ama artık tanımlı olmayan bir tür üstü çizili listelenir ki seçim görülüp kaldırılabilsin. Profil kaydedildiğinde özel türlerin XSD override'ları da yerleşik türlerinki gibi hemen derlenir
- `xslt.assets.seed-path` (env: `XSLT_ASSETS_SEED_PATH`): açılışta içeriği asset dizinine aynı göreli yollarla kopyalanan (üzerine yazan) dizin. Kendi imajına gömülen veya salt-okunur bağlanan (ConfigMap, Secret) özel asset'leri — özel belge türleri, varsayılan XSLT şablonları, profiller — yazılabilir asset dizinine taşımak içindir. Seed'de olmayan dosyalara dokunulmaz; nokta ile başlayan yollar (ConfigMap `..data`) ve `auto-generated/` atlanır. Okunamayan girdiler ve bağlantı döngüleri uyarıyla atlanır, açılışı durdurmaz. Asset dizininin kendisi ya da üst dizini seed olarak verilirse seed hiç uygulanmaz (asset dizini her açılışta iç içe kopyalanırdı). Seed kaynak kabul edildiği için seed ile gelen dosyalara (ör. `validation-profiles.yml`, varsayılan şablonlar) admin API veya yönetim arayüzüyle yapılan düzenlemeler sonraki açılışta geri alınır; bu dosyalar seed kaynağında değiştirilmelidir. Asset dizinindeki kopya seed'dekinden farklıysa üzerine yazılırken dosya adıyla uyarı loglanır
- `custom-assets.example/` içinde çalışan bir özel belge türü örneği: `SAMPLE_VOUCHER` tanımı (`urn:example:sample:voucher-1` / `Voucher`), ortak tiplerini göreli yolla import eden küçük bir XSD seti (`custom/sample/`, testlerdeki fikstürle aynı yapı), Schematron olarak `UBLTR_MAIN` ve uydurma verili örnek belge. Mekanizmayı göstermek için yazılmıştır, herhangi bir resmi standarda karşılık gelmez
- `eArsivVeri` kökü için `EARCHIVE_DATA` belge ve şema tipi. Genel e-Arşiv paketi `EArsiv.xsd`'nin yanında aynı namespace'te `eArsivVeri.xsd` (serbest meslek makbuzu verisi) yayımlıyor; servis bu namespace'teki her kökü e-Arşiv raporu sayıp `EArsiv.xsd` ile doğruladığı için `eArsivVeri` belgeleri "Cannot find the declaration of element 'eArsivVeri'" hatası alıyordu. Şema paket sync'iyle zaten indiriliyordu; artık `validator/earchive/schema/eArsivVeri.xsd` ile doğrulanıyor. Paketteki `earsiv_schematron.xsl` bu kök için kural içermediğinden Schematron adımı atlanır. Dosya eski bir sync'te yoksa reload hata saymaz
- `xslt.sandbox.resource-dirs` (env: `XSLT_SANDBOX_RESOURCE_DIRS`): varsayılan XSLT şablonlarının ve Schematron'un (özel kurallar dahil) dosya okuyabileceği mutlak dizinler. Varsayılan boş; boşken davranış değişmez. Dizindeki var olan dosyalar mutlak `file:` URI'siyle `doc()`, `document()`, `unparsed-text*()`, `json-doc()` ve `xsl:include`/`xsl:import` ile okunabilir; dizin dışı, ağ, listeleme, yazma, ortam değişkenleri ve JVM özellikleri kapalı kalır. `/v1/transform` ile gönderilen ve belgeye gömülü XSLT bu istisnayı kullanamaz. Ayar yalnızca başlangıçta okunur; geçersiz değerde uygulama başlamaz. Dizine sonradan eklenen dosyalar yeniden başlatmadan okunur
- Filigran görünümü kod değiştirmeden özelleştirilebiliyor: asset dizinine konan `watermark.css` dahili stilden sonra eklenir. Dosya açılışta okunur; dosya izleyiciyle ve `POST /v1/admin/assets/reload` ile sonradan da uygulanır. UTF-8, en çok 64 KB, `</style` içeremez; geçersizse yok sayılır ve reload sonucunda hata olarak görünür. Satır sayısı `xslt.watermark.rows` ile ayarlanır (env: `XSLT_WATERMARK_ROWS`, varsayılan 200, 1–1000)

### Değişen

- GİB otomatik ilk sync'i artık asset dizininin tamamı değil, GİB paketlerinin yazıldığı `validator/` dizini boşken çalışır. Önceden açılışta konan herhangi bir dosya (örn. `default_transformers/` altına bir şablon ya da `validation-profiles.yml`) ilk sync'i tamamen engelliyor ve servis XSD/Schematron olmadan açılıyordu. **Yükseltme notu:** `validator/` dizini boş olup başka dosyalar içeren kurulumlar (örn. yalnız dönüşüm için şablon ve profil bağlayanlar) artık açılışta GİB paketlerini indirmeye çalışır; ağ erişimi yoksa her açılışta başarısız sync logları ve `xslt_gib_sync_total{status="failure"}` görülür. Asset dizini salt-okunur ve ağ erişimi varsa paketler her açılışta indirilir, ardından yazma başarısız olur. İki durumda da istenmiyorsa `VALIDATION_ASSETS_GIB_AUTO_SYNC=false` verin. Otomatik sync, paketlerin sabit adla yazdığı şablonları (`default_transformers/` altında `eDovizAlim_Base.xslt`, `eDovizSatim_Base.xslt`, `eDekont_Base.xslt`, `eGiderPusulasi_Base.xslt`) hedefte varsa korur; elle tetiklenen sync (`POST /v1/admin/packages/sync`) eskisi gibi GİB kopyasını yazar. `validator/` sembolik bağlantıysa bağlantı izlenir; dizin taranamazsa dolu kabul edilir ve sync atlanır
- e-Arşiv raporu ve e-Döviz Schematron sonuçları (bkz. Düzeltilen): kurallara uyan rapor artık `validSchematron=true` döner; ihlaller tek birleşik hata yerine satır başına ayrı hata olarak gelir (boşluklar tek boşluğa indirgenir, `ruleId`/`test` yine boş). Genel e-Arşiv XSL'i her mesajı satır sonuyla bitirdiği için orada her mesaj ayrı hatadır; e-Döviz XSL'i bazı kuralların mesajını satır sonu olmadan yazdığından bu mesajlar bir sonrakiyle tek hata olarak gelmeye devam eder. **Yükseltme notu:** hata sayısına ya da ilk hatanın metnine bakan istemciler etkilenir. `text` bastırma kuralları artık her hataya (satıra) ayrı uygulanır; birleşik kalan e-Döviz mesajlarında birleşik metinle eşleşir. Önceden birleşik metinle hiç eşleşmeyen `.*ifade.*` kalıpları çalışmaya başlar; sahte hatayı gizlemek için yazılmış `(?s)` ya da `<\?xml` içeren kalıplar artık hiçbir şeyi gizlemez ve gerçek hatalar görünür
- `eArsivVeri` kökü artık `EARCHIVE_REPORT` değil `EARCHIVE_DATA` olarak tespit edilir (bkz. Eklenen). **Yükseltme notu:** bu belgeler için `EARCHIVE_REPORT` kapsamlı bastırma kuralları ve `xsd-overrides.EARCHIVE_REPORT` artık uygulanmaz, `EARCHIVE_DATA` anahtarına taşınmalıdır. Schematron adımı olmadığından profil verilse de `suppressionInfo` boş döner
- `POST /v1/admin/assets/reload` yanıtındaki `components` listesine ilk sırada `Custom Document Types` bileşeni eklendi (tanım dosyası yoksa `OK`, 0 öğe); `eArsivVeri.xsd` yüklü kurulumlarda `XSD Schemas` sayısı bir artar. Bileşenleri sıra numarasıyla okuyan izleme betikleri güncellenmeli. Süren bir reload varken gelen çağrı artık hemen dönmez, onu bekler (bkz. Düzeltilen)

### Düzeltilen

- Profil düzenleyicideki bastırma kuralı **Kapsam** listesinde `EARCHIVE_REPORT_EDOVIZ`, `EARCHIVE_REPORT_EGIDER_PUSULASI` gibi uzun tip adları iki sütunlu hücreye sığmayıp yandaki adın üstüne taşıyordu. Adlar artık alt çizgilerden sonra satır kırar
- Bir asset reload sürerken gelen reload isteği atlanıyordu ("Reload zaten devam ediyor, atlanıyor"). Boş asset dizininde GİB ilk sync'i sırasında dosya izleyicinin başlattığı reload sürerken son paket (`edefter`) yazılıp `syncAll()` reload istediğinde bu istek düşüyor; sync 7/7 başarılı bitse de e-Defter XSD ve Schematron'ları bir sonraki dosya değişikliğine ya da yeniden başlatmaya kadar yüklenmiyordu. Artık eşzamanlı çağrı süren reload'u bekler; kendi isteğinden sonra başlamış bir reload tamamlandıysa onun sonucunu alır, yoksa yeni bir reload çalışır. Aynı anda bekleyen çağrılar tek reload'da birleşir. Bir bileşenin hatası (aşırı derin iç içe bir XSD'nin yol açtığı `StackOverflowError` dahil) yalnız o bileşeni `FAILED` yapar, XSD bileşeninde yalnız o şemayı düşürür; reload başka bir hatayla yarıda kesilirse ona bağlanan bekleyen çağrılar önceki başarılı sonucu değil `FAILED` sonucunu alır ve dosya izleyicinin tetiklediği reload'daki hata loglanır. `POST /v1/admin/assets/reload` ve diğer admin uçları bu durumda "Reload already in progress" yerine gerçek reload sonucunu döner
- Dosya izleyici yalnızca açılışta var olan dizinleri izliyordu: sonradan oluşan dizinler (GİB sync'in oluşturduğu paket dizinleri, sonradan eklenen asset dizinleri) içindeki değişiklikler algılanmıyordu. Yeni dizinler oluştukları anda izlemeye alınıyor; başka ada taşınıp yerine aynı yolda yeni dizin oluşturulan bir dizin (örn. `mv custom/sample custom/sample.bak && mkdir custom/sample`) de yeniden izleniyor
- İzlenen bir alt dizin silinince (GİB sync hedef dizinlerini silip yeniden yazar) dosya izleyici tamamen duruyordu. Artık yalnızca o dizinin kaydı düşüyor; izleme ancak asset dizininin kendisi kaybolursa durur. Olay kuyruğu taştığında (`OVERFLOW`) değişiklik yok sayılmak yerine reload planlanıyor ve oluşturma olayı kaybolmuş olabilecek dizinler için asset dizini taranıp henüz izlenmeyen dizinler izlemeye alınıyor. Sürüm yönetiminin indirme (`staging/`) ve geçmiş (`history/`) dizinleri asset sayılmaz ve izlenmez; sürüm onayı reload'u kendisi tetikler
- `custom-assets.example/validation-profiles.yml` içindeki `my-company` profili tanımlı olmayan `unsigned` profilinden miras alıyordu; dizin olduğu gibi kullanıldığında profil yüklemesi hata veriyordu. README'deki örnekle aynı `unsigned` profili eklendi
- Düz metin üreten ön-derlenmiş Schematron XSL'lerinde (GİB e-Arşiv `earsiv_schematron.xsl`, e-Döviz) sahte hata: hiçbir kural tetiklenmese de serializer'ın yazdığı XML bildirimi ayrıştırılamadığı için `<?xml version="1.0" encoding="UTF-8"?>` metni hata olarak dönüyor ve geçerli raporlar Schematron'dan geçemiyordu; gerçek ihlallerde de tüm mesajlar bildirimle birlikte tek bir hataya birleşiyordu. Bildirim artık atlanıyor, boş çıktı hata sayılmıyor ve düz metin çıktıda her satır ayrı bir hata olarak dönüyor; `text` bastırma kuralları satır başına uygulanabiliyor. XSL'in satır sonu yazmadan bıraktığı mesajlar (e-Döviz paketinde bazı kurallar) ayrılamaz ve tek hata olarak gelir
- Filigran çok sayfalı belgelerde yalnızca ilk sayfada çıkıyordu. Filigran artık belgenin tamamını kaplayan tek bir katman (`#watermark`): satırlar 300 px arayla döşeniyor (200 satır), ekranda katman belgeyle birlikte kayıyor, yazdırmada ve PDF'te her sayfaya basılıyor. Stiller id seçici ve `!important` ile XSLT tasarımındaki stillerden korunuyor; çapraz yazı eski WebKit motorları için `-webkit-transform` ile de veriliyor. Satır başına metin sayısı yine `xslt.watermark.count` ile ayarlanıyor. `<head>` yoksa stil katmanla birlikte body'ye ekleniyor; `</body>` yoksa içerik değiştirilmeden dönüyor
- Yönetim panelinde asset'leri yeniden yükleme düğmesi yoktu: "Asset Reload" kartı sekmeli tasarıma geçişte sayfadan düşmüştü. Kart "GİB Sync" sekmesinin başına geri eklendi. Artık başarısız ya da kısmen yüklenen bileşenlerin hata mesajlarını da gösteriyor; bir bileşen yüklenemezse başarı yerine uyarı bildirimi çıkıyor
- `validation-profiles.yml` ayrıştırılamadığında (YAML sözdizimi ya da yapı hatası) servis yalnız uyarı logu yazıyor, reload sonucunu "başarılı, 0 profil" olarak dönüyor ve bütün profilleri sessizce düşürüyordu. Artık reload sonucu `FAILED` dönüyor; hata satır ve sütun bilgisiyle `errors` içinde raporlanıyor. Önceki geçerli profiller ve global Schematron kuralları korunuyor. Dosyanın olmaması ve boş `profiles:` anahtarı hata sayılmıyor
- `POST /v1/transform` yanıtında `X-Xslt-Custom-Error` ve `X-Xslt-Security-Violations` header'ları, değer ISO-8859-1 dışı bir karakter (ı, ş, ğ, İ, uzun tire gibi) içerdiğinde Tomcat tarafından yanıttan tamamen çıkarılıyordu. "Gömülü XSLT ile dönüşüm başarısız: …" önekiyle başlayan gömülü XSLT hataları ve "Harici script kaynağı engellendi: …" ihlalleri bu yüzden istemciye hiç ulaşmıyordu. Değerler artık ISO-8859-1'e indiriliyor: ü/ç/ö korunuyor, ı/ş/ğ/İ `i`/`s`/`g`/`I`'ya, uzun/kısa tire `-`'ye, tipografik tırnaklar düz tırnağa çevriliyor, karşılığı olmayan karakterler `?` oluyor, kontrol karakterleri boşluk oluyor. Değer yüzde-kodlanmadığı için .NET istemcisi ve web arayüzü değişmeden çalışıyor; yalnız ASCII ve ü/ç/ö içeren mesajlar eskisiyle bayt bayt aynı

### Güvenlik

- Schematron doğrulaması kullanıcı belgesini harici DTD yüklemeyen ve harici entity çözmeyen bir ayrıştırıcıyla okuyor. Önceden Saxon'un varsayılan ayrıştırıcısı DOCTYPE'taki `SYSTEM` entity'lerini (`file:`, `http:` ve dosya adına göre göreli yollar) çözüyordu; değer `<sch:value-of>` ya da profil kurallarındaki `{{xpath}}` yer tutucularıyla hata mesajına taşınabiliyordu. `/v1/validate` belge türü tespiti DOCTYPE içeren belgeleri zaten `400` ile reddettiği için bu yol HTTP üzerinden açık değildi; doğrulayıcı artık o kontrole bağlı değil. Doğrulayıcı düzeyinde iç DTD alt kümesindeki entity'ler çalışmaya devam ediyor ve yüklenen dosyanın adı belgenin `base-uri()` değeri olarak kalıyor
- XSLT dönüşümleri sandbox'landı. Kullanıcının yüklediği XSLT, belgeye gömülü XSLT ve varsayılan şablonlar artık ortam değişkenlerini (`environment-variable()` her zaman boş döner), JVM özelliklerini (`system-property()`), dosya sistemini ve ağı (`unparsed-text`, `unparsed-text-lines`, `unparsed-text-available`, `json-doc`, `collection`, `uri-collection`, `doc`/`document`, `xsl:include`/`xsl:import`; izinli protokol listesi boş) okuyamıyor; `href` içeren `xsl:result-document` derlenmiyor; `transform()` ve `load-xquery-module()` kullanılamıyor. Girdi XML'i ve XSLT harici DTD ve harici entity çözülmeden okunuyor (XXE); aynı kısıt `parse-xml()` için de geçerli. İç DTD alt kümesindeki entity'ler çalışmaya devam ediyor. Özel ya da gömülü XSLT bu kısıtlara takılırsa önceki davranışla aynı şekilde varsayılan XSLT'ye dönülüyor ve neden `X-Xslt-Custom-Error` header'ında bildiriliyor; varsayılan şablonun kendisi takılırsa `422` dönüyor. Önceden varsayılan şablon yolunda URI çözümleme kısıtı yoktu
- Schematron doğrulaması da aynı sandbox'ta çalışıyor. Yönetici API'si ya da doğrulama profilleriyle tanımlanan özel kurallar dahil, derlenen Schematron kuralları ve önceden derlenmiş Schematron XSL'leri ortam değişkenlerine, JVM özelliklerine, dosya sistemine ve ağa erişemiyor. Kural kaydı bu kısıtlar yüzünden reddedilmiyor; engellenen bir erişim doğrulama sırasında `Schematron doğrulama hatası: ...` mesajlı tek bir hata kaydı döndürüyor. GİB paketlerinin `sch:include` çözümlemesi ve standart kuralların sonuçları değişmedi

## [1.4.0] - 2026-10-04

### Eklenen

- GİB arşiv indirme URL'leri artık kod değiştirmeden geçilebilir. GİB bazen paket linklerini geçici olarak değiştirdiği için (örn: `e-FaturaPaketi.zip` yerine `e-FaturaPaketi%20(29).zip`) iki yöntem eklendi: `validation-assets.gib.sync.package-urls.<paket-id>` yapılandırması (env: `GIB_PACKAGE_URL_<PAKET-ID>`) ile kalıcı override ve admin API'de `POST /v1/admin/packages/sync` ile `POST /v1/admin/packages/sync-preview` endpoint'lerinin `url` parametresi ile tek seferlik override. İstek anındaki URL, yapılandırmadaki değere göre önceliklidir; `url` verilmişse `package` parametresi zorunludur. Web arayüzündeki GİB Paket Sync kartında paket seçimi ve özel URL girişi eklendi

## [1.3.2] - 2026-09-08

### Eklenen

- `EDOVIZ_ALIM`, `EDOVIZ_SATIM`, `EDEKONT`, `EGIDER_PUSULASI` XSLT dönüşüm tipleri; ayrıca enum'da bulunup şablon eşlemesi olmayan `ECHECK` (e-Adisyon) tamamlandı ve web arayüzünde eksik olan `ESMM` eklendi. Java API, web arayüzü ve .NET istemci aynı listeyi kullanıyor
- Resmi e-Döviz, e-Dekont ve e-Gider Pusulası görüntüleme şablonları için GİB paket senkronizasyonu; arşiv formatı (ZIP/RAR) URL uzantısına değil magic byte'lara göre belirleniyor
- e-Dekont paketinde XSLT ayrı dosya olarak bulunmadığından resmi örnek XML'in içindeki gömülü şablon çıkarılıp `eDekont_Base.xslt` adıyla kaydediliyor; yalnızca placeholder içeren örnekler atlanıyor
- Paket eşleştirmelerinde sabit hedef dosya adı desteği; bu sayede paylaşılan `default_transformers/` dizinine yazan paketler diğer belge tiplerinin şablonlarını silmiyor
- `eArsivRaporu` raporları için ürün bazlı şema doğrulama: `EARCHIVE_REPORT_EDOVIZ`, `EARCHIVE_REPORT_EDEKONT` ve `EARCHIVE_REPORT_EGIDER_PUSULASI` şema tipleri ile e-Döviz raporu için `EARCHIVE_REPORT_EDOVIZ` Schematron tipi. GİB aynı `eArsivRaporu` kökünü her ürün paketinde farklı bir `eArsiv.xsd` ile yayınladığı için rapor ailesi `baslik`'tan sonraki içerik elemanına (`belge`, `bankReceipt`, `eGiderPusulasi` ve iptal varyantları) göre belirleniyor; tanınmayan içerik genel `EARCHIVE_REPORT` şemasına düşüyor. Şemalar aynı namespace'te aynı kök elementi ve sekiz tip adını farklı tanımlarla içerdiği için birleştirilemez — tek bir `SchemaFactory`'ye verildiklerinde hata üretmeyip sessizce yalnızca ilki kullanılır
- Ürün paketlerinden gelen `eArsiv.xsd` dosyaları, dosya adları aynı olduğu için ayrı dizinlere (`validator/earchive-edoviz/`, `validator/earchive-edekont/`, `validator/earchive-egider-pusulasi/`) senkronize ediliyor. Bu paketler XAdES ve xmldsig şemalarını `xs:import` ile isteyip göndermediğinden imza şemaları genel e-Arşiv paketinin dizininden çözülüyor
- `transformType` için OpenAPI şeması elle yazılan liste yerine doğrudan `TransformType` enum'ından üretiliyor
- `.dockerignore`

### Değişen

- `SchemaValidationType.EARCHIVE` sabiti `EARCHIVE_REPORT` olarak yeniden adlandırıldı. Bu tip e-Arşiv faturasını değil `eArsivRaporu` belgesini doğruluyor; `DocumentType` ve `SchematronValidationType` zaten `EARCHIVE_REPORT` adını kullanıyordu. `validation-profiles.yml` içindeki `xsd-overrides` anahtarları ve bastırma kurallarının `scope` değerleri istek anında düz string karşılaştırmasıyla eşleştiği için eski ad sessizce eşleşmeyi bırakırdı; profil okunurken eski `EARCHIVE` adı otomatik olarak yeni ada çevriliyor ve bir uyarı loglanıyor. Prometheus'taki `schema_type` / `document_type` etiket değeri de değiştiği için bu tipe ait zaman serisi eski adla devam etmez
- `belge`, `bankReceipt` ve `eGiderPusulasi` içerikli `eArsivRaporu` belgeleri daha önce genel `EArsiv.xsd` ile doğrulanıyordu; bu şema söz konusu içerik elemanlarını tanımlamadığı için doğrulama her zaman başarısız oluyordu. Bu raporlar artık ilgili ürün paketinin şemasına yönleniyor. e-Dekont ve e-Gider Pusulası paketleri Schematron göndermediği için o ailelerde Schematron adımı atlanıyor
- GİB paket senkronizasyonundaki tüm dosya eşleştirmeleri artık zorunludur; paket yapısı değişir veya herhangi bir eşleşme bulunamazsa kısmi güncelleme yerine işlem bütünüyle başarısız olur. Tüm eşleşmeler hedef dizine dokunulmadan önce çözümlendiği için başarısız sync mevcut asset'leri bozmaz. Bu davranış mevcut `efatura`, `ubltr-xsd`, `earsiv` ve `edefter` paketleri için de geçerlidir
- Gömülü XSLT çıkarımı, içeriğin gerçekten XSLT olduğunu doğruluyor; geçersizse belge içi şablon yoksayılıp varsayılan şablona dönülüyor (`defaultXslUsed` ile raporlanır). Şablon içindeki dahili DTD entity tanımları kabul edilir, harici DTD/entity çözümlemesi kapalıdır
- `earsiv` paketi `v1.1_6` yerine `v1.1_8` sürümüne bağlandı. `EArsiv.xsd`'ye `YTBTEVKIFAT` ve `YTBTEVKIFATIADE` fatura tipleri eklendi, `earsiv_schematron.xsl`'deki "`ytbBilgileri` zorunlu" kuralı bu iki tipi de kapsıyor ve `esuRaporID` birden fazla kez tekrarlanabiliyor. **Genel e-Arşiv raporlarında doğrulama sıkılaşıyor:** `aliciType` ve `aliciTypeMin` içindeki `tuzelKisi`/`gercekKisi` ikilisi, ikisi de `minOccurs="0"` olan bir `xs:sequence` yerine artık zorunlu bir `xs:choice`. Bu iki tip `fatura`, `mustahsilMakbuz`, `serbestMeslekMakbuz` ve `adisyon` kayıtlarının zorunlu `aliciBilgileri` / `mustahsilBilgileri` alanında kullanıldığı için, alıcı bloğu boş bırakılmış raporlar önceden geçerken artık reddediliyor. GİB'in yürürlükteki kural setini yansıtması için bu sürüm esas alındı; gerekirse mevcut XSD override mekanizmasıyla profil bazında gevşetilebilir. Paketteki XAdES ve xmldsig şemaları `v1.1_6` ile birebir aynı olduğundan ürün paketlerinin bu dizinden çözdüğü imza şemaları etkilenmiyor

### Düzeltilen

- Schematron runtime derleyicisi, `xsl:variable` → `xsl:param` dönüşümünü yalnızca top-level (stylesheet çocuğu) değişkenlere uyguluyor. GİB e-Defter `.sch` dosyalarındaki kural seviyesi lokal değişkenler (örn. `currencyCodeList`) yanlışlıkla `xsl:param` yapılıp "xsl:param must not be preceded by other instructions" hatasına yol açıyordu; çok satırlı açılış tag'leri de doğru işleniyor
- XSD override'lı doğrulama yolunda lokal şema resolver'ı kurulmuyordu; e-Defter/XBRL şemalarının HTTP `xs:import` referansları artık bu yolda da lokal dosyalara yönleniyor
- Göreceli `xs:import` / `xs:include` referansları önce bildiren şemanın kendi konumuna göre, sonra arama dizinlerinde göreceli yolun tamamına göre çözümleniyor; yalnızca dosya adı eşleştirilmediği için farklı alt dizinlerdeki aynı adlı şemalar birbirinin yerine yüklenemiyor
- Dockerfile release JAR'ını `*.jar` joker'i yerine adıyla eşleştiriyor; build context'te kalmış eski JAR'lar image'a alınamıyor
- Ayrı dağıtılan e-Döviz, e-Dekont ve e-Gider Pusulası paketleri sync edilmemişse XSD ve Schematron reload'u bunları hata saymıyor. Aksi halde bu tipler eklendiği için mevcut her kurulum reload sonrası `PARTIAL` durumuna düşüyordu. Atlama yalnızca dosyanın yokluğunda geçerlidir; dosya mevcut olup derlenemiyorsa hata olarak raporlanmaya devam ediyor
- Lokal şema resolver'ı, ayrıştırılamayan HTTP `xs:import` referanslarında istisna fırlatıp şema derlemesini kesmiyor; referans çözümlenemedi sayılıp varsayılan davranışa dönülüyor
- Lokal şema resolver'ı çözdüğü dosyalara sabit `UTF-8` encoding'i dayatmıyor; import edilen şemanın kendi XML bildirimindeki encoding geçerli oluyor

### Güvenlik

- Arşiv çıkarma; girdi sayısı, girdi başına boyut ve toplam açılmış boyut ile sınırlandırıldı. Sınırlar başlıkta beyan edilen boyuta güvenilmeden akış sırasında uygulanıyor
- Path traversal koruması ZIP ve RAR çıkarma yolları arasında paylaşılıyor

## [1.3.1] - 2026-06-07

### Eklenen

#### Çalıştırılabilir JAR Release

Proje artık her sürüm için tek dosyalık, çalıştırılabilir bir `.jar` (Spring Boot fat JAR) üretip GitHub Release'e ekler.

- **Versiyonlu artifact** — Çıktı `mersel-xslt-service-<sürüm>.jar` adıyla üretilir; web arayüzü (UI) JAR içine gömülüdür, tek dosya self-contained çalışır: `java -jar mersel-xslt-service-<sürüm>.jar`
- **Tag'den sürüm besleme** — Build sürümü `-PappVersion` ile dışarıdan geçirilebilir; CI/CD release workflow'u sürümü git tag'inden (`v*`) otomatik alır
- **Otomatik GitHub Release** — `v*` tag push edildiğinde release workflow UI'ı build eder, `.jar` artifact'ını Release'e upload eder (Docker imajı ve .NET NuGet client'ına ek olarak)
- **Lokal release helper** — `scripts/release.sh` ile lokalde sürüm bump (`patch`/`minor`/`major` veya explicit), CHANGELOG finalize, test/build, signed commit + immutable tag işlemleri tek komutla yapılır; push kullanıcıya bırakılır (`--dry-run` desteği ile)

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
