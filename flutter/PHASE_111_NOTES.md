# Phase 111 — the legacy health row, the 409 that eats a record, and making it visible

*In progress.* Picking up the decision Phase 107 handed to the integrator:
do the one-time data repair for pre-Phase-105 examination rows, at
`schemaVersion` 45, and make an unretryable outbox conflict observable
rather than silent.

Notes land here as the work does.
