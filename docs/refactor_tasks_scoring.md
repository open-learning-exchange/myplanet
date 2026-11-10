# Agent scoring — myPlanet task-list benchmark (2026-11-10)

Weights: rating = 100 × (E/5)^0.35 × (I/5)^0.4 × (F/5)^0.25 — weighted geometric mean, so a 1 on any axis drags the whole score down (multiplicative, per the brief). E = evidence quality after my own grep/read (premise held fully = 4, partially = 3, held but fix/impact weak = 2, +1 when the agent's evidence was line-precise or measured); I = impact; F = feasibility discounted for behavioural/merge risk.

Credit: each shipped merged task is worth 1 point. **Equal split** = 1/n over the n lists that proposed it. **Completeness split** (primary, per the brief) = shares proportional to each list's completeness score (0–5) for that task, independent of list order. A list with several tasks inside one merged task gets one share for that list. Dropped tasks = 0. **Quality-weighted** = completeness share × rating/100.

## Per list

| List | Prompt | Signature | Submitted | Good | Dropped | In unique | In dup | Equal-split | Compl-split | per task | Quality-weighted | QW per task |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| L01 | P1 perf quick wins | Devin · Devin · normal | 10 | 10 | 0 | 10 | 0 | 10.00 | 10.00 | 1.000 | 6.19 | 0.619 |
| L02 | P1 perf quick wins | GitHub Copilot · Grok-4.7 · high | 10 | 10 | 0 | 7 | 3 | 8.25 | 8.14 | 0.814 | 5.33 | 0.533 |
| L03 | P1 perf quick wins | GitHub Copilot · Kimi K3 · high | 10 | 10 | 0 | 7 | 3 | 8.33 | 8.31 | 0.831 | 5.33 | 0.533 |
| L04 | P1 perf quick wins | Codex · GPT-6 · high | 10 | 10 | 0 | 8 | 2 | 8.83 | 8.72 | 0.872 | 5.35 | 0.535 |
| L05 | P1 perf quick wins | Claude Code · Opus 5.5 · low | 10 | 10 | 0 | 9 | 1 | 9.50 | 9.56 | 0.956 | 7.03 | 0.703 |
| L06 | P1 perf quick wins | Antigravity · Claude Opus 4.6 · high | 10 | 10 | 0 | 8 | 2 | 8.75 | 8.82 | 0.882 | 4.82 | 0.482 |
| L07 | P2 repository boundaries | Devin · Cognition Devin · high | 10 | 8 | 2 | 4 | 4 | 5.58 | 5.44 | 0.544 | 3.66 | 0.366 |
| L08 | P2 repository boundaries | GitHub Copilot · Grok-4.7 · high | 10 | 8 | 2 | 5 | 3 | 6.25 | 6.19 | 0.619 | 4.23 | 0.423 |
| L09 | P2 repository boundaries | GitHub Copilot · Kimi K3 · high | 10 | 10 | 0 | 10 | 0 | 10.00 | 10.00 | 1.000 | 4.83 | 0.483 |
| L10 | P2 repository boundaries | OpenAI Codex · GPT-6 · high | 10 | 10 | 0 | 3 | 7 | 5.08 | 5.13 | 0.513 | 3.05 | 0.305 |
| L11 | P2 repository boundaries | Claude Code · Opus 5.5 · low | 10 | 10 | 0 | 5 | 5 | 7.08 | 7.20 | 0.720 | 4.83 | 0.483 |
| L12 | P2 repository boundaries | Antigravity · Gemini 3.1 Pro · high | 10 | 10 | 0 | 6 | 4 | 8.00 | 7.98 | 0.798 | 5.18 | 0.518 |
| L13 | P3 refactor round | Devin · Cognition AI · normal | 10 | 10 | 0 | 5 | 5 | 7.25 | 7.01 | 0.701 | 4.22 | 0.422 |
| L14 | P3 refactor round | GitHub Copilot · Grok-4.7 · high | 10 | 10 | 0 | 6 | 4 | 7.50 | 7.56 | 0.756 | 4.53 | 0.453 |
| L15 | P3 refactor round | Copilot · Kimi K3 · high | 10 | 10 | 0 | 1 | 9 | 4.92 | 4.96 | 0.496 | 3.74 | 0.374 |
| L16 | P3 refactor round | OpenAI Codex · GPT-6 · high | 10 | 10 | 0 | 6 | 4 | 7.50 | 7.62 | 0.762 | 4.45 | 0.445 |
| L17 | P3 refactor round | Claude Code · (model unsigned) · low | 10 | 10 | 0 | 8 | 2 | 8.83 | 8.97 | 0.897 | 6.20 | 0.620 |
| L18 | P3 refactor round | Antigravity · Claude Opus 4.6 · high | 10 | 10 | 0 | 7 | 3 | 8.33 | 8.40 | 0.840 | 4.96 | 0.496 |

## Per agent (3 prompts each)

| Agent | Lists | Submitted | Good | Dropped | Equal-split | Compl-split | per task | Quality-weighted | QW per task | Rank (QW) |
|---|---|---|---|---|---|---|---|---|---|---|
| Claude Code | L05, L11, L17 | 30 | 30 | 0 | 25.42 | 25.72 | 0.857 | 18.06 | 0.602 | 1 |
| Antigravity | L06, L12, L18 | 30 | 30 | 0 | 25.08 | 25.21 | 0.840 | 14.96 | 0.499 | 2 |
| Copilot/Grok-4.7 | L02, L08, L14 | 30 | 28 | 2 | 22.00 | 21.89 | 0.730 | 14.10 | 0.470 | 3 |
| Devin | L01, L07, L13 | 30 | 28 | 2 | 22.83 | 22.45 | 0.748 | 14.07 | 0.469 | 4 |
| Copilot/Kimi K3 | L03, L09, L15 | 30 | 30 | 0 | 23.25 | 23.26 | 0.775 | 13.90 | 0.463 | 5 |
| Codex/GPT-6 | L04, L10, L16 | 30 | 30 | 0 | 21.42 | 21.46 | 0.715 | 12.85 | 0.428 | 6 |

## Dropped (premise did not hold)

| Task | Title | Why |
|---|---|---|
| L07.7 | Replace the team-notification update-then-insert pair with a single upsert (roadmap 1, 7, 9) | update-then-insert confirmed NotificationsRepositoryImpl:343-350, but TeamNotification PK is random UUID with no unique (parentId,type) index, so @Upsert on PK would always insert -> proposed fix wrong without a unique index |
| L07.8 | Narrow ChatViewModel to TeamsShareRepository + UserReadRepository and lift the community-summary fallback into the model (roadmap 1, 3, 9) | premise false: ChatViewModel already injects TeamsShareRepository (:57) and UserReadRepository (:56) |
| L08.1 | split user id lookups so each side can use its index (roadmap 1+7+9) | premise false: EXPLAIN QUERY PLAN shows SQLite uses MULTI-INDEX OR on id PK + _id index for both getById and getUsersByAnyIdsInternal |
| L08.2 | split team id lookup and member-count existence check (roadmap 1+7) | premise false: getByTeamId `_id = ? OR teamId = ?` uses MULTI-INDEX OR (PK + teamId index); same for EXISTS subquery |

## Provenance — every raw task

| Raw | Agent | Verdict | Completeness | Merged into | n lists | Compl share | Rating |
|---|---|---|---|---|---|---|---|
| L01.1 | Devin | ok-partial | 3 | R105 `teams-joined-members-dedupe-reads` | 1 | 1.000 | 54 |
| L01.2 | Devin | ok | 4 | R049 `uploadconfigs-hoist-prefs-user-lookups` | 1 | 1.000 | 71 |
| L01.3 | Devin | ok | 4 | R066 `examdao-limit1-and-submissions-flow-zip` | 1 | 1.000 | 64 |
| L01.4 | Devin | ok | 4 | R059 `urlutils-hosturl-cache` | 1 | 1.000 | 66 |
| L01.5 | Devin | ok | 4 | R067 `examtaking-other-choice-hoist` | 1 | 1.000 | 64 |
| L01.6 | Devin | ok | 4 | R095 `voicesadapter-leaders-by-name` | 1 | 1.000 | 61 |
| L01.7 | Devin | ok | 4 | R092 `submissions-adapters-date-cache` | 1 | 1.000 | 61 |
| L01.8 | Devin | ok | 3 | R125 `feedbackadapter-label-cache` | 1 | 1.000 | 46 |
| L01.9 | Devin | ok | 3 | R097 `voiceslabelmanager-label-memo` | 1 | 1.000 | 61 |
| L01.10 | Devin | ok | 4 | R040 `notifications-task-team-names-dedupe` | 1 | 1.000 | 71 |
| L02.1 | Copilot/Grok-4.7 | ok | 4 | R002 `dashboard-challenge-gate-early` | 2 | 0.444 | 91 |
| L03.1 | Copilot/Kimi K3 | ok | 5 | R002 `dashboard-challenge-gate-early` | 2 | 0.556 | 91 |
| L02.2 | Copilot/Grok-4.7 | ok | 4 | R056 `progress-earned-exam-passes-batch` | 1 | 1.000 | 66 |
| L02.3 | Copilot/Grok-4.7 | ok | 4 | R031 `coursedetail-parallel-reads` | 1 | 1.000 | 71 |
| L02.4 | Copilot/Grok-4.7 | ok | 4 | R028 `takecourse-loadcourse-parallel` | 4 | 0.250 | 71 |
| L02.5 | Copilot/Grok-4.7 | ok | 4 | R016 `resourcesfragment-index-models` | 2 | 0.444 | 77 |
| L02.6 | Copilot/Grok-4.7 | ok | 3 | R102 `inlineresource-file-exist-cache` | 1 | 1.000 | 56 |
| L02.7 | Copilot/Grok-4.7 | ok | 4 | R037 `news-reactions-parse-cache` | 1 | 1.000 | 71 |
| L02.8 | Copilot/Grok-4.7 | ok | 3 | R064 `chat-sort-key-precompute` | 1 | 1.000 | 64 |
| L02.9 | Copilot/Grok-4.7 | ok | 3 | R082 `user-sort-lowercase-once` | 1 | 1.000 | 64 |
| L02.10 | Copilot/Grok-4.7 | ok | 2 | R115 `sha-checksum-buffer` | 1 | 1.000 | 49 |
| L03.2 | Copilot/Kimi K3 | ok | 4 | R042 `surveys-count-query` | 1 | 1.000 | 71 |
| L03.3 | Copilot/Kimi K3 | ok-weak | 2 | R134 `userentity-base64-memo` | 1 | 1.000 | 40 |
| L03.4 | Copilot/Kimi K3 | ok | 3 | R001 `retryutils-cancellation` | 3 | 0.250 | 95 |
| L03.5 | Copilot/Kimi K3 | ok | 4 | R075 `printstacktrace-transactionsync` | 1 | 1.000 | 64 |
| L03.6 | Copilot/Kimi K3 | ok | 3 | R074 `printstacktrace-fileuploader` | 1 | 1.000 | 64 |
| L03.7 | Copilot/Kimi K3 | ok | 4 | R004 `healthexam-cancellation-safe` | 2 | 0.500 | 85 |
| L03.8 | Copilot/Kimi K3 | ok | 3 | R072 `printstacktrace-course-viewmodels` | 1 | 1.000 | 64 |
| L03.9 | Copilot/Kimi K3 | ok | 3 | R111 `meetup-dead-trycatch` | 1 | 1.000 | 49 |
| L03.10 | Copilot/Kimi K3 | ok | 3 | R073 `printstacktrace-feedback-healthexam-models` | 1 | 1.000 | 64 |
| L04.1 | Codex/GPT-6 | ok | 3 | R048 `textpreview-bounded-read` | 1 | 1.000 | 71 |
| L04.2 | Codex/GPT-6 | ok | 4 | R032 `coursedetail-vm-markdown-offmain` | 1 | 1.000 | 71 |
| L04.3 | Codex/GPT-6 | ok | 3 | R039 `notifications-grouping-offmain` | 1 | 1.000 | 71 |
| L04.4 | Codex/GPT-6 | ok | 4 | R012 `coursesteps-cancel-child-launches` | 2 | 0.444 | 80 |
| L04.5 | Codex/GPT-6 | ok | 3 | R090 `resourcesfilter-filter-before-search` | 1 | 1.000 | 61 |
| L04.6 | Codex/GPT-6 | ok | 3 | R060 `fileutils-displayname-projection` | 3 | 0.273 | 64 |
| L04.7 | Codex/GPT-6 | ok | 2 | R113 `pdfthumb-sizeof-ceil` | 1 | 1.000 | 49 |
| L04.8 | Codex/GPT-6 | ok | 2 | R107 `chatsearch-bucket-concat` | 1 | 1.000 | 49 |
| L04.9 | Codex/GPT-6 | ok | 2 | R108 `exporter-getexamid-substringbefore` | 1 | 1.000 | 49 |
| L04.10 | Codex/GPT-6 | ok | 2 | R087 `jsonutils-double-parse` | 1 | 1.000 | 61 |
| L05.1 | Claude Code | ok | 5 | R017 `surveys-question-count-sql` | 1 | 1.000 | 77 |
| L05.2 | Claude Code | ok | 5 | R003 `submissions-revupdate-markuploaded-tx` | 1 | 1.000 | 86 |
| L05.3 | Claude Code | ok | 4 | R013 `uploadconfigs-markuploaded-tx` | 1 | 1.000 | 80 |
| L05.4 | Claude Code | ok | 4 | R041 `progress-count-steps-sql` | 1 | 1.000 | 71 |
| L05.5 | Claude Code | ok | 5 | R027 `courses-getbyid-skip-steps` | 1 | 1.000 | 72 |
| L05.6 | Claude Code | ok | 4 | R034 `download-links-buffer-drain` | 1 | 1.000 | 71 |
| L05.7 | Claude Code | ok | 4 | R038 `notification-read-sync-semaphore` | 1 | 1.000 | 71 |
| L05.8 | Claude Code | ok | 4 | R044 `teamvoices-single-load-sql-count` | 1 | 1.000 | 71 |
| L05.9 | Claude Code | ok | 5 | R016 `resourcesfragment-index-models` | 2 | 0.556 | 77 |
| L05.10 | Claude Code | ok | 4 | R096 `voicesadapter-shared-zoom-viewer` | 1 | 1.000 | 61 |
| L06.1 | Antigravity | ok | 2 | R123 `examanswer-multiselect-freqmap` | 1 | 1.000 | 46 |
| L06.2 | Antigravity | ok | 4 | R028 `takecourse-loadcourse-parallel` | 4 | 0.250 | 71 |
| L06.3 | Antigravity | ok | 3 | R076 `requests-joinedmembers-parallel` | 1 | 1.000 | 64 |
| L06.4 | Antigravity | ok | 4 | R062 `chat-sharetargets-parallel` | 2 | 0.571 | 64 |
| L06.5 | Antigravity | ok | 2 | R128 `resourcesfilter-facet-prenormalize` | 1 | 1.000 | 46 |
| L06.6 | Antigravity | ok | 2 | R129 `storagecategory-toggle-single` | 1 | 1.000 | 46 |
| L06.7 | Antigravity | ok | 4 | R008 `notifications-group-expand-normalize` | 1 | 1.000 | 85 |
| L06.8 | Antigravity | ok | 3 | R124 `exporter-drawmultiline-measure` | 1 | 1.000 | 46 |
| L06.9 | Antigravity | ok | 2 | R119 `tts-csv-buildstring` | 1 | 1.000 | 49 |
| L06.10 | Antigravity | ok | 3 | R130 `voiceslabel-addlabel-set-cache` | 1 | 1.000 | 46 |
| L07.1 | Devin | ok | 4 | R050 `usersync-move-shelf-reads` | 1 | 1.000 | 71 |
| L07.2 | Devin | ok-partial | 3 | R099 `userread-promote-profile-reads` | 1 | 1.000 | 60 |
| L07.3 | Devin | ok-partial | 3 | R010 `userread-narrow-dashboard-vms` | 4 | 0.188 | 82 |
| L07.4 | Devin | ok | 4 | R011 `userread-narrow-resource-vms` | 3 | 0.308 | 82 |
| L07.5 | Devin | ok | 3 | R098 `login-guest-flow-into-vm` | 1 | 1.000 | 60 |
| L07.6 | Devin | ok | 4 | R051 `community-blank-filter-sql` | 2 | 0.444 | 69 |
| L07.7 | Devin | DROP | 0 | — | — | 0 | — |
| L07.8 | Devin | DROP | 0 | — | — | 0 | — |
| L07.9 | Devin | ok | 3 | R054 `health-user-sort-sql` | 1 | 1.000 | 66 |
| L07.10 | Devin | ok | 4 | R019 `userread-narrow-course-vms` | 2 | 0.500 | 75 |
| L08.1 | Copilot/Grok-4.7 | DROP | 0 | — | — | 0 | — |
| L08.2 | Copilot/Grok-4.7 | DROP | 0 | — | — | 0 | — |
| L08.3 | Copilot/Grok-4.7 | ok | 4 | R036 `mylife-userid-sargable` | 1 | 1.000 | 71 |
| L08.4 | Copilot/Grok-4.7 | ok | 3 | R088 `personal-dao-sargable` | 1 | 1.000 | 61 |
| L08.5 | Copilot/Grok-4.7 | ok | 4 | R023 `resourceactivity-groupby-bare` | 1 | 1.000 | 74 |
| L08.6 | Copilot/Grok-4.7 | ok | 4 | R006 `questiondao-stale-delete-tx` | 2 | 0.500 | 85 |
| L08.7 | Copilot/Grok-4.7 | ok | 4 | R065 `communityservices-narrow-teams` | 1 | 1.000 | 64 |
| L08.8 | Copilot/Grok-4.7 | ok | 4 | R053 `life-vm-getcurrentuserid-read` | 2 | 0.444 | 66 |
| L08.9 | Copilot/Grok-4.7 | ok | 3 | R085 `coursesteps-next-download-set-repo` | 1 | 1.000 | 61 |
| L08.10 | Copilot/Grok-4.7 | ok | 4 | R010 `userread-narrow-dashboard-vms` | 4 | 0.250 | 82 |
| L09.1 | Copilot/Kimi K3 | ok-weak | 2 | R136 `log-community-repo` | 1 | 1.000 | 34 |
| L09.2 | Copilot/Kimi K3 | ok-weak | 2 | R140 `log-retry-repo` | 1 | 1.000 | 34 |
| L09.3 | Copilot/Kimi K3 | ok-weak | 2 | R137 `log-health-repo` | 1 | 1.000 | 34 |
| L09.4 | Copilot/Kimi K3 | ok-weak | 2 | R139 `log-personals-repo` | 1 | 1.000 | 34 |
| L09.5 | Copilot/Kimi K3 | ok-weak | 2 | R138 `log-news-model` | 1 | 1.000 | 34 |
| L09.6 | Copilot/Kimi K3 | ok | 3 | R057 `teamstasks-filters-into-vm` | 1 | 1.000 | 66 |
| L09.7 | Copilot/Kimi K3 | ok | 3 | R093 `surveys-search-extract` | 1 | 1.000 | 61 |
| L09.8 | Copilot/Kimi K3 | ok | 3 | R086 `events-toggle-requery` | 1 | 1.000 | 61 |
| L09.9 | Copilot/Kimi K3 | ok | 3 | R083 `chat-latest-rev-sql` | 1 | 1.000 | 61 |
| L09.10 | Copilot/Kimi K3 | ok | 4 | R069 `notifreceiver-inject-broadcast` | 1 | 1.000 | 64 |
| L10.1 | Codex/GPT-6 | ok-weak | 2 | R120 `life-reorder-owner-check` | 1 | 1.000 | 48 |
| L10.2 | Codex/GPT-6 | ok-weak | 2 | R131 `stale-delete-bounded-coursestep` | 1 | 1.000 | 44 |
| L10.3 | Codex/GPT-6 | ok-weak | 2 | R132 `stale-delete-bounded-question` | 1 | 1.000 | 44 |
| L10.4 | Codex/GPT-6 | ok | 4 | R005 `payloadbuilder-cancellation` | 2 | 0.500 | 85 |
| L10.5 | Codex/GPT-6 | ok | 4 | R018 `dictionary-db-before-file` | 2 | 0.571 | 75 |
| L10.6 | Codex/GPT-6 | ok | 4 | R011 `userread-narrow-resource-vms` | 3 | 0.103 | 82 |
| L10.7 | Codex/GPT-6 | ok | 4 | R011 `userread-narrow-resource-vms` | 3 | 0.103 | 82 |
| L10.8 | Codex/GPT-6 | ok | 4 | R019 `userread-narrow-course-vms` | 2 | 0.500 | 75 |
| L10.9 | Codex/GPT-6 | ok | 4 | R010 `userread-narrow-dashboard-vms` | 4 | 0.250 | 82 |
| L10.10 | Codex/GPT-6 | ok | 4 | R011 `userread-narrow-resource-vms` | 3 | 0.103 | 82 |
| L11.1 | Claude Code | ok | 5 | R010 `userread-narrow-dashboard-vms` | 4 | 0.312 | 82 |
| L11.2 | Claude Code | ok | 5 | R011 `userread-narrow-resource-vms` | 3 | 0.385 | 82 |
| L11.3 | Claude Code | ok | 4 | R015 `notifications-vm-drop-context` | 2 | 0.444 | 77 |
| L11.4 | Claude Code | ok | 4 | R055 `life-vm-double-read` | 1 | 1.000 | 66 |
| L11.5 | Claude Code | ok | 5 | R051 `community-blank-filter-sql` | 2 | 0.556 | 69 |
| L11.6 | Claude Code | ok | 4 | R047 `tests-mockito-to-mockk` | 1 | 1.000 | 71 |
| L11.7 | Claude Code | ok | 4 | R068 `hilt-modules-into-di` | 1 | 1.000 | 64 |
| L11.8 | Claude Code | ok | 3 | R084 `community-ids-into-state` | 1 | 1.000 | 61 |
| L11.9 | Claude Code | ok | 3 | R103 `login-saveusers-into-vm` | 1 | 1.000 | 56 |
| L11.10 | Claude Code | ok | 4 | R029 `chat-current-user-into-vm` | 2 | 0.500 | 71 |
| L12.1 | Antigravity | ok | 4 | R078 `settings-vm-drop-usersessionmanager` | 1 | 1.000 | 64 |
| L12.2 | Antigravity | ok | 4 | R079 `takecourse-unused-usm-and-narrow` | 1 | 1.000 | 64 |
| L12.3 | Antigravity | ok | 4 | R029 `chat-current-user-into-vm` | 2 | 0.500 | 71 |
| L12.4 | Antigravity | ok-partial | 3 | R018 `dictionary-db-before-file` | 2 | 0.429 | 75 |
| L12.5 | Antigravity | ok | 2 | R126 `mylife-visibility-rowcount` | 1 | 1.000 | 46 |
| L12.6 | Antigravity | ok | 3 | R021 `teamlog-delete-tx` | 1 | 1.000 | 75 |
| L12.7 | Antigravity | ok | 4 | R006 `questiondao-stale-delete-tx` | 2 | 0.500 | 85 |
| L12.8 | Antigravity | ok | 3 | R089 `resources-vm-narrow-and-sort-dispatcher` | 1 | 1.000 | 61 |
| L12.9 | Antigravity | ok | 3 | R091 `submissiondetail-narrow-and-statein` | 1 | 1.000 | 61 |
| L12.10 | Antigravity | ok | 5 | R053 `life-vm-getcurrentuserid-read` | 2 | 0.556 | 66 |
| L13.1 | Devin | ok | 4 | R071 `printstacktrace-batch` | 1 | 1.000 | 64 |
| L13.2 | Devin | ok | 4 | R028 `takecourse-loadcourse-parallel` | 4 | 0.250 | 71 |
| L13.3 | Devin | ok | 3 | R062 `chat-sharetargets-parallel` | 2 | 0.429 | 64 |
| L13.4 | Devin | ok | 3 | R035 `exporter-batch-exam-lookup` | 1 | 1.000 | 71 |
| L13.5 | Devin | ok | 4 | R014 `automerge-batch-pr-view` | 2 | 0.444 | 77 |
| L13.6 | Devin | ok | 4 | R025 `test-hoist-static-mocks-loginsync` | 2 | 0.444 | 72 |
| L13.7 | Devin | ok | 4 | R026 `test-hoist-static-mocks-userrepo` | 2 | 0.444 | 72 |
| L13.8 | Devin | ok | 2 | R122 `coursesfilter-englishvalues-cache` | 1 | 1.000 | 46 |
| L13.9 | Devin | ok | 2 | R112 `networkmonitor-delay-rename` | 1 | 1.000 | 49 |
| L13.10 | Devin | ok | 2 | R110 `hoist-invariant-getstring` | 1 | 1.000 | 49 |
| L14.1 | Copilot/Grok-4.7 | ok | 4 | R022 `ci-master-build-cache` | 1 | 1.000 | 74 |
| L14.2 | Copilot/Grok-4.7 | ok | 4 | R030 `retryinterceptor-intent-out-robolectric` | 2 | 0.500 | 71 |
| L15.2 | Copilot/Kimi K3 | ok | 4 | R030 `retryinterceptor-intent-out-robolectric` | 2 | 0.500 | 71 |
| L14.3 | Copilot/Grok-4.7 | ok | 4 | R060 `fileutils-displayname-projection` | 3 | 0.364 | 64 |
| L16.8 | Codex/GPT-6 | ok | 4 | R060 `fileutils-displayname-projection` | 3 | 0.364 | 64 |
| L14.4 | Copilot/Grok-4.7 | ok | 4 | R052 `test-hoist-mocks-voicesnewssync` | 3 | 0.333 | 66 |
| L15.5 | Copilot/Kimi K3 | ok | 4 | R052 `test-hoist-mocks-voicesnewssync` | 3 | 0.333 | 66 |
| L16.3 | Codex/GPT-6 | ok | 4 | R052 `test-hoist-mocks-voicesnewssync` | 3 | 0.333 | 66 |
| L14.5 | Copilot/Grok-4.7 | ok | 4 | R033 `coursesteps-markdown-offmain` | 1 | 1.000 | 71 |
| L14.6 | Copilot/Grok-4.7 | ok-weak | 2 | R135 `teamcourses-picker-offmain` | 1 | 1.000 | 36 |
| L14.7 | Copilot/Grok-4.7 | ok | 3 | R127 `playstore-quota-releases-payload` | 1 | 1.000 | 46 |
| L14.8 | Copilot/Grok-4.7 | ok | 3 | R100 `automerge-publish-failed-n+1` | 1 | 1.000 | 56 |
| L14.9 | Copilot/Grok-4.7 | ok | 3 | R058 `test-imageutils-spy-decode` | 1 | 1.000 | 66 |
| L14.10 | Copilot/Grok-4.7 | ok | 4 | R061 `test-stepexam-textutils` | 3 | 0.364 | 64 |
| L18.5 | Antigravity | ok | 3 | R061 `test-stepexam-textutils` | 3 | 0.273 | 64 |
| L16.5 | Codex/GPT-6 | ok | 4 | R061 `test-stepexam-textutils` | 3 | 0.364 | 64 |
| L15.1 | Copilot/Kimi K3 | ok | 4 | R001 `retryutils-cancellation` | 3 | 0.333 | 95 |
| L17.7 | Claude Code | ok | 5 | R001 `retryutils-cancellation` | 3 | 0.417 | 95 |
| L15.3 | Copilot/Kimi K3 | ok | 5 | R026 `test-hoist-static-mocks-userrepo` | 2 | 0.556 | 72 |
| L15.4 | Copilot/Kimi K3 | ok | 5 | R025 `test-hoist-static-mocks-loginsync` | 2 | 0.556 | 72 |
| L15.6 | Copilot/Kimi K3 | ok | 4 | R028 `takecourse-loadcourse-parallel` | 4 | 0.250 | 71 |
| L15.7 | Copilot/Kimi K3 | ok | 4 | R004 `healthexam-cancellation-safe` | 2 | 0.500 | 85 |
| L15.8 | Copilot/Kimi K3 | ok | 4 | R005 `payloadbuilder-cancellation` | 2 | 0.500 | 85 |
| L15.9 | Copilot/Kimi K3 | ok | 3 | R020 `networkmonitor-cancellation` | 1 | 1.000 | 75 |
| L15.10 | Copilot/Kimi K3 | ok | 3 | R063 `publicsurvey-timeprovider` | 2 | 0.429 | 64 |
| L18.7 | Antigravity | ok | 4 | R063 `publicsurvey-timeprovider` | 2 | 0.571 | 64 |
| L16.1 | Codex/GPT-6 | ok | 5 | R012 `coursesteps-cancel-child-launches` | 2 | 0.556 | 80 |
| L16.2 | Codex/GPT-6 | ok | 3 | R043 `teamcourses-coalesce-loads` | 1 | 1.000 | 71 |
| L16.4 | Codex/GPT-6 | ok | 3 | R080 `test-freespace-log-mock` | 1 | 1.000 | 64 |
| L16.6 | Codex/GPT-6 | ok | 3 | R104 `test-userentity-encode-mocks` | 1 | 1.000 | 56 |
| L16.7 | Codex/GPT-6 | ok | 3 | R133 `test-reachability-hoist-mocks` | 1 | 1.000 | 43 |
| L16.9 | Codex/GPT-6 | ok | 2 | R114 `search-prefix-buffer` | 1 | 1.000 | 49 |
| L16.10 | Codex/GPT-6 | ok | 3 | R118 `timing-script-index-sources` | 1 | 1.000 | 49 |
| L17.1 | Claude Code | ok | 4 | R024 `uploadhealth-await` | 1 | 1.000 | 74 |
| L17.2 | Claude Code | ok | 4 | R094 `syncactivity-injected-appscope` | 1 | 1.000 | 61 |
| L17.3 | Claude Code | ok | 4 | R077 `resources-sort-dispatcher` | 1 | 1.000 | 64 |
| L17.4 | Claude Code | ok | 3 | R106 `chat-parse-default-dispatcher` | 1 | 1.000 | 49 |
| L17.5 | Claude Code | ok | 3 | R116 `stableid-platform-free` | 1 | 1.000 | 49 |
| L17.6 | Claude Code | ok | 4 | R007 `basecontainer-viewlifecycle-scope` | 1 | 1.000 | 85 |
| L17.8 | Claude Code | ok | 4 | R009 `takecourse-error-path` | 1 | 1.000 | 85 |
| L17.9 | Claude Code | ok | 5 | R015 `notifications-vm-drop-context` | 2 | 0.556 | 77 |
| L17.10 | Claude Code | ok | 4 | R045 `test-drop-log-mocks` | 1 | 1.000 | 71 |
| L18.1 | Antigravity | ok | 4 | R070 `playstore-quota-double-measure` | 1 | 1.000 | 64 |
| L18.2 | Antigravity | ok | 3 | R121 `coauthors-lazy-collaborators` | 1 | 1.000 | 46 |
| L18.3 | Antigravity | ok | 5 | R014 `automerge-batch-pr-view` | 2 | 0.556 | 77 |
| L18.4 | Antigravity | ok | 4 | R046 `test-pdfthumb-sdk-pin` | 1 | 1.000 | 71 |
| L18.6 | Antigravity | ok | 3 | R109 `healthmapper-drop-textutils` | 1 | 1.000 | 49 |
| L18.8 | Antigravity | ok | 3 | R101 `feedback-dual-lookup` | 1 | 1.000 | 56 |
| L18.9 | Antigravity | ok | 2 | R117 `tags-dedupe-shortcircuit` | 1 | 1.000 | 49 |
| L18.10 | Antigravity | ok | 3 | R081 `user-securitykeys-skip-upsert` | 1 | 1.000 | 64 |

## Counts and checks

- Lists: 18 · raw tasks: 180 (10 per list) · good: 176 · dropped: 4 · shipped merged tasks: 140 (115 unique, 25 merged from 2+ lists)
- Point-sum check: equal-split sum 140.0000, completeness-split sum 140.0000, shipped 140 → PASS; quality-weighted sum 87.9400 = Σratings/100 87.9400 → PASS; good+dropped = 180 = raw 180

## Guesses and judgment calls

- L17 is signed "Claude Code · effort low" with no model; it is scored in the Claude Code slot with L05/L11. The Antigravity slot mixes models (L06/L18 Claude Opus 4.6, L12 Gemini 3.1 Pro), so it is aggregated by service as signed.
- Prompt grouping (P1 = L01–06, P2 = L07–12, P3 = L13–18) is inferred from the list order and document titles.
- Impact and feasibility scores are my judgment. No Gradle build, test run, or on-device profiling was done. SQL index claims were checked with `EXPLAIN QUERY PLAN` on a synthetic schema rebuilt from the `@Entity` indices, using host sqlite3 rather than Android's SQLite build.
- The "narrow VM to `UserReadRepository`" proposals (11 tasks across 5 lists) were split into three merged tasks by ViewModel family (dashboard/profile, resources/submission, course). L10.10 (Personals + Resources) is a tie and was placed with resources. L11.2 covers both resource and course VMs and was placed with resources (3 of its 5 VMs).
- Tasks that apply the same pattern to different files (L09.1–L09.5 Log removal, L10.2/L10.3 bounded stale delete) stay separate tasks, so each counts as unique.
- Tasks that overlap without being the same were kept separate, with the overlap noted in the backlog. Examples: L13.1 printStackTrace batch vs R001/R005, L17.8 TakeCourse error path vs the TakeCourse parallel-load task, L18.5 broad mock cleanup vs L14.10/L16.5.
- When one list has several tasks inside one merged task, the list gets a single share. Its tasks split that share evenly in the provenance table.
- Agent claims about open-PR file ownership were not re-verified, so some shipped tasks may touch files owned by open PRs.
- L03.3 (Base64 memo) is ambiguous between a per-path cache, which would hit, and a per-instance cache, which would not. It is kept at a low rating.
