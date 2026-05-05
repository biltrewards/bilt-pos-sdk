# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.7.0] - 2026-05-05

### Added

- Kotlin SDK encryption support and `BiltNexoTerminalClient` with AES-256-CBC + HMAC-SHA256 message protection ([#54](https://github.com/biltrewards/bilt-pos-sdk/pull/54))
- Documentation for Stored Value Cards (gift card balance, activation, deactivation, reload) ([#55](https://github.com/biltrewards/bilt-pos-sdk/pull/55))
- Documentation for card acquisition and gift card flows ([#47](https://github.com/biltrewards/bilt-pos-sdk/pull/47))
- Receipt Requirements documentation with Fiserv EMV guidelines and Bilt Platform fields ([#48](https://github.com/biltrewards/bilt-pos-sdk/pull/48), [#49](https://github.com/biltrewards/bilt-pos-sdk/pull/49))
- Documentation for `ForceEntryMode` in unreferenced refunds ([#44](https://github.com/biltrewards/bilt-pos-sdk/pull/44))
- Documentation for gift card balance fields in payment responses ([#60](https://github.com/biltrewards/bilt-pos-sdk/pull/60))
- Payment status display documentation and home page card ([#61](https://github.com/biltrewards/bilt-pos-sdk/pull/61), [#62](https://github.com/biltrewards/bilt-pos-sdk/pull/62))
- Image element on `LineItem` and Discount support in Display schema ([#36](https://github.com/biltrewards/bilt-pos-sdk/pull/36), [#43](https://github.com/biltrewards/bilt-pos-sdk/pull/43))

### Changed

- Replaced legacy NexoBlob encryption with CMS EnvelopedData for stronger interoperability and key handling ([#53](https://github.com/biltrewards/bilt-pos-sdk/pull/53))
- XSD cleanup: removed banner image element and simplified waiting handling ([#43](https://github.com/biltrewards/bilt-pos-sdk/pull/43))

## [0.5.10] - 2025

### Added

- Documentation for `ForceEntryMode` in unreferenced refunds to restrict card entry methods

## [0.5.9] - 2025

### Fixed

- TaxType element ordering in XSD schema to restore backwards compatibility ([#36](https://github.com/biltrewards/bilt-pos-sdk/pull/36))

## [0.5.8] - 2025

### Added

- Receipt parsing helpers documentation ([#37](https://github.com/biltrewards/bilt-pos-sdk/pull/37))
- CONTRIBUTING.md ([#41](https://github.com/biltrewards/bilt-pos-sdk/pull/41))

### Security

- Bump picomatch from 4.0.3 to 4.0.4 ([#38](https://github.com/biltrewards/bilt-pos-sdk/pull/38))
- Bump brace-expansion in /schema ([#39](https://github.com/biltrewards/bilt-pos-sdk/pull/39))
- Bump handlebars from 4.7.8 to 4.7.9 ([#40](https://github.com/biltrewards/bilt-pos-sdk/pull/40))

## [0.5.7] - 2025

Initial public release. Java and Kotlin libraries for integrating with Bilt payment terminals via the Nexo Sale to POI v3.0 protocol.

### Added

- Nexo Retailer SDK for Android (Kotlin/Java) with 245+ type-safe model classes
- BiltNexoTerminalClient with AES-256-CBC encryption and HMAC-SHA256 authentication
- Support for payments, refunds, reversals, gift card transactions, diagnosis, transaction status, and abort operations
- Display helpers for terminal screens (standby, QR codes, receipts, images)
- Input handling for confirmations, signatures, digit/decimal strings, and menu selections
- Receipt XML parsing helpers and schema
- Display XSD schema with code generation
- CLI tool for testing terminal interactions
- Documentation site with GitHub Pages
- Maven Central publishing
