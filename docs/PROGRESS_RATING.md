# Two-Number Progress Rating

A portable way to answer "how far along is this?" on a long rewrite, port, or migration —
without the single percentage that everyone learns to ignore. Nothing here is specific to this
repo; adopting it elsewhere needs only the two definitions and the seven rules.

It came out of ~10 rounds of rating a Kotlin→Flutter port, including the rounds where the
rating was wrong. The rules exist because each one was broken first.

## The two numbers

**N1 — Parity.** Does the target behaviour exist in the new thing at all? Breadth. Answered by
mapping features to their counterparts and counting what has no counterpart.

**N2 — Shippable.** Could you hand this to real users *today*, in place of the incumbent?

Always report both. A single number hides the interesting gap, which is where features exist
and still lose data silently. On the port this pair sat at 98/94: nothing left that you could
*name* as missing, and still not something to ship, because a certified exam's verification
photo had never once reached the server and nothing logged an error.

`N2 ≤ N1` always. If you compute otherwise, one of them is wrong.

## The seven rules

1. **Report the countable pair alongside the scores.** The scores are a summary; these are the
   falsifiable part:
   - **defects per newly-audited surface** — count them each time you audit something that had
     no coverage. On the port this ran 2–5 per screen and stayed flat.
   - **untested share of hand-written lines** — exclude generated and localisation files.
   If you can only produce one artifact from this document, produce these two.

2. **Declare the denominator, or admit there is none.** "98" implies 98% of a counted whole. If
   you never enumerated the incumbent's behaviour surface, say so in the same breath. An
   undenominated score is a feeling with a decimal point.

3. **A score may not rise on a documentation correction.** Discovering that nothing was missing
   changes *your knowledge*, not the artifact. This is the easiest rule to break, because the
   relief of resolving a long-standing unknown feels exactly like progress.

4. **A score may not rise on test count alone.** Tests are evidence of *verification*, not of
   correctness. A suite growing while defect-per-surface holds steady means you are finding the
   same density of bugs in new places — that is discovery, not improvement.

5. **The discovery rate is the check on N1.** If every audit still turns up real defects, N1 is
   too high no matter how complete the breadth looks. Falling discovery rate is the only honest
   evidence that a long tail is running out. Watch the *trend*, not the count.

6. **Movement needs a named cause.** Each change states which artifact changed and how. "+1 this
   round" with nothing to point at is momentum, not measurement — and momentum is what makes a
   score drift toward 100 no matter what the code does.

7. **N2 is gated on named blockers, not vibes.** Keep the list explicit and current: untested
   surfaces, environments never actually exercised (background jobs, real devices, offline),
   unreviewed locales and accessibility, anything reachable only through a platform dialog. N2
   cannot exceed what that list allows, and the list is the answer to "what would it take?".

## Rough bands

Anchors for a session with no rating history, so the first number isn't invented.

| Band | N1 — parity | N2 — shippable |
|---|---|---|
| <50 | whole subsystems absent | not a candidate |
| 50–79 | main flows exist, several features missing | internal demo only |
| 80–94 | no feature you can *name* is missing; details drift | pilot with a fallback; blockers still listed |
| 95–99 | gaps are quirk-level and found only by audit | blockers are known, bounded, and shrinking |
| 100 | reserved: an audit round that finds nothing | you have already shipped it |

Treat 100 as unreachable by assertion. It is earned by a round that looked hard and came back
empty, not by running out of things you remembered to check.

## Required report shape

```
N1 parity: <n> (<what moved it, or "unchanged">)
N2 shippable: <n> (<what moved it, or "unchanged">)
Defects per audited surface this round: <n> across <what>
Untested share: <n>% (<lines> of <total>)
Blockers on N2: <explicit list>
Denominator: <what N1 is a fraction of, or "uncounted">
```

## Failure modes to expect

- **Anchoring.** Wherever you start, you will move 1–2 points per round. Had the port's rating
  begun at 80 instead of 96 it would read 88 today on identical evidence. Re-derive the score
  from the blocker list occasionally instead of incrementing the last one.
- **Rating your own work.** The party that wrote the code is the worst judge of whether it is
  finished. Rule 1's counts are the defence: they do not care who wrote them.
- **Confusing "on screen" with "working".** A screen that renders is worth much less to N2 than
  to N1. Silent write-path failures — the bytes that never upload, the row that never syncs —
  cost N2 heavily and cost N1 nothing, which is exactly why one number cannot carry both.
