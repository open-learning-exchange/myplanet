# Phase 152 — a 409 should be recovered, not merely survived (Lane C)

Phase 148 made a 409 **safe**: one row, one POST, terminal, re-armed only by a
changed request. This phase makes it **recovered**, by porting
`UploadCoordinator.kt:169-204` — GET the document that already exists, adopt its
`_rev`, and report the upload as the success it effectively was.

*Notes are written as the work proceeds; this file is a stub until the design
section lands.*
