# Phase 110 — the exam retry model (in progress)

Slice: port Kotlin's cannot-advance-past-a-wrong-answer exam model, so
`mistakes` accumulates and is uploaded, and a finished submission's answers are
all `isPassed = true` as a consequence. Plus the two companion decisions Phase
106 logged with it: per-answer `grade`, and the submission status
(`requires grading`).

Notes to follow.
