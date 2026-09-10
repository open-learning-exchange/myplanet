# Phase 108 — chat repository and screens: coverage, and the defects it found

## A correction to the brief before anything else

The task described `lib/repository/chat_repository_impl.dart` as having **no
tests at all**. It had 22, across `test/repository/chat_repository_test.dart`
(18) and `test/repository/chat_sync_test.dart` (4), plus 7 for
`ui/chat/chat_history_screen.dart`. The genuinely uncovered surfaces were
`savePendingChat`, `sendContinueChatRequest`'s failure paths,
`fetchAiProviders`, the mapper round trip, and all 240 lines of
`ui/chat/chat_detail_screen.dart` — and every defect below sits in one of
those.

The more useful lesson is the *opposite* of "untested code hides defects".
The worst defect here was in code that **was** tested. The test fixtures
spelled the server's response key the same wrong way the code read it, so
both halves agreed with each other and neither agreed with the server.

## Defects fixed, with failing-first evidence

Each was demonstrated failing on the pre-fix code before the fix was written.
Where a fix could be reverted independently, it was, and the failure
reproduced.

### 1. The CouchDB write receipt was read under a key the server never sends

`model/ChatResponse.kt:10` declares

```kotlin
@SerializedName("couchDBResponse") var couchDBResponse: CouchDBResponse? = ...
```

Gson's `@SerializedName` is the only authority on the wire format, and it says
the key is `couchDBResponse`. The port read `response['couchdb']`.

Consequences, both silent:

- `sendNewChatRequest` found no object, so no `id`, so it took its
  "saved without a document id" branch on **every successful answer** — the
  AI's reply was discarded, nothing was persisted, and the user saw an error
  banner. New chats could not be created at all.
- `sendContinueChatRequest` computed `newRev = …?['rev'] ?? rev` and so always
  kept the old revision. The next follow-up sent a stale `_rev`, which CouchDB
  rejects as a conflict; a conversation could not get past its second turn.

The port's own comment cites `couchDBResponse.id` two lines above the code that
reads `couchdb` — the field name was read off the Kotlin correctly and then
transcribed as a JSON key incorrectly. The three tracked fixtures
(`chat_repository_test.dart:75,95,129`) used `'couchdb'` too, so the suite
asserted the bug was correct. Those fixtures are now the real key.

**Failing first:** `a new chat adopts the id from couchDBResponse` →
`Expected: <Instance of 'ChatSuccess'> Actual: <Instance of 'ChatError'>`;
`a continuation advances the rev from couchDBResponse` →
`Expected: '2-b' Actual: '1-a'`.

**This is the round-trip shape the brief asked me to look for**, in a new
variant: not two halves of one app disagreeing, but the app and its own tests
agreeing with each other against the server.

### 2. A continuation the server never answered dropped the question

Kotlin calls `continueConversation(id, message, "", rev)` on **both** its
non-success branch (`ChatRepositoryImpl.kt:113`) and its exception branch
(`:117`). Only the `catch` had been ported — and `_postChat` converts every
`NetworkError`/`NetworkException` into `return null` rather than throwing, so
the offline send, the one case the behaviour exists for, took the unported
path. The question vanished with no row, no bubble, and no error beyond a
banner.

`_insertChatsInternal`'s `_hasUnansweredQuery` guard exists specifically to
stop a later sync overwriting that trailing empty-response row — so as
written, the guard was protecting something nothing ever wrote.

**Failing first:** `keeps the question when every retry fails` and
`keeps the question when the server answers a failure`, both
`Expected: an object with length of <2> … has length of <1>`.

### 3. Every offline chat was queued under the same primary key

```dart
final id = existingId ?? 'local-chat-\${DateTime.now().microsecondsSinceEpoch}';
```

Single-quoted with an escaped `$`, so this is not interpolation — the id was
the literal 51-character string `local-chat-${DateTime.now()…}`, identical for
every call. `chatDao.upsertAll` is `insertAllOnConflictUpdate`, so the second
message typed offline **replaced** the first, and since `existing` is only
looked up when `existingId != null`, the first question was not merged either.
It was gone before the outbox ever drained.

**Failing first:**
`Expected: not 'local-chat-${DateTime.now().microsecondsSinceEpoch}' / Actual:
'local-chat-${DateTime.now().microsecondsSinceEpoch}'`.

### 4. An offline message sorted to the bottom of its own history

`savePendingChat` wrote no `createdDate`/`updatedDate`, so
`sortChatsByRecency`'s `_maxDate` scored the row 0 and it landed below every
conversation the user had ever had. Kotlin writes a date on every persistence
path (`ChatRepositoryImpl.kt:253-254`, `:279-280`). A continuation now keeps
the created date it already has and moves only its updated date, matching
`addConversation`.

**Failing first:** `Expected: 'Just now' Actual: 'Capital of Iceland?'`.

### 5. An empty revision could erase a good one

Kotlin's `addConversation` assigns `_rev` only `if (!newRev.isNullOrEmpty())`.
`ChatDao.updateConversation` writes the column unconditionally, and
`newRev = …?['rev'] ?? rev` does not fall back on an empty string — so a reply
carrying `"rev": ""` would overwrite `1-a` with `''`, losing the handle the
next upload needs and making the row look never-confirmed to `deleteNotIn`.
The guard is at the call site, because the DAO lives in `app_database.dart`,
which this lane does not own.

**Failing first, with a caveat worth recording:** this test *passed* on the
pre-fix code, for the wrong reason — defect 1 meant the `??` always fell back
to the caller's non-empty rev, so the empty string could never arrive. It
fails as soon as defect 1 is fixed and the guard is absent (verified:
`Expected: '1-a' Actual: ''`). A test that passes only because of another
defect is not evidence of anything.

### 6. Sending could take the screen down on a keystroke

`_sendMessage` called `_scrollController.position.maxScrollExtent`
unguarded. `ScrollController.position` asserts when nothing is attached, and
nothing is: the message `ListView` is replaced by an empty-state `Column`
until there is at least one message, and a rebuild is a frame away rather than
an `await` away. `onSubmitted` bypasses the disabled send button, so pressing
enter with no session reached `_sendMessage`, the notifier declined the
message, no bubble was added, and the assert fired.

Now scrolled from a post-frame callback behind `hasClients`.

**Failing first:** `ScrollController not attached to any scroll views.` at
`chat_detail_screen.dart:234`, on three tests.

### 7. Tapping any chat in the history list opened an error page

```dart
context.push('${Routes.chat}/${chat.id}');
```

`Routes.chat` is already the *template* `/life/chat/:chatId`, so this pushed
`/life/chat/:chatId/<id>` — three segments where the router defines two. No
route matched and there is no `errorBuilder`, so every conversation in the
list opened go_router's error page. The list was decorative. The feedback list
one route down shows the convention: build the child path from the list's own
route, which is what this now does.

(The "new chat" button pushes the bare template and matches it *by accident*,
with `chatId == ':chatId'`. That works only because `loadChat` is broken —
see the handover items — so it is left alone rather than patched around; it
needs a route, which is not this lane's file.)

**Failing first:** `Found 0 widgets with text "detail:c1"`.

### 8. A chat with no stored title showed "Untitled chat"

`ChatHistoryAdapter.onBindViewHolder` names a row by
`conversations[0].query` and falls back to `item.title` only when there is no
first query. The port read `chat.title` alone — and `ChatMapper.fromDoc`
writes `null` when the document omits the field, which synced documents do.
So a row with the question sitting right there in its conversation displayed
as "Untitled chat". The `?` avatar for a genuinely nameless chat is
deliberately unchanged; only the title rule moved.

Two existing fixtures set a `title` that disagreed with the first query. Under
Kotlin's rule the query wins, so those fixtures now keep the two in
agreement — they were testing the title, not the precedence — and two new
tests make them disagree on purpose.

**Failing first:** `Found 0 widgets with text "How do I plant maize?"` and
`Found 0 widgets with text "the real question"`.

### 9. A newline reached the request body

`ChatDetailFragment.kt:288` sends
`"${binding.editGchatMessage.text}".replace("\n", " ")`. The port trimmed
only, on a `maxLines: 4` field, so a pasted or soft-keyboard newline survived
into `content`.

**Failing first:** `Expected: ['first line second line'] Actual: ['first
line\nsecond line']`.

## Tests added

- `test/repository/chat_write_back_test.dart` (14, new): the response
  envelope, both continuation failure paths, the revision guard,
  `savePendingChat`, `fetchAiProviders`, the `buildNewChatDoc` →
  `fromDoc` → `parseConversations` round trip, `_design` filtering, and the
  null/empty user-name guard.
- `test/ui/chat/chat_detail_screen_test.dart` (10, new — the screen had
  none): empty state, turn rendering, the error banner, the send button's
  trim/flatten/clear, the blank-message and no-session gates, the keyboard
  submit that used to crash, and the provider menu including an unavailable
  entry.
- `test/ui/chat/chat_history_screen_test.dart` (+3): navigation to the detail
  route, and title precedence in both directions.

Nothing was skipped or quarantined. No `app_en.arb` keys added; no schema
change; `schemaVersion` untouched at 44.

## Handover — confirmed defects this lane does not own

Found while auditing the slice (`parity-auditor`, opus/max, which also
overturned one of my hypotheses — see the end). All are in files assigned to
nobody this round, so they are reported rather than edited, per the
one-file-one-owner rule.

**`lib/providers/app_providers.dart` + `lib/core/utils/url_utils.dart` —
the AI endpoint is never reached.** `ChatApiService.sendChatRequest` POSTs to
`UrlUtils.hostUrl` *itself*, which is `"$scheme://$hostIp/ml/"` for `.org`/
`.gt` hosts and `"$scheme://$hostIp:5000/"` otherwise; `fetchAiProviders` GETs
`"${hostUrl}checkProviders/"`. The port is constructed with
`serverUrl = credentialFreeDbUrl(config)` — the CouchDB `…/db` URL — and posts
to `'$serverUrl/chat'`. For `http://10.0.0.5:5000` Kotlin posts to
`http://10.0.0.5:5000/` and the port to `http://10.0.0.5:5000/db/chat`, a
CouchDB path. `url_utils.dart` has no `hostUrl` counterpart at all;
`ServerConfig.serverUrl` keeps the raw typed URL, so one is buildable. Two
divergences (origin *and* path suffix) that both need fixing. Fixing defect 1
above is necessary but not sufficient — until this lands, no chat request
reaches a server that could answer it.

**`lib/providers/chat_provider.dart:205-211` — the queued question is
replaced by the error text.** `sendMessage(String message)` is shadowed by the
pattern binding in `case ChatError(message: final message):`, so
`savePendingChat(query: message)` receives `"Request failed"`. The bubble on
screen is right; the row written to `chat_history` and handed to the outbox
carries the error string as the user's question.

**`lib/providers/chat_provider.dart:108-115` — `loadChat` can never find
anything.** It calls `getChatHistoryForUser(null)`, which correctly returns
`[]` for a null name (matching `ChatRepositoryImpl.kt:120-125`), so `rows` is
always empty and the method returns without touching state. Both call sites
are affected. Because `chatConversationProvider` is not auto-dispose, opening
an older chat leaves the *previous* conversation on screen and appends the
next message to the wrong document. This is why fixing defect 7 alone does not
finish the job — the route now resolves, and the screen it lands on is still
blank.

**`lib/repository/chat_uploader.dart` — the outbox posts the wrong thing, and
the next sync then deletes the row.** `_serializeChat` sets
`'content': row.conversations`, the whole conversations JSON string, where
`ContentData.content` is the user's plain query; it hardcodes `model: ''`; and
the handler discards `data['chat']`, so the local turn keeps `response: ''`.
`markUploaded` sets `docId`/`rev` but leaves the primary key as the local id —
after which `insertChatHistoryFromSync` skips the server document (its
`_hasUnansweredQuery` lookup now matches the local row) and `deleteNotIn`
deletes the local one (it has a `rev` now, and its local id is not in
`savedIds`). The conversation disappears from the device on the first sync
after a successful drain. Also `data['couchdb']` at `:78` is the same wrong
key as defect 1.

**`lib/providers/chat_provider.dart:44-46` — full-conversation search
defaults to the wrong field.** `ChatHistoryFragment.kt:45-46` sets
`isFullSearch = false, isQuestion = false` and the layout checks no toggle
button, so `ChatViewModel.kt:153-159` maps the default to
`ChatSearchMode.RESPONSE`. The port defaults to `question`.

**`lib/providers/chat_provider.dart:190` — the revision is not re-read before
a continuation.** `ChatDetailFragment.kt:291-296` calls
`getLatestRev(_id) ?: _rev` before every send; the port sends
`currentState.rev`, so a revision advanced by a sync or an outbox drain is
missed and the send conflicts. `getLatestRev` is correct and has no production
caller.

Smaller, same rule: the provider auto-selects nothing where
`ChatDetailFragment.kt:449-455` clicks the first available provider (so the
port posts `{"name":"default","model":""}`); the search guard is `isEmpty`
where `ChatViewModel.kt:147` is `isBlank`; a model column would be needed on
`ChatEntries` to carry `aiProvider.model` through the outbox at all (a schema
change, hence not attempted).

Unported and undocumented as deferred: chat sharing to voices
(`extractSharedViewInIds`, the share target tree, `shareChatToVoices` —
`ChatHistoryAdapter.kt:137-283`), conversation pagination
(`ChatViewModel.PAGE_SIZE = 20` and `LOAD_MORE`), and the per-send
alternative-URL mapping.

Also noted, not changed: the port's `sync` runs `deleteNotIn` where the Kotlin
`chat_history` walk has no delete step at all — the same rule the notifications
sync follows. Its non-empty-`rev` guard makes it defensible in isolation, but
it becomes destructive in combination with the uploader item above.

## Two hypotheses of mine that were wrong

Recorded because the audit's most valuable output was the overturning, not the
confirmations.

- **I suspected the chat calls were missing an `Authorization` header.** They
  are not. `ApiInterface.chatGpt` and `checkAiProviders` take only
  `@Url`/`@Body`, and `NetworkModule` installs no auth interceptor — Kotlin
  sends no header here, and neither does the port. At parity; do not "fix" it.
- **I suspected `_searchByTitle`'s `isEmpty` skip diverged from Kotlin's
  `null` skip** (and the same in `_fullConvoSearch`). No behavioural
  difference for any non-blank query: where Kotlin gets `null` the port gets
  `''`, and `''` fails both `startsWith` and contains-all-words anyway. The
  only divergence reachable through it is the blank-query guard, listed above.
  Likewise Kotlin's `ignoreCase: true` is redundant on both sides, since
  `normalizeText` lowercases first.
