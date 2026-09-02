# Phase 103 — first tests for `EditAchievementScreen` and `UserInformationScreen`

Two of the port's largest hand-written surfaces had no tests at all:
`ui/achievements/edit_achievement_screen.dart` (634 lines) and
`ui/exam/user_information_screen.dart` (415 lines). 39 new tests (1559 pass),
and they found **thirteen** defects. Every fix below was demonstrated failing
against the pre-fix code first, one defect at a time.

## `UserInformationScreen`

**The additional-fields toggle was gated on the wrong state, and the one live
caller was the one that lost.** Kotlin shows `btnAdditionalFields` exactly when
the form opens *collapsed* — `shouldHideElements = true`, which is what
`BaseExamFragment` passes for every survey that is not from the nation — so the
respondent can open the name/email/phone/level blocks; with
`shouldHideElements = false` the layout's defaults show all four blocks and
`initViews` hides the button outright, because that mode has no second state to
toggle into. The port's parameter is the negation (`showAdditionalFields`) but
the button was gated on it directly, so `public_survey_screen`
(`showAdditionalFields: false`) rendered the year-of-birth form with **no way to
reach the other fields**, while the router's default rendered the full form plus
a "Hide additional fields" button Kotlin does not have there. **A negated
parameter negates every rule that reads it, not just the one that is obviously
about the initial state.**

**The profile document used keys Planet does not read.**
`UserSurveyProfile.toJson()` is the specification, and three of its rules had
been lost: an empty field is **omitted** rather than sent as `""`; a picked
birth date travels as `birthDate` in ISO-8601 and a year of birth as an `age` in
years; and `betaEnabled` is always present. The port wrote `dob`, `birthYear`,
empty strings for every unfilled field, and no `betaEnabled`. This is not
cosmetic: `SubmissionsRepository.serialize` puts `'user': _asDocument(row.user)`
straight into the CouchDB document, so a team survey uploaded a respondent
profile Planet cannot read. The public-survey path had been *partly* rescued
after the fact — `SurveysRepository._sanitizeRespondent` coerces
`birthYear` → `age` and `dob` → `birthDate` at send time — which is worth
noting as a smell in its own right: **a sanitizer on one of two upload paths is
evidence the producer is wrong, not that the paths differ.** The document is now
built by a top-level `userSurveyProfileJson` pinned by its own unit tests (the
`reportExportDateSuffix` precedent), and `_sanitizeRespondent` keeps its
coercion for rows an earlier build already completed and left in the outbox.

**First name carried a required validator.** `createUserProfile` requires
nothing but the year of birth, and only while that block is on screen. The
port's `_formKey.currentState!.validate()` therefore blocked the save outright
for a respondent whose profile has no first name.

**The language and level dropdowns hand-rolled their own vocabularies.** The
port offered Primary/Secondary/High School/College/University where
`R.array.level` is Beginner/Intermediate/Advanced/Expert, and English names
where `R.array.language` carries the native ones — and both arrays were
*already* ported, as `memberLevels`/`memberLanguages` in
`core/utils/constants.dart`, for `become_member_screen`. Same shape as the
Phase 78 `normalizeText` duplicate and the Phase 95 avatar helpers: **when a
Kotlin resource array is already ported, use it; a second copy is a second
vocabulary, and this one reached the server.**

**A profile whose `level` or `language` is not among the dropdown's items takes
the screen down.** `DropdownButtonFormField` asserts when its value is not one
of its items, out of `build`. This was reachable from the port's own earlier
writes (a survey completed by the previous build stored `College` on the user
document) — and it was hidden only because the prefill itself was dead, which
is the next defect. **A dead prefill hid a crash; fixing the read is what
surfaced it.** The prefill now falls back to the first entry, which is what the
Kotlin spinner shows regardless of the profile.

**`_loadUserData` read `sessionProvider` with `valueOrNull` from `initState`.**
Phase 100's shape again, on a screen that never watches that provider: the read
initialises it, so the value is still `AsyncLoading` and nothing was ever
prefilled. It awaits the provider's future now, and the upload-queue step does
the same *inside its own* `try` — a session that will not resolve is not a
failure to save, and the submission has already been marked complete by then.
Worth recording honestly: the queue step's `valueOrNull` was **not**
demonstrable as broken, because by submit time the provider has resolved from
the `initState` read. The prefill is where the bug bites.

Deliberately **not** ported: Kotlin's collapse resets the birth-date *label* to
"Birth date" but leaves the `dob` field set, so re-expanding submits a date the
form no longer shows. The port clears the value, which is what the label reset
implies. And Kotlin's `submitForm` has a third branch — an empty submission id
routes to `userRepository.updateProfileFields` for
`BaseDashboardFragment`'s profile prompt — that this screen does not need,
because `submissionId` is required here and the profile prompt has no port.

## `EditAchievementScreen`

**A first-time achievement could never reach the server.**
`AchievementsRepository.serialize` emitted `'_id': row.couchId`, and nothing
fills `couchId` in until an upload has already succeeded — `getOrInitialize`
sets `''` and `update` preserves it — so every ledger the user had just authored
serialized as `'_id': ''`, the outbox handler rejected it with "Achievement
ledger carried no _id", and the row sat pending forever. Kotlin has one field:
an `Achievement`'s `_id` **is** its primary key, set by `initializeAchievement`
to the derived `"$userId@$planetCode"`, so its first PUT creates
`achievements/<that id>`. `serialize` now falls back to `row.id`, which is that
same derived id, so a device that already carries an initialized row is
repaired too (fixing `getOrInitialize` alone would not have been).

This is the phase's most useful lesson, and it is a variant of Phase 100's
verification photo and Phase 74's reactions: **the key existed
(`AchievementsRepository.idFor`) and the serializer read a different field, so
the two halves never met.** What made it survive was the test suite:
`achievements_uploader_test.dart` hand-patched `couchId` into the row with a raw
drift update in every test that needed one — a state the app never produces —
and its one test of the empty case, `'handler abandons when the ledger has no
couch id'`, **codified the defect as intended behaviour**. Both halves passed
alone. That test is now `'handler PUTs a never-synced ledger to its derived
id'`, with the guard's own coverage kept as an outbox row whose payload names no
document (what an older build could have left behind). **A test that sets up
state by writing the database directly is a test that has stopped checking
whether the app can produce that state.**

**Achievement and reference entries could not be removed.**
`EditAttachementBinding.ivDelete` and `EditOtherInfoBinding.ivDelete` drop the
entry from the array and re-render; the port rendered an edit action only, so an
entry added by mistake was permanent.

**An achievement could be dated in the future.** Kotlin sets
`dpd.datePicker.maxDate = now.timeInMillis` on the entry dialog's picker; the
port passed `lastDate: DateTime(2100)`. (Same family as the Phase 53 team
finance future-date cap.)

**The birth-date row printed the stored column.** Kotlin renders
`getFormattedDate(user.dob, "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")`, i.e. the
`TimeUtils` default `EEEE, MMM dd, yyyy`; the port put `_dobIso` on screen, so a
synced profile showed `1990-05-02T00:00:00.000Z` in the field. Now a top-level
`achievementBirthDate`, which keeps Kotlin's split between the displayed text
and the saved value, and its `"N/A"` for a value that will not parse. No zone
conversion: `DateFormat` reads the instant's own fields and a `...Z` value
parses to UTC, so `toUtc()` would have moved the day for any reader east of
Greenwich.

**Six fields were stored untrimmed.** `btnUpdate` trims the header, goals and
purpose alongside the name fields; `saveAchievement` trims an entry's title,
description and link; the reference dialog trims the name. The port trimmed only
the four name/place fields — so the same input stored differently in the two
apps. Exactly the Phase 90 `saveHealthProfile` shape (which trimmed two of
five).

**An attached resource lost its document.** `showResourceListDialog` stores
`list[ii].serializeResource()` — the whole resource document — where the port
stored `{'title': name}`, so the achievement reached the server naming resources
it could not identify. `ResourcesRepository.serializeResource` was already
ported and unused from here; the dialog now rebuilds the array from the library
rows on OK, which is what the Kotlin's positive button does.

**Viewing a CV whose bytes are gone did nothing.** `btnViewCvEdit`'s else
branch toasts `file_not_found`; the port returned silently, so the button looked
broken.

### The CV/resume round trip

The lane flagged this path as the likely site of another bytes-and-row key
mismatch. **It is sound**: the screen writes with
`AchievementFiles.write(resumeFileName: _pickedCvName)` and stores that same
string on the row, and `AchievementsUploader._uploadResumeAttachment` reads it
back with `readResumeBytes(payload['resumeFileName'])` — one string, and both
sides pass it through the same `_segment` basename reduction, so even a
separator in a picked name resolves to the same slot. What was missing was any
test of the pair: the uploader's tests inject `readResumeBytes`, and the screen
had no tests, so nothing had ever written a byte and read it back. The new test
picks a PDF, saves, and then reads through the uploader's own default
`readResumeBytes` with the row's serialized payload.

Making that testable needed a seam, since `file_picker` wants a platform channel
`flutter test` cannot serve: **`core/system/file_pick.dart`** (`FilePick.instance`
+ `PickedFile`), in the shape of Phase 51's `PhotoCapture`. `PickedFile` keeps
Kotlin's split between the pick and the read — `readBytes` is a closure, so the
extension check still rejects a non-PDF without reading it, as
`pickCvLauncher` does. `personals_screen`, `add_resource_screen` and
`team_reports_screen` still call `FilePicker.pickFiles` directly and would each
gain testable pick paths by moving to it; left alone here to keep this lane off
their surfaces.

## Notes for the next person writing tests here

* `AchievementFiles.write` from inside the fake-async zone needs **40**
  `runAsync`+`pump` rounds, not the 8 that serve drift: `Directory.create` plus
  `writeAsBytes(flush: true)` is several io round trips, and each one needs its
  own real event-loop turn. Eight rounds gets as far as `create` and then stops,
  and the symptom is not an error — `_save`'s `catch (_)` swallows nothing
  because nothing threw; the row simply keeps its old `resumeFileName` and the
  test reports a value mismatch. `take_exam_screen_test`'s photo test already
  uses 40 for the same reason.
* Both screens' bodies are `ListView(children: [...])` and run well past the
  600px test fold. These files set `tester.view.physicalSize` to a tall surface
  in `pumpScreen` (with `addTearDown(tester.view.reset)`) rather than scrolling
  between assertions — the first place in this suite to do that; scrolling
  remains fine and is what the older files do.
* Never `pumpAndSettle` after Save/Update on either screen: the user-info Save
  button holds a `CircularProgressIndicator` while `_isSubmitting`, and the
  achievement screen swaps its whole body for a "Saving…" placeholder while real
  file work runs.
* `l10nOf(tester)` in the achievement tests reads the screen's own
  `AppLocalizations` rather than repeating a literal — which is how the test
  survives the `selectResources` fix below.

## For the integrator

**One proposed `.arb` change** (not made here, per the lane's constraint). The
`selectResources` value carries Android's whitespace-preserving quoting as
literal text, so the button reads `"Select resources: "` with visible quotes.
In XML those quotes are the mechanism for keeping the trailing space, not part
of the value:

| file | current | proposed |
|---|---|---|
| `app_en.arb` | `"\"Select resources: \""` | `"Select resources: "` |
| `app_fr.arb` | `"\"Sélectionner des ressources: \""` | `"Sélectionner des ressources: "` |
| `app_ne.arb` | `"\"स्रोतहरू छान्नुहोस्: \""` | `"स्रोतहरू छान्नुहोस्: "` |
| `app_so.arb` | `"\"Xullo khadka: \""` | `"Xullo khadka: "` |

The other half is done: `tool/arb_from_strings_xml.dart` now strips the wrapping
quotes (`_unquote`), so a regeneration will not reintroduce them. It is the only
affected key across all five locale files.

Also worth an `.arb` follow-up, though nothing here depends on it:
`fileNotFound` is `"File not found locally"` where Kotlin's `file_not_found`
takes a `%s` and names the file. The new toast uses the key as it stands.

**No schema change.** Drift stays at v44; no table or column was touched.

**No new localized strings.** Everything used here already had a key
(`delete`, `fileNotFound`, `memberLevels`/`memberLanguages` are Dart constants,
not `.arb`).
