# Changelog

Формат основан на [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), версионирование — [SemVer](https://semver.org/).

## [Unreleased]

### Added
- Репозиторий, GPLv3 LICENSE, README skeleton
- NOTICE с third-party лицензиями
- Документы: threat-model, legal, backend, key-rotation, trust-chain, SECURITY, CONTRIBUTING
- build-tools: build_xray.sh + Dockerfile (gomobile bind для Xray-core)
- GitHub PR/issue templates
- .gitattributes (LF для shell, CRLF для bat)

### Changed
- (пусто)

### Removed
- (пусто)

### Security
- Сформулирована STRIDE threat model
- Описана политика responsible disclosure

---

## Формат будущих записей

```
## [1.0.0] — 2026-XX-XX
### Added
- Первый релиз MVP: ByeDPI-обход, одна кнопка, kill-switch, ...

### Fixed
- ...

### Security
- CVE-YYYY-NNNNN — описание
```

Ссылки на diff (добавить после первого релиза):
<!-- [Unreleased]: https://github.com/thesmithmode/ozero/compare/v1.0.0...HEAD -->
