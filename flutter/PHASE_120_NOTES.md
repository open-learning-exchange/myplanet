# Phase 120 — the submissions and notifications sync-in shape

Work in progress. This lane takes Phase 116's D5, D6 and D7:

- **D5 / D6** — the submissions sync-in shape (`userId` derived from the nested
  user object, `deleteNotIn` keyed on CouchDB ids while local rows carry a sha1,
  and `parent`/`user` stored as Dart map literals).
- **D7** — `NotificationParser.resolveType` has no caller, so every
  server-originated notification is stored with its raw server type and is
  therefore mislabelled and unactionable.

Notes are filled in as the work lands.
