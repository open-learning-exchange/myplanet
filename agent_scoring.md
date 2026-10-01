# Agent scoring

Weights used for task ratings: **evidence quality 40%**, **impact 35%**, and **risk-adjusted feasibility 25%**. Duplicate credit is split by completeness of the submitted work order (specific evidence, bounded implementation, acceptance criteria, and focused tests); tied-completeness proposals split equally, regardless of list order. All merged ties in this pass were materially complete, so they split equally.

| Agent | Raw submitted | Unique-good tasks | Duplicate-good merged tasks | Good-credit points | Points / submitted | Quality-weighted points | Quality / submitted |
|---|---:|---:|---:|---:|---:|---:|---:|
| Devin SWE 2 | 30 | 17 | 11 | 22.2500 | 0.7417 | 15.2325 | 0.5077 |
| Codex Sol 5.6 | 30 | 14 | 13 | 19.3667 | 0.6456 | 13.5227 | 0.4508 |
| Claude Opus 5.5 | 30 | 16 | 13 | 21.4500 | 0.7150 | 14.6668 | 0.4889 |
| Copilot Gemini 3.8 Flash | 30 | 12 | 17 | 19.2000 | 0.6400 | 13.2827 | 0.4428 |
| Copilot Kimi K3 | 33 | 21 | 9 | 24.6167 | 0.7460 | 16.7718 | 0.5082 |
| Copilot Grok 4.6 | 30 | 12 | 12 | 17.1167 | 0.5706 | 11.8035 | 0.3934 |

## Dropped after working-tree verification

- **None.** Every submitted premise was located in the current checkout. Alternative work orders in list 11 count as raw submissions because each is independently actionable; they were not treated as plans or deferred sections.

## Reconciliation

- **Counts:** 18 lists / 183 raw tasks / 124 shipped merged tasks.
- **Point-sum check:** unrounded shares total **124.0000**, matching **124** shipped tasks exactly. The displayed four-decimal table values sum to **124.0001** because of independent row rounding.
- **Quality-weighted sum:** **85.2800** rating-adjusted points.
- **Guesses:** “raw” includes the three explicitly labeled alternatives in list 11; compound submissions remained one raw task; equally complete duplicate submissions share credit; the requested scoring table is shipped as this companion file.
