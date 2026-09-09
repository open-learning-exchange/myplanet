# Phase 151 — `""` and null are not the same thing

Lane B of a four-lane round. Brief: `PHASE_149_NOTES.md` § *Reported, not fixed*
item 1 — a status-less Planet submission unlocks a course step in Kotlin and
locks it in the port, because Kotlin's `JsonUtils.getString` stores `""` where
the port's `getStringOrNull` stores null, and `NOT (NULL = 'pending')` is NULL.

**Status: in progress.** This file is written as the phase goes; see the final
revision for what landed and § *Reported, not fixed* for the reach of the
divergence that this lane did not close.
