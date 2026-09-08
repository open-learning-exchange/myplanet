# Phase 148 — the outbox's permanent-refusal policy (Lane C)

Two jobs, both policy calls that three consecutive rounds reported and could
not take:

1. A health examination whose upload answers `id` without `rev` is abandoned on
   its first attempt and then re-POSTs forever
   (`PHASE_142_NOTES.md` *Reported, not fixed* item 6, and the `1004e90`
   amendment it points at).
2. A permanent refusal accretes one dead outbox row and one doomed POST per
   sync, unbounded, across all twenty uploaders
   (`PHASE_138_NOTES.md` *Reported, not fixed* → "The abandoned-row accretion
   Phase 134 reported now has a twentieth instance").

*In progress — this file is written as the lane goes.*

## Reported, not fixed

*(to be filled in)*
