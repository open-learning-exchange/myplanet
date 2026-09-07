# Phase 127 — the bell row's timestamp, and selection mode

Phase 124's two self-reported items, in the order it recommended: the timestamp
first, because it is wrong on every row, and selection mode second, because it
is additive.

Two `parity-auditor` passes at `effort: max` were run, one on the Kotlin before
any Dart and one on the finished diff. **The second did not finish** — it hung
on a probe of its own that drove the real screen through `pumpAndSettle`, which
is the documented trap for this port (the loading `CircularProgressIndicator`
spins it to its ten-minute default), and it was stopped after ~50 minutes. Its
injections up to that point were replayed by hand and are in the table below;
two of them found tests that were not load-bearing. What a completed pass would
still have covered — the `_selectedIds` lifecycle across a stream re-emit, and
the two `getByIds`-then-write pairs — is named in *Reported, not fixed*. The first overturned a conclusion this lane had
already written into a code comment, and the reason it could is worth stating
plainly: **the lane's own first reading of the Kotlin was wrong, in exactly the
way this document keeps warning about.** See *The reading that was wrong* below.

---

## Item 1 — what each bucket formats to

`NotificationsAdapter.ItemViewHolder.formatRelativeTime`
(`NotificationsAdapter.kt:152-163`) and `getDateFormatter()` (`:43-53`). `diff`
is `System.currentTimeMillis() - createdAt`, computed once per bind; the
absolute arm formats **`createdAt`**, not `diff`.

| `diff` | arm | English |
|---|---|---|
| `< 60_000` | `just_now` | `Just now` |
| `< 3_600_000` | `minutes_ago`, `diff / 60_000` | `1 min ago` … `59 min ago` |
| `< 86_400_000` | `hours_ago`, `diff / 3_600_000` | `1 hr ago` … `23 hr ago` |
| `< 172_800_000` | `yesterday` | `Yesterday` |
| `< 604_800_000` | `days_ago`, `diff / 86_400_000` | `2 days ago` … `6 days ago` |
| otherwise | `MMM d, yyyy` of `createdAt` | `Aug 31, 2026` |

Three things the arithmetic decides, all pinned:

* **No bucket can render a zero.** Each arm's lower bound is the divisor, so
  integer division yields 1 or more. `0 min ago` is unreachable.
* **`days_ago` renders 2..6 — never 1, never 7.** The `yesterday` arm owns the
  whole of day one, and 7 days *exactly* is `else`, since the comparison is `<`.
  So the ungrammatical `1 days ago` that the Kotlin string would produce never
  renders — by the arrangement of the arms, not by anything in the string.
  Phase 124's note called the bucket "2–7 days", which is off by the upper
  bound.
* **A future `createdAt` reads "Just now".** `diff < 60_000L` is satisfied by
  every negative value, so clock skew lands in the first arm and no `~/` ever
  sees a negative dividend. This is reachable: `parseNotification` takes
  `createdAt` from the *server's* `time` field, and these devices are offline
  with drifting clocks.

The >7-day arm **drops the time of day**, which the port's
`DateFormat.yMMMd().add_jm()` did not.

## `relative_time.dart`: a second formatter, not an extended one

Phase 124 flagged `relativeTimeLabel` as "not a drop-in" and was right, but the
reason it gave — different buckets — is the weakest of the three. The decisive
one is that **the Android app renders two different sentences**:

| | ports | source of strings | 5 minutes |
|---|---|---|---|
| `relativeTimeLabel` | `TimeUtils.getRelativeTime` → `DateUtils.getRelativeTimeSpanString` | the Android **framework**'s own strings | `5 minutes ago` |
| `notificationTimestampLabel` | `NotificationsAdapter.formatRelativeTime` | **`strings.xml`**, `minutes_ago` = `%1$d min ago` | `5 min ago` |

Plus: no `Yesterday` bucket and no absolute arm in the former. Folding the
notification row into `relativeTimeLabel` would have had to change the wording
on either the bell or the dashboard/profile, and both changes are wrong. So:
two functions, **in the same file**, with the difference documented at the
second one — because two relative-time formatters in two files is precisely how
the port ended up with two disagreeing `normalizeText`s (Phase 78).

Both take the two instants as parameters rather than reading the clock, which is
the shape `relativeTimeLabel`'s callers already use (they subtract at the call
site) — and the absolute arm needs `createdAt` itself, not just the elapsed
time.

### The ARB keys: why three of them could never derive

`justNow` was already the Kotlin `just_now`, translated in all five locales. The
other three were **the port's own ICU plurals** (`{count, plural, =1{1 minute
ago} …}`) sitting under `minutesAgo`/`hoursAgo`/`daysAgo` — the camelCase names
of Kotlin's single-form strings. `tool/arb_from_strings_xml.dart` skips plurals
outright and says why (filling `other` from the Kotlin and leaving `=1` in
English puts two languages in one rendered string), so the tool could see a name
match and had to refuse it. Every locale carried bare English, flagged `x-mt`.

The fix is a rename, and it is contained: **the plurals moved to
`relativeMinutesAgo`/`relativeHoursAgo`/`relativeDaysAgo`** (values and `x-mt`
flags intact — nothing was lost, they were English), and the Kotlin names now
hold the Kotlin strings. Then the derivation ran.

### Did the `yesterday` translations come across? All four keys, all five locales

Yes — **20 human translations already shipping in the Android app, for free.**
`yesterday` derived by name (rule 1); the three format strings derived through
Phase 121's placeholder path, which lines `%1$d` up with `{count}` by argument
index.

| ARB key | from | ar | es | fr | ne | so |
|---|---|---|---|---|---|---|
| `yesterday` | `yesterday` | `أمس` | `Ayer` | `Hier` | `हिजो` | `Shalay` |
| `minutesAgo` | `minutes_ago` | `منذ {count} دقيقة` | `Hace {count} min` | `Il y a {count} min` | `{count} मिनेट अघि` | `{count} daqiiqo ka hor` |
| `hoursAgo` | `hours_ago` | `منذ {count} ساعة` | `Hace {count} h` | `Il y a {count} h` | `{count} घण्टा अघि` | `{count} saacad ka hor` |
| `daysAgo` | `days_ago` | `منذ {count} أيام` | `Hace {count} día(s)` | `Il y a {count} jour(s)` | `{count} दिन अघि` | `{count} maalmood ka hor` |

Note `ne`/`so` put the count *first* and the Semitic/Romance locales put it
after a preposition — the reordering Phase 121 exists for.

### One divergence that survives, and one that was fixed on audit

**Digits diverge.** Kotlin reaches the relative strings through
`Resources.getString(id, args)` → `String.format(configLocale, …)`, which
localises `%d`, so an Arabic device shows `منذ ٥ دقيقة` in Arabic-Indic digits;
`gen-l10n` emits a bare `'$count'`, so the port shows ASCII. It cannot be
reproduced faithfully even with `NumberFormat`, because `intl`'s own `ar`
symbols carry an ASCII zero digit while `ne`'s carry `०`. The test that
asserts the Arabic strings now **says so in a comment**, because otherwise it
reads as evidence of parity on the one point where the apps differ.

**The absolute arm's locale was fixed.** The first draft passed no locale and
justified it with "`initializeDateFormatting` is never called, so a locale
argument would throw for `ar`/`ne`/`so`". That was wrong for two of the three:
`flutter_localizations` registers the date tables through
`initializeDateFormattingCustom`, and `ar` and `ne` both resolve. Measured, in a
widget test with the app's real delegates:

```
en -> Aug 31, 2026     ne -> अगस्ट ३१, २०२६
ar -> أغسطس 31, 2026    so -> THREW ArgumentError: Invalid locale "so"
es -> ago 31, 2026     fr -> août 31, 2026
```

So the arm now formats with `l10n.localeName`, cached per locale as Kotlin
caches per `Locale`, and falls back to the unlocalised formatter on any failure.
Somali is the only locale that needs the fallback (it is in neither
`flutter_localizations`' generated table nor `intl`'s own
`date_symbol_data_local`), and it gets English month names where the Kotlin gives
it `Abr`/`Lul`.

The `catch` is deliberately **broad**, not a `so` allowlist and not one exception
type: `intl` raises `ArgumentError` for a locale missing from a registered set
and `LocaleDataException` when nothing is registered at all — a bare unit test
sees the second. A date is not worth an exception escaping `build`, which is the
Phase 95 shape.

Kotlin renders ASCII digits in this arm for *every* locale, because
`DateTimeFormatterBuilder.toFormatter(Locale)` uses `DecimalStyle.STANDARD`
rather than the locale's; `intl` uses the locale's, so Nepali reads
`अगस्ट ३१, २०२६` against Kotlin's `अगस्ट 31, 2026`. Nepali digits in a Nepali
sentence are not a defect, and chasing byte parity would mean reproducing a Java
quirk that the Kotlin's own relative arms already contradict — the Android app
mixes numbering systems inside one list.

---

## The reading that was wrong

Phase 124 recorded that `markAsRead` stamps `createdAt = now` "faithfully", and
asked this lane to establish whether Kotlin does the same before treating it as
a bug. This lane's first answer was **no, it is a defect** — and wrote a test
group, a DAO method and three doc comments on that basis. It was wrong, and the
ground-truth audit is what caught it.

There are **two** Kotlin `markAsRead` DAO queries differing in exactly one
column, and two repository methods over them, belonging to two different
screens:

| DAO | stamps `createdAt`? | repository method | reached from |
|---|---|---|---|
| `markAsRead(notificationId)` (`NotificationDao.kt:15-16`) | **no** | `markNotificationAsRead(id, userId)` (`:36-43`) | `DashboardActivity.markDatabaseNotificationAsRead` (`:701-703`) — a tap on an Android tray notification's *body* |
| `markAsRead(ids, createdAt)` (`:45-46`) | **yes** | `markNotificationsAsRead(Set)` (`:113-120`) | the notifications screen, and the tray's *Mark as Read* action button (`NotificationActionReceiver.kt:81`) |
| `markAllUnreadAsRead(userId, createdAt)` (`:48-49`) | **yes** | `markAllUnreadAsRead` (`:122-128`) | *Mark all read* |
| `markSummaryAsRead(userId, type)` (`:12-13`) | **no** | the `summary_` branch of the first | the tray |

`NotificationsViewModel.markAsRead(id)` (`:207-209`) calls
`markNotificationsAsRead(**setOf(id)**)` — the bulk, **stamping** one. So the
row's *Mark as read* button and a row tap **do** restamp `createdAt` in the
Android app. It is a quirk, not a bug to fix: the list is sorted
`isRead ASC, createdAt DESC`, so a read row jumps to the top of its group and,
now that the timestamp is relative, reads "Just now". Both apps have to share it
or their lists order differently. Kotlin even has a path that shows it
immediately — the tray action stamps and then broadcasts
`NOTIFICATION_READ_FROM_SYSTEM`, which `DashboardActivity` turns into a full
`loadNotifications`.

The port had **one** method blending the two: the summary branch of the tray
handler with the stamping of the screen's. The screen accidentally matched
Kotlin; the tray path did not. Both are now separate:

* the screen calls `markAsRead({id})`, which stamps and returns the ids that
  existed (Kotlin filters through `getIdsByIds` and its caller gates on the
  result);
* `markNotificationAsRead` keeps its `summary_` branch and no longer stamps,
  over a new `NotificationDao.markOneAsRead`.

**`markNotificationAsRead` now has no caller in the port**, because
`NotificationActionReceiver` and the dashboard's tray handling are unported. It
is kept rather than deleted, and pinned by tests, precisely because the screen
was wired to it by mistake: the phase that ports tray actions needs it to be
right, and a silently-stamping tray path is the defect that was already there.
Recorded here rather than left for an audit to find — see *Reported, not fixed*.

**And the stamp is uploaded.** `TransactionSyncManager.kt:464-503` PUTs
`addProperty("time", notification.createdAt.time)`, the stamped value, and the
port's `syncNotificationReads` sends `'time': notification.createdAt`. So
reading a notification rewrites the server document's time in both apps and the
"Just now" propagates cross-device. At parity, and worth knowing before someone
reports it as a port bug.

One behavioural difference the reactive stream creates, not this phase:
`notificationsProvider` watches the drift stream, so a just-read row **moves
immediately** in Flutter, where Kotlin's ViewModel updates its in-memory copy
with `notif.copy(isRead = true)` and keeps the old `createdAt` and the old
position until the next reload. Visible before this phase too — the port already
drew the stamped date — but it is louder now that the text changes as well.

---

## Item 2 — selection mode

`NotificationsAdapter.bind`'s `isSelectionMode` branch (`:129-149`),
`_selectedIds` and the three actions over it
(`NotificationsViewModel.kt:39,145-153,173-205`), and the bulk bar
(`NotificationsFragment.kt:104-107`, `fragment_notifications.xml:44-93`).

Held as **local widget state** in a `ConsumerStatefulWidget`, following Phase
50's resource catalog (`resources_screen.dart:35`) rather than inventing a second
pattern — and, like the Kotlin, **there is no mode flag**: `isSelectionMode` is
`_selectedIds.isNotEmpty()`, derived. That single fact is what makes "deselect
the last row" an exit, in both apps, with no code for it.

| Kotlin | port |
|---|---|
| long-press any row (`:145-148`) | `ListTile.onLongPress` |
| `cbSelect` visible, `clickable="false"` (`row_notifications.xml:33-42`) | a non-interactive `Checkbox` as the tile's `leading`, replacing the port's icon |
| `btnMarkAsRead` hidden (`:132`) | `trailing: null` while selecting |
| row tap toggles; long-press listener set to **null** (`:133-134`) | `onTap` toggles, `onLongPress: null` |
| `ltBulkActionBar` shown, `ltTopBar` hidden (`:104-107`) | the AppBar becomes count + *Mark read* + *Delete* + close |
| `tvSelectedCount` = `selected_count` | `l10n.selectedCount(n)` |

Kotlin's `ltTopBar` holds **both** *Mark all as read* and the all/read/unread
spinner, so selection mode hides the filter too. That is not cosmetic: it is what
stops the quirk below from being trivially reachable.

### The quirks reproduced rather than corrected

Each of these is Kotlin behaviour, read from source (there is almost no Kotlin
test coverage here — `NotificationsViewModelTest.kt:216-233` covers the toggle
and nothing else), and each has a test saying so:

* **A read row can be selected**, and re-marking it is not a no-op:
  `markNotificationsAsRead` does not filter on `isRead`, so it re-stamps
  `createdAt` and re-flags `needsSync` for every selected server row. Gating
  selection on unread would be an improvement, not a port.
* **A group header stays selection-unaware** — `NotificationListItem.Header`
  carries neither `isSelected` nor `isSelectionMode`, and `HeaderViewHolder.bind`
  wires only the expansion toggle. So a group can be collapsed while its rows are
  selected: the bar still counts them and the bulk action still acts on rows the
  user cannot see.
* **There is no *select all***, no back-button handling (no
  `OnBackPressedCallback` in the fragment; `resources_screen.dart` has no
  `PopScope` either, so this is parity by omission) and **no confirmation on the
  bulk delete**.
* **`deleteSelected` is a plain local `DELETE`.** Nothing is enqueued, no
  tombstone, no `_deleted` document — `UploadConfigs` mentions notifications
  nowhere — and the notifications sync-in deliberately runs no `deleteNotIn`
  (Phase 98's reasoning: a prune would evict the locally-authored count/storage
  rows that have no server document). So **a deleted server-originated
  notification comes back on the next sync.** A round-trip test states it rather
  than leaving it to be discovered.

### One deliberate difference

The port's **swipe-to-delete is not a port of anything** — Kotlin has no
`ItemTouchHelper` anywhere and deletes only through the bulk bar. It stays
(Phase 124 shipped it), but it now **yields** while selecting
(`DismissDirection.none`), on the same reasoning that makes Kotlin disarm the
long-press there: while a selection is open the row's gestures belong to the
selection. The row's own confirmation dialog is also why the bulk delete has
none — the swipe confirms because it is a Flutter gesture that can fire by
accident, and Kotlin has no dialog to copy for the button.

### l10n, and a fourth key repaired

`selectedCount`, `markSelectedAsRead` and `cancelSelection` are new and derive
from `selected_count`/`mark_selected_as_read`/`cancel_selection` in all five
locales. *Delete* reuses the existing `delete` — which is Kotlin's
`delete_selected` text exactly (`Delete`), where the port's existing
`deleteSelected` key says "Delete Selected" and belongs to the storage screen.

`selectedCount` is deliberately **not** the existing `storageSelectedCount`,
though its English is identical and `resources_screen.dart` reuses that one:
Kotlin ships two strings here and they disagree in Arabic
(`تم تحديد %1$d` against `%1$d محدد`). The notifications screen shows what
Kotlin's notifications screen shows.

And `markAllRead` was repaired. It was named and worded just differently enough
from Kotlin's `mark_all_as_read` — "Mark all read" against "Mark all as read" —
that **neither** derivation rule could fire: the camelCase of the Kotlin name is
`markAllAsRead`, and the English did not match either. Renamed, with Kotlin's
exact English. Nepali and Somali had no value at all; Arabic and French carried
`x-mt` machine output, so those were deleted and re-derived (the documented
workflow, and a repair — machine output replaced by the human translation
shipping in the Android app). French's machine output happened to *equal* the
human translation, so only its flag changed; Arabic's did not. **Spanish already
held the Kotlin value unflagged and was left alone** — replacing one valid
translation with another is not a repair. Hence es +3 where the rest are +4.

`test/l10n/placeholder_integrity_test.dart`'s pinned counts move to
ar 410, es 462, fr 409, ne 411, so 411.

---

## Failing-first evidence

Item 1 is a behaviour change, so each claim was demonstrated red first. Item 2
is additive; its tests are new coverage of new code, and the reverts below are
what makes them load-bearing rather than incidental.

The starting point the brief asked for — a screen test asserting the row shows
an absolute date today:

```
a row older than a week reads as a date with no time of day
  Expected: exactly one matching candidate
    Actual: _TextWidgetFinder:<Found 0 widgets with text "Aug 8, 2026": []>

a recent row reads as a relative time, not an absolute date
  Expected: exactly one matching candidate
    Actual: _TextWidgetFinder:<Found 0 widgets with text "5 min ago": []>
```

And the mark-as-read split, red on the pre-fix repository:

```
the tray path leaves createdAt alone, because that SQL has no createdAt
  Expected: <500>
    Actual: <1788776537131>          // the wall clock, over the row's real age

the screen path restamps, because the Kotlin does
  Expected: Set:['task-1', 'task-2']
    Actual: <2>                      // returned a count, not the ids that existed
```

Every revert below was replayed and confirmed red, then restored.

| revert | red |
|---|---|
| the row draws `DateFormat.yMMMd().add_jm()` again | 2 screen tests |
| the tray path stamps `createdAt` | 2 |
| the bulk path returns a count instead of the ids that existed | 2 |
| the absolute arm passes no locale | 1 |
| the absolute arm loses its unknown-locale fallback | 1 (`so` throws out of `build`) |
| `relativeTimeLabel` left pointing at the Kotlin-named keys | 2 |
| `markAllAsRead` stamps outside its `isRead = 0` scope | 2 |
| the `days_ago` boundary widens from 7 days to 8 | 1 |
| the swipe stays armed in selection mode | 1 (**after the test was fixed** — see below) |
| the row's checkbox takes a non-null `onChanged` | 1 (**added below**) |

The `relativeTimeLabel` one is the one to remember, because **it compiles and
`flutter analyze` reports no issues**: the old key generated
`String minutesAgo(num count)` and the new one `String minutesAgo(int count)`,
and `elapsed.inMinutes` satisfies both. Left unrepointed, the dashboard's
last-sync strip and the profile's last-login row would have switched silently to
`5 min ago`, and `relativeTimeLabel(l10n, day)` would have rendered the
ungrammatical `1 days ago` — which **its** day bucket starts at, where the
notification row's cannot reach it. A rename with no compiler error is exactly
the shape that ships.

### Two tests that were not load-bearing

Green is not evidence, so each guard was re-checked by **injecting the defect it
claims to catch**. Two survived, both in selection mode.

**The swipe guard.** `swiping is disabled while selecting` asserted only that
nothing was deleted — and nothing is deleted either way, because an armed swipe
reaches `confirmDismiss`, opens the confirmation dialog and returns false. The
test passed for the wrong reason. It now drags **outside** selection mode first
as a control (the dialog appears, and is dismissed), then drags inside it and
asserts **no dialog** — which is what distinguishes "the swipe was never armed"
from "the swipe was armed and declined".

**The checkbox.** Nothing pinned `onChanged: null`. A Checkbox with a non-null
handler *absorbs* the tap rather than letting it reach the row, so the one
control that looks most tappable in selection mode would have done nothing at
all. Kotlin's `cbSelect` is `clickable="false"` `focusable="false"` and the row
owns the click. A test now taps the checkbox and asserts the row toggled.

### One injection that turned out to be a non-difference

Arming `onLongPress` in selection mode leaves the suite green, and **that is
correct** rather than a gap. With `onLongPress: null` no long-press recognizer
is registered, so a press-and-release is recognised as a *tap* and toggles;
armed, the long-press handler toggles. Same outcome. Android agrees —
`setOnLongClickListener(null)` leaves the click listener active, so a long press
there also falls through to `onClick`. The `null` is a faithful transcription of
`NotificationsAdapter.kt:134` with no observable consequence, and the test
written to pin it was deleted rather than kept asserting a premise that is not
true of either app.

---

## Reported, not fixed

1. **`NotificationsRepository.markNotificationAsRead` has no caller.** It is the
   correct port of Kotlin's tray/dashboard handler, and it is now correct where
   before it silently stamped — but `NotificationActionReceiver` and
   `DashboardActivity.handleNotificationIntent` are unported, so nothing reaches
   it. Under this document's reachability doctrine that is a "ported, tested,
   green and dead" row, and it is named here rather than left for an audit. The
   argument for keeping it: the screen *was* wired to it, the summary branch is
   real Kotlin, and deleting it means the next phase re-derives the same
   distinction from scratch. `NotificationDao.markSummaryAsRead` was already in
   this position before this phase.
2. **`ARB` derivation still loses a deliberate trailing space** on the
   plain-text by-name path (`tool/arb_from_strings_xml.dart:211` writes
   `translated[exactNamed]?.trim()` without `_mirrorTrailingSpace`). Phase 124
   reported it and declined on ownership grounds; this lane owns `tool/` and
   still declines, for a different reason: no key this phase adds goes through
   that path with a trailing space, so a fix would be untested by anything here.
   It wants its own change with a key that demonstrates it.
3. **Every date in the port except this one is `en_US`.** `DateFormat` is
   constructed without a locale in `edit_achievement_screen.dart:731` and
   `task_deadline_notifier.dart:59`, and `initializeDateFormatting` is never
   called — the notification row works only because `flutter_localizations`
   registers the tables as a side effect. Fixing it properly is one
   initialization call plus a sweep of every date in the app, which is a phase,
   not a hunk. This phase leaves the app inconsistent on purpose: one screen
   right beats every screen wrong.
4. **The read row moves immediately in Flutter** and only on the next reload in
   Kotlin, because the port's list is a drift stream and Kotlin's is a
   `StateFlow` of an in-memory copy. Not introduced here and not fixable without
   giving the port its own in-memory shadow of the list, which would be a worse
   trade.
5. **A `%d` in a relative string renders ASCII digits** where an Arabic or
   Nepali device shows its own. Explained above; `intl` cannot reproduce the
   Kotlin's per-locale choice in either direction.
6. **The second audit pass did not complete**, so two areas are less examined
   than the rest and are named rather than implied. First, the `_selectedIds`
   lifecycle against a *live* stream: the tests drive selection over a
   `Stream.value`, so nothing exercises a re-emit that removes a selected row
   mid-selection. Kotlin's equivalent quirk is known and reproduced by
   construction (`loadNotifications` never clears `_selectedIds`, and the port
   holds the set outside the stream the same way), but it is reasoned, not
   measured. Second, `markAsRead` and `deleteNotifications` both read
   `getByIds` and then write, which is two statements where Kotlin also uses
   two (`getIdsByIds` then the update) — so the interleaving is at parity, but
   neither app takes a transaction and nothing here tests concurrent callers.
