# Phase 121 — the placeholder keys, a class the derivation tool could never reach

Phase 117 handed over one key. It is a class, and this phase measures it before
changing anything, because the number is the deliverable.

## The size of the class

| | |
|---|---|
| Kotlin strings in `values/strings.xml` | 1052 |
| …carrying a format specifier (`%s`, `%1$s`, `%d`, `%.1f`) | **92** |
| ARB template message keys | 873 |
| …declaring an ICU placeholder | 76 |
| …of which plural/select bodies | 23 |
| …of which simple `{name}` messages | 53 |
| simple messages whose skeleton matches a Kotlin format string | 11 |
| …rejected because the skeleton carries no word | 3 |
| **derived** | **8 keys × 5 locales = 40 values** |

Every one of the 92 has a human translation shipping in all five locales of the
Android app. `tool/arb_from_strings_xml.dart` skipped any template value
containing `{`, so it could never read one of them.

**Only 8 keys came back, and that is the honest answer, not a shortfall.** The
class is 92 Kotlin strings wide but the port has minted its own English for most
of what it renders: of the 53 simple placeholder messages in `app_en.arb`, 42
have no Kotlin counterpart with the same words at all (`Syncing {completed} of
{total} areas`, `Rate {title}`, `Year must be between {min} and {max}` — port
screens Kotlin does not have, or port phrasing of screens it does). Those are a
`nameOnly`/`containment` judgement about what a screen should say, which is
Phase 114's line and still not a derivation. Recovering them means editing
`app_en.arb` first.

## The conversion, and why it is index-aware rather than positional

Android numbers its arguments and lets a translator move them; ICU names them.
The tool now reduces both notations to a `_FormatString` — literal segments plus
the ordered ids of the holes between them — matches on the literal, zips the two
English hole lists to get **argument index → placeholder name**, and applies
that map to wherever the *translation's* holes fall.

A left-to-right substitution reads correctly on every string whose translation
keeps the English order, and silently prints the wrong value into a sentence on
the ones that do not. Four Kotlin strings reorder:

```
ne/download_progress             %2$d मध्ये %1$d फाइलहरू डाउनलोड भएका छन्
ne/download_progress_with_errors %2$d मध्ये %1$d फाइलहरू … (केही त्रुटिहरूसहित)
ne/steps_done_of_total           %2$d मध्ये %1$d सम्पन्न
ar/member_description            (‎%2$d زيارة) ‎%1$s
```

`format_derivation_test.dart` drives the first of these through the real XML:
positional substitution yields `{completed} मध्ये {total}`, i.e. a device three
files into eight reading "eight of three"; the index-aware conversion yields
`{total} मध्ये {completed}`. A second test runs the Spanish of the same string,
which keeps the English order, so the first cannot pass by reversing everything.

**One correction to Phase 117.** Its note said `values-ne:1010`
(`course_progress`) reorders its placeholders. It does not — `%1$s को %2$s प्रगति`
keeps ascending index order; it moves the placeholders relative to the *words*,
which any substitution handles. The claim that the conversion must be
index-aware is right and the named instance is not the evidence for it. The four
strings above are.

### `courseProgressCount`, before and after

| | before | after |
|---|---|---|
| ar | *absent → "Progress 3 of 8"* | `التقدم {current} من {max}` |
| es | *absent → "Progress 3 of 8"* | `Progreso {current} de {max}` |
| fr | *absent → "Progress 3 of 8"* | `Progression {current} sur {max}` |
| ne | *absent → "Progress 3 of 8"* | `{current} को {max} प्रगति` |
| so | *absent → "Progress 3 of 8"* | `Habka {current} ee {max}` |

All eight derived keys were absent in every locale that got one, so the value
each replaces is the English fallback — nothing was overwritten except values
flagged `x-mt` or holding the English template verbatim.

The eight: `appVersion`, `courseProgressCount`, `currentCv`, `errorOccurred`,
`fileCountMany`, `fileNotFound`, `reportDateDetails`, `storageSelectedCount`.
Plus `stepsHeading`, Phase 117's third key, which needed no format layer — it is
plain text and the existing English-match rule picked it up once it was asked.

## The guards, and the one that earned its place

Four rules reject, in order. Three are obvious in hindsight; the third is not.

* **Plurals and selects are out of scope.** A Kotlin `%d` string is one
  sentence, `{count, plural, =0{…} =1{…} other{…}}` is three. Filling `other`
  from the Kotlin and leaving `=0`/`=1` in English would put two languages
  inside one rendered string — worse than the English fallback. 23 keys.
* **An unsupported conversion rejects the whole string.** `%.1f` says "one
  decimal place" and `{value}` does not. One Kotlin string (`float_placeholder`).
* **A skeleton with no letter in it is not evidence.** This is the one that
  matters. `%1$s (%2$s)` is punctuation: byte-identical in all five locales, so
  deriving from it adds a value that says nothing — and it matches *any* key of
  that shape. `ratingCompact` ("{average} ({count})") matched Kotlin's
  `user_name`, which is a person's name beside their login count. Same
  punctuation, different words, and no test in this tree could have caught the
  swap because the rendered string is identical in English. Requiring a letter
  throws the wrong match out along with the two useless ones
  (`userNameWithLogins`, `percentageValue` — `%s%%` in every locale, which is
  the template already).
* **Every declared placeholder must survive**, which is
  `placeholder_integrity_test.dart`'s rule applied at the source.

Matching is **exact skeleton only** — no punctuation or casing tier. Those tiers
strip a value's trailing punctuation and give the template's back, which is not
safe next to a hole, and nothing in the corpus matches at them anyway.

## Phase 118's residue: 42 → 26

Two rules, both narrow, close 16 of the 42.

**A namesake breaks a tie.** Where two Kotlin names share one English text and
disagree in translation, the tool reported `no-unanimous` and stopped. But a
candidate whose name camel-cases to the ARB key is the same string identified
*twice* — by its words and by its name — and that is more evidence, not less.
The `--adopt` header already said to "prefer the name whose usage site matches
the ARB key's"; nothing implemented it. It settles 13 of the 25: `addResource`
takes `add_resource` over `add_res`; `joinRequests` and `notifGroupJoinRequests`
share both their candidates and each picks its own namesake. **4 remain**, all
`progressFilterCompleted` — `completed` vs `status_completed`, genuinely no name
to break the tie.

**A locale entry that is still the English is not a translation.** Phase 118
wrote that rule down as advice for a reader; the tool did not have it. With the
namesake rule in, it stopped being advice: `values-so` renders `settings` as
"Settings", and that string is the namesake of the ARB key `settings`, so it
would have won the tie against the real Somali `Goobooyinka` the moment anyone
flagged that key. Proposals byte-identical to the English template are now
dropped before any tie-break runs.

**Whitespace class is not a difference worth adopting.** `app_fr.arb` writes
`Rapport créé le\u{a0}: {created}` where `values-fr` writes an ordinary space.
French typography wants the no-break space; the words are identical. There is no
translation to gain and a nicety to lose, so "same words, different space
character" is now `already` rather than `apply`, and a test pins the French
value.

### What is left, and why

* **22 `keep-human`** (es 17, so 3, fr 1, ne 1). Two valid renderings, e.g.
  `es:amount` Monto/Cantidad, `fr:unselectAll` "Tout désélectionner"/
  "Désélectionner tout". Needs a speaker. This is where the lane stops.
* **4 `no-unanimous`**, all `progressFilterCompleted`.
* **4 tool false positives, confirmed rather than re-derived.** Phase 118 named
  three; there is a fourth of the same shape. `es:reports` (`Informes` vs
  `informes`), `so:settings` (`Goobooyinka` vs `goobooyinka`) — both are the
  case-aligned form already, reported only because `_alignInitialCase` runs in
  the `casing` tier and these match at `exact`, where no transform runs.
  `es:myProgress` and `es:myCourses` hold the good value against a camelCase
  artefact in the Spanish XML, so they will be reported forever. That is the
  correct state, not a to-do. Extending the case alignment to every tier would
  fix the first two and has a blast radius across all 873 keys; it is a phase of
  its own.
* **132 `nameOnly` + `containment` per locale**, unchanged. Recovering one means
  editing `app_en.arb`.

## The finding this phase did not go looking for

**259 values across ar/es/fr were flagged `x-mt` while being byte-identical to
the translation shipping in the Android app** (ar 62, es 122, fr 75). The flag
means "unreviewed machine output, a human still has to look at this". One has —
whatever pipeline produced this particular copy of the words, they are the words
a translator wrote for the Kotlin app. The tool's own reconcile rule already
said a stale flag on a Kotlin-derived value should be dropped; it only ever
fired on values it had just *adopted*, never on the ones that needed no
adopting, which is an inconsistency rather than a new policy.

Cleared. **Nothing a user sees changed** — this is marking only, and it is one
line to revert (`_reconcileMachineTranslationFlags`' `already` branch) if the
reading is rejected. It is called out here rather than buried because it moves
the human-reviewed counts more than the 33 new translations do, and a count that
moves for two different reasons in one phase is exactly the kind of thing that
later reads as drift.

Same shape as Phase 118's own generalisation: a report built to answer "what can
I adopt?" cannot answer "what is already fine and mislabelled?", and this one
was invisible because the `already` verdict is the one the report does not print.

## Counts, before → after

| locale | values | human (unflagged) | of which new this phase |
|---|---|---|---|
| ar | 823 → 829 | 327 → 395 | 6 derived + 62 flags cleared |
| es | 861 → 864 | 323 → 448 | 3 derived + 122 flags cleared |
| fr | 861 → 864 | 316 → 394 | 3 derived + 75 flags cleared |
| ne | 415 → 421 | 389 → 396 | 7 derived |
| so | 415 → 421 | 389 → 396 | 7 derived |

`gen-l10n`'s untranslated report moves with them: ar 50 → 44, es/fr 12 → 9,
ne/so 458 → 452.

## Wording for `CLAUDE.md`

Replace the **Current l10n state** passage (Phase 118's suggested text is now
out of date on both the counts and the tool's reach):

> **Current l10n state, and one open decision.** The template `app_en.arb` has
> 873 keys. Arabic, Spanish and French carry 829–864 values each, of which
> 394–448 are human translations and the rest machine output flagged `x-mt`.
> Nepali and Somali carry 421 values, 396 human — and the other 452 keys fall
> back to English on purpose. Nothing here is translated by hand: the human
> share comes from reading the Kotlin `values-*/strings.xml`, which ships real
> translations in all five languages, and preferring them wherever the port had
> minted its own English for a string Kotlin already had. Phase 114 recovered
> 431 that way, Phase 118 deleted 899 `[Nepali] `/`[Somali] ` markers that were
> displacing the English fallback with the English plus a tag, and Phase 121
> reached the class both were structurally blind to: **92 Kotlin strings carry a
> `%s`/`%d` format specifier and the derivation tool skipped every message with
> a placeholder in it**, so `courseProgressCount` read "Progress 3 of 8" to a
> Spanish learner while "Progreso %1$s de %2$s" shipped in the Android app.
> `tool/arb_from_strings_xml.dart` now converts Android's positional specifiers
> to ICU named placeholders **by argument index, never by position** — four
> Kotlin locale strings reorder their arguments, and a left-to-right
> substitution renders "eight of three" on those. `--candidates` reports what is
> left and `--adopt` applies the confident ones; **re-run `--adopt` whenever a
> phase adds an English key**, because the Kotlin app may already ship the
> translation. **Match on English text, never on the key name** — `achievements`
> and Kotlin's `myAchievements` are the same concept and different strings —
> except as a *tie-break* between two Kotlin names that share one English and
> disagree, where the namesake is the same string identified twice. Never adopt
> a value that is itself English (`values-so` renders `settings` as "Settings"),
> and never derive from a skeleton with no word in it (`%1$s (%2$s)` matches any
> key of that shape and carries no translation). What still needs a human is 22
> two-valid-renderings disagreements, the 452 keys per locale Nepali and Somali
> have never had, and a visual RTL review in Arabic; a confidently wrong
> translation can mislead a learner where an English fallback merely
> inconveniences them.

For the Migration progress table's Localisation row: the honest figure is now
**394–448 human of 873 in ar/es/fr, 396 of 873 in ne/so**. The score can move
from ~30 to ~34 — the derived slice is small, and most of the count movement is
truthfulness about strings that were already correct rather than new coverage.

## What is not here

* **No schema change.** No table, converter or index; `schemaVersion` stays 45.
* **No `lib/` change at all.** `lib/l10n/*.arb` are data; the generated
  `app_localizations*.dart` are gitignored build output.
* **The three `app_en.arb` keys that write printf in their own English** —
  `communityEarnings`, `perSurvey`, `yourEarnings` — are still skipped. They
  declare ICU placeholders and write `%1$d`, so ICU never interpolates and the
  getter drops its argument *in English too*. That is a defect in the template,
  not in the derivation, and fixing it is a decision about what those screens
  say.
