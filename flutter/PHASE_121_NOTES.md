# Phase 121 — placeholder keys, the class the derivation tool could never reach

*(in progress)*

`tool/arb_from_strings_xml.dart` skips every template value carrying an ICU
placeholder or a printf specifier. Kotlin ships human translations for those
strings in all five locales. This phase measures that class and derives what
can be derived unambiguously.
