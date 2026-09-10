# Phase 118 — the l10n residue, and the 899 placeholders nobody had counted

Lane C. `lib/l10n/*.arb`, `tool/arb_from_strings_xml.dart`, `test/l10n/`. The
Kotlin `app/src/main/res/values*/strings.xml` are the source of truth and were
not modified.

## The brief, and the rule it turned on

Phase 114 recovered 431 human translations from the Kotlin `strings.xml` and
left a residue it deliberately did not apply. This lane finishes the half of
that residue where a user is currently looking at English, under a rule Phase
114 arrived at the hard way and this lane inherited:

> "never overwrite a human translation" was too blunt; the right rule is never
> *degrade* one. Replacing English, a `[Language]` placeholder, or a camelCase
> artefact with a real translation is a repair. Replacing one valid translation
> with another is not your call.

That rule is what makes the two buckets need opposite treatment, and it is also
what turned a 20-string task into a 919-string one.

## Counts

**Examined:** 65 residue candidates (33 `no-unanimous`, 32 `keep-human`) plus
10 values `--adopt` had never seen, plus a full scan of all five locale files
for values that are English or a marker — 4,257 values in total.

**Applied: 929 values.** 30 in the residue proper, 899 placeholder deletions.

**Left: 42 candidates**, in three kinds, all argued below.

| locale | values | x-mt | human | falls back to English |
|---|---|---|---|---|
| ar | 821 → 823 | 496 → 496 | 325 → **327** | 49 → 47 |
| es | 859 → 861 | 538 → 538 | 321 → **323** | 11 → 9 |
| fr | 859 → 861 | 545 → 545 | 314 → **316** | 11 → 9 |
| ne | 859 → **415** | 475 → **26** | 384 → **389** | 11 → **455** |
| so | 859 → **415** | 476 → **26** | 383 → **389** | 11 → **455** |

The template is 870 keys. Nepali and Somali losing 444 values each is the point
of the second half of this phase, not a regression — see below.

---

## Part 1 — the residue Phase 114 named (30 values)

### `no-unanimous`: 8 of 33, the ones where English was shipping

Two or more Kotlin names share one English string and disagree in translation,
so Phase 114 had nothing to prefer and skipped all 33. But in 8 of them the
value the port was *actually shipping* was the English itself, or the external
translator's `[Nepali] `/`[Somali] ` marker — and there any of the disagreeing
translations is a repair rather than a coin toss.

| | before (what shipped) | after | from |
|---|---|---|---|
| `so:settings` | `Settings` | `Goobooyinka` | `action_settings` |
| `so:joinRequests` | `[Somali] Join requests` | `Su'aasha xubinaha` | `join_requests` |
| `so:addResource` | `[Somali] Add resource` | `Kudar Ilaha` | `add_resource` |
| `so:descriptionIsRequired` | `[Somali] Description is required` | `Sharaxaadda waa loo baahan yahay` | `desc_is_required` |
| `so:progressFilterCompleted` | `[Somali] Completed` | `Dhammaystay` | `status_completed` |
| `ne:joinRequests` | `[Nepali] Join requests` | `सम्मिलित गर्न अनुरोधहरू` | `join_requests` |
| `ne:addResource` | `[Nepali] Add resource` | `स्रोत थप्नुहोस्` | `add_resource` |
| `ne:progressFilterCompleted` | `[Nepali] Completed` | `पूरा भयो` | `status_completed` |

**The tie-break, so a later pass can argue with it: match the Kotlin name whose
own usage site matches the ARB key's usage site, and never take a value that is
itself English.** Both halves earned their keep:

* `joinRequests` renders the team-members tab (`team_members_screen.dart:39`),
  which is Kotlin's `join_requests`; `notifGroupJoinRequests` renders a
  notification group heading, which is `notif_group_join_requests`. The two ARB
  keys now mirror Kotlin's two names exactly, in both locales, rather than
  collapsing onto whichever translation read better to someone who does not
  speak the language.
* `progressFilterCompleted` is the courses progress filter
  (`courses_screen.dart:614`), so `status_completed`, not the bare `completed`.
* `addResource` titles the add-resource screen, so `add_resource` — whose value
  carries a trailing `:` that the punctuation rule strips, as the template has
  none.
* `so:settings` is where the usage rule had to yield, and the "never take
  English" half decided it. `values-so` renders `settings` as the literal
  string "Settings", so the name-match offers nothing; `action_settings` is both
  the only Somali on offer and what Kotlin's `SettingsActivity` titles itself
  with (`SettingsActivity.kt:57`). Capitalised upward to the template, as the
  tool's own casing rule does.
* `so:descriptionIsRequired` is the one place the usage rule was overruled on
  other grounds. Usage says `description_is_required` — that is what Kotlin's
  `AddResourceActivity.kt:218` uses, and the port's caller is the same screen.
  Its Somali is `Sharraxaad waa loo baahan yahay`, against `desc_is_required`'s
  `Sharaxaadda waa loo baahan yahay`. The two names' English is *identical*, so
  the semantic tie-break has nothing to say, which frees an orthographic one:
  `sharaxaad` appears 5 times in `values-so` and `sharraxaad` once. Took the
  spelling that is not the outlier.

None of these is a claim about which Somali or Nepali reads better. Each is a
claim that a real translation shipping in the Android app beats English on a
screen, which is a judgement a non-speaker is allowed to make.

### `keep-human`: the presentation-case group (11 values)

Phase 114's own words: *"a presentation fix somebody could just make; it is not
a translation question."* The ARB had copied Kotlin's lowercase English, where
`app_en.arb` capitalises. First character aligned upward, nothing else touched.

| key | es | fr | so |
|---|---|---|---|
| `view` | `ver` → `Ver` | `afficher` → `Afficher` | `eeg` → `Eeg` |
| `exportCancelled` | `exportación…` → `Exportación…` | `exportation…` → `Exportation…` | `dhoofinta…` → `Dhoofinta…` |
| `failedToSaveCsvFile` | `error al…` → `Error al…` | `échec de…` → `Échec de…` | `waa lagu…` → `Waa lagu…` |
| `complete` | `completa` → `Completa` | — | — |
| `serverPinLabel` | `pin del servidor` → `Pin del servidor` | — | — |

### One artefact (1 value)

`es:myCourses` held `misCursos` — the Spanish XML's own camelCase artefact, not
a translation. Same shape as `miProgreso`, but the *mirror image*: for
`myProgress` the ARB holds the good value (`Mi Progreso`) and Kotlin holds the
artefact, so that one is left alone; for `myCourses` the ARB held the artefact.
Now `Mis cursos`, which is what `values-fr` and `values-ne` say in their own
languages (`Mes cours`, `मेरो पाठ्यक्रमहरू`) where Spanish glitched.

### 10 values the tool had never seen

`teamDocuments` and `confirmLeaveTeam` are English keys added after Phase 114
ran, and both match Kotlin exactly. `--adopt` picked them up in all five
locales for free. Worth knowing that this recurs: **every phase that adds an
English key should re-run `--adopt`**, because the Kotlin app may already ship
the translation.

---

## Part 2 — what the measurement found: 899 markers, not two

Phase 114 named two instances of the `[Language] ` marker (`ne:joinRequests`,
`so:joinRequests`) because those are the two its candidate report could see —
the report only surfaces a key when a Kotlin English string matches it. Scanning
the locale files directly instead:

**449 Nepali and 450 Somali values were `[Nepali] ` / `[Somali] ` followed by
the English template verbatim.** Checked, not assumed: all 899 have the
remainder byte-identical to `app_en.arb`, and all 899 are flagged `x-mt`. Not
one carries a partial translation, a reordering, or a single translated word.

So the record in `CLAUDE.md` was wrong in a way that mattered: **Nepali and
Somali were never machine-translated at all.** The 475/476 strings counted there
as "machine output" were 449/450 markers plus 26 real machine strings. Spanish,
French and Arabic did go through the translator; those two got a tag.

### Why a marker is worse than nothing

`gen-l10n` falls back to `app_en.arb` for a key a locale omits — and
`locale_coverage_test.dart` already asserts exactly that, with a comment saying
untranslated keys "have to read as English rather than as an empty label". A
marker *displaces* that fallback with the same English under a bracket. A Somali
user did not see an untranslated app; they saw `[Somali] Join requests`, which
reads as a bug.

Nothing could catch it. The value is well-formed JSON with the right
placeholders, `gen-l10n` compiles it, `flutter analyze` is silent, and every
structural test passes: the key exists, it is non-empty, it is not a duplicate,
and it is *honestly* flagged unreviewed. Only reading the text finds it — the
same shape as the Phase 114 backslash artefacts and the Phase 109 `__0____`
tokenisation, and the third instance of it. **A locale file's failures are in
its values, and only a test that reads them will ever see one.**

### What was done

The 899 values and their now-empty `@key` blocks are deleted, so those keys
fall back to English: the same words, without the tag. Guarded by a new test in
`placeholder_integrity_test.dart` whose net is wider than the two markers that
shipped (`^\s*\[[^\]]{1,20}\]`), because the next tool will choose a different
one; no legitimate value in any locale, the template included, opens with a
bracketed word.

**One check worth recording, because it is what makes the deletion safe rather
than merely tidy:** after deleting, `--candidates` reports **zero** `apply` in
all five locales. A deleted key is "replaceable", so any of the 899 with a
Kotlin translation available at an adoptable tier would have surfaced as an
adoption. None did. Every string deleted here had no better source to draw on —
English fallback was genuinely the best available value, and the marker was
strictly worse than it.

This landed as its own commit so it can be dropped alone if a reviewer prefers
the marker's one virtue: it makes the hole visible in the file. That virtue is
real but cheaper elsewhere — `flutter gen-l10n` prints `"so": 455 untranslated
message(s)` on every build, and `--unreviewed` lists them — and it is not worth
a bracket on a learner's screen.

---

## Residue deliberately left (42 candidates)

**25 `no-unanimous`** (ar 6, es 5, fr 5, ne 2, so 7). Every one now carries a
real translation in its locale, so the disagreement is between two valid
renderings — `es:settings` Configuraciones/Configuración,
`so:submit` Ku Dir/Gudbi, `fr:filter` Filtre/Filtrer. Not a non-speaker's call,
and nothing is broken while it waits.

**17 genuine `keep-human` disagreements** (es 14, plus `unselectAll` in fr, ne
and so). Both sides valid: `es:amount` Monto/Cantidad,
`es:noteRequired` "La nota es obligatoria"/"Se requiere una nota",
`fr:unselectAll` "Tout désélectionner"/"Désélectionner tout". **These need a
speaker, and this is the line where this lane stops.**

**3 `keep-human` entries that are tool false positives**, listed so the next
pass does not spend time on them:

* `es:reports` — the ARB's `Informes` is the case-aligned form of Kotlin's
  `informes`, i.e. already correct. It is reported only because the tool aligns
  first-character case in the `casing` tier and this key matches at `exact`,
  where no transform runs. Extending the alignment to every tier would fix this
  and would have a blast radius across all 870 keys; measuring that is a
  phase of its own, not a change to slip into this one.
* `es:myProgress` and `es:myCourses` — both now hold the good value against the
  Spanish XML's camelCase artefact, so both will be reported forever. That is
  the correct state, not a to-do.

**127 `nameOnly` + `containment` per locale.** Untouched: recovering one means
editing `app_en.arb`, which is a judgement about what a screen should say.
Phase 114's tables are still the place to start.

**26 `x-mt` values per locale that are byte-identical to the English** (ar 27,
es 28, fr 44, ne 26, so 26). The same "nothing here" class as the markers,
without the tag — mostly plural and placeholder strings the translator passed
through. Left alone deliberately: deleting them changes nothing a user sees, and
the `x-mt` flag is a truthful record that the key went through the machine pass
and produced nothing. Worth a sweep only if someone wants the counts to mean
"has a translation" rather than "has a value".

---

## Wording for `CLAUDE.md`

Replace the **Current l10n state** passage (Phase 114's suggested text is now
wrong about ne/so, in the specific way this phase found):

> **Current l10n state, and one open decision.** The template `app_en.arb` has
> 870 keys. Arabic, Spanish and French carry 823–861 values each, of which
> 316–327 are human translations and 496–545 are machine output flagged `x-mt`.
> Nepali and Somali carry 415 values, 389 human — and the other 455 keys fall
> back to English on purpose. Phase 114 raised the human share by 431 strings
> without translating anything: it read the Kotlin `values-*/strings.xml` — real
> human translations already shipping in the Android app — and preferred them
> wherever the port had minted its own English for a string Kotlin already had.
> Phase 118 finished its residue where English was shipping (30 values) and then
> found what the residue was a sample of: **449 Nepali and 450 Somali values
> were `[Nepali] `/`[Somali] ` plus the English template verbatim, with no
> translation content at all** — the external pass never translated those two
> languages, it tagged them. A marker is worse than the English fallback it
> displaces, because a learner reads the bracket as a bug, so all 899 are
> deleted and `test/l10n/placeholder_integrity_test.dart` fails if one returns.
> `tool/arb_from_strings_xml.dart --candidates` reports what is left and
> `--adopt` applies the confident ones; **re-run `--adopt` whenever a phase adds
> an English key**, because the Kotlin app may already ship the translation.
> **Match on English text, never on the key name** — `achievements` and Kotlin's
> `myAchievements` are the same concept and different strings. Where two Kotlin
> names share one English and disagree, prefer the name whose *usage site*
> matches the ARB key's, and never adopt a value that is itself English
> (`values-so` renders `settings` as "Settings"). What still needs a human is 17
> two-valid-renderings disagreements and the 455 keys per locale that Nepali and
> Somali have never had; a confidently wrong translation can mislead a learner
> where an English fallback merely inconveniences them.

And for the Migration progress table's Localisation row, which read "231–256 of
858 keys per locale" at ~30: the honest figure is **316–327 human of 870 in
ar/es/fr with the rest machine-translated, and 389 human of 870 in ne/so with
the rest English**. The score should not move up on this phase — the phase
*lowered* the value count by 899 and raised the truthfulness of the number,
which is worth more than the number.

## One thing that generalises

The assigned task was 65 candidates. The finding was 899 strings, and it came
from one scan that the candidate report structurally could not produce: the
report only lists a key when a Kotlin string matches it, so a key with no Kotlin
counterpart is invisible to it no matter how broken its value is. **A report
built to answer "what can I adopt?" cannot answer "what is wrong?", and reading
it as though it could is how 899 defects sat behind a residue of two.** The
scan that found them is three lines of Python over the same files.
