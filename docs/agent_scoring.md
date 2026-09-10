# Agent scoring — refactor task round (2026-09-10)

Benchmark of 14 task lists against `docs/refactor_tasks.md`. 6 agents submitted 2 lists each
(prompt 1 = performance quick wins, prompt 2 = repository boundaries); the two openhands agents
submitted prompt 1 only. 10 tasks per list.

## Method

- **verify first**: every raw task's premise was checked against the working tree at `022ee7d`
  (grep/read of the cited files, functions, line numbers and callers). A task whose load-bearing
  premise failed scores **0** and is listed under "Dropped" in the backlog.
- **merge**: surviving tasks that target the same defect were merged into one backlog entry.
- **credit**: each shipped backlog task is worth exactly **1 point**, split among the agents that
  proposed it. Unique = 1.0. Duplicated = split **by completeness of the submission** (evidence
  depth, correctness of the prescribed fix, caller/risk analysis), not by order — baseline 1/n,
  skewed to the more complete list. Typical splits: 0.5/0.5, 0.65/0.35, 0.8/0.2, 0.5/0.3/0.2.
- **normalize**: points ÷ tasks submitted, so the 10-task openhands lists compare fairly.
- **quality-weight**: Σ(share × rating/100), where rating is the backlog's
  0.40·evidence + 0.35·impact + 0.25·risk-adjusted-feasibility score.

## Table

| agent | model | submitted | dropped | pts | pts/sub | quality pts | qual/sub |
|---|---|---:|---:|---:|---:|---:|---:|
| claude | opus 5 | 20 | 0 | 19.80 | **0.990** | 16.25 | **0.812** |
| devin | swe 1.7 | 20 | 0 | 16.50 | 0.825 | 10.39 | 0.519 |
| copilot-kimi | kimi k3 | 20 | 2 | 15.65 | 0.782 | 10.39 | 0.520 |
| codex | sol 5.6 | 20 | 0 | 15.30 | 0.765 | 10.39 | 0.519 |
| copilot-grok | grok 4.5 | 20 | 2 | 13.90 | 0.695 | 10.43 | 0.521 |
| jules | gemini 3.1 pro | 20 | 7 | 13.00 | 0.650 | 6.08 | 0.304 |
| openhands-glm | glm 5.2 | 10 | 0 | 7.65 | 0.765 | 4.74 | 0.474 |
| openhands-deepseek | deepseek v4 flash | 10 | 5 | 4.20 | 0.420 | 2.85 | 0.285 |

Point-sum check: **106.000 / 106 shipped tasks** — every task's shares sum to 1.0.

## Reading the numbers

- **claude** is the outlier on both axes: zero dropped tasks across 20 submissions, every line
  reference exact, and by far the highest quality-weighted score (0.812 vs a 0.52 pack). It also
  found the two highest-rated items in the backlog (main-thread health decrypt, the
  `CoursesProgressAdapter` stale-listener bug) and was the only agent to consistently pre-check
  test call sites and PR-ownership conflicts.
- **devin / kimi / codex / grok** are a genuine four-way tie on quality (10.39–10.43). They
  differ in shape, not level: devin has the most accurate small findings and won most duplicate
  splits; codex's raw-count parity hides a weaker hit rate (its picks skew to micro-allocation
  work); kimi has the widest spread — the best single find outside claude (the PDF main-thread
  render) alongside two dropped tasks with actively wrong prescriptions; grok has the highest
  ceiling (Glide sizing, CouchDB `skip` paging) and two premises that dissolved on inspection.
- **jules** submitted 20 verifiable-but-trivial tasks: 7 dropped as literal no-ops (caching an
  O(1) `.size`, `mapNotNull` → `mapNotNullTo`), and most survivors are `Date().time` →
  `System.currentTimeMillis()` swaps rated in the 30s. High premise-accuracy, near-zero impact —
  hence rank 6 on points but last-but-one on quality per submission.
- **openhands-glm** punches above its submission count (0.765 pts/sub, matching codex on half the
  lists) — small, correct, well-evidenced utility fixes.
- **openhands-deepseek** is the weakest: 5 of 10 dropped, including two work orders that are
  explicitly self-cancelling ("if the body has zero user reads today, keep the task as a no-op
  verification") and one that prescribes `.copy()` on a non-data class. Its one strong find (the
  per-step exam-count N+1) is rated 87 and is the round's 5th-best task, so the failure mode is
  variance, not blindness. The document is also visibly degraded — mangled punctuation,
  self-contradicting SQL sketches.

## Systemic failure modes worth noting

1. **Prescription-not-premise errors.** Several tasks named a real smell but prescribed a fix
   that would break something: kimi's in-place `MyLife` mutation (kills DiffUtil), codex's
   per-tuple progress queries (N round trips for one over-read), devin's `isTeamLeader` lookup
   (extra query replacing a free in-memory scan).
2. **The Room index blind spot.** Four agents proposed composite indices; three of them
   (kimi ×2, devin ×1) either forbade or omitted the `AppDatabase` version bump. Room validates
   the schema identity hash on open and throws at an unchanged version, so those tasks as written
   would break existing installs. Only openhands-deepseek bumped it.
3. **Unverified "already fixed" claims.** grok's download buffering and half of its offline-scan
   task, plus codex's `ORDER BY weight` addition, target work the tree already does.
4. **`.size` theatre.** jules (7×) and devin (1×) treated O(1) field reads as hot-path costs.
