# Megaplan crosswalk and method

## Method

1. **Join key.** All three backlogs record which raw tasks (`Lxx.yy` = list xx, task yy, out of 18 lists × 10) went into each merged task. Opus's `refactor_tasks_scoring.md`, Devin's `refactor_tasks_scoring.md` and Codex's `scoring_table.md` were parsed for those ids.
2. **Matching.** Each Opus task was matched to every Devin and Codex task that shares a raw id. The best Jaccard match supplies that plan's rating. Bundled raw tasks (e.g. L13.01, a printStackTrace sweep across many files) produce extra cross-references but never merge two Opus tasks.
3. **Score.** Each plan's ratings were converted to percentile ranks (ties take the mean rank), and a task's score is the mean over the plans that kept it. This cancels the three different rating scales: Codex rates almost everything 70-95, while Opus reserves 70+ for real correctness or query wins.
4. **Leftovers.** Devin/Codex tasks with no Opus counterpart are exactly the raw tasks Opus dropped. Each was re-checked against `2093a62` where it was cheap to do so, then classed as *optional* (Appendix A) or *rejected* (Appendix B). Raw tasks dropped by both Opus and Codex were rejected without re-litigating.
5. **Corrections applied:** dictionary `loadCount` (Codex beats Opus), challenge-gate test breakage (Opus beats Devin), and the CI cache premise (Codex caveat on Opus #1). Each is marked **Megaplan correction/caution/note** in the task.
6. **Known limits.** Codex verified against `3d02e24` (7 commits behind `2093a62`; those commits touched `BaseContainerFragment`, `ChatRepositoryImpl`, `News.kt`, `ResourceViewer*`, `UserProfileFragment`, `VoicesUploader`, `DownloadUtils`). No task was implemented or built for this merge. Open-PR file-ownership claims in the task text were not re-checked.

## Crosswalk

| M | score | Opus | O | Devin | D | Codex | C | raw |
|---|---|---|---|---|---|---|---|---|
| M001 | 97 | #2 | 76 | R002 | 91 | T009 | 91 | L02.01, L03.01 |
| M002 | 97 | #3 | 71 | R005, R071 | 85 | T001, T015, T008, T014 | 95 | L10.04, L13.01, L15.08 |
| M003 | 95 | #9 | 69 | R001, R071 | 95 | T008, T015, T001, T014 | 91 | L03.04, L13.01, L15.01, L17.07 |
| M004 | 93 | #4 | 71 | R018 | 75 | T005 | 93 | L10.05, L12.04 |
| M005 | 92 | #17 | 55 | R006, R132 | 85 | T004 | 93 | L08.06, L10.03, L12.07 |
| M006 | 91 | #12 | 69 | R024 | 74 | T003 | 93 | L17.01 |
| M007 | 90 | #7 | 71 | R017 | 77 | T023 | 86 | L05.01 |
| M008 | 89 | #5 | 71 | R027 | 72 | T017 | 88 | L05.05 |
| M009 | 89 | #22 | 55 | R003 | 86 | T019 | 87 | L05.02 |
| M010 | 84 | #6 | 71 | R041 | 71 | T022 | 86 | L05.04 |
| M011 | 84 | #10 | 69 | R028, R072, R009 | 71 | T016, T026, T014 | 88 | L03.08, L13.02, L15.06, L17.08 |
| M012 | 83 | #23 | 55 | R048 | 71 | T011 | 91 | L04.01 |
| M013 | 83 | #30 | 50 | R016 | 77 | T018 | 87 | L02.05, L05.09 |
| M014 | 83 | #31 | 50 | R013 | 80 | T025 | 85 | L05.03 |
| M015 | 82 | #8 | 71 | R044 | 71 | T031 | 83 | L05.08 |
| M016 | 80 | #1 | 78 | R022 | 74 | T057 | 75 | L14.01 |
| M017 | 78 | #11 | 69 | R030 | 71 | T040 | 79 | L14.02, L15.02 |
| M018 | 78 | #20 | 55 | R015 | 77 | T052 | 77 | L11.03, L17.09 |
| M019 | 77 | #15 | 55 | R011, R019, R091, R089, R079, R010 | 82 | T062, T053, T060, T061, T037 | 75 | L07.04, L07.10, L08.10, L10.06, L10.07, L10.08, L10.10, L11.02, L12.02, L12.08, L12.09 |
| M020 | 77 | #16 | 55 | R010, R011, R084 | 82 | T061, T062, T053, T106 | 75 | L07.03, L07.04, L08.10, L10.09, L10.10, L11.01, L11.08 |
| M021 | 76 | #29 | 50 | R028 | 71 | T026, T016 | 84 | L02.04, L06.02, L13.02, L15.06 |
| M022 | 74 | #14 | 58 | R034 | 71 | T049 | 77 | L05.06 |
| M023 | 73 | #63 | 38 | R004, R081, R071, R009 | 85 | T014, T015, T029, T001, T016, T008 | 90 | L03.07, L13.01, L15.07, L17.08, L18.10 |
| M024 | 72 | #32 | 48 | R056 | 66 | T024 | 85 | L02.02 |
| M025 | 72 | #72 | 38 | R012 | 80 | T007 | 91 | L04.04, L16.01 |
| M026 | 70 | #13 | 67 | R046 | 71 | T069 | 73 | L18.04 |
| M027 | 68 | #28 | 52 | R081 | 64 | T029, T014 | 83 | L18.10 |
| M028 | 65 | #52 | 39 | R036 | 71 | T047 | 78 | L08.03 |
| M029 | 63 | #18 | 55 | R053 | 66 | T075 | 72 | L08.08, L12.10 |
| M030 | 63 | #26 | 53 | R047 | 71 | T082 | 71 | L11.06 |
| M031 | 63 | #70 | 38 | R026 | 72 | T033 | 82 | L13.07, L15.03 |
| M032 | 63 | #105 | 34 | R008 | 85 | T021 | 86 | L06.07 |
| M033 | 62 | #24 | 55 | R082 | 64 | T058 | 75 | L02.09 |
| M034 | 62 | #64 | 38 | R025, R061 | 72 | T036, T086, T043 | 80 | L13.06, L15.04, L18.05 |
| M035 | 61 | #33 | 48 | R050 | 71 | T087 | 71 | L07.01 |
| M036 | 61 | #35 | 45 | R120 | 48 | T002 | 94 | L10.01 |
| M037 | 61 | #94 | 37 | R014 | 77 | T028 | 83 | L13.05, L18.03 |
| M038 | 60 | #39 | 39 | R060 | 64 | T038 | 80 | L04.06, L14.03, L16.08 |
| M039 | 59 | #41 | 39 | R061 | 64 | T043, T086, T036 | 79 | L14.10, L18.05 |
| M040 | 58 | #73 | 38 | R033 | 71 | T042 | 79 | L14.05 |
| M041 | 57 | #65 | 38 | R077, R071, R004 | 64 | T015, T014, T037, T001, T008 | 89 | L13.01, L15.07, L17.03 |
| M042 | 56 | #36 | 39 | R029 | 71 | T081 | 71 | L11.10, L12.03 |
| M043 | 56 | #54 | 39 | R090 | 61 | T034 | 81 | L04.05 |
| M044 | 55 | #25 | 55 | R096 | 61 | T071 | 72 | L05.10 |
| M045 | 55 | #27 | 53 | R040 | 71 | T112 | 64 | L01.10 |
| M046 | 55 | #37 | 39 | R051 | 69 | T073 | 72 | L07.06, L11.05 |
| M047 | 55 | #40 | 39 | R089, R077 | 61 | T037, T015, T062 | 80 | L12.08, L17.03 |
| M048 | 55 | #71 | 38 | R031 | 71 | T051 | 77 | L02.03 |
| M049 | 54 | #19 | 55 | R098 | 60 | T066 | 73 | L07.05 |
| M050 | 54 | #49 | 39 | R055 | 66 | T072 | 72 | L11.04 |
| M051 | 54 | #59 | 39 | R079 | 64 | T060 | 75 | L12.02 |
| M052 | 54 | #81 | 38 | R037 | 71 | T055 | 76 | L02.07 |
| M053 | 53 | #21 | 55 | R078 | 64 | T098 | 69 | L12.01 |
| M054 | 53 | #45 | 39 | R131 | 44 | T006 | 92 | L10.02 |
| M055 | 53 | #98 | 35 | R039 | 71 | T035 | 81 | L04.03 |
| M056 | 53 | #103 | 34 | R032 | 71 | T030 | 83 | L04.02 |
| M057 | 51 | #66 | 38 | R052 | 66 | T050 | 77 | L14.04, L15.05, L16.03 |
| M058 | 51 | #69 | 38 | R063 | 64 | T039 | 80 | L15.10, L18.07 |
| M059 | 50 | #42 | 39 | R100 | 56 | T044 | 78 | L14.08 |
| M060 | 49 | #67 | 38 | R062 | 64 | T045 | 78 | L06.04, L13.03 |
| M061 | 46 | #34 | 46 | R099 | 60 | T080 | 71 | L07.02 |
| M062 | 46 | #47 | 39 | R086 | 61 | T074 | 72 | L09.08 |
| M063 | 43 | #53 | 39 | R069 | 64 | T101 | 68 | L09.10 |
| M064 | 43 | #83 | 38 | R076 | 64 | T068 | 73 | L06.03 |
| M065 | 43 | #87 | 38 | R091 | 61 | T053 | 77 | L12.09 |
| M066 | 43 | #111 | 33 | R070 | 64 | T032 | 83 | L18.01 |
| M067 | 42 | #60 | 39 | R135 | 36 | T046 | 78 | L14.06 |
| M068 | 42 | #112 | 32 | R057 | 66 | T048 | 78 | L09.06 |
| M069 | 40 | #113 | 27 | R058 | 66 | T056 | 76 | L14.09 |
| M070 | 39 | #100 | 34 | R045, R061 | 71 | T086, T093, T043, T036 | 71 | L17.10, L18.05 |
| M071 | 38 | #46 | 39 | R085 | 61 | T104 | 67 | L08.09 |
| M072 | 37 | #44 | 39 | R084 | 61 | T106, T061 | 65 | L11.08 |
| M073 | 37 | #51 | 39 | R126 | 46 | T076 | 72 | L12.05 |
| M074 | 37 | #68 | 38 | R080, R045 | 64 | T093 | 70 | L16.04, L17.10 |
| M075 | 37 | #88 | 38 | R066 | 64 | T090 | 70 | L01.03 |
| M076 | 37 | #91 | 38 | R075 | 64 | T092 | 70 | L03.05 |
| M077 | 36 | #50 | 39 | R103 | 56 | T102 | 68 | L11.09 |
| M078 | 35 | #48 | 39 | R087 | 61 | T116 | 60 | L04.10 |
| M079 | 35 | #89 | 38 | R094 | 61 | T083 | 71 | L17.02 |
| M080 | 34 | #77 | 38 | R074 | 64 | T105 | 67 | L03.06 |
| M081 | 34 | #92 | 38 | R104 | 56 | T070 | 72 | L16.06 |
| M082 | 33 | #61 | 39 | R119 | 49 | T103 | 68 | L06.09 |
| M083 | 32 | #101 | 34 | R064 | 64 | T079 | 71 | L02.08 |
| M084 | 31 | #79 | 38 | R068 | 64 | T115 | 60 | L11.07 |
| M085 | 31 | #93 | 38 | R095 | 61 | T096 | 69 | L01.06 |
| M086 | 30 | #43 | 39 | R115 | 49 | T114 | 62 | L02.10 |
| M087 | 30 | #62 | 39 | R130 | 46 | T100 | 68 | L06.10 |
| M088 | 30 | #90 | 38 | R118 | 49 | T084 | 71 | L16.10 |
| M089 | 30 | #95 | 37 | R106 | 49 | T059 | 75 | L17.04 |
| M090 | 30 | #109 | 33 | R065 | 64 | T085 | 71 | L08.07 |
| M091 | 30 | #110 | 33 | R035 | 71 | T111 | 64 | L13.04 |
| M092 | 29 | #38 | 39 | R124, R108 | 46 | T107 | 65 | L04.09, L06.08 |
| M093 | 29 | #74 | 38 | R067 | 64 | T123 | 53 | L01.05 |
| M094 | 28 | #82 | 38 | R133 | 43 | T067 | 73 | L16.07 |
| M095 | 27 | #96 | 37 | R073 | 64 | T113 | 63 | L03.10 |
| M096 | 26 | #85 | 38 | R116 | 49 | T099 | 69 | L17.05 |
| M097 | 25 | #108 | 34 | R059 | 66 | T124 | 52 | L01.04 |
| M098 | 24 | #86 | 38 | R092 | 61 | T125 | 51 | L01.07 |
| M099 | 24 | #106 | 34 | R088 | 61 | T095 | 69 | L08.04 |
| M100 | 24 | #107 | 34 | R105 | 54 | T078 | 71 | L01.01 |
| M101 | 23 | #57 | 39 | R139 | 34 | T117 | 59 | L09.04 |
| M102 | 23 | #58 | 39 | R140 | 34 | T119 | 57 | L09.02 |
| M103 | 23 | #78 | 38 | R109 | 49 | T108 | 65 | L18.06 |
| M104 | 23 | #84 | 38 | R114 | 49 | T109 | 65 | L16.09 |
| M105 | 22 | #56 | 39 | R138 | 34 | T121 | 54 | L09.05 |
| M106 | 20 | #55 | 39 | R136 | 34 | T128 | 50 | L09.01 |
| M107 | 20 | #75 | 38 | R124 | 46 | T107 | 65 | L06.08 |
| M108 | 18 | #80 | 38 | R111 | 49 | T126 | 51 | L03.09 |
| M109 | 18 | #99 | 35 | R127 | 46 | T088 | 70 | L14.07 |
| M110 | 18 | #102 | 34 | R107 | 49 | T097 | 69 | L04.08 |
| M111 | 16 | #76 | 38 | R125 | 46 | T122 | 54 | L01.08 |
| M112 | 9 | #104 | 34 | R112 | 49 | T131 | 44 | L13.09 |
| M113 | 6 | #97 | 36 | R137 | 34 | T129 | 49 | L09.03 |

## Contested and rejected

| raw | Devin | Codex | verdict |
|---|---|---|---|
| L17.06 | R007 | T012 | optional |
| L15.09 | R020 | — | reject |
| L12.06 | R021 | T063 | reject |
| L08.05 | R023 | T118 | optional |
| L05.07 | R038 | T010 | reject |
| L03.02 | R042 | T027 | optional |
| L16.02 | R043 | T013 | reject |
| L01.02 | R049 | — | reject |
| L07.09 | R054 | — | reject |
| L09.09 | R083 | — | reject |
| L09.07 | R093 | T065 | optional |
| L01.09 | R097 | T120 | reject |
| L18.08 | R101 | T054 | optional |
| L02.06 | R102 | — | reject |
| L13.10 | R110 | T127 | optional |
| L04.07 | R113 | T020 | reject |
| L18.09 | R117 | T091 | reject |
| L18.02 | R121 | T041 | reject |
| L13.08 | R122 | T110 | reject |
| L06.01 | R123 | T064 | reject |
| L06.05 | R128 | T094 | reject |
| L06.06 | R129 | T089 | reject |
| L03.03 | R134 | T077 | reject |
| L07.08 | — | T130 | optional |

## Coverage check

- Opus tasks carried: 113/113. Devin tasks referenced: 140/140. Codex tasks referenced: 131/131.
- Contested: 7 optional + 17 rejected = 24.
- Tasks kept by all three plans: 113; by two: 0; by Opus only: 0.
