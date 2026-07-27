# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- `CheckoutSession` — a high-level, stateful checkout API (`com.bilt.pos.session`) on top of `BiltNexoTerminalClient`: basket with three-level tax rules and automatic terminal display, member identification (terminal-prompted and POS-driven), customer input/PIN wrappers, linked/unlinked refunds and voids, an external-display client for routing display/input traffic, and a `pay()` orchestration that sequences rebate redemption → point redemption → stored value → card payment → award with commit tracking, reverse-order rollback, and `onError`-driven retry. All terminal operations are lazy — chains execute via `execute()`/`get()`/`getOrNull()`. New `session` demo command in the CLI and a [CheckoutSession guide](docs/checkout-session.md) in the docs.
- `CheckoutSession` stored value lifecycle (`storedValueBalance`/`Activate`/`Load`/`Unload`/`Deactivate`/`Reserve`/`Reverse`/`Duplicate` with a typed `StoredValueCard`), transaction-status options (receipt reprint, non-payment originals), `playSound`/`stopSound`, `getTotals()`, and `updateInputDisplay()` (nexo `InputUpdate` for in-progress prompts).

### Fixed

- `docs/refund-referenced.md` placed `OriginalPOITransaction` under `PaymentData`; per the nexo schema it belongs under `PaymentTransaction`.

- Network-error recovery for `BiltNexoTerminalClient` (on by default): re-sends an in-flight request under an `X-Bilt-Request-Id` correlation header across transient network failures, with at-most-once execution guaranteed by terminal-side dedupe. Includes a configurable keep-alive ping and HTTP protocol / pong diagnostics, mirrored across the Java and Kotlin clients and the CLI ([#83](https://github.com/biltrewards/bilt-pos-sdk/pull/83))
- Receipt Requirements documentation with Fiserv EMV guidelines and Bilt Platform fields

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
