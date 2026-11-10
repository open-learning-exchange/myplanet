# Scoring table — 180 raw tasks → 113 merged

## Method

- **Rating (1–100)** = `100 · E^0.25 · I^0.5 · F^0.25`, a weighted geometric mean with weights **evidence 25 %, impact 50 %, risk-adjusted feasibility 25 %**.
  - E (evidence) = 0.5 + 0.1·c for a premise that holds, or 0.3 + 0.1·c for a premise that holds only after correction. c is the 1–5 completeness of the best surviving version, judged after verification (wrong steps cost points).
  - I (impact) 1–5 maps to 0.15 / 0.30 / 0.50 / 0.75 / 1.00; F (risk) maps L/M/H to 1.0 / 0.75 / 0.5.
  - Typical results: a sound micro-cleanup ≈ 38–39; a sound low-impact change ≈ 53–55; a real query or correctness win ≈ 69–71; a cross-cutting win ≈ 76–79.
- **Credit:** each merged task that ships is worth 1 point, split among the agents whose version survived verification. The split is proportional to **completeness × coverage**, where coverage is the fraction of the merged task that version covers. List order plays no part. A rejected variant gets 0.
  - The `1/n` column splits the point equally instead, as a reference. Bundled raw tasks earn slices in each merged task they cover.
- **Per-submitted** = points ÷ 30 tasks submitted. **Quality-weighted** = Σ share × rating/100.

## Agent scores

| agent | lists | submitted | survived | dropped | merged tasks (unique / shared) | points (completeness-weighted) | points (1/n) | per submitted | quality-weighted | quality / submitted |
|---|---|---|---|---|---|---|---|---|---|---|
| ClaudeCode | 05, 11, 17 | 30 | 28 | 2 | 18 / 12 | 22.78 | 22.41 | 0.759 | 11.71 | 0.390 |
| Copilot·Grok | 02, 08, 14 | 30 | 26 | 4 | 16 / 11 | 19.79 | 19.98 | 0.660 | 8.54 | 0.285 |
| Antigravity | 06, 12, 18 | 30 | 23 | 7 | 13 / 15 | 18.36 | 19.39 | 0.612 | 7.86 | 0.262 |
| Copilot·Kimi | 03, 09, 15 | 30 | 25 | 5 | 12 / 12 | 18.25 | 16.98 | 0.608 | 7.75 | 0.258 |
| Codex | 04, 10, 16 | 30 | 27 | 3 | 13 / 9 | 17.78 | 16.82 | 0.593 | 7.47 | 0.249 |
| Devin | 01, 07, 13 | 30 | 23 | 7 | 13 / 13 | 16.04 | 17.42 | 0.535 | 6.60 | 0.220 |
| **total** | 18 | 180 | 152 | 28 | | **113.00** | **113.00** | | **49.93** | |

Point-sum check: Σ completeness-weighted = 113.0000, Σ 1/n = 113.0000, shipped merged tasks = 113. Σ quality-weighted = 49.9300, and Σ(rating)/100 = 49.9300.

Models behind each list: 01 = devin · devin (cognition ai) · normal; 02 = copilot grok-4.7 high; 03 = github copilot · kimi k3 · high; 04 = Codex · GPT-6 · high; 05 = claude code · claude opus 5.5 · low; 06 = antigravity claude opus 4.6 high; 07 = devin · cognition devin (opus-class) · high; 08 = github-copilot grok-4.7 high; 09 = github copilot · kimi k3 · high; 10 = OpenAI Codex · GPT-6 · high; 11 = claude code · opus 5.5 · low; 12 = antigravity gemini 3.1 pro high; 13 = devin · cognition ai · normal; 14 = copilot grok-4.7 high; 15 = copilot kimi k3 high; 16 = OpenAI Codex · GPT-6 · high; 17 = Claude Code · effort low; 18 = antigravity claude opus 4.6 high.

## Per list (agent × prompt)

| list | agent | prompt | survived | dropped | points | quality-weighted | mean rating of tasks it is canonical for |
|---|---|---|---|---|---|---|---|
| 01 | Devin | perf | 8 | 2 | 8.00 | 3.11 | 38.9 (8) |
| 02 | Copilot·Grok | perf | 9 | 1 | 7.10 | 3.20 | 46.9 (7) |
| 03 | Copilot·Kimi | perf | 8 | 2 | 5.35 | 2.33 | 37.8 (5) |
| 04 | Codex | perf | 9 | 1 | 7.59 | 2.98 | 39.2 (8) |
| 05 | ClaudeCode | perf | 9 | 1 | 8.67 | 5.35 | 61.3 (9) |
| 06 | Antigravity | perf | 7 | 3 | 6.29 | 2.42 | 39.4 (7) |
| 07 | Devin | repo | 7 | 3 | 4.01 | 1.98 | 49.7 (3) |
| 08 | Copilot·Grok | repo | 7 | 3 | 4.83 | 1.90 | 36.2 (4) |
| 09 | Copilot·Kimi | repo | 8 | 2 | 8.00 | 3.02 | 37.8 (8) |
| 10 | Codex | repo | 10 | 0 | 4.10 | 2.17 | 56.2 (5) |
| 11 | ClaudeCode | repo | 10 | 0 | 7.27 | 3.15 | 45.1 (10) |
| 12 | Antigravity | repo | 9 | 1 | 6.48 | 2.96 | 44.2 (6) |
| 13 | Devin | round | 8 | 2 | 4.03 | 1.51 | 34.7 (3) |
| 14 | Copilot·Grok | round | 10 | 0 | 7.86 | 3.43 | 45.5 (8) |
| 15 | Copilot·Kimi | round | 9 | 1 | 4.90 | 2.39 | 38.0 (4) |
| 16 | Codex | round | 8 | 2 | 6.09 | 2.32 | 38.0 (7) |
| 17 | ClaudeCode | round | 9 | 1 | 6.84 | 3.21 | 50.6 (7) |
| 18 | Antigravity | round | 7 | 3 | 5.59 | 2.48 | 47.5 (4) |

## Provenance per merged task (sorted by rating)

| # | rating | merged task | contributors: share (equal 1/n) |
|---|---|---|---|
| 1 | 78 | ci-master-debug-cache | L14-T1 Copilot·Grok 1.00 (1.00) |
| 2 | 76 | challenge-gate | L02-T1 Copilot·Grok 0.50 (0.50), L03-T1 Copilot·Kimi 0.50 (0.50) |
| 3 | 71 | payloadbuilder-cancel | L10-T4 Codex 0.48 (0.33), L15-T8 Copilot·Kimi 0.48 (0.33), L13-T1 Devin 0.04 (0.33) |
| 4 | 71 | dictionary-count-before-file | L10-T5 Codex 0.64 (0.50), L12-T4 Antigravity 0.36 (0.50) |
| 5 | 71 | courses-title-no-steps | L05-T5 ClaudeCode 1.00 (1.00) |
| 6 | 71 | progress-step-count-sql | L05-T4 ClaudeCode 1.00 (1.00) |
| 7 | 71 | survey-question-count-sql | L05-T1 ClaudeCode 1.00 (1.00) |
| 8 | 71 | teamvoices-single-load | L05-T8 ClaudeCode 1.00 (1.00) |
| 9 | 69 | retryutils-cancel | L15-T1 Copilot·Kimi 0.43 (0.25), L17-T7 ClaudeCode 0.43 (0.25), L03-T4 Copilot·Kimi 0.11 (0.25), L13-T1 Devin 0.04 (0.25) |
| 10 | 69 | takecourse-error-path | L17-T8 ClaudeCode 0.42 (0.25), L03-T8 Copilot·Kimi 0.28 (0.25), L15-T6 Copilot·Kimi 0.21 (0.25), L13-T2 Devin 0.08 (0.25) |
| 11 | 69 | retryinterceptor-robolectric-removal | L15-T2 Copilot·Kimi 0.50 (0.50), L14-T2 Copilot·Grok 0.50 (0.50) |
| 12 | 69 | health-upload-await | L17-T1 ClaudeCode 1.00 (1.00) |
| 13 | 67 | pdfthumbnailtest-sdk28 | L18-T4 Antigravity 1.00 (1.00) |
| 14 | 58 | download-link-buffer-drain | L05-T6 ClaudeCode 1.00 (1.00) |
| 15 | 55 | userread-batch-B | L11-T2 ClaudeCode 0.23 (0.09), L07-T4 Devin 0.18 (0.09), L07-T10 Devin 0.09 (0.09), L10-T7 Codex 0.09 (0.09), L10-T8 Codex 0.09 (0.09), L08-T10 Copilot·Grok 0.06 (0.09), L12-T2 Antigravity 0.06 (0.09), L12-T8 Antigravity 0.06 (0.09), L10-T6 Codex 0.05 (0.09), L10-T10 Codex 0.05 (0.09), L12-T9 Antigravity 0.05 (0.09) |
| 16 | 55 | userread-batch-A | L11-T1 ClaudeCode 0.24 (0.14), L07-T3 Devin 0.24 (0.14), L08-T10 Copilot·Grok 0.22 (0.14), L10-T9 Codex 0.12 (0.14), L11-T8 ClaudeCode 0.07 (0.14), L07-T4 Devin 0.06 (0.14), L10-T10 Codex 0.06 (0.14) |
| 17 | 55 | questiondao-stale-delete | L10-T3 Codex 0.53 (0.33), L08-T6 Copilot·Grok 0.26 (0.33), L12-T7 Antigravity 0.21 (0.33) |
| 18 | 55 | life-narrow | L12-T10 Antigravity 0.71 (0.50), L08-T8 Copilot·Grok 0.29 (0.50) |
| 19 | 55 | login-guest-orchestration | L07-T5 Devin 1.00 (1.00) |
| 20 | 55 | notifications-vm-no-context | L11-T3 ClaudeCode 0.62 (0.50), L17-T9 ClaudeCode 0.38 (0.50) |
| 21 | 55 | settings-vm-usersession | L12-T1 Antigravity 1.00 (1.00) |
| 22 | 55 | submission-batch-tx | L05-T2 ClaudeCode 1.00 (1.00) |
| 23 | 55 | text-preview-bound | L04-T1 Codex 1.00 (1.00) |
| 24 | 55 | usersort-lowercase-once | L02-T9 Copilot·Grok 1.00 (1.00) |
| 25 | 55 | voicesadapter-shared-zoom-viewer | L05-T10 ClaudeCode 1.00 (1.00) |
| 26 | 53 | mockito-to-mockk | L11-T6 ClaudeCode 1.00 (1.00) |
| 27 | 53 | notifications-teamname-helper-dedup | L01-T10 Devin 1.00 (1.00) |
| 28 | 52 | userrepo-skip-redundant-upsert | L18-T10 Antigravity 1.00 (1.00) |
| 29 | 50 | takecourse-parallel | L06-T2 Antigravity 0.36 (0.25), L02-T4 Copilot·Grok 0.27 (0.25), L13-T2 Devin 0.21 (0.25), L15-T6 Copilot·Kimi 0.16 (0.25) |
| 30 | 50 | resourcesfragment-id-maps | L05-T9 ClaudeCode 0.67 (0.50), L02-T5 Copilot·Grok 0.33 (0.50) |
| 31 | 50 | uploadconfigs-mark-tx | L05-T3 ClaudeCode 1.00 (1.00) |
| 32 | 48 | progress-earned-exam-batch | L02-T2 Copilot·Grok 1.00 (1.00) |
| 33 | 48 | usersync-rescope | L07-T1 Devin 1.00 (1.00) |
| 34 | 46 | userread-hoist-ratings-markdown-health | L07-T2 Devin 1.00 (1.00) |
| 35 | 45 | life-reorder-owner-check | L10-T1 Codex 1.00 (1.00) |
| 36 | 39 | chathistory-current-user-vm | L11-T10 ClaudeCode 0.56 (0.50), L12-T3 Antigravity 0.44 (0.50) |
| 37 | 39 | community-blank-filter-sql | L11-T5 ClaudeCode 0.56 (0.50), L07-T6 Devin 0.44 (0.50) |
| 38 | 39 | exporter-getexamid | L04-T9 Codex 0.81 (0.50), L06-T8 Antigravity 0.19 (0.50) |
| 39 | 39 | fileutils-displayname | L04-T6 Codex 0.36 (0.33), L14-T3 Copilot·Grok 0.36 (0.33), L16-T8 Codex 0.29 (0.33) |
| 40 | 39 | resources-vm-sort-dispatcher | L12-T8 Antigravity 0.59 (0.50), L17-T3 ClaudeCode 0.41 (0.50) |
| 41 | 39 | stepexamtest-mocks | L14-T10 Copilot·Grok 0.87 (0.50), L18-T5 Antigravity 0.13 (0.50), ~~L16-T5~~ rejected |
| 42 | 39 | automerge-publishfailed-checkruns | L14-T8 Copilot·Grok 1.00 (1.00) |
| 43 | 39 | checksum-buffer | L02-T10 Copilot·Grok 1.00 (1.00) |
| 44 | 39 | communitytab-state-hoist | L11-T8 ClaudeCode 1.00 (1.00) |
| 45 | 39 | coursestep-stale-delete-bounded | L10-T2 Codex 1.00 (1.00) |
| 46 | 39 | coursesteps-next-step-download-filter | L08-T9 Copilot·Grok 1.00 (1.00) |
| 47 | 39 | events-toggle-requery | L09-T8 Copilot·Kimi 1.00 (1.00) |
| 48 | 39 | jsonutils-redundant-parse | L04-T10 Codex 1.00 (1.00) |
| 49 | 39 | lifevm-single-read | L11-T4 ClaudeCode 1.00 (1.00) |
| 50 | 39 | login-saveusers-vm | L11-T9 ClaudeCode 1.00 (1.00) |
| 51 | 39 | mylife-updatevisibility-rowcount | L12-T5 Antigravity 1.00 (1.00) |
| 52 | 39 | mylifedao-param-or | L08-T3 Copilot·Grok 1.00 (1.00) |
| 53 | 39 | notificationreceiver-inject-broadcast | L09-T10 Copilot·Kimi 1.00 (1.00) |
| 54 | 39 | resourceslistfilter-filter-first | L04-T5 Codex 1.00 (1.00) |
| 55 | 39 | strip-log-community | L09-T1 Copilot·Kimi 1.00 (1.00) |
| 56 | 39 | strip-log-news | L09-T5 Copilot·Kimi 1.00 (1.00) |
| 57 | 39 | strip-log-personals | L09-T4 Copilot·Kimi 1.00 (1.00) |
| 58 | 39 | strip-log-retry | L09-T2 Copilot·Kimi 1.00 (1.00) |
| 59 | 39 | takecoursefragment-unused-inject | L12-T2 Antigravity 1.00 (1.00) |
| 60 | 39 | teamcourses-picker-io | L14-T6 Copilot·Grok 1.00 (1.00) |
| 61 | 39 | tts-csv-buildstring | L06-T9 Antigravity 1.00 (1.00) |
| 62 | 39 | voiceslabel-updateaddlabel | L06-T10 Antigravity 1.00 (1.00) |
| 63 | 38 | healthexam-save-cancel | L03-T7 Copilot·Kimi 0.46 (0.20), L15-T7 Copilot·Kimi 0.23 (0.20), L17-T8 ClaudeCode 0.18 (0.20), L18-T10 Antigravity 0.09 (0.20), L13-T1 Devin 0.05 (0.20) |
| 64 | 38 | loginsyncmanagertest-hoist | L15-T4 Copilot·Kimi 0.62 (0.33), L13-T6 Devin 0.31 (0.33), L18-T5 Antigravity 0.08 (0.33) |
| 65 | 38 | resourcesvm-load-catch | L15-T7 Copilot·Kimi 0.61 (0.33), L17-T3 ClaudeCode 0.27 (0.33), L13-T1 Devin 0.12 (0.33) |
| 66 | 38 | voicesnewssynctest-hoist | L15-T5 Copilot·Kimi 0.43 (0.33), L16-T3 Codex 0.43 (0.33), L14-T4 Copilot·Grok 0.13 (0.33) |
| 67 | 38 | chat-sharetargets-parallel | L06-T4 Antigravity 0.74 (0.50), L13-T3 Devin 0.26 (0.50) |
| 68 | 38 | freespacetest-mocks | L16-T4 Codex 0.80 (0.50), L17-T10 ClaudeCode 0.20 (0.50) |
| 69 | 38 | publicsurvey-timeprovider | L15-T10 Copilot·Kimi 0.57 (0.50), L18-T7 Antigravity 0.43 (0.50) |
| 70 | 38 | userrepoimpltest-hoist | L15-T3 Copilot·Kimi 0.67 (0.50), L13-T7 Devin 0.33 (0.50) |
| 71 | 38 | coursedetail-parallel | L02-T3 Copilot·Grok 1.00 (1.00) |
| 72 | 38 | coursesteps-child-jobs | L16-T1 Codex 0.57 (0.50), L04-T4 Codex 0.43 (0.50) |
| 73 | 38 | coursesteps-markdown-offmain | L14-T5 Copilot·Grok 1.00 (1.00) |
| 74 | 38 | examtaking-hoist | L01-T5 Devin 1.00 (1.00) |
| 75 | 38 | exporter-drawmultiline | L06-T8 Antigravity 1.00 (1.00) |
| 76 | 38 | feedbackadapter-labels | L01-T8 Devin 1.00 (1.00) |
| 77 | 38 | fileuploader-printstacktrace | L03-T6 Copilot·Kimi 1.00 (1.00) |
| 78 | 38 | healthitemmapper-textutils | L18-T6 Antigravity 1.00 (1.00) |
| 79 | 38 | hilt-modules-into-di | L11-T7 ClaudeCode 1.00 (1.00) |
| 80 | 38 | meetup-dead-trycatch | L03-T9 Copilot·Kimi 1.00 (1.00) |
| 81 | 38 | news-reaction-cache | L02-T7 Copilot·Grok 1.00 (1.00) |
| 82 | 38 | reachabilityworkertest-hoist | L16-T7 Codex 1.00 (1.00) |
| 83 | 38 | requests-vm-parallel | L06-T3 Antigravity 1.00 (1.00) |
| 84 | 38 | resourcessearch-append | L16-T9 Codex 1.00 (1.00) |
| 85 | 38 | stableid-platform-free | L17-T5 ClaudeCode 1.00 (1.00) |
| 86 | 38 | submission-adapters-datecache | L01-T7 Devin 1.00 (1.00) |
| 87 | 38 | submissiondetail-collapse-statein | L12-T9 Antigravity 1.00 (1.00) |
| 88 | 38 | submissions-limit1-zip | L01-T3 Devin 1.00 (1.00) |
| 89 | 38 | syncactivity-inject-scope | L17-T2 ClaudeCode 1.00 (1.00) |
| 90 | 38 | timing-summary-index | L16-T10 Codex 1.00 (1.00) |
| 91 | 38 | transactionsync-printstacktrace | L03-T5 Copilot·Kimi 1.00 (1.00) |
| 92 | 38 | userentityencodetest-hoist | L16-T6 Codex 1.00 (1.00) |
| 93 | 38 | voicesadapter-leaders-map | L01-T6 Devin 1.00 (1.00) |
| 94 | 37 | automerge-batch-prview | L13-T5 Devin 0.59 (0.50), L18-T3 Antigravity 0.41 (0.50) |
| 95 | 37 | chat-paginator-default | L17-T4 ClaudeCode 1.00 (1.00) |
| 96 | 37 | model-serializer-printstacktrace | L03-T10 Copilot·Kimi 1.00 (1.00) |
| 97 | 36 | strip-log-health | L09-T3 Copilot·Kimi 1.00 (1.00) |
| 98 | 35 | notifications-grouping-flowon | L04-T3 Codex 1.00 (1.00) |
| 99 | 35 | playstore-quota-graphql | L14-T7 Copilot·Grok 1.00 (1.00) |
| 100 | 34 | drop-dead-log-mocks | L17-T10 ClaudeCode 0.55 (0.50), L18-T5 Antigravity 0.45 (0.50) |
| 101 | 34 | chat-sort-decorate | L02-T8 Copilot·Grok 1.00 (1.00) |
| 102 | 34 | chatsearch-buckets | L04-T8 Codex 1.00 (1.00) |
| 103 | 34 | coursedetail-vm-offmain | L04-T2 Codex 1.00 (1.00) |
| 104 | 34 | networkmonitor-rename | L13-T9 Devin 1.00 (1.00) |
| 105 | 34 | notifications-toggle-group-unread | L06-T7 Antigravity 1.00 (1.00) |
| 106 | 34 | personaldao-countbytitle | L08-T4 Copilot·Grok 1.00 (1.00) |
| 107 | 34 | teams-members-dedup | L01-T1 Devin 1.00 (1.00) |
| 108 | 34 | urlutils-hosturl-cache | L01-T4 Devin 1.00 (1.00) |
| 109 | 33 | communityservices-narrow | L08-T7 Copilot·Grok 1.00 (1.00) |
| 110 | 33 | exporter-log-catches | L13-T4 Devin 1.00 (1.00) |
| 111 | 33 | playstore-forecast-double-measure | L18-T1 Antigravity 1.00 (1.00) |
| 112 | 32 | teamstasks-filters-to-vm | L09-T6 Copilot·Kimi 1.00 (1.00) |
| 113 | 27 | imageutilstest-glide | L14-T9 Copilot·Grok 1.00 (1.00) |

## Dropped raw tasks (28): premise false, or the change is wrong as specified

| raw | agent | title | why |
|---|---|---|---|
| L01-T2 | Devin | hoist shared reads out of upload serializer lambdas (roadmap 5+7) | Its per-run snapshot is captured by @Singleton `val` configs, so it would freeze the device name and planet codes for the process lifetime. androidId is already cached (VersionUtils.kt:55). |
| L01-T9 | Devin | memoize `formatLabelValue` in `VoicesLabelManager` (roadmap 7) | Hot-path premise false: known labels short-circuit via LABEL_VALUE_TO_NAME (:125), so formatLabelValue runs only for custom labels. |
| L02-T6 | Copilot·Grok | cache downloaded-file hits while course-step resources scroll (roadmap 7) | The cache semantics are wrong: the two exists checks use different paths (resourceId vs id), so one hit cannot stand in for the other. |
| L03-T2 | Copilot·Kimi | Collapse the per-survey submission-existence loops into single COUNT queries (roadmap 1+7) | The proposed COUNT SQL flips the result for zero-question surveys and blank ids (:215-221). N is usually 0-2. |
| L03-T3 | Copilot·Kimi | Memoize profile-image Base64 encoding behind UserEntity.serialize (roadmap 5+7, supports 9) | The per-instance memo never hits: each upload gets a fresh UserEntity from Room (UploadConfigs.kt:242/:257). |
| L04-T7 | Codex | Charge every PDF thumbnail a positive cache weight (roadmap 7+8) | Premise never occurs: thumbnails render at about 240dp (hundreds of KiB), so the floor error is under 0.5%. |
| L05-T7 | ClaudeCode | Cap the notification read sync at the file's existing concurrency limit (roadmap 5+7) | Premise false: OkHttp already caps requests at 20 per host (NetworkModule.kt:101/:116), so there are no 'hundreds of concurrent requests'. |
| L06-T1 | Antigravity | replace multi-select answer dual-list sort with frequency-map comparison (roadmap 7+8) | Not cheaper: groupingBy/eachCount allocates two LinkedHashMaps with boxed counts, versus sorting 2-6 strings. |
| L06-T5 | Antigravity | pre-normalize facet filter sets once per pass in ResourcesListFilter (roadmap 3+7) | No win: the lowercase-set lookup needs per-item lowercasing (an allocation), versus allocation-free equalsIgnoreCase over 0-3 criteria. |
| L06-T6 | Antigravity | avoid full-list remap and recount on single-item toggle in StorageCategoryViewModel (roadmap 3+7) | It swaps one O(n) pass for another. The 'unknown id still emits' claim is false (MutableStateFlow drops equal values). |
| L07-T7 | Devin | Replace the team-notification update-then-insert pair with a single upsert (roadmap 1, 7, 9) | @Upsert keys on a random UUID primary key, so it neither dedupes (parentId,type) nor adds atomicity. It also breaks 3 tests. |
| L07-T8 | Devin | Narrow ChatViewModel to TeamsShareRepository + UserReadRepository and lift the community-summary fallback into the model (roadmap 1, 3, 9) | Already done: ChatViewModel injects UserReadRepository and TeamsShareRepository (ChatViewModel.kt:56-57). |
| L07-T9 | Devin | Push patient-list sorting into SQLite ORDER BY (roadmap 1, 7, 9) | SQL NOCASE/NULL ordering differs from Kotlin lowercase and stable sort, and the table is small. It also states several wrong facts (dob/age are strings; the full list is still returned). |
| L08-T1 | Copilot·Grok | split user id lookups so each side can use its index (roadmap 1+7+9) | Premise false: id (PK) and _id are both indexed, and SQLite's multi-index OR handles `id = ? OR _id = ?`. |
| L08-T2 | Copilot·Grok | split team id lookup and member-count existence check (roadmap 1+7) | Premise false: the only caller already tries getById first (TeamsRepositoryImpl.kt:1369), and the OR is index-servable. |
| L08-T5 | Copilot·Grok | make opened-resource observation return one stable row per resource (roadmap 1+7+8) | No consumer reads title or time: the only consumer maps to a resourceId set. The subquery makes the Flow more expensive. |
| L09-T7 | Copilot·Kimi | extract survey search matching out of SurveysViewModel into a tested pure component (roadmap 3+8) | Premise false: SurveysViewModelTest.kt:154-245 already covers the ranking, so this is a pure relocation. |
| L09-T9 | Copilot·Kimi | resolve latest chat revision with a SQL MAX query instead of loading all revs (roadmap 7+1) | Premise false: ChatHistory id == _id (ChatRepositoryImpl.kt:186-187), so there is at most one rev row. It would also move logic out of five unit tests. |
| L12-T6 | Antigravity | wrap TeamLogDao.deleteByIds in @Transaction and short-circuit on empty input (roadmap 1+7+8) | Premise false: the only caller is already inside withTransaction, and an empty input already runs no query. |
| L13-T10 | Devin | Hoist invariant `getString`/`Calendar` calls out of hot lambdas (roadmap 7) | Its Calendar reuse would collapse every marked day onto the last meetup's date (each CalendarDay keeps its own Calendar). The getString premise is false (elvis-only). |
| L13-T8 | Devin | Cache `englishValues` locale contexts in `CoursesFilterFragment` (roadmap 7) | Cannot be implemented as written: englishValues is a companion function with 5 external callers. It runs 2-4 times per tap. |
| L15-T9 | Copilot·Kimi | rethrow cancellation instead of Result.retry() in NetworkMonitorWorker (roadmap 5+8) | Premise false: the try block contains no suspension point, and WorkManager ignores a stopped worker's Result. |
| L16-T2 | Codex | Coalesce active team-course loads and cancel superseded requests (roadmap 3+7) | Premise false: the VM is fragment-scoped with a fixed teamId. Coalescing same-key calls would drop the refresh after a remove/add. |
| L16-T5 | Codex | Reuse class-level exam test instrumentation without leaking stubs (roadmap 1+8) | Wrong optimization: it hoists a TextUtils mock that nothing uses, instead of deleting it. Its premise that the parsing tests need the existing answers is false. |
| L17-T6 | ClaudeCode | Bind BaseContainerFragment's view-touching coroutines to the view lifecycle (roadmap 8 + 7) | Premise false: showDownloadDialog is not suspend and checks isAdded, so the coroutine never outlives the view. |
| L18-T2 | Antigravity | defer collaborator API pagination until non-filtered coauthors exist (roadmap 8) | It defeats the documented fail-fast (coauthors.sh:22), uses wrong identifiers, and its acceptance is inverted. |
| L18-T8 | Antigravity | combine dual ID/remoteId lookup and use injected Gson in FeedbackRepositoryImpl (roadmap 1+7, advances 9) | Both parts are wrong as written: binding `:ids` twice in 900-chunks exceeds the 999-variable limit, and the injected Gson (serializeNulls) changes output. |
| L18-T9 | Antigravity | deduplicate single-link tag lookup and short-circuit empty tag names in TagsRepositoryImpl (roadmap 1+7, advances 9) | The empty short-circuit never triggers (the caller guards it), the step returns the wrong type, and the helper changes the tag order. |

## Partly dropped (10): the task survives, this part does not

| raw | agent | dropped part |
|---|---|---|
| L02-T8 | Copilot·Grok | getLatestRev half: maxByOrNull evaluates the selector once per element, and there is at most one row per _id. |
| L04-T8 | Codex | searchByTitle half: a single + already allocates exactly one list. |
| L07-T3 | Devin | FeedbackListViewModel target: already narrowed (FeedbackListViewModel.kt:20). |
| L08-T4 | Copilot·Grok | delete/update OR split: `_id` is unindexed, so two statements are never cheaper. |
| L12-T4 | Antigravity | DictionaryActivity half: loadCount() is the only path that wires the search button. |
| L13-T4 | Devin | Batch-exam-lookup half: the N+1 does not exist (generateMultipleSubmissionsPdf already batches). |
| L13-T9 | Devin | KEEP→REPLACE half: inputData is constant; REPLACE starves under reconnect flaps and breaks a test. |
| L14-T4 | Copilot·Grok | Shared Room DB half: unsafe under Robolectric's per-test Application reset. |
| L17-T3 | ClaudeCode | step 3 (drop the IO wrapper in loadResources) would move whole-library mapping to Main. |
| L18-T3 | Antigravity | Step 3 (skip the second publish_failed): the release run can complete between :470 and :605. |
