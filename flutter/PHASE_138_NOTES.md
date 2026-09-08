# Phase 138 — the adopted survey clone's lifecycle, and `pendingUploads`' missing status test

Lane A of a four-lane round. Two jobs, both handed over precisely by earlier
rounds' *Reported, not fixed* lists:

1. **An adopted team survey clone is destroyed on the next sync** (Phase 136's
   top item). Kotlin uploads the clone so it gains a `_rev` and joins every
   later walk's keep set; the port has neither the uploader nor a prune
   exemption, so `SurveyDao.deleteNotIn` deletes it and orphans its answer
   sheets.
2. **`SubmissionDao.pendingUploads` has no status test where Kotlin's sweep
   does** (Phase 134's top item). A behaviour decision, not an obvious repair.

Work in progress; this file is written as the phase runs.
