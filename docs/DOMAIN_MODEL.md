# myPlanet Domain Model

This document explains the learning domain that myPlanet operates in — who the people are, what they do, and how the data concepts connect. Read this before working on any feature, because the app's language and data model only make sense once you understand the domain.

> All models named here are Room `@Entity` classes in `app/src/main/java/org/ole/planet/myplanet/model/`. The old `Realm*` class names are gone — e.g. `RealmUser` is now `UserEntity`, `RealmMyCourse` is `MyCourse`, `RealmNews` is `News`.

---

## Table of Contents

1. [The Platform: Planet and myPlanet](#the-platform-planet-and-myplanet)
2. [People: Roles and Relationships](#people-roles-and-relationships)
3. [Learning Content](#learning-content)
4. [Assessments: Exams and Surveys](#assessments-exams-and-surveys)
5. [Teams and Enterprises](#teams-and-enterprises)
6. [Community and Voices](#community-and-voices)
7. [Progress and Achievement](#progress-and-achievement)
8. [How Everything Connects](#how-everything-connects)
9. [Sync, Community, and Nation](#sync-community-and-nation)
10. [Glossary](#glossary)

---

## The Platform: Planet and myPlanet

**Planet** is the web-based Learning Management System (LMS) run by Open Learning Exchange (OLE). It lives on a local server — often a Raspberry Pi or similar device — in a school, library, or community centre. It holds all the learning content: resources, courses, surveys, user records, everything.

**myPlanet** is the Android app that connects to a Planet server. It lets learners download content onto their phone and keep learning when the server is unreachable. When connectivity returns, the app syncs back any progress, submissions, and activity data.

The app is designed for **low-connectivity environments**. Everything a learner can do offline — reading a resource, taking a course, completing an exam — is designed to work without the server present. The sync mechanism reconciles local changes with the server later.

---

## People: Roles and Relationships

### Learners

Learners are the primary users of myPlanet. They browse and download resources, enrol in courses, take exams, complete surveys, join teams, and post in community discussions. Most of what the app does is built for them.

Their activity is tracked across courses (`CourseProgress`), exams (`Submission`), and resource views (via activity logs).

### Managers (Coaches / Administrators)

A manager is a `UserEntity` where `isManager()` returns `true` — meaning `rolesList` contains `"manager"` or `userAdmin` is `true`. In the app's UI and in discussions, managers are sometimes called **coaches** or **administrators** depending on context.

Managers can:
- View learner submissions and progress
- Send surveys to learners
- Manage teams they lead
- View reports for enterprises they oversee

Note: `LoginSyncManager.kt` (private `isManager()`, around line 179) re-implements the same `"manager"` substring check independently on the raw `JsonObject` instead of calling `UserEntity.isManager()`. If you're changing manager-detection logic, grep for `.contains("manager")` as well as `.isManager()` — there are two copies to keep in sync.

### Leaders

A leader is a step below manager — a `UserEntity` where `rolesList` contains `"leader"`, checked via `UserEntity.isLeader()`. This helper has **zero call sites** in the current codebase — it is effectively dead code. Leaders have elevated permissions within a specific team or enterprise and appear with `isLeader = true` on their `MyTeam` membership record — that per-team flag is separate from the unused global `UserEntity.isLeader()` role check.

### Guests

A guest is a user whose ID starts with `"guest"`. The vast majority of the codebase checks this directly with `user?.id?.startsWith("guest")` rather than calling a helper — that pattern appears in ~22 files (`BellDashboardFragment`, `SettingsActivity`, `CoursesFragment`, `ResourcesFragment`, `TeamDetailFragment`, `UserRepositoryImpl`, `UserSessionManager`, and more).

A more careful helper, `UserEntity.isGuest()`, also exists. It checks `_id?.startsWith("guest_")` (note: the `_id` field and an underscore, unlike the ad-hoc checks on `id`) OR'd with a role check (`"guest"` role present and `"learner"` role absent). It is the more correct check — but it is not used consistently, so don't assume every guest-gated code path uses it, and note the two patterns are not textually identical.

Guests have read-only access. Many features — joining a course, submitting a survey, posting in Voices — show a prompt asking guests to become full members first (`BecomeMemberActivity`).

---

## Learning Content

### Resources (The Library)

A **resource** is a single piece of educational content — a PDF, video, audio file, image, or external link. Learners browse the library, download resources to their device, and open them offline.

**Key model:** `MyLibrary`

Important fields:
- `mediaType` / `resourceType` — what kind of content it is (PDF, video, audio, etc.)
- `resourceOffline` — `true` when the file is downloaded locally
- `resourceLocalAddress` / `resourceRemoteAddress` — local path vs server URL
- `userId` — list of learner IDs who have added this resource to their personal library ("My Library")
- `courseId` + `stepId` — if the resource belongs to a course step, these are set; a resource can exist both in the general library and inside a course step
- `tag` — list of tag IDs linking to `TagEntity`; used for filtering

Resources are rated (1–5 stars) via `Rating`. The `type` field on a rating is `"resource"` or `"course"` to distinguish what's being rated.

### Tags

Tags are labels attached to resources and courses for filtering and discovery. The persisted model is **`TagEntity`** (`@Entity(tableName = "tag")` — fields `name`, `tagId`, `linkId`, `attachedTo`, `docType`, `db`); the plain `data class Tag(id, name)` is a UI-side model only — don't conflate the two.

### Courses

A **course** is a structured learning path made up of ordered **steps**. A learner joins a course, progresses through its steps in sequence, and may need to pass exams embedded in the steps.

**Key models:** `MyCourse`, `CourseStep`

How they connect:
- A `MyCourse` has a list of `CourseStep` objects (`courseSteps`, an `@Ignore` in-memory field populated from the `CourseStep` table).
- Each `CourseStep` has a `courseId` linking it back to the course and holds a title, description, and step-level resource count.
- Resources attached to a specific step have both `courseId` and `stepId` set on their `MyLibrary` record.
- Exams and surveys are both optional per step, not guaranteed — a step can have zero, one, or more of each attached. Both share the same `StepExam` model and are distinguished by the `type` field: `"courses"` for exams, `"surveys"` for surveys. When present, a `StepExam` carries both `courseId` and `stepId`. `CoursesRepositoryImpl.getCourseStepData()` queries these separately — exams (`type = "courses"`) and surveys (`type = "surveys"`) — both returned as `List<StepExam>` that can be empty. `getCourseExamCount()` counts however many exam-type `StepExam` rows exist for a course, with no constraint tying that count to the number of steps; there's no equivalent survey-count helper.

A learner's membership in a course is tracked by the `userId` list on `MyCourse` — joining a course adds the learner's ID to that list. Progress through steps is tracked by `CourseProgress` records (one per step, per learner).

**Certifications:** `Certification` is a static reference list synced down from the server — it maps a certification `name` to `courseIds` (stored as a JSON **string**, not a list column). It has no `userId` and no completion/progress field; nothing in the codebase marks a certification as "earned" by a learner.

Course completion itself is tracked separately and per-learner: `ProgressRepositoryImpl.getCompletedCourses(userId)` (`ProgressRepositoryImpl.kt:152-183`) checks `CourseProgress` and counts a course complete once every one of its steps has a `passed = true` record. The dashboard (`BellDashboardFragment.showBadges()`, with the recoloring in `setColor()`) renders one star-icon badge per completed course on the profile card. `isCourseCertified(courseId)` — a `Certification` lookup with **no** `userId` parameter — is then used only to recolor that already-rendered badge: bright if the completed course happens to belong to some certification's course list, dim grey otherwise. So "certification" in this app is a label on a course, surfaced as a color cue on a learner's completion badge — not a separate achievement a learner unlocks.

---

## Assessments: Exams and Surveys

Both exams and surveys share the same underlying model (`StepExam`) and submission model (`Submission`). They differ in intent and where they appear.

### Exams

An **exam** is an assessment attached to a course step. It tests whether a learner has mastered the step's content. The learner must pass (exceed `passingPercentage`) to mark the step as complete. Exams are taken inside the course-taking flow via `ExamTakingFragment`.

The `StepExam` `type` field is `"courses"` for course exams. Questions are `ExamQuestion` objects (stored separately and linked by exam ID). A learner's answers are `Answer` objects collected inside a `Submission`.

### Surveys

A **survey** is a questionnaire that does not have a pass/fail outcome. Surveys are used by managers to gather feedback, health data, or other responses from learners. They can be:
- Standalone (assigned to learners directly from `SurveyFragment`)
- Attached to a course step (the `StepExam` `type` field is `"surveys"`)
- Team surveys (linked via `teamId` on `StepExam`)

Surveys can be sent to specific learners or made available community-wide. `SendSurveyFragment` handles the manager-side flow of assigning a survey to learners.

### Submissions

Every exam attempt or survey response creates a `Submission`. Key fields:
- `parentId` — the exam/survey ID this is a response to
- `type` — `"exam"` or `"survey"`
- `userId` — the learner who submitted
- `answers` — list of `Answer` objects (an `@Ignore` field; answers persist in their own table)
- `status` — `"pending"`, `"graded"`, etc.
- `uploaded` — `false` until the submission is synced back to the server

---

## Teams and Enterprises

Teams and enterprises are both represented by `MyTeam`. The `type` field distinguishes them:
- `type = "team"` — a collaborative group of learners
- `type = "enterprise"` — a group with financial tracking and reporting, typically modelling a small business or cooperative

(`MyTeam` also has a separate `teamType` field — sync-related, distinct from `type`.)

`MyTeam` rows do double duty: besides the team document itself, **membership, join-request, and report records are separate `MyTeam` rows** distinguished by `docType` (`"membership"`, `"request"`, `"report"`) — see `TeamsRepositoryImpl`. A learner's membership is a `docType = "membership"` row linking `userId` to `teamId`, and join requests are `docType = "request"` rows (not the legacy `requests` string field on the team document).

### Teams

A **team** is a group of learners who collaborate on tasks, share courses and resources, hold discussions (via team-scoped Voices/news), run surveys together, and schedule meetups via a shared calendar.

Teams have:
- **Members** — `docType = "membership"` rows linking `userId` to `teamId`
- **Leader** — a member with `isLeader = true` on their membership record
- **Tasks** (`TeamTask`) — action items assigned to members with deadlines
- **Courses** — a list of course IDs the team has added (`courses` on `MyTeam`)
- **Resources** — linked via `resourceId`
- **Surveys** — team-scoped surveys where `StepExam.teamId` is set
- **Meetups** (`Meetup`) — scheduled events on the team calendar
- **News/discussions** — `News` records with `viewableBy = "team"` and `viewableId = teamId`
- **Join requests** — `docType = "request"` rows, visible to the leader

### Enterprises

An **enterprise** extends the team model with financial tracking. The `TeamDetailFragment` renders a different tab set for enterprises: instead of a Courses tab there is a Finances tab, and a Reports tab is added. Enterprises track `beginningBalance`, `sales`, `otherIncome`, `wages`, and `otherExpenses` directly on `MyTeam`.

### Team Detail Tabs

Inside `TeamDetailFragment`, the visible tab set is built by `buildPages()` (`TeamDetailFragment.kt:76-94`). For a member — or any viewer when the team is public (`isMyTeam || team?.isPublic == true`) — it's this fixed sequence; note several slots are either/or, not both:

| Tab | Teams | Enterprises |
|-----|-------|-------------|
| Chat | ✓ | ✓ |
| Plan / Mission | Plan | Mission |
| Members | ✓ | ✓ |
| Tasks | ✓ | ✓ |
| Calendar | ✓ | ✓ |
| Surveys | ✓ | ✓ |
| Courses / Finances | Courses | Finances |
| Reports | — | ✓ |
| Resources / Documents | Resources | Documents |

For a non-member viewing a non-public team, `buildPages()` only returns Plan/Mission and Members — none of the other tabs are shown.

`Resources` and `Documents` are mutually exclusive, the same as `Courses`/`Finances` — a team gets one or the other based on `isEnterprise`, never both, and both tab variants render the same underlying `TeamResourcesFragment` (`TeamPageConfig.kt`) under a different title.

`TeamPageConfig.kt` also defines `JoinRequestsPage`, `ApplicantsPage`, and `TeamPage` as valid page configs. None of them are ever emitted by `buildPages()`, but they are not dead: `TeamPagerAdapter` matches on them for fragment reuse, and `JoinRequestsPage.id` is a deep-link navigation target — `DashboardActivity` and `NotificationsFragment` pass it via a `navigateToPage` bundle argument (e.g. from a join-request notification) — just not a tab a leader browses to from the tab bar.

---

## Community and Voices

### Community

The **Community** section of the app shows information about the local Planet community — its name, leaders, and services. This is pulled from server configuration data.

### Voices ("Our Voices")

**Voices** is the community discussion board. Any member (non-guest) can post a message, reply to posts, and filter by label. It functions like a community forum or social feed.

Posts are `News` records. Key fields:
- `message` — the post text
- `viewableBy` — controls scope: `"community"` for the main board, `"team"` for team-scoped discussions, `"nation"` for nation-level visibility
- `viewableId` — the team ID when `viewableBy = "team"`
- `replyTo` — the `_id` of the parent post if this is a reply
- `labels` — list of label strings (e.g. `"offer"`, `"help"`, `"advice"`)
- `imageUrls` — list of image URLs attached to the post

The same `News` model is also used for **team chat** (inside `TeamDetailFragment`'s Chat tab) and **enterprise news**, scoped to the team via `viewableId`.

### Meetups

**Meetups** are scheduled events. They can be community-level or team-scoped. A meetup (`Meetup`) has a title, description, start/end dates and times, location, category, and recurrence pattern. Team meetups carry a `teamId`.

### Feedback

Learners can submit feedback about the app or content via `FeedbackFragment`. Feedback (`Feedback`) is synced to the server and can be viewed and responded to; replies are stored as a JSON string in its `messages` field (the plain `FeedbackReply` class is the parsed form).

---

## Progress and Achievement

### Course Progress

`CourseProgress` tracks a learner's progress through a course step-by-step. One record exists per learner per step:
- `userId` + `courseId` + `stepNum` identify the record
- `passed` — `true` once the learner has passed the step (including any embedded exam)

The app computes an overall progress percentage from these records when displaying the course progress bar.

### Achievements

`Achievement` is a learner's personal profile of accomplishments. They can record goals (`goals`), a purpose statement, personal achievements, references, links, and upload a resume. This is self-authored content, not auto-generated from course completions.

### Ratings

`Rating` captures a learner's star rating and optional comment for a resource or course. Ratings are tied to the item via `item` (the resource/course ID) and `type` (`"resource"` or `"course"`). Aggregate rating data (average, count) is denormalized onto `MyLibrary` (`averageRating`, `timesRated`) for display performance.

### Health Records

`HealthExamination` stores data recorded during community health screenings. The vital-sign and demographic fields — temperature, pulse, blood pressure, height, weight, vision, hearing, age, gender, and medical conditions (`conditions`) — are stored as plain columns.

The sensitive free-text fields (observations, diagnosis, treatments, medications, immunizations, allergies, x-rays, lab tests, referrals) are serialized into a single JSON blob in the `data` field and encrypted with the subject's key before storage — `AndroidDecrypter.decrypt(data, user.key, user.iv)`. So encryption here is partial by design: the free-text clinical detail in `data` is encrypted at rest, while the vitals, dates, and codes are not.

Whether an exam was self-administered or entered by someone else is tracked by the `isSelfExamination` boolean, set in `AddExaminationActivity` as `currentUser?._id == pojo?._id` — i.e. whether the logged-in user and the exam's subject are the same person.

### Notifications

**`AppNotification`** (`@Entity(tableName = "notifications")`) holds persisted in-app notifications — task deadlines, new survey assignments, sync events, etc. — with a `type`, a `relatedId` pointing to the relevant entity, and a `link` for deep navigation. The plain `Notification` data class is a UI-list model derived from it — don't conflate the two.

---

## How Everything Connects

```
UserEntity (learner)
├── joins → MyCourse (course)
│   ├── has steps → CourseStep
│   │   ├── has resources → MyLibrary (resourceId + stepId)
│   │   └── has exam/survey → StepExam
│   │       └── learner submits → Submission
│   │           └── contains answers → Answer
│   └── learner progress → CourseProgress (one per step)
│
├── saves resource to library → MyLibrary (userId list)
│   └── learner rates → Rating (type = "resource")
│
├── rates course → Rating (type = "course")
│
├── joins → MyTeam (team or enterprise; membership = docType "membership" row)
│   ├── has tasks → TeamTask
│   ├── has meetups → Meetup
│   ├── has discussions → News (viewableBy = "team")
│   ├── has surveys → StepExam (teamId set)
│   │   └── learner submits → Submission
│   └── has courses → courseIds list on MyTeam
│
├── posts in community → News (viewableBy = "community")
│
├── completes standalone survey → Submission (type = "survey")
│
├── records health data → HealthExamination
│
├── (completed courses may carry) → Certification (label grouping courses)
│
└── writes achievements → Achievement
```

---

## Sync, Community, and Nation

Planet installations are organized in a two-level hierarchy:

- **Community** — a local Planet server. This is what myPlanet connects to. Each community has a `planetCode` (its unique identifier) and a `parentCode` (pointing to its nation).
- **Nation** — a regional or national Planet server that aggregates data from multiple communities.

Every data record in myPlanet carries `planetCode` (which community created it) and `parentCode` (which nation it belongs to). This is how Planet knows where a piece of data originated during sync.

`SharedPrefManager` stores the current server URL, `planetCode`, `parentCode`, and `communityName` for the active connection. When learners log in, their `UserEntity` record carries the same codes.

During sync, `SyncManager` pulls updates from the server for each data type (courses, resources, ratings, news, etc.) and pushes any locally-created or locally-modified records back up. The `uploaded` / `isUpdated` flags on models track what still needs to be sent.

---

## Glossary

| Term | Meaning in myPlanet |
|------|---------------------|
| **Learner** | A full member user who consumes content and takes assessments |
| **Manager / Coach** | A user with `"manager"` role or `userAdmin = true`; oversees learners and content |
| **Leader** | A member with `isLeader = true` on their team membership record (the global `"leader"` role check is unused) |
| **Guest** | A read-only user; prompted to register before accessing participation features |
| **Resource** | A single downloadable content item (PDF, video, audio, etc.) in the library |
| **Library** | The collection of all available resources; "My Library" is a learner's personal saved subset |
| **Course** | A structured learning path with ordered steps, resources, and embedded exams |
| **Step** | One unit within a course; has a title, description, attached resources, and optional exam |
| **Exam** | A pass/fail assessment attached to a course step |
| **Survey** | A non-graded questionnaire; can be standalone, step-attached, or team-scoped |
| **Submission** | A learner's recorded attempt at an exam or survey, containing their answers |
| **Team** | A collaborative group with shared tasks, courses, discussions, and a calendar |
| **Enterprise** | A team variant with financial tracking (income, expenses, reports) |
| **Voices** | The community discussion board; posts are called "voices" |
| **News** | The underlying model for Voices posts and team chat messages (`News`) |
| **Meetup** | A scheduled event on a community or team calendar |
| **Achievement** | A learner's self-authored portfolio of goals and accomplishments |
| **Certification** | A label grouping multiple courses; surfaced only as a color cue on completion badges — never "earned" as a record |
| **Rating** | A 1–5 star review left by a learner on a resource or course |
| **Tag** | A label attached to resources and courses for filtering (`TagEntity`) |
| **Community** | The local Planet server and its associated user base |
| **Nation** | A regional Planet server that aggregates multiple communities |
| **planetCode** | Identifier for the community a record was created in |
| **parentCode** | Identifier for the nation the community belongs to |
| **Sync** | The process of exchanging data between myPlanet and the Planet server |
