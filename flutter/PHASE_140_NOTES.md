# Phase 140 — the chat share (Lane C)

Kotlin's chat history can **share a conversation into the voices feed** — to a
team, an enterprise, or the planet community. The port had none of it. Phase 133
checked upstream `7167684` ("smoother chat history model payload sharing"),
found the port unaffected, and said why: not because the port matches, but
because `ChatSharePayload.buildShareMap` has no counterpart at all.

## What the share actually is

Not a system share sheet, and not a chat-to-chat forward. A shared chat becomes
a **`news` document** — the same table the voices feed reads — with `chat: true`
and the whole conversation embedded under a nested `news` object:

```
ChatHistoryAdapter (share icon)
  → target dialog → team/enterprise picker → note dialog
  → ChatSharePayload.buildShareMap(chat, note, team, section, nowMillis)
  → ChatViewModel.shareChatToVoices
       ├ VoicesRepositoryImpl.isAlreadyShared(chatId, viewInId) → snackbar
       └ VoicesRepositoryImpl.createNews → News.createNews → newsDao.upsert
  → UploadManager.uploadNews (a full-table sweep, on the next sync)
```

So the repository is `VoicesRepository`, not `ChatRepository`, and the upload
path is the voices one. Every column the payload fills — `newsId`, `newsRev`,
`newsUser`, `aiProvider`, `newsTitle`, `conversations`, `newsCreatedDate`,
`newsUpdatedDate`, `chat` — **already existed** on the port's `NewsEntries`
table and was already written to the wire by `VoicesRepository.serialize`. What
was missing was a writer, and everything upstream of it. No schema bump needed.

## What landed

| Piece | Where | Ports |
|---|---|---|
| `buildChatShareMap`, `ChatShareTarget`, `ChatShareSection` | `lib/repository/chat_repository.dart` | `model/ChatSharePayload.kt`, the three `TeamSummary` fields the share reads |
| `createFromShareMap`, `isAlreadyShared`, `planetNewsMessages`, `extractSharedViewInIds` | `lib/repository/voices_repository.dart` | the `map["news"]` branch of `News.createNews`, `VoicesRepositoryImpl:77-81`, `NewsDao.getPlanetMessages`, `ChatRepositoryImpl:288-315` |
| `chatShareTargetsProvider`, `sharedChatDestinationsProvider`, `ChatShareActions` | `lib/providers/chat_provider.dart` | `ChatViewModel.loadShareTargets` / `shareChatToVoices` / the fragment's gate |
| share affordance + three dialogs | `lib/ui/chat/chat_history_screen.dart` | `ChatHistoryAdapter.bindShareChat`, `showGrandChildRecyclerView`, `showEditTextAndShareButton` |
| 7 English keys | `lib/l10n/app_en.arb` | `share_chat`, `share_with_team_enterprise`, `add_note`, `chat_already_shared_to_destination`, `join_team_first`, `join_enterprise_first`, plus a shared-confirmation string |

48 new tests in four files. Every claim below was mutation-tested: each piece
was reverted in turn and the expected test confirmed to fail (15 mutations, 15
caught — the list is at the bottom).

## The three reachability questions

**Who writes this table?** `createFromShareMap`, and nothing else. Before this
phase the chat columns on `news` had a mapper that read them, a serializer that
wrote them to the wire, and a viewer that could render them — and no writer at
all, which is why the round trip was green and dead.

**Can the writer produce values the reader's predicate matches?** This is where
the trap was. The already-shared marker is computed by
`extractSharedViewInIds(getPlanetNewsMessages(user.planetCode))`, and
`getPlanetMessages` is `WHERE docType = 'message' AND createdOn = :planetCode`.
So a share whose row omits `createdOn`, or writes any `docType` but `message`,
uploads correctly and is invisible to every "already shared" checkmark. Both
pairings have a test that drives the writer and then the reader —
`a share is found by planetNewsMessages and mapped to its chat` — and both fail
when the writer is mutated. Likewise `newsId` must be the *chat's* CouchDB id,
because that is what `isAlreadyShared` looks a row up by; sourcing it from the
outer map instead fails four tests across two files.

**Does anything navigate here?** The share icon on each chat row is the only
entrance, and `every chat row offers a share action` is the test that says so.
Renaming its key turns four widget tests red.

A fourth question this slice needed: **does anything deliver it?** Kotlin
uploads news by sweeping the entire table on every sync
(`getNewsForUpload` = `newsDao.getAll()` minus guests) — there is no queue, so
a share cannot be stranded. The port has no such sweep, so `ChatShareActions`
enqueues at write time, the shape `VoicesActions` already uses.
`a share writes the voices row and queues it for upload` asserts the outbox row
and its payload; deleting the enqueue turns it red.

## Two Kotlin lines deliberately not carried across

**`News.kt:187`'s `newsObj?.replace("=", ":")`.** A fossil from when the value
was a `HashMap.toString()` (`{_id=abc}`) rather than JSON. It is a **no-op in
Kotlin**, and not for any reason visible in the source: `JsonUtils.gson` is a
bare `Gson()`, whose `htmlSafe` default escapes `=` to `=` inside every
string, so the encoded payload contains no `=` to replace. Dart's `jsonEncode`
emits `=` raw. Porting the line would rewrite every `=` in the user's own title
and transcript — `2+2=4`, a URL query string, base64 padding — and CouchDB
would keep the damage. Dropping it *preserves* behaviour rather than diverging
from it. **My first draft of this note had the reasoning backwards** — it said
the Kotlin corrupts the text and the port declines to. It does not; the
ground-truth `parity-auditor` pass caught it by running Gson rather than reading
it. The behaviour is the same either way, but the note would have seeded a false
belief about the Kotlin, so it is corrected here and at the code.

**The localised `viewInSection`.** `ChatHistoryAdapter` passes
`context.getString(R.string.teams)` / `R.string.enterprises` /
`R.string.community` straight into the payload, so the value that reaches
CouchDB is whatever the handset's locale renders. Every reader on both sides
compares against the literal `"community"` (`isVisibleToUser`,
`calculateSortDate`, `deletePost`, and the port's ports of all three), so **a
community share made on a Spanish or French handset is invisible in the
community feed of both apps, permanently, in the stored document**. The port
writes the stable literals `community` / `teams` / `enterprises`, which is what
every other writer on both sides already does (`VoicesFragment` writes
`"community"`, `TeamsVoicesFragment` writes `"teams"`, the port's
`createTeamPost` writes `"teams"`). In English the only observable difference is
that `R.string.enterprises` is `"Enterprises"` with a capital E, and nothing
reads that value. This is the `9c037f2` lesson — routing on a localised display
string is the bug, not the baseline.

## Smaller divergences, each deliberate

- **A null-titled chat.** Upstream `7167684` changed `"${chatHistory.title}".trim()`
  to `(chat.title ?: "").trim()`; a Kotlin template of null renders the four
  characters `null`, so an untitled chat used to be shared under the title
  `"null"`. The **current** behaviour is ported and pinned, and reinstating the
  template turns two payload tests red.
- **A chat with no conversations.** Kotlin's `gson.toJson(null)` writes the
  string `"null"`, which `createNews` hands to `fromJson(..., JsonArray)`; that
  throws `JsonSyntaxException`, is caught, and leaves the column null. The port
  writes `[]`, whose consumer also leaves the column null. Same end state, no
  exception on the way.
- **`viewIn`'s `name` key is absent, not null.** `getViewInJson` writes
  `map["name"]`, which `buildShareMap` never sets; Gson's `serializeNulls` is
  off, so the key is dropped. This is load-bearing rather than cosmetic:
  `shareToCommunity` back-fills the first entry's name only when
  `!containsKey('name')` (Kotlin's `!obj.has("name")`, false for an explicit
  null), so writing `"name": null` would silently disable that back-fill for
  every post sharing the helper. `_viewInJson` now omits it, and a test shares a
  chat to a team and then to the community and asserts the name arrives.
- **A community target that does not exist.** Kotlin still renders and enables
  the community row when `shareTargets.community` is null (the row comes from a
  static map), so the user picks it, types a note, taps *share chat* — and
  nothing happens: no row, no upload, no snackbar, no log. The port does not
  render the row at all in that case (`a planet with no community offers no
  community entry`). Silence is not a behaviour worth porting.
- **The shared-destination cache is refreshed after a successful share.**
  Kotlin's `ChatHistoryFragment:225-235` appends the new `News` to its list but
  passes the *unchanged* `sharedViewInIds`, so the checkmark does not appear
  until a realtime sync signal rebuilds the screen — and a second share of the
  same chat walks the whole dialog flow again before the snackbar tells the user
  it was pointless. The port invalidates `sharedChatDestinationsProvider`. The
  "already shared" path stays reachable (another device, another user on the
  same planet, or a row whose `createdOn` differs from the viewer's planet code)
  and has its own test.
- **`ChatShareOutcome.unavailable` instead of a dropped tap.** The fragment's
  `if (chatId.isNotEmpty() && viewInId.isNotEmpty())` gate is kept — a chat the
  server has never seen cannot be shared, because the payload's `newsId` is what
  every duplicate check keys on — but the port says so in a snackbar rather than
  discarding the tap.

## Faithful quirks worth naming, because they look like bugs

- **The row's own `updatedDate` is `0`.** `createNews` reads `map["updatedDate"]`
  from the **outer** map, which `buildShareMap` never writes — the millis only
  go into the nested object. So `updatedDate` is 0 while `newsUpdatedDate`
  carries the share time. My first cut of the round-trip test asserted the share
  time here and failed; the *test* was wrong.
- **Three different timestamps.** `time` is `Date().time` inside `createNews`,
  `newsCreatedDate`/`newsUpdatedDate` are the dialog's `nowMillis` (one read,
  since `7167684`), and `updatedDate` is 0. Not collapsed.
- **Stringified millis.** Every value of the payload is a `String`, the nested
  `createdDate`/`updatedDate` included; `JsonUtils.getLong`'s
  `asString.toLongOrNull()` fallback is what turns them back into numbers, and
  they are numbers on the wire. `_shareMillis` ports that fallback, and removing
  it turns two tests red.
- **The nested `conversations` is a JSON array encoded into a string**, which is
  why `createNews` re-parses it out of a string primitive. A port that wrote a
  real array would store **null** conversations with nothing erroring.
- **`isAlreadyShared` and the checkmark cache disagree on purpose.**
  `isAlreadyShared` is a case-*insensitive* raw substring match on `viewIn`
  (`"_id":"<id>"`) with no planet scope; `extractSharedViewInIds` parses and
  compares case-sensitively over rows scoped to the viewer's planet code. Both
  are ported as they are, with a test on each axis. Unifying them would be a
  behaviour change.
- **`messageType` and `messagePlanetCode` are `""` for a community share**,
  because the community `TeamSummary` is synthesized with both null.
- **Team and enterprise eligibility is membership-scoped, and an empty
  membership set short-circuits to an empty list** rather than falling through
  to the whole catalog.

## Reported, not fixed

Four of these need a file another lane owns this round. Each names the file, the
change, and why.

1. **`teams` has no `teamPlanetCode` column**, so `messagePlanetCode` is `""` on
   every chat share the port sends. `MyTeam.teamPlanetCode` is read from the
   team document (`MyTeam.kt:86`) and is the third of the three `TeamSummary`
   fields the payload uses. Fix: a nullable `teamPlanetCode` text column on
   `Teams` in `lib/data/local/tables.dart`, populated in
   `lib/data/local/team_mapper.dart`, and a `schemaVersion` bump in
   `lib/data/local/app_database.dart` (**Lane A's file**, and no number is
   allocated to this lane). `ChatShareTarget.teamPlanetCode` already exists and
   is already tested with a non-null value, so only `_targetOf` changes.
   Severity: low — Kotlin sends `""` too for a team document that omits the
   field, and nothing in either app reads it back.
2. **`NewsDao` has no `getByNewsId` or `getPlanetMessages`.** `isAlreadyShared`
   and `planetNewsMessages` walk `getAll()` and filter in Dart instead. Same
   result set, more rows read — the Kotlin filters in memory after
   `getByNewsId` anyway. Fix: two queries on `NewsDao` in
   `lib/data/local/app_database.dart` (**Lane A's file**):
   `SELECT * FROM news WHERE newsId = ?` and
   `WHERE docType = 'message' COLLATE NOCASE AND createdOn = ? COLLATE NOCASE`.
3. **The `viewInSection` deviation is not in the migration tracker.**
   `docs/kotlin-to-flutter-migration.md` (**Lane A's file**) lists faithful
   quirks and deliberate deviations, and this is in neither. Suggested line, for
   the deviations list: *"`viewIn[].section` is written as the stable literal
   `community`/`teams`/`enterprises`. `ChatHistoryAdapter` passes the localised
   `strings.xml` value, so a community share from a non-English handset writes a
   translated section and is invisible to `isVisibleToUser`, which matches
   `"community"`."*
4. **A locally authored `news` row carries no `user` object.**
   `News.createNews` sets `news.user = gson.toJson(user.serialize())` and the
   uploaded document carries the whole user object; the port's `createPost` has
   never passed a `userJson`, so `serialize` writes `'user': null` for every
   post the port authors, chat shares included. `createFromShareMap` accepts a
   `userJson` parameter and is ready for it. **Do not wire `UserMapper.toDoc`
   into it** — that is the `_users` PUT body and carries `derived_key`/`salt`;
   Planet reads `user.name` and `user._id` off a news document, so this wants a
   small display-only projection. Pre-existing and port-wide, not introduced
   here; `lib/providers/voices_provider.dart` is the other caller.

Two more, outside anyone's file set:

5. **`chatShareTargetsProvider` scopes memberships by `session.id`** where the
   Kotlin uses `currentUser?._id`. Identical for a synced account (both are the
   CouchDB `_id`), different for one created offline, whose `id` is a random
   UUID. The port's own teams catalog uses `session.id`, so this follows the
   port's convention rather than the Kotlin's; worth one deliberate decision
   somewhere rather than two conventions.
6. **The port's "root team" predicate is `docType IS NULL` where Kotlin's is
   `teamId IS NULL OR TRIM(teamId) = ''`.** Pre-existing in `watchCatalog` and
   shared with the whole teams catalog; noted because the share now depends on
   it too.

## Mutations run

Each was applied alone to green code and reverted; all 15 were caught.

| Mutation | Caught by |
|---|---|
| `title` back to the pre-`7167684` template | payload: untitled chat, trimmed title |
| `chat` flag flipped | payload + round trip + wire doc |
| nested `conversations` written as raw text, not JSON | 4 tests across two files |
| empty conversation list stored as `[]` | round trip: column stays null |
| `createdOn` not written | 4 tests: the planet-scoped readers |
| `docType` not `message` | the same 4 |
| `newsId` sourced from the outer map | 4 across two files |
| `viewIn` writes an explicit `"name": null` | round trip incl. the back-fill pair |
| `isAlreadyShared` made case-sensitive | round trip: case-insensitive match |
| `_shareMillis` string branch removed | round trip + wire doc |
| duplicate check removed | provider: second share refused |
| enqueue removed | provider: outbox row |
| membership scoping removed | provider: 2 target tests |
| unsynced-chat gate removed | provider: `unavailable` |
| share affordance renamed | 4 widget tests |
