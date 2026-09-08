# Phase 141 — l10n recovery (Lane D)

The brief: measure what is still recoverable from the Kotlin `values-*/strings.xml`,
verify the Phase 121 placeholder fix is complete, recover what recovery can reach,
and say precisely what is left and why. **No machine translation was generated.**

## The headline

**The recoverable pool is essentially exhausted, and the reason is a floor rather
than a backlog.** 16 values were recovered (below). The next 38 values a looser
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
- **`fr/storagePdfs`**: the `.arb` carried the English "PDFs"; `values-fr` says
  "PDF".

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
**38 values across the five locales**. I measured what those 38 are before writing
any code, and **almost all of them are degradations**:

- `mySurveys` → the literal token **`mySurveys`** in all five locales, replacing
  Arabic `استطلاعاتي` and French `Mes enquêtes`. Kotlin's `my_survey` is untranslated
  everywhere; `my_library` is `"mylibrary"` everywhere.
- `addedToMyLibrary` (ar) → `تمت الإضافة إلى myLibrary`, an English token spliced
  into an Arabic sentence.
- `myPersonals` (so) → `Dhamaan xogtaaga caafimaadka` — "all your health
  information", a copy-paste error in `values-so`, not a translation of "My personals".
- `logOut` (fr) → `Se déconnecter` replaced by `Déconnexion`: substituting one valid
  translation for another, which the rules forbid outright.

**47 of the Kotlin app's 1055 strings are byte-identical to their English in at
least four of the five locales.** The `my*` compound family is the worst of them,
and it is exactly what a looser tier reaches — because the port deliberately
re-spaced Kotlin's `myLibrary`/`mySurveys` into "My Library"/"My surveys". The port's
English is a *correction* of Kotlin's, and Kotlin's translations inherit the original
defect.

So the ladder's floor is not an oversight. **It is the line past which the Kotlin
data stops being trustworthy**, and the honest report is that these 11 keys are
unrecoverable rather than pending.

Two guards now hold that line, both mutation-tested (each was confirmed to fail when
the code it pins is reverted):

- `isUntranslatedSource` in `tool/arb_from_strings_xml.dart` — rejects a proposal
  equal to the Kotlin string's **own** English where that differs from the template's.
  The existing guard compared against the *template's* English, which is only sound
  while every tier matches English byte-identical to it. It is a no-op today by
  design; it is what keeps the floor safe by construction rather than by luck.
  Pinned in `format_derivation_test.dart`.
- `no locale value is an untranslated Kotlin source string` in
  `placeholder_integrity_test.dart` — the same invariant asserted over the shipped
  `.arb` files, so *any* route into the state fails, not only the one I anticipated.
  **This test found `aiChat` on its first run**, which is the only reason that fix
  is in this phase.

## Reported, not fixed

1. **`app_en.arb`'s three printf keys are a live user-visible bug, not just a
   derivation blocker.** `communityEarnings` and `yourEarnings` declare
   `{amount: int}` while their English is `'Community total earnings: **$%1$d** / 500'`.
   ICU never interpolates `%1$d`, so the generated getter takes the argument and
   drops it:

   ```dart
   String communityEarnings(int amount) {
     return 'Community total earnings: **\$%1\$d** / 500';   // app_localizations_en.dart:2474
   }
   ```

   `lib/ui/components/challenge_dialog.dart:145,146,149,150` calls both. **The
   challenge dialog renders the literal `%1$d` to users in every language, English
   included.** `app_en.arb` is not this lane's file, so this is a report.

   The fix is `%1$d` → `{amount}` and `%1$s` → `{status}`. Note what it does and does
   not unlock: **`perSurvey` then derives in all five locales** (Kotlin's
   `%1$s per survey` matches the template literal exactly). `communityEarnings` and
   `yourEarnings` do **not** — Kotlin writes `/$500`, the template writes `/ 500`, so
   the literals differ and the exact-literal rule correctly refuses. So: 3 rendering
   bugs fixed, 5 human translations unlocked.

2. **Four `x-mt` flags sit on values that are byte-identical to a Kotlin human
   translation, and I deliberately left them.** `ar/profitLoss`, `es/height`,
   `es/weight`, `es/bloodPressure`. Unlike `ar/progressFilterCompleted` (fixed above,
   where the Kotlin English is byte-identical to the template's), these matched at the
   *name-only* tier where the English differs materially — Kotlin's `height` is
   "Height (cm)", the template's is "Height". Byte-identity to a translation of
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
