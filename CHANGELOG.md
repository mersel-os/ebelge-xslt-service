# Changelog

Bu proje [Semantic Versioning](https://semver.org/) kurallarını takip eder.

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
