# Phase 150 — `my_library` preservation, and four corrections in `app_database.dart`

Lane A of a four-lane round. Five items, all landing in
`flutter/lib/data/local/app_database.dart` and its neighbours:

1. **The schema bump destroys resources the user created offline** and leaves
   `resourceOffline` false on every downloaded resource
   (`PHASE_146_NOTES.md` *Reported, not fixed* item 1). Outranks the rest.
2. The challenge tally excludes replies where Kotlin's counts them
   (`PHASE_147_NOTES.md` item 1).
3. Three false dartdoc claims on those same queries (`PHASE_142_NOTES.md`
   item 5, `PHASE_147_NOTES.md` item 2).
4. `HealthExaminationDao.markUploaded(id, null)` erases a stored `_rev`
   (`PHASE_148_NOTES.md` item 2) — a blocker for Lane C.
5. The `surveys` entry in `_localAuthorityTables` carries a claim its own phase
   retracted (`PHASE_143_NOTES.md` item 4).

Notes are written as the work lands; the *Reported, not fixed* section is at the
bottom.
