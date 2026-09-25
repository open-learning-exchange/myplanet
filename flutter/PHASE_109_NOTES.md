# Phase 109 — the l10n placeholder guard, and marking the machine translation

Lane A of the four-lane round. Touches `test/l10n/`, `tool/arb_from_strings_xml.dart`
and `lib/l10n/*.arb` (metadata only — **no translated text changed**).

## 1. The guard: `test/l10n/placeholder_integrity_test.dart`

The external translation pass that filled the five locale files from ~250 keys to ~850
tokenised placeholders before translating and never restored them. 58 strings shipped
broken into the merge: 38 Arabic values carrying a literal `__0____` where a value
belongs (`ratingOutOfFive` → `'التقييم: __0____ من 5'`), and 20 more across all five
locales that simply dropped a placeholder the template declares.

`flutter analyze` caught **one** of the 58, and by accident — a dropped placeholder is a
lint only when `gen-l10n` happens to emit an unused local for it. The other 57 compiled
and analyzed clean. `test/l10n/locale_coverage_test.dart` could not see them either: it
guards the locale set and the key set, and never looks inside a value.

Two checks, over every value in every locale file:

**(a) no tokenisation artefact.** Three named patterns, so the failure says which one
matched: the exact sentinel shape that shipped (`_{2,}\d+_{2,}`), any run of two or more
underscores (a wider net — the next tokeniser will pick a different sentinel, and no
legitimate value in any locale contains one today), and Kotlin's printf specifiers
(`%1$s`/`%1$d`), which ICU never interpolates.

**(b) every placeholder the template declares is present.** The authority is
`app_en.arb`'s `@<key>.placeholders` metadata, **not** a regex over `{...}` in the
English text. That distinction is load-bearing: an ICU plural body is full of braces that
are not placeholders — `{count, plural, =1{one file} other{}}` yields spurious names like
`1`, `No` and `Are` — and the first pass at this analysis reported 54 "missing"
placeholders where only 20 were real. Matching is `{name}` and its ICU-qualified forms
(`{count, plural, …}`), with whitespace allowed inside the braces.

Both collect every defect and assert the whole list is empty, rather than stopping at the
first: a run tells you the full extent of the damage, named as `locale:key`, with the
offending value and the missing placeholder inline. Fixing does not require re-deriving
the analysis.

### Demonstrated catch

Checked out the five locale files as they stood at `c2132ab2f^` — the merge commit before
the repair, i.e. the broken files as harvested — and ran the new test against them:

```
91 value(s) carry a placeholder that was tokenised for translation and never restored:
  ar:questionNumber contains a tokenisation sentinel (__0__) — "سؤال __0____"
  ar:ratingOutOfFive contains a tokenisation sentinel (__0__) — "التقييم: __0____ من 5"
  …
62 value(s) drop a placeholder app_en.arb declares:
  es:fileNotFound drops "fileName" — "Archivo no encontrado localmente"
  …
```

**58 distinct keys flagged — exactly the 58 the repair commit removed, key for key.**
(153 defect lines rather than 58 because the two artefact patterns overlap on the Arabic
values and several keys drop more than one placeholder.) The guard passes on the repaired
files, so it is a catch, not a blanket refusal.

### One thing the guard says that the template will have to answer

`communityEarnings`, `perSurvey` and `yourEarnings` declare ICU placeholders
(`amount`, `status`) while writing their **English** with Kotlin's `%1$d`/`%1$s`:

```json
"perSurvey": "%1$s per survey",
"@perSurvey": { "placeholders": { "status": { "type": "String" } } }
```

`gen-l10n` therefore emits `String perSurvey(String status)` and never interpolates the
argument — the value is dropped in *every* language, English included. 15 of the 58
removals were these three across the five locales. They are absent from all locales now,
so the guard is green; it will fail the moment one is re-added, which is correct, because
no translation of that string can be right until `app_en.arb` says `{status}`.

`app_en.arb` is not this lane's file. **Follow-up for whoever owns it:** convert those
three to ICU placeholders and check their call sites. `tool/arb_from_strings_xml.dart`
now skips printf-carrying template values so a re-run cannot walk the defect into the
locale files in the meantime.

## 2. Marking the machine translation

~560 keys per locale are Google Translate output with no human review, sitting
indistinguishably beside the ~250 Phase 47 strings derived from the Kotlin
`values-*/strings.xml` — translations already shipping in the Android app.

**Mechanism: a per-key ARB attribute, `"@<key>": {"x-mt": true}`, in the locale file.**

Why that rather than a single list at the top of each file:

- It is the ARB spec's own extension shape (`x-`-prefixed resource attributes), so it is
  data `gen-l10n` is meant to ignore rather than something it tolerates by luck. Verified
  on the pinned 3.44.8, including on placeholder-bearing keys, where a locale-side
  metadata block could plausibly have overridden the template's declaration and stripped
  a getter's parameters — it does not, and the generated output is byte-identical before
  and after the marking.
- **A flag cannot drift from the string it describes.** A central list goes stale exactly
  the way Phase 69's regeneration did: a key is deleted and the list still names it, or a
  key is added and nobody updates it — silently, with nothing that reads as data loss.
  Attached to the key, the only remaining drift is an orphaned block, which one of the
  new tests forbids outright.
- Reviewing is then a local edit next to the string being reviewed, so `git diff` shows
  the review itself. A central list would make every approval an edit to one hot region
  of the file.

The split, derived from the key sets immediately before the harvest (`3c503f0`) — no
pre-existing value was altered by the harvest, so key membership is a clean partition:

| locale | machine-translated (unreviewed) | human-reviewed | falls back to English |
|---|---|---|---|
| ar | 557 | 259 | 46 |
| es | 620 | 234 | 8 |
| fr | 594 | 260 | 8 |
| ne | 595 | 259 | 8 |
| so | 595 | 259 | 8 |

Listing them, per locale:

```
dart tool/arb_from_strings_xml.dart --unreviewed        # every locale
dart tool/arb_from_strings_xml.dart --unreviewed fr     # one, as key<TAB>value
```

Four further tests pin the marking: every locale carries some, no flag outlives the
string it marks, the template declares no review state of its own, and the
human-reviewed counts are pinned per locale — so a future harvest that adds translations
without deciding their review state fails here instead of quietly restoring the state
this phase was opened to end.

## 3. The tool preserves it

`tool/arb_from_strings_xml.dart` already carried non-String values verbatim (Phase 69's
`@currentCv` fix), so flags survive a merge. Preserving them is not sufficient, though —
they also have to stay *true*, and the tool is what makes them false:

- a key the tool derives from the Kotlin XML is a human translation already shipping in
  the Android app, so a flag on one is stale. The documented way to refresh a translation
  is *delete the key and re-run*, which leaves precisely that stale flag behind;
- a flag whose key has no value marks nothing, and would attach itself to whatever value
  is written for that key next.

`_reconcileMachineTranslationFlags` drops both cases, keeping any `placeholders`
declaration in the same block and deleting only an emptied one. The run summary now
reports how many strings remain unreviewed and how many flags it cleared, so a run that
strips the marking is visible instead of silent.

Verified end to end on `fr` by seeding all three shapes — a stale flag on a deleted
derivable key (`login`), an orphan naming nothing (`@ghostKey`), and a flag beside a
placeholder declaration (`@currentCv`) — then re-running: `3 flag(s) cleared`, `login`
re-derived and unflagged, `@ghostKey` gone, `@currentCv` left holding its placeholders
and nothing else, and all 594 legitimate flags intact.

### One unrelated defect fixed in the same file

`_unquote` stripped Android's whitespace-preserving quotes but not its backslash escapes,
which the XML parser leaves alone because they are not XML syntax. `values-fr` writes
`Impossible d\'ajouter un dossier de santé.`, and a re-run would have written that
backslash into `app_fr.arb` and onto a French screen. Found by running the tool: it is
one of four keys the XML can now derive into the gaps the repair left. Every escape
Android documents is undone; an unknown one keeps its backslash rather than being
silently eaten.

Those four derivable keys (`myGoalsDescription`, `myPurposeDescription`,
`summaryOfAchievements`, `unableToAddHealthRecord`) are **not** added here — this lane
changes no translated text. They are a free win for whoever runs the tool next.

## Wording for `CLAUDE.md`

This supersedes the "not yet decided" paragraph in the l10n section (the one beginning
"**Current l10n state, and one open decision.**"). Suggested replacement:

> **Current l10n state.** The template `app_en.arb` carries 862 keys; the five locale
> files sit at 816–854, so only 8 keys per locale (46 for Arabic) fall back to English.
> The Phase 47 derivations came from the Kotlin `strings.xml` — real human translations
> already shipping in the Android app — but the ~600 keys added by the 2026-09 harvest
> are Google Translate output with no human review. **The open decision is closed: they
> are marked.** Phase 109 flags each one in its locale file as
> `"@<key>": {"x-mt": true}` — the ARB spec's extension shape, ignored by `gen-l10n`,
> attached to the key so it cannot drift from what it describes the way a central list
> would. `dart tool/arb_from_strings_xml.dart --unreviewed [locale]` lists what still
> needs a human: 557 ar, 620 es, 594 fr, 595 ne, 595 so. The tool clears a flag when it
> derives a human translation over it, and `test/l10n/placeholder_integrity_test.dart`
> fails if a flag orphans or if the human-reviewed count moves without someone saying so.
>
> That same test is the guard the harvest needed and did not have. The translator
> tokenised placeholders and never restored them: 38 Arabic strings shipped a literal
> `__0____` where a value belongs, and 20 more across the locales dropped a placeholder
> the template declares. **`flutter analyze` caught exactly one of the 58**, as an
> incidental `unused_local_variable`. The guard checks every locale value for a
> tokenisation artefact and for every placeholder `app_en.arb`'s
> `@<key>.placeholders` **metadata** declares — metadata, not a regex over `{...}`,
> because an ICU plural body is full of braces that are not placeholders and a naive scan
> reports 54 "missing" where 20 are real. Reconstructed against the pre-repair files it
> flags all 58, key for key. **When a translation and a fact about it have to stay
> together, attach the fact to the key, not to a list.**
>
> Known template defect the guard names: `communityEarnings`, `perSurvey` and
> `yourEarnings` declare ICU placeholders while writing their English with Kotlin's
> `%1$d`/`%1$s`, so `gen-l10n` emits a parameter it never interpolates and the value is
> dropped in every language, English included. They are absent from all five locales
> until `app_en.arb` is corrected.
