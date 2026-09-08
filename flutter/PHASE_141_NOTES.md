# Phase 141 — l10n recovery (Lane D)

Status: in progress.

## Baseline (measured on `3025d04`)

| Locale | ARB keys | of which `"x-mt": true` |
|---|---:|---:|
| en (template) | 906 | — |
| ar | 851 | 433 |
| es | 886 | 416 |
| fr | 886 | 469 |
| ne | 444 | 25 |
| so | 444 | 25 |

Goal: measure the pool still recoverable from the Kotlin `values-*/strings.xml`,
recover it, and report precisely what is left and why.
