# Progress Rating for a Long Port

A portable way to answer "how far along is this?" on a long rewrite, port, or migration —
without the single percentage that everyone learns to ignore. Nothing here is specific to this
repo; adopting it elsewhere needs only the definitions and the rules.

It came out of ~10 rounds of rating a Kotlin→Flutter port, including the rounds where the
rating was wrong. The rules exist because each one was broken first.

> **This repo's live figure is the dimensioned table in `CLAUDE.md`** ("Migration progress"),
> not a number derived here. This document is the *method*; that table is the instantiation,
> and it wins. Score the dimensions first and let the summary follow — never the reverse.
>
> That ordering is the correction this document was written by getting wrong. Ten rounds were
> reported as a two-number pair around "98 parity / 94 shippable" while the dimensioned table,
> built independently, put behavioural parity near **70** and the composite near **79**. The
> two are not really in conflict: "98" was measuring *feature breadth* — could a missing
> feature still be named — and breadth genuinely is ~95. It was **labelled** parity, which is
> depth, and depth was never scored at all. A summary number inherits the authority of whatever
> it is called, so mislabelling one dimension quietly overstated the whole port for ten rounds.
> Hence rule 8.

## The dimensions

Score these separately. Each needs its own denominator, and each is named for what it actually
measures — see rule 8 for why that is not pedantry.

- **Feature breadth** — does a counterpart *exist*? Cheap to measure, and flattering.
- **Behavioural parity** — does it *match*, quirks included? Only ever revealed by auditing,
  and normally the limiter.
- **Test coverage** — what fraction is verified, against the incumbent's own suite as a yardstick.
- **Localisation / accessibility** — countable, and usually the largest single hole.
- **Operational readiness** — which environments have actually been *run*: background jobs,
  real devices, offline, the headless paths.

Then two summary lines, and only then:

- **Composite** — where the whole effort stands, named for its limiting dimension.
- **Shippable** — could you hand it to real users *today*, in place of the incumbent?

Keep shippable separate, and never above the composite. A feature can be present, tested and
localised while still losing data silently — on this port a certified exam's verification photo
had never once reached the server, and nothing logged an error. That costs shippability while
costing breadth nothing, which is exactly why one number cannot carry both.

## The rules

1. **Report the countable pair alongside the scores.** The scores are a summary; these are the
   falsifiable part:
   - **defects per newly-audited surface** — count them each time you audit something that had
     no coverage. Trust the highest-rigour count you have: casually reviewing a screen while
     writing tests for it surfaced 2–5 here, while a dedicated parity audit of the same kind of
     surface found 7–18 per screen pair. The gap between those two figures *is* the measurement
     error in a self-assessed score.
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

5. **The discovery rate is the check on behavioural parity.** If every audit still turns up real
   defects, that score is too high no matter how complete the breadth looks. Falling discovery rate is the only honest
   evidence that a long tail is running out. Watch the *trend*, not the count.

6. **Movement needs a named cause.** Each change states which artifact changed and how. "+1 this
   round" with nothing to point at is momentum, not measurement — and momentum is what makes a
   score drift toward 100 no matter what the code does.

7. **Shippable is gated on named blockers, not vibes.** Keep the list explicit and current: untested
   surfaces, environments never actually exercised (background jobs, real devices, offline),
   unreviewed locales and accessibility, anything reachable only through a platform dialog.
   It cannot exceed what that list allows, and the list is the answer to "what would it take?".

8. **Score dimensions, not a summary — and never let a dimension borrow another's name.**
   Rate breadth, depth, coverage, localisation and operational readiness *separately*, each with
   its own denominator, then summarise. Breadth is cheap to measure and flattering; depth is
   expensive and is almost always the limiter. Reporting breadth under the word "parity" is the
   specific mistake that produced a ten-round overstatement here. If a dimension has no
   denominator, it does not get a number — it gets a sentence.

## Rough bands

Anchors for a session with no rating history, so the first number isn't invented.

| Band | Behavioural parity | Shippable |
|---|---|---|
| <50 | whole subsystems absent | not a candidate |
| 50–79 | main flows exist, several features missing | internal demo only |
| 80–94 | no feature you can *name* is missing; details drift | pilot with a fallback; blockers still listed |
| 95–99 | gaps are quirk-level and found only by audit | blockers are known, bounded, and shrinking |
| 100 | reserved: an audit round that finds nothing | you have already shipped it |

Treat 100 as unreachable by assertion. It is earned by a round that looked hard and came back
empty, not by running out of things you remembered to check.

## Required report shape

One row per dimension, each with the denominator that earns it a number, then the summary
last — so a reader can argue with the basis rather than the headline:

```
| Dimension           | Basis (with denominator)                      | Est. |
| Feature breadth     | <n of m features have a counterpart>          |  <n> |
| Behavioural parity  | <defects per audited surface, and the trend>   |  <n> |
| Test coverage       | <tests / files, vs the incumbent's>            |  <n> |
| Localisation        | <keys per locale of total>                     |  <n> |
| Operational         | <which environments have actually been run>    |  <n> |

Composite: <n>/100 — <the limiting dimension, named>
Shippable: <n>/100 — <explicit blocker list>
Moved this round by: <artifact that changed, or "nothing">
```

Keep "shippable" as a separate line rather than folding it in: a feature can be present,
tested, localised and still lose data silently, and that costs shippability while costing
breadth nothing.

## Failure modes to expect

- **Anchoring.** Wherever you start, you will move 1–2 points per round. This port's pair began
  at 96 and read 98 ten rounds later on evidence that, scored dimensionally, supports about 79.
  Re-derive from the dimensions periodically instead of incrementing the last figure.
- **Rating your own work.** The party that wrote the code is the worst judge of whether it is
  finished. Rule 1's counts are the defence: they do not care who wrote them.
- **Confusing "on screen" with "working".** A screen that renders scores full marks on breadth
  and tells you nothing about parity. Silent write-path failures — bytes that never upload, a
  row that never syncs — are invisible to breadth and fatal to shippability.
- **Two raters, two answers.** When an independent pass disagrees with yours, the dimensioned
  one wins; a summary that cannot be decomposed cannot be defended. That is how the 98-vs-70
  gap above was caught, and it was caught by someone else, not by re-reading my own number.
