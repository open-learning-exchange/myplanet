# Phase 141 — l10n recovery (Lane D)

The brief: measure what is still recoverable from the Kotlin `values-*/strings.xml`,
verify the Phase 121 placeholder fix is complete, recover what recovery can reach,
and say precisely what is left and why. **No machine translation was generated.**

## The headline

**The recoverable pool is essentially exhausted, and the reason is a floor rather
than a backlog.** 16 values were recovered (below). The next 42 values a looser
matcher would reach are not a missed opportunity — they are a trap that would have
replaced real translations with English tokens, and the largest single remaining
block is blocked by a defect in `app_en.arb`, which this lane does not own.

## 1. The measurement

Template `app_en.arb` is **906** keys; the Kotlin `values/strings.xml` is **1055**
strings. Final state of the five locale files:

| Locale | ARB keys | `x-mt` | human | absent | value == English |
|---|---:|---:|---:|---:|---:|
| ar | 853 | 432 | 421 | 53 | 36 |
| es | 889 | 416 | 473 | 17 | 48 |
| fr | 888 | 469 | 419 | 18 | 71 |
| ne | 446 | 25 | 421 | 460 | 35 |
| so | 446 | 25 | 421 | 460 | 43 |

**Do not read the last column as a defect count.** For French and Spanish most of
it is correct — "Description", "Date", "Documents", "Format" *are* the French
words. For Arabic and Nepali, 30 of the ~35 are the ICU plural keys plus
`disclaimerContent`, and the rest are endonyms (`spanish` → `español`) or invariant
tokens (`HTML`, `N/A`, `myPlanet`). This column was my own first cut at "the
recoverable pool" and it was wrong by about an order of magnitude; the number that
matters is what a *safe match against the Kotlin XML* can produce, which is the
next section.

### The pool, decomposed by why a key cannot be recovered

Of the keys with no human value in a given locale, matched against the Kotlin XML:

| Bucket | ar | es | fr | ne | so | Recoverable? |
|---|---:|---:|---:|---:|---:|---|
| No Kotlin counterpart at all (port-minted UI text) | 433 | 389 | 434 | 433 | 433 | **No** — nothing to derive from |
| Kotlin name matches, English differs (`report` tier) | 53 | 47 | 53 | 53 | 53 | **No** — needs an `app_en.arb` judgement |
| Exact/punctuation English match | 12 | 21 | 30 | 11 | 19 | mostly already correct; 16 taken |

The first row is the real answer to "how much is left to recover": **~390–434 keys
per locale have no Kotlin string to recover from.** They are the port's own screens
— `videoFileNotFound`, `exportCancelled`, the resource-viewer error states. Only a
translator can fill them.

## 2. What was recovered — 16 values

All by re-running the existing tool; no new matching rule was needed.

- **`takeTestCount`, `retakeTestCount`, `redoSurvey` × 5 locales = 15.** Template
  keys added after Phase 121's derivation run; Kotlin's `take_test`, `retake_test`
  and `redo_survey` ship human translations in all five `values-*` files. Nobody
  re-ran the tool after the keys landed. *(This is the recurring one — Phase 130
  found the same shape with `playbackSpeed`. Re-running the tool after any template
  key is added costs nothing and is not automated.)*
- **`fr/storagePdfs`**: the `.arb` carried the English "PDFs", and French supplies
  "PDF". Note the provenance, because my first write-up got it wrong:
  `values-fr/storage_pdfs` is *itself* untranslated ("PDFs"); the value comes from
  the sibling `filter_pdfs`, a different Kotlin key with the same English, whose
  French is "PDF". The untranslated namesake is dropped by the
  `proposal == templateValue` rule, leaving `filter_pdfs` as the unanimous
  candidate. The recovery is sound — a French human wrote "PDF" for that exact
  English — but it is not the namesake key.

Plus one marking-only fix and one deletion, neither of which changes a rendered
string:

- **`ar/progressFilterCompleted`** lost a stale `x-mt` flag. It already held
  `status_completed`'s Arabic (`مكتمل`) word for word, but the recovery pass tested
  unanimity *before* it tested whether the value was already one of the candidates,
  and Kotlin's `completed`/`status_completed` disagree in Arabic — so the key was
  ruled `no-unanimous` and never reached flag reconciliation. Fixed by ordering the
  verdict cascade correctly.
- **`aiChat` dropped from ar/fr/ne/so.** All four held the English "AI Chat" as an
  unflagged *human* translation, because `values-ar/-fr/-ne/-so` leave Kotlin's
  `ai_chat` untranslated. Spanish is the only locale that rendered it ("Chat IA"),
  which is what makes the other four a gap rather than a deliberate invariant. Now
  falls back to the template; on screen that is one capital letter, and the key
  rejoins the review queue instead of inflating the human-reviewed count.

## 3. Question 2 — is anything still being skipped structurally?

Phase 121's placeholder fix is **complete and correct**. The format layer derives
22–27 human translations per locale that no plain-text rule could reach. Four
structural exclusions remain, three of them legitimate:

| Skipped | Count | Verdict |
|---|---:|---|
| ICU plural/select template keys | 23 | **Legitimate.** Android `<plurals>` do exist (4 names) but none matches a template key by English — Kotlin's `minutes` is "%d minutes", the template's `relativeMinutesAgo` is "{count} minutes ago". Filling `other` and leaving `=0`/`=1` in English is worse than the fallback. |
| Literal with no word (`ratingCompact`, `percentageValue`, `userNameWithLogins`) | 3 | **Legitimate.** `%1$s (%2$s)` is punctuation; it matches any key of that shape. |
| `<plurals>` elements never read by `_readStringsXml` | 4 names | **Legitimate**, per the row above — and reading them would be *actively unsafe*: `values-ne`'s `minutes`/`hours`/`days` contain **Somali** (`%d daqiiqad`) and `values-so`'s contain **Nepali** (`%d मिनेट`). The two files are swapped upstream. |
| **printf in the template** (`communityEarnings`, `yourEarnings`, `perSurvey`) | 3 | **A defect — see below.** |

Phase 121's headline reordering case is worth a correction: the four Kotlin strings
whose translations reorder arguments (`ne/download_progress`,
`ne/download_progress_with_errors`, `ne/steps_done_of_total`, `ar/member_description`)
have **no matching template key**, so the reordering machinery is currently
unexercised by shipped data. It is still right, and `format_derivation_test.dart`
pins it directly as a unit test — which is the correct place for it.

## 4. The trap: why the tier ladder stops where it does

11 template keys match the Kotlin XML *only* under internal-whitespace
normalisation — one notch below the `casing` tier. Adding that tier would adopt
**42 values across the five locales** (ar 5, es 6, fr 9, ne 11, so 11). My first
count said 38; it missed `usernameOnlyLettersNumbers` in ar/fr/ne/so, which does
match (an internal space before a comma) and does substitute one valid translation
for another. The error under-counted the damage, so it argued the point weakly
rather than wrongly. I measured them before writing any code, and **almost all are
degradations**:

- `mySurveys` → the literal token **`mySurveys`** in ar/fr/ne/so, replacing Arabic
  `استطلاعاتي` and French `Mes enquêtes`. **Spanish is the exception** and translates
  both `my_survey` (`misEncuestas`) and `my_library` (`miBiblioteca`) — an earlier
  draft of this file said "all five", which is wrong and understates how the guard
  has to work: it must pass Spanish while refusing the other four. In ne/so there is
  no existing value at all, so the tier would *add* an English token rather than
  overwrite a translation. And the tool writes the case-aligned form (`MySurveys`),
  not the bare token.
- `addedToMyLibrary` (ar) → `تمت الإضافة إلى myLibrary`, an English token spliced
  into an Arabic sentence.
- `myPersonals` (so) → `Dhamaan xogtaaga caafimaadka` — "all your health
  information", a copy-paste error in `values-so`, not a translation of "My personals".
- `logOut` (fr) → `Se déconnecter` replaced by `Déconnexion`: substituting one valid
  translation for another, which the rules forbid outright.

**47 of the Kotlin app's 1055 translatable strings are byte-identical to their
English in at least four of the five locales** (1055 excludes the one
`translatable="false"` entry; the raw element count is 1056). The `my*` compound family is the worst of them,
and it is exactly what a looser tier reaches — because the port deliberately
re-spaced Kotlin's `myLibrary`/`mySurveys` into "My Library"/"My surveys". The port's
English is a *correction* of Kotlin's, and Kotlin's translations inherit the original
defect.

So the ladder's floor is not an oversight. **It is the line past which the Kotlin
data stops being trustworthy**, and the honest report is that these 11 keys are
unrecoverable rather than pending.

Two guards hold that line. **Both were wrong in their first cut, and an audit pass
against my own finished work is what found it** — the unit test pinning them passed
because its fixture fabricated an input the pipeline cannot produce, which is the
exact "a fixture that fabricates a join is not evidence" shape this project keeps
relearning.

- **`isUntranslatedSource` in `tool/arb_from_strings_xml.dart` — the guard that
  matters.** It refuses a Kotlin string whose locale value is still its own English,
  where that English differs from the template's. The first cut judged
  `_proposal`'s *output*, and `_proposal` runs `_alignInitialCase` for every
  non-`exact` tier — so `values-ar`'s `mySurveys` arrived as `MySurveys` and no
  longer equalled the `mySurveys` it is a copy of. **It returned false on both
  examples its own comment named**, and simulating the whitespace tier with the
  guard on and off gave 42 adoptions either way: it blocked nothing. It now judges
  the raw `values-<locale>` string, which is where the question is actually
  answerable — "did this translator leave the source in place" is a fact about the
  XML, not about our rendering of it. Verified firing on `mySurveys`/`mylibrary`,
  correctly passing Spanish's `misEncuestas` and the invariant `HTML`, and pinned
  against the real XML rather than hand-made inputs.
- **`no locale value is an untranslated Kotlin source string` in
  `placeholder_integrity_test.dart` — the backstop.** It found `aiChat` on its first
  run, but that was also *the only thing it could ever have found*: it keyed on
  `_camelCase`, which maps `my_survey` to `mySurvey` (not a template key), and read
  the XML with a regex that left Android's quoting on, so `my_library` came back as
  `"mylibrary"` with quotes and could never match. It now parses the XML properly
  (which also fixes three silent misparses — a self-closing `<string/>` was
  swallowing the next element entirely, so `message_placeholder` appeared nowhere in
  the map) and matches loosely enough to reach the `my*` family.
  **Its limit, stated rather than papered over:** the comparison is byte-exact, so it
  catches a value that *is* the source but not the case-aligned form the tool would
  write. It cannot: at the ARB level "the untranslated source" and "the port's own
  English" are the same string modulo case and spacing for exactly this class,
  because the port's English *is* a re-spacing of Kotlin's. Loosening it to reach
  `Mylibrary` also flags `appTitle`. Only the tool sees what a proposal would
  *overwrite*, which is where the damage is.

## Reported, not fixed

1. **`app_en.arb`'s three printf keys render their own format specifier.**
   `communityEarnings` and `yourEarnings` declare `{amount: int}` while their
   English is `'Community total earnings: **$%1$d** / 500'`. ICU never interpolates
   `%1$d`, so the generated getter takes the argument and drops it:

   ```dart
   String communityEarnings(int amount) {
     return 'Community total earnings: **\$%1\$d** / 500';   // app_localizations_en.dart:2474
   }
   ```

   Kotlin renders these correctly (`ChallengePrompter.kt:41,55` passes the argument
   through `getString`), so it is a genuine parity defect, in every language
   including English.

   **It is not, however, "live", and an earlier draft of this file said it was.**
   The only caller is `lib/ui/components/challenge_dialog.dart:145,146,149,150`, and
   `lib/providers/challenge_provider.dart:30-31,55-60` gates that dialog on
   `promptStart = 2024-11-30` / `promptEnd = 2025-01-16` plus one of six hardcoded
   server URLs. **That window closed 20 months ago**, so no user sees this today.
   The accurate statement is *a latent rendering defect on a currently unreachable
   screen* — and calling it live was the reachability over-read this project has now
   been caught by three times, in the same direction each time. `app_en.arb` is not
   this lane's file either way.

   The fix is `%1$d` → `{amount}` and `%1$s` → `{status}`. What it unlocks is
   smaller than I first wrote: **`perSurvey` would derive in all five locales**
   (Kotlin's `%1$s per survey` matches the template literal exactly), but
   `perSurvey` **has zero callers in `lib/`**, so those five values would render
   nowhere. `communityEarnings`/`yourEarnings` do **not** derive at all — Kotlin
   writes `/$500`, the template `/ 500`, so the literals differ and the
   exact-literal rule correctly refuses. So: 3 rendering defects corrected, 0
   translations reaching a screen.

2. **Four `x-mt` flags sit on values that are byte-identical to a Kotlin human
   translation, and I deliberately left them.** `ar/profitLoss`, `es/height`,
   `es/weight`, `es/bloodPressure`. Unlike `ar/progressFilterCompleted` (fixed above,
   where the Kotlin English is byte-identical to the template's), three of these matched at the
   *name-only* tier where the English differs materially — Kotlin's `height` is
   "Height (cm)", the template's is "Height". (`ar/profitLoss` is not one of them:
   `Profit/Loss` against `Profit / loss` differs only in spacing and case, so it is
   one of the 11 below-floor keys of §4. The conservative decision stands; the
   rationale I gave fits the other three.) Byte-identity to a translation of
   *different* English is weak evidence: "Altura" is simply the obvious translation
   of "Height", so the machine and the human agreeing proves the string is *right*,
   not that it was *reviewed*. A flag wrongly cleared hides a string from review
   permanently; a flag left on a correct string costs a reviewer seconds. I took the
   asymmetric side. A human reviewer can clear all four in a minute.

3. **`values-ne` and `values-so` have their `minutes`/`hours`/`days` `<plurals>`
   swapped** — Nepali contains Somali and vice versa. This is a bug in the Kotlin
   app (`app/`), outside both this lane and the port. It costs the port nothing today
   because those plurals are unreachable (§3), but it will mislead anyone who tries to
   derive from them later.

4. **English is stored *as a value* for ~30 keys each in ar and ne** — mostly the ICU
   plural family (`itemsSynced`, `relativeMinutesAgo`, `syncedResources`, …) plus
   `noFileSelected`, `surveySentToUsers`, `fileSelected`, `feedbackTypeRequired`,
   `receiptSelected`, `storageDeleteSelectedConfirm`. They render identically to a
   fallback, so there is no user-visible defect — but `gen-l10n`'s untranslated count
   (ar 53, ne 460) **understates** the real gap by roughly 26 per locale, and each key
   is counted as human-reviewed. Deleting them is the Phase 118 move and would be
   correct; I did not, because separating them from the legitimately invariant values
   (endonyms, `HTML`, `disclaimerContent`) is a per-key judgement rather than a
   derivation, and that judgement is precisely the human pass the l10n row needs. The
   list is one `json.load` away — see the query in §1.

5. **Nothing automatically re-runs the derivation when a template key is added.**
   15 of this phase's 16 recovered values existed in the Kotlin XML the whole time
   and were missed only because Phases 122–140 added template keys without re-running
   the tool. Phase 130 hit the identical shape. A CI step, or a test asserting the
   tool produces no diff, would close it permanently — but it belongs to whoever owns
   `flutter.yml`, not to this lane.

## What the audit changed

A `parity-auditor` pass at `effort: max` was run against this phase's own finished,
already-green work, as the round requires. It found that **both guards were inert**
— each failing on the exact example its own doc comment named — plus five
measurement overstatements (§2 `storagePdfs` provenance, §4 "all five locales" and
"38 values", the 1055/1056 denominator, and the "live bug" over-read above). None of
it was a defect in the shipped translation data, which the audit reproduced
byte-identically from the pre-141 state; all of it was in the guards and the claims.

That is the second time in this phase the same mistake shape appeared: **a green
test whose fixture fabricated the input.** The unit test pinning
`isUntranslatedSource` passed `proposal: 'mySurveys'`, a value `_proposal` cannot
emit, so it certified a guard that never fired. The tests now drive the real
`values-*/strings.xml`. Worth stating plainly because I had already written the
"fixtures that fabricate a join are not evidence" rule into this very file's
reasoning before walking into it.

## The gate

```
flutter gen-l10n                                        ✅  ar 53 / es 17 / fr 18 / ne 460 / so 460 untranslated
dart format --output=none --set-exit-if-changed lib test tool   ✅  468 files, 0 changed
flutter analyze                                         ✅  No issues found
flutter test                                            ✅  2462 passed (was 2457; +5 new l10n guards)
```

`dart tool/arb_from_strings_xml.dart` then `--adopt` then the plain run again
produces **no further change** — idempotence verified by checksum across three runs,
including after the `aiChat` deletion, which the tool correctly does not re-add.
