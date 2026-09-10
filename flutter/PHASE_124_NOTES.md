# Phase 124 — `formatNotification`, and the bell row that read `7`

Phase 120's third deferred item. `NotificationsViewModel.formatNotification`
(`NotificationsViewModel.kt:354-425`) rewrites every notification's text per
**resolved** type and hands the result to `Html.fromHtml`, which fills the row's
single TextView (`NotificationsAdapter.kt:116-127`, `row_notifications.xml`).
The port had none of that: it drew a type label as the row's title and the
stored `message` verbatim underneath. Since `updateResourceNotification` stores
*only the count*, the port's own resource notification rendered as the single
character `7` — a row no learner can interpret and none would report.

Two `parity-auditor` passes at `effort: max`: one on the Kotlin before writing
any Dart, one on the finished diff. The first overturned the central design
decision of this phase (see *The rendering decision*), and is the reason this
lane ships a small HTML parser it had argued against.

---

## What each type formats to, and which part is the title

There is **one** part for the message. `row_notifications.xml` holds a `title`
TextView and a `timestamp` TextView — plus a `CheckBox` and a *Mark as read*
`Button`, which are affordances rather than text — and **no icon**: `iconResFor`
is called once per *group*, from `HeaderViewHolder`. So Kotlin's whole
notification text is the row's primary line, and the port's "type label above
the raw message" was an invention twice over: `AppNotification.title`, which the
port's row preferred when set, is a column **nothing in either app ever
writes** — `parseNotification` never assigns it and `model/Notification.kt` has
no field for it.

The port keeps its per-row **icon**, which by that same citation Kotlin does not
have. That is a deliberate addition — it makes a long list scannable — and it is
why the type *label* was the thing removed instead: the label duplicated the
group header outright, where the icon does not. Said plainly here because the
first draft of these notes used the layout to justify deleting the label while
the code two lines away kept the icon.

| resolved type | text | markup |
|---|---|---|
| `resource` | `You have 7 resources not downloaded` (`resource_notification`) — a non-numeric message is passed through | none |
| `storage` | `Storage running low: 8%` — `<= 10` and `<= 40` produce the *same* string, `> 40` is `Storage available: N%`, a non-percentage is passed through with its `%` | none |
| `task` | `Read chapter 3 is due in Thu 12, August 2027` (`task_notification`), prefixed `<b>Reading Club</b>: ` when the team resolves | bold team name, colon **outside** |
| `join_request` | `<b>Join Request:</b> Jane has requested to join My Team` — only when the row's **raw** type is `join_request`; otherwise the server's message, verbatim | bold prefix, colon **inside** |
| `chat`, `team_join`, `voice_reply`, unresolved | the message, verbatim | whatever the server sent |

Ported to `lib/ui/notifications/notification_format.dart` as two halves:
`notificationHtml` composes exactly what Kotlin composes, quirks intact, and
`renderNotificationHtml` (`notification_html.dart`) turns it into spans. Keeping
them apart is what lets the composition stay literally faithful — including the
double space below — while the renderer accounts for what a user never sees.

Consistency with Phase 49's navigation holds by construction: the formatter and
`NotificationDestinationResolver` switch on the same `resolvedNotificationType`,
so a row that reads as a task opens a task list and a row that reads as a join
request opens the requests tab.

---

## The rendering decision, and why the obvious answer was wrong

The brief asked for structured title/subtitle emphasis over an HTML renderer,
"if it reproduces what a user sees" — and I wrote it that way first, with a
comment claiming the only markup in play was the port's own `<b>` and that
"Planet's notification messages are plain sentences". **The ground-truth audit
killed that with the repo's own fixtures.** `NotificationsRepositoryImplTest.kt`
shows what the server actually sends:

```
:168  "<b>Jane</b> has requested to join <b>\"My Team\"</b> team."   type team
:192  "You have been added to <b>\"My Team\"</b> team."               type team
:214  "<b>Jane</b> replied to your message."                          type replyMessage
```

Those three are precisely the arms `formatNotification` passes through
unchanged — so **the arms that carry markup in production are the arms that do
no formatting**, and a span list built only from the port's own emphasis would
have drawn `<b>` on screen for the most common notifications in the app. The
first design was worse than the defect it replaced.

So: spans **and** a parser. `notification_html.dart` is ~140 lines and
deliberately narrow, because the input is a closed set:

* `<b>`/`<strong>`, `<i>`/`<em>` → emphasis flags;
* an unknown tag is dropped and its text kept, as TagSoup does — so a task
  titled `Read <chapter 3>` renders identically in both apps;
* a small HTML4 entity table plus numeric references, and an unrecognised
  `&foo;` is left alone;
* runs of spaces and newlines collapse; leading whitespace is dropped; a tab is
  not collapsed (AOSP collapses `' '` and `'\n'` only).

No dependency: the alternative is a full HTML widget package for four tags, and
`Text.rich` draws these spans exactly as the `Spanned` renders. What is **not**
reproduced is block layout — `<p>`/`<div>` become one newline where AOSP emits
paragraph breaks, and lists lose their bullets. Nothing in the corpus has one.

That collapsing rule is not decoration. `storage_running_low` is
`"Storage running low: "` — Android-quoted so AAPT keeps the trailing space —
and `formatStorageNotification` composes `"$prefix ${it}%"`, adding a second.
The Kotlin's own string carries a double space that only `Html.fromHtml` hides.
The port composes the same two spaces and collapses them at render, so the
composition can be diffed against the Kotlin line by line. It also means the
French translation, whose XML is *unquoted* and so has no trailing space,
renders with the same single space it does in the Android app.

---

## Did the Kotlin translations come across? Seven of nine, yes

Keys were named so the derivation tool could match them, and
`dart tool/arb_from_strings_xml.dart` picked up **all seven that have a Kotlin
counterpart, in all five locales** — human translations already shipping in the
Android app, at no cost:

| ARB key | from | derived |
|---|---|---|
| `resourceNotificationMessage` | `resource_notification` | ar es fr ne so |
| `taskNotificationMessage` | `task_notification` | ar es fr ne so |
| `storageRunningLow` | `storage_running_low` | ar es fr ne so |
| `storageAvailable` | `storage_available` | ar es fr ne so |
| `joinRequestPrefix` | `join_request_prefix` | ar es fr ne so |
| `userRequestedToJoinTeam` | `user_requested_to_join_team` | ar es fr ne so |
| `markAsRead` | `mark_as_read` | ar es fr ne so |
| `unknownUser` | — | English fallback |
| `unknownTeam` | — | English fallback |

Two of them are Phase 121's placeholder path doing its job:
`taskNotificationMessage` came across as Nepali's `{title} {date} मा समाप्त
हुन्छ`, argument order rewritten, which is the whole reason that path exists.

`resourceNotification` / `taskNotification` / `storageNotification` /
`joinRequestNotification` — the names Phase 120 predicted this work would want
— are the *type labels* the old row drew, and they still hold their own
translations, so the new keys took `…Message`-style names instead. The type
labels are now unused by the row (the group header names the type, as in
Kotlin); the keys are left in place rather than deleted, because their five
locale files carry real translations and a future caller may want them.

`unknownUser`/`unknownTeam` have no Kotlin string to derive from: Kotlin
hardcodes the English literals `"Unknown User"` / `"Unknown Team"` in
`NotificationsRepositoryImpl` (`:184,188,229,242,248`). The port's repository
leaves those fields **null** and the formatter fills them from the ARB, so the
fallback is translatable — the same correction Phase 95 made to `MyHealthScreen`'s
hardcoded `'Unknown'`. Rendered English is identical.

`test/l10n/placeholder_integrity_test.dart`'s pinned human-reviewed counts move
by exactly 7 per locale (`markAsRead` arrived with the second pass below).

---

## Which types can actually reach this code

Worth writing down, because it decides what the tests can honestly claim.

`AppNotification` is constructed in exactly three places, all in
`NotificationsRepositoryImpl`: the `resource` row (`:68`), the `storage` row
(`:99`), and `parseNotification` (`:370`), which stores the server document's
type verbatim. `refresh()` is `= Unit`; `insert(doc)` has no production caller;
`utils/NotificationUtils.kt` only builds Android tray notifications and touches
no DAO. So:

* **reachable and reformatted**: `resource` (the port's own `"7"`), `storage`
  (the port's own `"8%"`, and only ever `<= 10` — the writer *deletes* the row
  above that, so the `<= 40` and `> 40` arms are dead for every message either
  app authors);
* **reachable and verbatim**: the server's `team`, `newTask`, `newResource`,
  `replyMessage` documents;
* **not reachable through any writer**: the join-request lookup needs a row
  whose *raw* type is `join_request`. Neither app authors one — `AppNotification`
  is built in exactly the three places above, and the Dart writers are the same
  three — but `parseNotification` stores the server's `type` verbatim and
  `join_request` is in `KNOWN_TYPES`, so a Planet document literally typed that
  way *would* land here. None is evidenced anywhere in the tree; the same caveat
  applies to the storage arms above, which are dead as far as the local writer
  goes. It is ported for fidelity, and the
  tests that exercise it drive the pure function directly rather than implying
  the feature works. Worth knowing why it would be *bad* if it fired:
  `extractRelatedId` returns null for every raw type but `team`/`replyMessage`/
  `newTask`, so such a row would carry no `relatedId`, hit the `''` fallback and
  read "Join Request: Unknown User has requested to join Unknown Team" — worse
  than the server's own sentence.

The `newTask` hole Phase 120 recorded as item 4 is confirmed and preserved:
`loadNotifications` partitions on the **stored raw** type before resolution
(`:79-83`), so a server task notification gets no team-name lookup and renders
without its `<b>Team</b>:` prefix even though it groups and routes as a task.
Resolving before partitioning would be an improvement, not a port.

---

## Two Kotlin-vs-Dart primitives that do not match

Both are the same shape as Phase 78's `normalizeText` duplicate: a stdlib
function that looks equivalent and is not.

**`int.tryParse` is not `toIntOrNull`.** It accepts surrounding whitespace and
has no 32-bit ceiling. `" 7"` is null in Kotlin (so the message shows raw) and
`7` in Dart; `"2147483648"` is null in Kotlin and a number in Dart, so the port
would have announced 2,147,483,648 undownloaded resources where the Kotlin shows
the raw string. `kotlinToIntOrNull` is the ASCII-and-range version, used by both
the resource and storage arms. One divergence is left in place and documented:
Kotlin's parser accepts any Unicode decimal digit (`"٧"` is 7); no writer in
either app produces one.

**Dart's `\s` is wider than Java's.** The task-date pattern is spelled
`[ \t\n\x0B\f\r]` rather than `\s`, because Dart's also matches U+00A0 — a
message with a non-breaking space would parse here and not there. `\b`, `\w` and
`\d` do agree, and `\w` really is `[A-Za-z0-9_]`, so the pattern's month
position matches any word, not a month name; the weekday tokens are
case-sensitive. All three are pinned.

---

## Failing-first evidence

Ten reverts, each demonstrated red against the finished tests.

| revert | red |
|---|---|
| the row draws `notification.message` again | 4 screen tests, `a resource notification reads as a sentence, not a bare digit` among them |
| `kotlinToIntOrNull` → `int.tryParse` | 2 |
| `renderNotificationHtml` → a plain passthrough | 13 across the format and screen suites |
| storage composes one space instead of Kotlin's two | 3 |
| the task-date pattern uses Dart's `\s` | 1 |
| the join-request arm stops testing the raw type | 2 (the server sentence is replaced by an all-Unknown one) |
| the context partitions on the resolved type | 1 (`newTask` would gain a prefix Kotlin does not give it) |
| whitespace before a tag moves into the next run | 2 |
| a read row is un-bolded instead of dimmed | 1 |
| the id no longer wins over the title for the team prefix | 1 |

Fourteen more from the second pass, each also confirmed red: the tag-start
guard, the numeric-entity range guard, the closing-tag underflow clamp, the
self-closing-tag split, `flush()`'s leading-whitespace guard, `'\r'` in the
collapsing set, the format-context provider's body, the merge order (against a
colliding stub), the empty-`relatedId` batch filter, the unread row's opacity,
the per-row *Mark as read* button, the *Mark all read* tab gate, the swipe
handler, and the tile receiving an empty context instead of the watched one.

The starting point was the one the brief asked for: a screen test asserting the
bell renders `"7"` today.

```
Expected: exactly one matching candidate
  Actual: _TextWidgetFinder:<Found 0 widgets with text
          "You have 7 resources not downloaded": []>
```

The whitespace-before-a-tag revert is not hypothetical — it was a **real bug in
the first cut of the parser**, found by its own tests: the space in
`<b>very <i>late</i></b>` was held over and emitted into the *next* span, so
`"You have been added to "` lost its trailing space to the bold run. A run that
ends at a tag owns the whitespace before it.

The Kotlin's own unit tests are mirrored case for case where they exist —
`NotificationsViewModelTest.kt:45-107` (`parseTaskDate`, the three storage arms,
the join-request prefix) and `NotificationsAdapterTest.kt:86-99` (the
`<b>Initial</b> text` render) — so a divergence in either app shows up as two
suites disagreeing.

---

## Files touched outside this lane's set

| file | why |
|---|---|
| `lib/data/local/app_database.dart` | **one added method**, `TeamTaskDao.getByTitles` — the port of `TeamTaskDao.kt:44-45`, which the team-name-by-title lookup needs and the port had no equivalent for. It sits in the team-task section Lane A owns; it is additive and adjacent to `getByAnyIds`, and **no column, table or `schemaVersion` change** (still 45). |
| `lib/providers/app_providers.dart` | +2 lines: the two DAOs the repository's new lookups need. |
| `lib/providers/notifications_provider.dart` | `notificationFormatContextProvider`, the ViewModel-equivalent that runs the lookups. |
| `lib/l10n/app_{en,ar,es,fr,ne,so}.arb` | 9 new keys in the template; 7 derived into each locale by the tool. |
| `test/l10n/placeholder_integrity_test.dart` | the pinned human-reviewed counts, +7 per locale. |
| `test/ui/notifications_screen_test.dart` | one existing test asserted the type label the row no longer draws; rewritten around the group label and the message, keeping its point (readers use the *resolved* type). |
| `test/repository/notifications_repository_test.dart`, `test/repository/notification_type_round_trip_test.dart`, `test/core/notifications/task_deadline_notifier_test.dart` | the repository constructor gained two required DAOs. Required, not optional-nullable: a silently-absent DAO returning no team names is exactly the class of defect this port keeps paying for. |

---

## Reported, not fixed

1. **The row's timestamp is still absolute.** `NotificationsAdapter`
   (`:152-163`) shows "just now / N minutes ago / Yesterday / N days ago", then
   `MMM d, yyyy` beyond a week (pinned at `NotificationsAdapterTest.kt:80-84`);
   the port shows `DateFormat.yMMMd().add_jm()`. It is the same *class* of
   defect as the bare `7` — a stored value drawn where Kotlin transforms it —
   and it sits inside the very line range this fix cites, so the honest reason
   it is not here is scope, not principle. Its impact is far smaller: legible
   but wrong, rather than uninterpretable. Three things the follow-up needs that
   are easy to underestimate: the ARB has `justNow`/`minutesAgo`/`hoursAgo`/
   `daysAgo` but **no `yesterday`** (the Kotlin has it, `strings.xml:1352`,
   translated in all five locales, so a new key plus a derivation run);
   `relativeTimeLabel` in `ui/components/relative_time.dart` is **not** a
   drop-in, because it ports a different Kotlin function (`TimeUtils.getRelativeTime`,
   four buckets, no Yesterday and no absolute arm); and the >7-day format drops
   the time of day. Note also that `markAsRead` stamps `createdAt = now`
   (faithfully, `app_database.dart`), so a row reads "Just now" the moment it is
   read — a quirk that only becomes visible once this lands.
2. **Selection mode is unported.** Long-press entry, the checkbox, the bulk bar
   and `markSelectedAsRead`/`deleteSelected`
   (`NotificationsAdapter.bind`'s `isSelectionMode` branch, `_selectedIds` in
   the ViewModel, `NotificationsFragment.kt:84-86`). The port has
   swipe-to-delete — its own invention; Kotlin deletes only through the bulk bar
   — and now the per-row *Mark as read* button, which is **not** part of
   selection mode: it lives in the non-selection branch on every unread row, and
   the second audit was right that folding it into this item hid a real gap. It
   is ported (see below); the rest of selection mode is not.
3. **A tool defect in the ARB derivation, one line.** The plain-text by-name
   path writes `translated[exactNamed]?.trim()`
   (`tool/arb_from_strings_xml.dart:211`) and never calls
   `_mirrorTrailingSpace`, which exists precisely to keep a deliberate trailing
   space (its own comment cites `selected` → `"Selected: "`). So
   `storageRunningLow` derived into five locales **without** the trailing space
   the template carries. Harmless here — the composer adds a space and the
   renderer collapses doubles, so all six locales render one space — but any
   future label-prefix key derived through that path loses its gap. The fix is
   to wrap that assignment the way `_proposal` does. Not made: `tool/` is
   another lane's file and this is not this lane's defect.
4. **Kotlin formats the resource count through `String.format`** with the
   configuration locale, so on an Arabic device it renders Arabic-Indic digits;
   the port's ARB placeholder is a plain `int` and always renders ASCII. Kotlin
   is internally inconsistent about this already — `formatStorageNotification`
   uses a Kotlin template, so its percentage is ASCII in both apps.
5. **`getTaskTeamNamesByTaskTitles` is called unconditionally** where Kotlin
   guards it with `if (taskTitles.isNotEmpty())` (`:107-111`). A genuine no-op:
   both the Kotlin impl (`:256`) and the port (`notifications_repository.dart`'s
   `if (keys.isEmpty) return const {};`) early-return, and no DAO query runs
   either way. An earlier draft of this file cited
   `NotificationsRepositoryImplTest.kt:666` as pinning the call count and
   concluded the two suites disagree — that citation is about the **DAO**, in a
   test of the repository method, and the port satisfies it too; there is no
   `exactly = 0` assertion over the ViewModel at all. The claim is withdrawn
   rather than quietly deleted, because "a citation is not a reading" is exactly
   how Phase 106's regression got in.


---

## A second audit, on the finished implementation

Green is not evidence. The diff was format-clean, analyze-clean and 2107 tests
green when a second `parity-auditor` pass at `effort: max` was aimed at it — and
it found one live defect, nine behaviours the suite could not see, and five
claims in this file or the code comments that were wrong. Its method is the one
that keeps working: replay every revert the fix claims to guard (all ten red),
then **inject new defects into the finished code and watch the suite stay
green**.

### The live defect: a `<` is not always a tag

```
'Compare 3 < 5 > 1 today'  →  'Compare 3 1 today'         // ' 5 ' deleted
'is 3 < b > 2?'            →  'is 3  2?', with ' 2?' BOLD  // '< b >' read as <b>
```

The parser scanned from `<` to the next `>` unconditionally, and trimmed the
result before reading the tag name. An ordinary sentence with a comparison in
it — server-authored text, the kind these arms pass through — lost its middle.
Now a `<` opens a tag only when a tag name can follow it (a letter, or `/` and a
letter), which is what HTML tokenization does, and the trim is gone.

### Two parity gaps the fix cited and did not close

Both are in `NotificationsAdapter.bind`, the function this phase ports one line
of:

* **the per-row *Mark as read* button** (`:137-141`, `row_notifications.xml:44-55`),
  visible on every unread row outside selection mode. Without it the port's only
  single-row read path was `onTap`, which also navigates away — so a learner
  could not clear one notification and keep reading the list. Ported, with
  `markAsRead` derived from the Kotlin `mark_as_read` in all five locales;
* **Mark all read is hidden on the Read tab** — Kotlin gates it on
  `count > 0 && currentFilter != "read"` (`NotificationsFragment.kt:101-102`)
  where the port gated on the count alone, offering an action with nothing to
  act on.

### The nine unpinned behaviours

Each could be reverted with all 2107 tests green. Now each is red.

| unguarded | why it matters |
|---|---|
| the numeric-entity range guard | `String.fromCharCode` throws above U+10FFFF, out of `build` — the whole bell screen, the Phase 95 shape |
| the `</b>`/`</i>` underflow clamp | a stray closing tag drove the counter negative and **every later emphasis in the row was lost** |
| the tag name splitting on `/` | `<br/>`, the conventional spelling, silently vanished instead of breaking the line |
| `flush()`'s leading-whitespace guard | a leading space before a tag survived into the row |
| `'\r'` in the collapsing set | the code and its own comment disagreed about which characters collapse |
| `notificationFormatContextProvider` | **no test at all** — the repository lookups, the context builder and the tile were each covered and nothing joined them. Phase 74's shape exactly. A screen test now drives provider → repository → DAO → row with nothing overridden between |
| the by-title/by-id merge order | the recording stub returned disjoint keys, so no collision existed to observe; a colliding stub now pins that an id wins |
| the empty-string `relatedId` paths | the documented `mapNotNull`-vs-`isNullOrEmpty` asymmetry had no test, and a documented quirk with no test is what a later cleanup deletes |
| four row behaviours | the unread row's opacity, the tap, the swipe, and *Mark all read* firing |

### The claims that were wrong

Corrected in place above, and worth listing because each was written
confidently: `yesterday` is **not** in the ARB (it is in the Kotlin, and the
timestamp follow-up needs a new key); the `exactly = 0` citation was about a DAO
in a different test at a different layer and the port already satisfies it; the
row layout does carry a checkbox and a button, so "a title and a timestamp and
nothing else" was wrong in a section that four pages later says the opposite;
the port draws a per-row icon that the citation used against the type label
rules out just as firmly; and "nothing writes a raw `join_request` row" is true
of the writers, not of the corpus.

One divergence the audit surfaced is now a *documented decision* rather than an
accident: Kotlin's `getTeamNamesByIds` substitutes the literal `"Unknown Team"`
for a cached team row with a null name (`TeamsRepositoryImpl.kt:433`), so it
renders a bold **Unknown Team:** prefix; the port drops the entry and renders
the sentence unprefixed. Saying less beats saying something wrong, and the
comment at the lookup now says so.
