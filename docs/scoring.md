# Agent scoring — myplanet refactor task lists

Rules as specified: unique good = 1; duplicated good = 1/n split by **completeness** (not list order); bad = 0. Every merged task distributes exactly 1 point. Quality-weighted variant multiplies each credit share by task rating/100. All agents submitted 20 raw tasks (2 prompts × 10).

| agent | submitted | credit | normalized (/20) | quality-weighted | qw-normalized |
|---|---|---|---|---|---|
| copilot grok 4.5 | 20 | 18.05 | 0.903 | 12.75 | 0.638 |
| claude opus 5.5 | 20 | 17.70 | 0.885 | 13.75 | 0.687 |
| copilot kimi k3 | 20 | 17.35 | 0.868 | 11.35 | 0.568 |
| devin swe 2 | 20 | 16.40 | 0.820 | 12.06 | 0.603 |
| copilot gemini 3.5 flash | 20 | 11.50 | 0.575 | 7.46 | 0.373 |
| codex sol 5.6 | 20 | 11.00 | 0.550 | 7.43 | 0.371 |

Point-sum check: Σ credit = 92.00 = 92 shipped tasks. ✓

## Reading it
- **grok** tops raw credit (unique, well-evidenced picks) but claude edges it on quality-weighted: claude's tasks rate higher on average (top-3 ratings are all claude's).
- **codex** and **gemini** rank low on credit mainly because their lists were pattern-heavy: codex's 10 perf tasks fold into one SDK-pin task; 8 of gemini's 10 perf tasks fold into one click-listener family. Each was still verified-good, just not 10 distinct tasks.
- **kimi** lost one task to a self-inconsistent open-PR violation (bounds NewsDao task), and two bounds tasks were too vague to rate well (MeetupDao projection, dictionary composition).
- **devin** is solid mid-pack: all 20 tasks verified, several strong finds (member-batch N+1, CommunitySyncWriter dedup), but several others overlap heavy open-PR churn, costing feasibility.

## Notes on merges
Duplicate credit splits (completeness): text-viewer bounded read — claude .65 / kimi .35 (claude adds off-main Markdown + tests); NotificationDao chunking — claude .65 / devin .35 (claude covers writes+projection+repo); TeamTaskDao seam — grok .55 / devin .45 (grok's port+DTO is the fuller boundary fix); LoginActivity→VM — devin .6 / claude .4 (devin covers Guest/ServerDialog extensions too); ResourcesRepository boundary — grok .5 / claude .5 (complementary, near-equal); Personals sealed result — grok .5 / gemini .5; download FGS check — claude .5 / grok .5 (complementary approaches).

Same-list pattern folds (counted once each): codex perf-1..10 (SDK pins), gemini perf-2..6,8..10 (click listeners), devin perf-6,7,8 (bind-time resource hoists), kimi perf-5+6 (setHasFixedSize).
