# Phase 114 — recovering the human translations the port left on the table

Lane B. `lib/l10n/*.arb`, `tool/arb_from_strings_xml.dart`, `test/l10n/`.

## The premise, and why it was bigger than one string

Phase 110 found that the exam retry snackbar used `incorrectAnswer` — "Incorrect
answer" — where Kotlin's `incorrect_ans` says "Incorrect answer, please try
again" and ships a real human translation of that sentence in all five locales.
It handed the fix back because locale files were not its lane.

That is not one string. It is a class, and the class has a mechanism behind it:
**`tool/arb_from_strings_xml.dart` merges, and existing values win.** The rule is
right for what it was written for — it stops a re-run deleting the 17 keys per
locale that were translated by hand and have no Kotlin counterpart — but it also
means the tool can only ever *add*. Once the external machine-translation pass
filled ~560 keys per locale, every one of them was frozen, including the ones
whose English the Kotlin app had been shipping a human translation of for years.
The tool would never look at them again.

So the recovery needed a mode that is allowed to overwrite, and a rule for what
it may overwrite that cannot cost anybody their work.

## `incorrect_ans`, before and after

`app_en.arb` changes too: the port's English was its own wording, and the retry
hint is the point of the string, not a flourish. Under Phase 110's gate this
snackbar is the only thing telling a learner to try again.

| | before | after |
|---|---|---|
| en | Incorrect answer | Incorrect answer, please try again |
| ar | إجابة غير صحيحة *(x-mt)* | إجابة غير صحيحة، يرجى المحاولة مرة أخرى |
| es | respuesta incorrecta *(x-mt)* | Respuesta incorrecta, por favor inténtalo de nuevo |
| fr | Réponse incorrecte *(x-mt)* | Réponse incorrecte, veuillez réessayer |
| ne | `[Nepali] Incorrect answer` *(x-mt)* | तपाईंले गलत उत्तर दिएका छन्, कृपया पुन: प्रयास गर्नुहोस् |
| so | `[Somali] Incorrect answer` *(x-mt)* | Jawaab aan sax ahayn, fadlan isku day |

Two of the five were not translations at all — they were the external tool's own
"nothing here" marker sitting in the file as a value. All five are now human, and
none carries `x-mt` any more.

`test/ui/exam/take_exam_screen_test.dart` follows the English in two `find.text`
literals. **That file is Lane A's**; the change is mechanical and is flagged for
the integrator rather than done quietly.

## The class

`dart tool/arb_from_strings_xml.dart --candidates` reports; `--adopt` applies.
Both are new. The report is the deliverable as much as the adoption is — 858 keys
against 1050 Kotlin strings is not a thing to eyeball.

**Matching is on English text, never on the key name.** The ARB key and the
Kotlin name agree about a concept, not about a string: `achievements` ↔
`myAchievements`, `teamLeader` ("You lead this team") ↔ `team_leader` ("Team
Leader"), `taskNotification` ("Task") ↔ `task_notification` ("%1$s is due in
%2$s"). A name-driven derivation would have swapped in translations of different
words, silently, in five languages at once. Three tiers are adopted:

| tier | rule | transformation |
|---|---|---|
| `exact` | identical after trimming | none |
| `punctuation` | identical once a trailing `: . … !` run and its space are dropped from both | strip that run from the translation, give it the template's own trailing punctuation back |
| `casing` | identical once case is ignored too | as above, plus align the first character to the template's case, upwards only |

and two are reported and never applied: `nameOnly` (name matches, English does
not) and `containment` (one English contains the other — the `incorrectAnswer`
shape, where the port paraphrased a string Kotlin already had). Fixing one of
those means changing `app_en.arb`, which is a judgement about what a screen
should say, not a derivation. A tier's candidates must also be **unanimous**:
`values/strings.xml` gives several names to one English phrase and they do not
always agree in translation.

**What may be overwritten** is only a value that is demonstrably not a human
translation: absent, flagged `x-mt`, still carrying an `[Nepali] `-style marker,
byte-identical to the English template, or the same translation differing only in
surrounding whitespace. Anything else is somebody's work and is left alone **even
when Kotlin disagrees with it** — 32 such disagreements are listed below rather
than resolved, because this script has no way to tell a better rendering from a
worse one.

Three punctuation details are worth knowing, because two of them were caught by
looking at output rather than by reasoning:

* the template's trailing punctuation is only re-attached **onto a word**.
  Nepali ends a sentence with the danda `।`, and appending a full stop produced
  `CSV फाइल सुरक्षित गर्न असफल।.`;
* a trailing space the template carries deliberately is preserved.
  `selectResources` is `"Select resources: "` in both `app_en.arb` and the Kotlin
  XML — Android's quoting exists precisely to protect that space, because the
  value is drawn straight after it — and a `.trim()` would close the gap in every
  language but English. Arabic had already lost it;
* the first-character case alignment is **upwards only**. Lowercasing a
  translation's first letter is not safe in general, and nothing here needs it.

### Two things the audit found that were not what it was looking for

**`app_es.arb` had never been through the derivation script at all.** A comment
in the tool said the Kotlin app has no `values-es`. It has — 1050 strings, the
same as every other locale. Spanish carried the most machine-translated strings
of any locale (620) for exactly that reason, and gained the most human ones
proportionally.

**Fourteen French strings and one Somali one were rendering a literal
backslash.** Android escapes an apostrophe in `strings.xml` as `\'`; the
backslash is the platform's, not the sentence's. `_unquote` learned to strip it
at some point, but values derived before that kept it, and nothing could see it:
the value is well-formed JSON, `gen-l10n` compiles it, no analyzer looks inside a
string. `Demandes d\'adhésion`, `Su\'aal`. `--adopt` repairs them wherever they
survive, independently of any match.

## Counts

Examined: **858 template keys** against **1050 Kotlin strings**, in 5 locales.
Matched at some tier: **445 keys** (268 `exact`, 10 `punctuation`, 85 `casing`,
42 `nameOnly`, 85 `containment` — a key stops at the first tier that hits).
Applied: **494 values** across the five locales, from the three adoptable tiers
only.

| locale | values | `x-mt` before → after | human before → after |
|---|---|---|---|
| ar | 816 → 821 | 557 → 496 (−61) | 259 → 325 |
| es | 854 → 859 | 620 → 538 (−82) | 234 → 321 |
| fr | 854 → 859 | 594 → 545 (−49) | 260 → 314 |
| ne | 854 → 859 | 595 → 475 (−120) | 259 → 384 |
| so | 854 → 859 | 595 → 476 (−119) | 259 → 383 |

**431 machine-translated strings replaced by human ones**, and 25 keys that had
no value at all now have one. `--adopt` is idempotent: a second run reports zero.

## Residue — what was found and deliberately not applied

Run `dart tool/arb_from_strings_xml.dart --candidates` for the live list with
before/after values. In summary:

**32 `keep-human`** — Kotlin has a different translation of the same English and
the ARB's is unflagged, so it is somebody's work. es 22, so 4, fr 4, ne 1. Two
recognisable groups: a genuine disagreement (`amount` Monto/Cantidad,
`unselectAll` "Tout désélectionner"/"Désélectionner tout", `myProgress` "Mi
Progreso" against the Spanish XML's camelCase artefact `miProgreso`), and a
first-letter case difference where the ARB copied Kotlin's lowercase English
(`view` → `ver`/`afficher`/`eeg`, `exportCancelled`, `failedToSaveCsvFile`).
The second group is a presentation fix somebody could just make; it is not a
translation question.

**33 `no-unanimous`** — two or more Kotlin names share the English and disagree
in translation, so there is nothing to prefer. so 12, ar 6, es 5, fr 5, ne 5.
Some are worth a human's five minutes because the current value is the *English*:
`so:settings` is still "Settings" and `ne:joinRequests` still
`[Nepali] Join requests`, blocked only by `join_requests` and
`notif_group_join_requests` disagreeing.

**42 `nameOnly` + 85 `containment`** — the English differs, so recovering one
means editing `app_en.arb`. This is the `incorrect_ans` shape and the list is
where the next pass should start; `incorrect_ans` itself would have appeared
under `containment`.

### nameOnly

| key | `app_en.arb` | Kotlin |
|---|---|---|
| `subjectLevel` | Subject | subject_level="Subject Level" |
| `levels` | Levels | levels="Levels*" |
| `invalidEmail` | Enter a valid email address | invalid_email="Invalid email." |
| `taskNotification` | Task | task_notification="%1$s is due in %2$s" |
| `achievements` | Achievements | achievements="myAchievements" |
| `submission` | Submission | submission="mySubmissions" |
| `editPersonal` | Edit personal item | edit_personal="Edit Personal" |
| `noSurveys` | No surveys available | no_surveys="surveys not available" |
| `surveyLoadFailed` | Survey could not be loaded | survey_load_failed="Couldn't load the survey. Please try again." |
| `surveySubmitFailed` | Could not save your answers | survey_submit_failed="Couldn't submit the survey. Please try again." |
| `sendSurveyTo` | Select users to send survey to | send_survey_to="Send Survey to:" |
| `surveySentToUsers` | Survey sent to selected users | survey_sent_to_users="Survey sent to users" |
| `noTeams` | No teams available | no_teams="teams not available" |
| `teamLeader` | You lead this team | team_leader="Team Leader" |
| `noTeamResources` | No resources linked to this team | no_team_resources="team resources not available" |
| `privateResource` | Private resource | private_resource="Private resource (only visible to this team)" |
| `selectOpenWith` | Select open with | select_open_with="Open" |
| `selectMedia` | Select media | select_media="Media" |
| `selectResourceType` | Select resource type | select_resource_type="Resource type" |
| `noTeamCourses` | No courses linked to this team | no_team_courses="team courses not available" |
| `noReports` | No financial reports yet | no_reports="No reports available" |
| `profitLoss` | Profit / loss | profit_loss="Profit/Loss" |
| `chatHistory` | Chat History | chat_history="chat history list" |
| `noChats` | No chats yet | no_chats="no previous chats" |
| `isUrgent` | Is this urgent? | is_urgent="Is your feedback Urgent? *" |
| `feedbackType` | Feedback type | feedback_type="Feedback Type: *" |
| `yourFeedback` | Your feedback | your_feedback="Your Feedback *" |
| `pleaseEnterFeedback` | Please enter your feedback | please_enter_feedback="Please enter feedback." |
| `takeTest` | Take test | take_test="take test [%d]" |
| `error` | Error | error="Error: %1$s" |
| `note` | Note | note="Note *", note_="notes" |
| `height` | Height | height="Height (cm)" |
| `weight` | Weight | weight="Weight (kg)" |
| `bloodPressure` | Blood pressure | blood_pressure="Blood Pressure (Systolic/Diastolic)" |
| `selfExamination` | Self-examination | self_examination="%s\nSelf Examination" |
| `retypePassword` | Retype password | retype_password="Confirm Password" |
| `courseNotStarted` | Course: Not started | course_not_started="%1$s not started" |
| `resourceFor` | For | resource_for="Resource For" |
| `subject` | Subject | subject="Subject(s)*:" |
| `addedToMyLibrary` | Added to My Library | added_to_my_library="added to myLibrary" |
| `myLibrary` | My Library | my_library="mylibrary" |
| `dataCleared` | All data cleared | data_cleared="Data cleared" |

### containment

| key | `app_en.arb` | Kotlin |
|---|---|---|
| `serverUrlLabel` | Server URL | please_enter_server_url_first="Please enter server url first.", server_url_not_configured="Server URL not configured" |
| `serverUrlHint` | https://planet.example.org | https_protocol="https://" |
| `invalidCredentials` | Name or password is incorrect. | password="password" |
| `missingAuthData` | Server response missing authentication data. | response="Response" |
| `emptyCredentials` | Username and password are required. | hint_name="username", password="password" |
| `backgroundSucceeded` | Completed successfully | completed="Completed", complete="complete", status_completed="Completed" |
| `backgroundRetryRequested` | Incomplete — Android will retry | complete="complete" |
| `syncForegroundNotice` | Keep myPlanet open until the sync finishes. Pending offline writes remain durable if the connection drops. | app_project_name="myPlanet", menu_myplanet="myPlanet", system_name="myPlanet" |
| `syncVoicesDescription` | Refresh community discussions and replies | menu_community="Community", community="community" |
| `syncFeedbackDescription` | Refresh feedback threads and responses | menu_feedback="Feedback", feedback="Feedback", response="Response" |
| `joinCourse` | Add to my courses | my_courses="My Courses" |
| `leaveCourse` | Remove from my courses | my_courses="My Courses" |
| `serverUrl` | Server URL | please_enter_server_url_first="Please enter server url first.", server_url_not_configured="Server URL not configured" |
| `notConfigured` | Not configured | server_not_configured_properly_connect_this_device_with_planet_server="Server not configured properly. Connect this device with Planet server", server_url_not_configured="Server URL not configured" |
| `communityCode` | Community code | menu_community="Community", community="community" |
| `dictionaryDescription` | Search downloaded definitions offline | download="Download", downloaded="Downloaded" |
| `dictionaryUnavailable` | Dictionary unavailable | dictionary="Dictionary" |
| `downloadDictionary` | Download dictionary | download="Download", dictionary="Dictionary" |
| `retryDownload` | Retry download | download="Download" |
| `dictionaryDownloadFailed` | The dictionary could not be downloaded. Your existing offline data was not changed. | download="Download", dictionary="Dictionary", downloaded="Downloaded" |
| `wordNotFound` | Word not available in the offline dictionary | dictionary="Dictionary" |
| `notifications` | Notifications | no_notifications="No notifications", no_unread_notifications="No unread notifications", no_read_notifications="No read notifications" |
| `notification` | Notification | downloading_started_please_check_notification="Downloading started, please check notification…", downloading_started_please_check_notificati="Downloading started, please check notification…", no_notifications="No notifications", no_unread_notifications="No unread notifications", no_read_notifications="No read notifications", downloading_images_please_check_notification="Downloading images, please check notification…" |
| `deleteNotificationConfirmation` | This notification will be removed from this device. | removed_from="removed from" |
| `teamNotification` | Team update | notif_group_team_updates="Team Updates" |
| `myHealth` | My health | my_health_saved_successfully="My health saved successfully" |
| `submissions` | Submissions | submission="mySubmissions", no_survey_submissions="no survey submissions", no_exam_submissions="no exam submissions" |
| `lastUpdated` | Last updated | disclaimer="\n    \n    <h1>Disclaimer</h1>\n    <p>Last updated: January 10, 2020</p>\n    <h1>Interpretation and Definitions</h1>\n    <h2>Interpretation</h2>\n    <p>The words of which the initial letter is capitalized have meanings defined under the following conditions.</p>\n    <p>The following definitions shall have the same meaning regardless of whether they appear in singular or in plural.</p>\n    <h2>Definitions</h2>\n    <p>For the purposes of this Disclaimer:</p>\n    <ul>\n        <li><strong>Company</strong> (referred to as either "the Company", "We", "Us" or "Our" in this Cookies Policy) refers to myPlanet.</li>\n        <li><strong>You</strong> means the individual accessing the Service, or the company, or other legal entity on behalf of which such individual is accessing or using the Service, as applicable.</li>\n        <li><strong>Application</strong> means the software program provided by the Company downloaded by You on any electronic device named myPlanet.</li>\n        <li><strong>Service</strong> refers to the Application.</li>\n    </ul>\n    <h1>Disclaimer</h1>\n    <p>The information contained on the Service is for general information purposes only.</p>\n    <p>The Company assumes no responsibility for errors or omissions in the contents of the Service.</p>\n    <p>In no event shall the Company be liable for any special, direct, indirect, consequential, or incidental damages or any damages whatsoever, whether in an action of contract, negligence or other tort, arising out of or in connection with the use of the Service or the contents of the Service. The Company reserves the right to make additions, deletions, or modifications to the contents on the Service at any time without prior notice.</p>\n    <p>The Company does not warrant that the Service is free of viruses or other harmful components.</p>\n    <h1>Medical Information Disclaimer</h1>\n    <p>The information about health provided by the Service is not intended to diagnose, treat, cure or prevent disease. Products, services, information and other content provided by the Service, including information linking to third-party websites are provided for informational purposes only.</p>\n    <p>Information offered by the Service is not comprehensive and does not cover all diseases, ailments, physical conditions or their treatment.</p>\n    <p>Individuals are different and may react differently to different products. Comments made on the Service by employees or other users are strictly their own personal views made in their own personal capacity and are not claims made by the Company nor do they represent the position or view of the Company.</p>\n    <p>The Company is not liable for any information provided by the Service with regard to recommendations regarding supplements for any health purposes.</p>\n    <p>The Company makes no guarantee or warranty with respect to any products or services sold. The Company is not responsible for any damages for information or services provided even if the Company has been advised of the possibility of damages.</p>\n    <h1>External Links Disclaimer</h1>\n    <p>The Service may contain links to external websites that are not provided or maintained by or in any way affiliated with the Company.</p>\n    <p>Please note that the Company does not guarantee the accuracy, relevance, timeliness, or completeness of any information on these external websites.</p>\n    <h1>Errors and Omissions Disclaimer</h1>\n    <p>The information given by the Service is for general guidance on matters of interest only. Even if the Company takes every precaution to ensure that the content of the Service is both current and accurate, errors can occur. Plus, given the changing nature of laws, rules, and regulations, there may be delays, omissions, or inaccuracies in the information contained on the Service.</p>\n    <p>The Company is not responsible for any errors or omissions, or for the results obtained from the use of this information.</p>\n    <h1>Fair Use Disclaimer</h1>\n    <p>The Company may use copyrighted material which has not always been specifically authorized by the copyright owner. The Company is making such material available for criticism, comment, news reporting, teaching, scholarship, or research.</p>\n    <p>The Company believes this constitutes a "fair use" of any such copyrighted material as provided for in section 107 of the United States Copyright law.</p>\n    <p>If You wish to use copyrighted material from the Service for your own purposes that go beyond fair use, You must obtain permission from the copyright owner.</p>\n    <h1>Views Expressed Disclaimer</h1>\n    <p>The Service may contain views and opinions which are those of the authors and do not necessarily reflect the official policy or position of any other author, agency, organization, employer, or company, including the Company.</p>\n    <p>Comments published by users are their sole responsibility and the users will take full responsibility, liability, and blame for any libel or litigation that results from something written in or as a direct result of something written in a comment. The Company is not liable for any comment published by users and reserves the right to delete any comment for any reason whatsoever.</p>\n    <h1>No Responsibility Disclaimer</h1>\n    <p>The information on the Service is provided with the understanding that the Company is not herein engaged in rendering legal, accounting, tax, or other professional advice and services. As such, it should not be used as a substitute for consultation with professional accounting, tax, legal, or other competent advisers.</p>\n    <p>In no event shall the Company or its suppliers be liable for any special, incidental, indirect, or consequential damages whatsoever arising out of or in connection with your access or use or inability to access or use the Service.</p>\n    <h1>"Use at Your Own Risk" Disclaimer</h1>\n    <p>All information in the Service is provided "as is", with no guarantee of completeness, accuracy, timeliness, or of the results obtained from the use of this information, and without warranty of any kind, express or implied, including, but not limited to warranties of performance, merchantability, and fitness for a particular purpose.</p>\n    <p>The Company will not be liable to You or anyone else for any decision made or action taken in reliance on the information given by the Service or for any consequential, special, or similar damages, even if advised of the possibility of such damages.</p>\n    <h2>Contact Us</h2>\n    <p>If you have any questions about this Disclaimer, You can contact Us:</p>\n    <ul>\n        <li>By email: myplanet@ole.org</li>\n        <li>By visiting this page on our website: <a href="https://ole.org/contact">https://ole.org/contact</a></li>\n    </ul>\n    \n    " |
| `uploaded` | Uploaded | about="\n    \n    <h3>MyPlanet</h3>\n    <p>myPlanet is a learning tool that is designed to work with Planet web application.\n        It has been used to improve early education, secondary schools, village health, youth workforce development,\n        and economic and community development.</p>\n    <p>Planet houses is a repository of free, open access and public domain resources to benefit all learners.</p>\n\n    <p>myPlanet is designed to be available to everyone, everywhere, all the time. It is portable, affordable, scalable and sustainable.\n        It runs on any android device such as tablets and mobile phones. It functions off, as well as on, the Internet.</p>\n\n    <p>This application enables schools and communities to have a complete multi-media library and learning system that periodically connects with Planet.\n        Configured devices can contain the learners' personal dashboard. This ensures learners can read books on their shelf and take courses offline - i.e\n        without connection to a central server. Learners are encouraged to rate from one to five stars the resources they use and the courses they take.\n        Periodically learners can sync with a server. Activity data are uploaded and new resources are downloaded in a matter of a few minutes unto myPlanet for offline use.</p>\n\n    <p>The dashboard also contains a record of achievements, a calendar of events, and an internal chat system for communicating with fellow members.</p>\n\n    <p>myPlanet has been proven highly effective in improving learning opportunities for over fifty thousand learners in more than 100 locations,\n        in schools throughout Nepal, Ghana, Kenya, and Rwanda, with Syrian refugees in Jordan, Somali refugees in Kenya, and village health workers in Uganda.</p>\n    \n    " |
| `noPersonals` | No personal items yet. Add a private note to get started. | get_started="GET STARTED" |
| `onboardingOfflineDescription` | Download resources for offline learning.\nAccess knowledge on the go. | download="Download", resources="Resources", download_resources="download Resources", ob_desc2_1="Download resources for offline learning." |
| `onboardingOpenDescription` | Choose your interests and dive into interactive content.\nCraft your unique learning journey. | ob_desc3_1="Choose your interests and dive into interactive content." |
| `onboardingPowerDescription` | Explore a world of knowledge at your fingertips.\n\n🌍 Offline Access: Learn without borders, anytime.\n🎯 Personalized: Tailor your learning path.\n📚 Interactive: Engage with videos, books, games.\n\nBegin your limitless learning journey with myPlanet! | app_project_name="myPlanet", menu_myplanet="myPlanet", system_name="myPlanet", ob_desc4_1="Explore a world of knowledge at your fingertips.", ob_desc4_2="🌍 Offline Access: Learn without borders, anytime.", ob_desc4_3="🎯 Personalized: Tailor your learning path.", ob_desc4_4="📚 Interactive: Engage with videos, books, games." |
| `category` | Category | filter_by_category="Filter By Category" |
| `sortResources` | Sort resources | resources="Resources" |
| `surveyHasNoQuestions` | This survey has no questions | question="Question" |
| `answerRequiredQuestions` | Answer all required questions | question="Question" |
| `searchEnterprises` | Search enterprises | enterprises="Enterprises" |
| `teamCourses` | Team courses | no_team_courses="team courses not available" |
| `taskTitle` | Task title | task_title_is_required="Task title is required" |
| `resourcesUnavailable` | Resources are unavailable | resources="Resources" |
| `noResourcesToAdd` | All available resources are already linked | resources="Resources" |
| `chatConversation` | Conversation | recycler_chat_label="conversations", full_conversation_response="Full conversation response" |
| `aiProviders` | AI providers | fetching_ai_providers="Fetching AI providers…" |
| `feedbackNotFound` | Feedback not found | menu_feedback="Feedback", feedback="Feedback" |
| `feedbackTypeRequired` | Please select a feedback type | menu_feedback="Feedback", feedback="Feedback" |
| `untitledFeedback` | Untitled feedback | menu_feedback="Feedback", feedback="Feedback" |
| `comments` | Comments | disclaimer="\n    \n    <h1>Disclaimer</h1>\n    <p>Last updated: January 10, 2020</p>\n    <h1>Interpretation and Definitions</h1>\n    <h2>Interpretation</h2>\n    <p>The words of which the initial letter is capitalized have meanings defined under the following conditions.</p>\n    <p>The following definitions shall have the same meaning regardless of whether they appear in singular or in plural.</p>\n    <h2>Definitions</h2>\n    <p>For the purposes of this Disclaimer:</p>\n    <ul>\n        <li><strong>Company</strong> (referred to as either "the Company", "We", "Us" or "Our" in this Cookies Policy) refers to myPlanet.</li>\n        <li><strong>You</strong> means the individual accessing the Service, or the company, or other legal entity on behalf of which such individual is accessing or using the Service, as applicable.</li>\n        <li><strong>Application</strong> means the software program provided by the Company downloaded by You on any electronic device named myPlanet.</li>\n        <li><strong>Service</strong> refers to the Application.</li>\n    </ul>\n    <h1>Disclaimer</h1>\n    <p>The information contained on the Service is for general information purposes only.</p>\n    <p>The Company assumes no responsibility for errors or omissions in the contents of the Service.</p>\n    <p>In no event shall the Company be liable for any special, direct, indirect, consequential, or incidental damages or any damages whatsoever, whether in an action of contract, negligence or other tort, arising out of or in connection with the use of the Service or the contents of the Service. The Company reserves the right to make additions, deletions, or modifications to the contents on the Service at any time without prior notice.</p>\n    <p>The Company does not warrant that the Service is free of viruses or other harmful components.</p>\n    <h1>Medical Information Disclaimer</h1>\n    <p>The information about health provided by the Service is not intended to diagnose, treat, cure or prevent disease. Products, services, information and other content provided by the Service, including information linking to third-party websites are provided for informational purposes only.</p>\n    <p>Information offered by the Service is not comprehensive and does not cover all diseases, ailments, physical conditions or their treatment.</p>\n    <p>Individuals are different and may react differently to different products. Comments made on the Service by employees or other users are strictly their own personal views made in their own personal capacity and are not claims made by the Company nor do they represent the position or view of the Company.</p>\n    <p>The Company is not liable for any information provided by the Service with regard to recommendations regarding supplements for any health purposes.</p>\n    <p>The Company makes no guarantee or warranty with respect to any products or services sold. The Company is not responsible for any damages for information or services provided even if the Company has been advised of the possibility of damages.</p>\n    <h1>External Links Disclaimer</h1>\n    <p>The Service may contain links to external websites that are not provided or maintained by or in any way affiliated with the Company.</p>\n    <p>Please note that the Company does not guarantee the accuracy, relevance, timeliness, or completeness of any information on these external websites.</p>\n    <h1>Errors and Omissions Disclaimer</h1>\n    <p>The information given by the Service is for general guidance on matters of interest only. Even if the Company takes every precaution to ensure that the content of the Service is both current and accurate, errors can occur. Plus, given the changing nature of laws, rules, and regulations, there may be delays, omissions, or inaccuracies in the information contained on the Service.</p>\n    <p>The Company is not responsible for any errors or omissions, or for the results obtained from the use of this information.</p>\n    <h1>Fair Use Disclaimer</h1>\n    <p>The Company may use copyrighted material which has not always been specifically authorized by the copyright owner. The Company is making such material available for criticism, comment, news reporting, teaching, scholarship, or research.</p>\n    <p>The Company believes this constitutes a "fair use" of any such copyrighted material as provided for in section 107 of the United States Copyright law.</p>\n    <p>If You wish to use copyrighted material from the Service for your own purposes that go beyond fair use, You must obtain permission from the copyright owner.</p>\n    <h1>Views Expressed Disclaimer</h1>\n    <p>The Service may contain views and opinions which are those of the authors and do not necessarily reflect the official policy or position of any other author, agency, organization, employer, or company, including the Company.</p>\n    <p>Comments published by users are their sole responsibility and the users will take full responsibility, liability, and blame for any libel or litigation that results from something written in or as a direct result of something written in a comment. The Company is not liable for any comment published by users and reserves the right to delete any comment for any reason whatsoever.</p>\n    <h1>No Responsibility Disclaimer</h1>\n    <p>The information on the Service is provided with the understanding that the Company is not herein engaged in rendering legal, accounting, tax, or other professional advice and services. As such, it should not be used as a substitute for consultation with professional accounting, tax, legal, or other competent advisers.</p>\n    <p>In no event shall the Company or its suppliers be liable for any special, incidental, indirect, or consequential damages whatsoever arising out of or in connection with your access or use or inability to access or use the Service.</p>\n    <h1>"Use at Your Own Risk" Disclaimer</h1>\n    <p>All information in the Service is provided "as is", with no guarantee of completeness, accuracy, timeliness, or of the results obtained from the use of this information, and without warranty of any kind, express or implied, including, but not limited to warranties of performance, merchantability, and fitness for a particular purpose.</p>\n    <p>The Company will not be liable to You or anyone else for any decision made or action taken in reliance on the information given by the Service or for any consequential, special, or similar damages, even if advised of the possibility of such damages.</p>\n    <h2>Contact Us</h2>\n    <p>If you have any questions about this Disclaimer, You can contact Us:</p>\n    <ul>\n        <li>By email: myplanet@ole.org</li>\n        <li>By visiting this page on our website: <a href="https://ole.org/contact">https://ole.org/contact</a></li>\n    </ul>\n    \n    " |
| `unavailable` | unavailable | video_unavailable="Video unavailable — file not found and could not connect to server" |
| `transaction` | Transaction | add_transaction="Add Transaction", transaction_added="Transaction added", finance_image="Transaction image" |
| `noServicesAvailable` | No services available | services="Services" |
| `examHasNoQuestions` | This exam has no questions | question="Question" |
| `examComplete` | Exam Complete | complete="complete" |
| `exitExamMessage` | Your progress will be lost. | progress="Progress" |
| `fieldRequired` | Required | title_is_required="Title is required", description_is_required="Description is required", note_is_required="Note is required", amount_is_required="Amount is required", date_is_required="Date is required", feedback_priority_is_required="Feedback priority is required.", feedback_type_is_required="Feedback type is required.", level_is_required="Level is required", subject_is_required="Subject is required", pin_is_required="Pin is required.", task_title_is_required="Task title is required", deadline_is_required="Deadline is required", name_is_required="Name is required", compulsory_first_name="first name required", compulsory_last_name="last name is required", compulsory_email="email is required", compulsory_phone_number="phone number is required", compulsory_date_of_birth="date of birth is required", new_apk_version_required_but_not_found_on_server="New apk version required but not found on server. Contact admin.", permission_required="Permission Required", microphone_permission_required="Microphone permission is required to record audio. Please enable it in the app settings.", camera_permission_required="Camera permission is required. Please enable it in the app settings.", desc_is_required="Description is required" |
| `yearOfBirthRequired` | Year of birth is required | year_of_birth="Year of birth" |
| `invalidYear` | Please enter a valid year | please_enter_a_valid_year_of_birth="please enter a valid year of birth", please_enter_a_valid_year_between_1900_and="please enter a valid year between 1900 and %1$d" |
| `unableToLoadVideo` | Unable to load video | unable_to_load="Unable to load " |
| `unableToLoadAudio` | Unable to load audio | unable_to_load="Unable to load " |
| `unableToLoadPdf` | Unable to load PDF | unable_to_load="Unable to load " |
| `updateHealth` | Update health | update_health_record="Update Health Record" |
| `addHealthRecord` | Add health record | unable_to_add_health_record="Unable to add health record." |
| `temperature` | Temperature | body_temperature="Body Temperature (°C)", vitals_format="\n        Temperature: %1$s\n\n        Pulse: %2$s\n\n        Blood Pressure: %3$s\n\n        Height: %4$s\n\n        Weight: %5$s\n\n        Vision: %6$s\n\n        Hearing: %7$s\n\n    ", invalid_input_must_be_between_30_and_40="Body temperature, must be between 30 and 40" |
| `observations` | Observations | observation="Observations and Notes", observations_notes_colon="\n        Observations and analysis: %1$s\n\n        %2$s\n\n        %3$s\n\n        %4$s\n\n        %5$s\n\n        Allergies: %6$s\n\n        X-ray: %7$s\n\n        Lab Tests: %8$s\n\n        Studies: %9$s\n\n    " |
| `conditions` | Conditions | disclaimer="\n    \n    <h1>Disclaimer</h1>\n    <p>Last updated: January 10, 2020</p>\n    <h1>Interpretation and Definitions</h1>\n    <h2>Interpretation</h2>\n    <p>The words of which the initial letter is capitalized have meanings defined under the following conditions.</p>\n    <p>The following definitions shall have the same meaning regardless of whether they appear in singular or in plural.</p>\n    <h2>Definitions</h2>\n    <p>For the purposes of this Disclaimer:</p>\n    <ul>\n        <li><strong>Company</strong> (referred to as either "the Company", "We", "Us" or "Our" in this Cookies Policy) refers to myPlanet.</li>\n        <li><strong>You</strong> means the individual accessing the Service, or the company, or other legal entity on behalf of which such individual is accessing or using the Service, as applicable.</li>\n        <li><strong>Application</strong> means the software program provided by the Company downloaded by You on any electronic device named myPlanet.</li>\n        <li><strong>Service</strong> refers to the Application.</li>\n    </ul>\n    <h1>Disclaimer</h1>\n    <p>The information contained on the Service is for general information purposes only.</p>\n    <p>The Company assumes no responsibility for errors or omissions in the contents of the Service.</p>\n    <p>In no event shall the Company be liable for any special, direct, indirect, consequential, or incidental damages or any damages whatsoever, whether in an action of contract, negligence or other tort, arising out of or in connection with the use of the Service or the contents of the Service. The Company reserves the right to make additions, deletions, or modifications to the contents on the Service at any time without prior notice.</p>\n    <p>The Company does not warrant that the Service is free of viruses or other harmful components.</p>\n    <h1>Medical Information Disclaimer</h1>\n    <p>The information about health provided by the Service is not intended to diagnose, treat, cure or prevent disease. Products, services, information and other content provided by the Service, including information linking to third-party websites are provided for informational purposes only.</p>\n    <p>Information offered by the Service is not comprehensive and does not cover all diseases, ailments, physical conditions or their treatment.</p>\n    <p>Individuals are different and may react differently to different products. Comments made on the Service by employees or other users are strictly their own personal views made in their own personal capacity and are not claims made by the Company nor do they represent the position or view of the Company.</p>\n    <p>The Company is not liable for any information provided by the Service with regard to recommendations regarding supplements for any health purposes.</p>\n    <p>The Company makes no guarantee or warranty with respect to any products or services sold. The Company is not responsible for any damages for information or services provided even if the Company has been advised of the possibility of damages.</p>\n    <h1>External Links Disclaimer</h1>\n    <p>The Service may contain links to external websites that are not provided or maintained by or in any way affiliated with the Company.</p>\n    <p>Please note that the Company does not guarantee the accuracy, relevance, timeliness, or completeness of any information on these external websites.</p>\n    <h1>Errors and Omissions Disclaimer</h1>\n    <p>The information given by the Service is for general guidance on matters of interest only. Even if the Company takes every precaution to ensure that the content of the Service is both current and accurate, errors can occur. Plus, given the changing nature of laws, rules, and regulations, there may be delays, omissions, or inaccuracies in the information contained on the Service.</p>\n    <p>The Company is not responsible for any errors or omissions, or for the results obtained from the use of this information.</p>\n    <h1>Fair Use Disclaimer</h1>\n    <p>The Company may use copyrighted material which has not always been specifically authorized by the copyright owner. The Company is making such material available for criticism, comment, news reporting, teaching, scholarship, or research.</p>\n    <p>The Company believes this constitutes a "fair use" of any such copyrighted material as provided for in section 107 of the United States Copyright law.</p>\n    <p>If You wish to use copyrighted material from the Service for your own purposes that go beyond fair use, You must obtain permission from the copyright owner.</p>\n    <h1>Views Expressed Disclaimer</h1>\n    <p>The Service may contain views and opinions which are those of the authors and do not necessarily reflect the official policy or position of any other author, agency, organization, employer, or company, including the Company.</p>\n    <p>Comments published by users are their sole responsibility and the users will take full responsibility, liability, and blame for any libel or litigation that results from something written in or as a direct result of something written in a comment. The Company is not liable for any comment published by users and reserves the right to delete any comment for any reason whatsoever.</p>\n    <h1>No Responsibility Disclaimer</h1>\n    <p>The information on the Service is provided with the understanding that the Company is not herein engaged in rendering legal, accounting, tax, or other professional advice and services. As such, it should not be used as a substitute for consultation with professional accounting, tax, legal, or other competent advisers.</p>\n    <p>In no event shall the Company or its suppliers be liable for any special, incidental, indirect, or consequential damages whatsoever arising out of or in connection with your access or use or inability to access or use the Service.</p>\n    <h1>"Use at Your Own Risk" Disclaimer</h1>\n    <p>All information in the Service is provided "as is", with no guarantee of completeness, accuracy, timeliness, or of the results obtained from the use of this information, and without warranty of any kind, express or implied, including, but not limited to warranties of performance, merchantability, and fitness for a particular purpose.</p>\n    <p>The Company will not be liable to You or anyone else for any decision made or action taken in reliance on the information given by the Service or for any consequential, special, or similar damages, even if advised of the possibility of such damages.</p>\n    <h2>Contact Us</h2>\n    <p>If you have any questions about this Disclaimer, You can contact Us:</p>\n    <ul>\n        <li>By email: myplanet@ole.org</li>\n        <li>By visiting this page on our website: <a href="https://ole.org/contact">https://ole.org/contact</a></li>\n    </ul>\n    \n    " |
| `healthRecordAdded` | Health record added successfully | added_successfully="Added successfully" |
| `centerOnDefault` | Center on default location | location="Location" |
| `noStorageUsed` | No downloaded files found | download="Download", downloaded="Downloaded" |
| `freeUpSpaceConfirm` | This will delete all downloaded resources to free up storage space. Continue? | download="Download", resources="Resources", continuation="continue", storage_delete_all="Delete All", downloaded="Downloaded" |
| `enterPassword` | Enter password | password="password" |
| `usernameAlreadyExists` | Username already exists | hint_name="username" |
| `addedToCourse` | Added to your courses | added_to="added to", our_courses="Our Courses" |
| `removedFromCourse` | Removed from your courses | removed_from="removed from", our_courses="Our Courses" |
| `takeCourse` | Take Course | about="\n    \n    <h3>MyPlanet</h3>\n    <p>myPlanet is a learning tool that is designed to work with Planet web application.\n        It has been used to improve early education, secondary schools, village health, youth workforce development,\n        and economic and community development.</p>\n    <p>Planet houses is a repository of free, open access and public domain resources to benefit all learners.</p>\n\n    <p>myPlanet is designed to be available to everyone, everywhere, all the time. It is portable, affordable, scalable and sustainable.\n        It runs on any android device such as tablets and mobile phones. It functions off, as well as on, the Internet.</p>\n\n    <p>This application enables schools and communities to have a complete multi-media library and learning system that periodically connects with Planet.\n        Configured devices can contain the learners' personal dashboard. This ensures learners can read books on their shelf and take courses offline - i.e\n        without connection to a central server. Learners are encouraged to rate from one to five stars the resources they use and the courses they take.\n        Periodically learners can sync with a server. Activity data are uploaded and new resources are downloaded in a matter of a few minutes unto myPlanet for offline use.</p>\n\n    <p>The dashboard also contains a record of achievements, a calendar of events, and an internal chat system for communicating with fellow members.</p>\n\n    <p>myPlanet has been proven highly effective in improving learning opportunities for over fifty thousand learners in more than 100 locations,\n        in schools throughout Nepal, Ghana, Kenya, and Rwanda, with Syrian refugees in Jordan, Somali refugees in Kenya, and village health workers in Uganda.</p>\n    \n    " |
| `teamCalendar` | Team Calendar | calendar="Calendar" |
| `removedFromMyLibrary` | Removed from My Library | removed_from="removed from" |
| `allResources` | All Resources | resources="Resources" |
| `filterResources` | Filter Resources | resources="Resources" |
| `surveyAdopted` | Survey adopted | survey_adopted_successfully="Survey adopted successfully!" |
| `activitySummary` | Activity | my_activity="My Activity", about="\n    \n    <h3>MyPlanet</h3>\n    <p>myPlanet is a learning tool that is designed to work with Planet web application.\n        It has been used to improve early education, secondary schools, village health, youth workforce development,\n        and economic and community development.</p>\n    <p>Planet houses is a repository of free, open access and public domain resources to benefit all learners.</p>\n\n    <p>myPlanet is designed to be available to everyone, everywhere, all the time. It is portable, affordable, scalable and sustainable.\n        It runs on any android device such as tablets and mobile phones. It functions off, as well as on, the Internet.</p>\n\n    <p>This application enables schools and communities to have a complete multi-media library and learning system that periodically connects with Planet.\n        Configured devices can contain the learners' personal dashboard. This ensures learners can read books on their shelf and take courses offline - i.e\n        without connection to a central server. Learners are encouraged to rate from one to five stars the resources they use and the courses they take.\n        Periodically learners can sync with a server. Activity data are uploaded and new resources are downloaded in a matter of a few minutes unto myPlanet for offline use.</p>\n\n    <p>The dashboard also contains a record of achievements, a calendar of events, and an internal chat system for communicating with fellow members.</p>\n\n    <p>myPlanet has been proven highly effective in improving learning opportunities for over fifty thousand learners in more than 100 locations,\n        in schools throughout Nepal, Ghana, Kenya, and Rwanda, with Syrian refugees in Jordan, Somali refugees in Kenya, and village health workers in Uganda.</p>\n    \n    ", chart_description="Login Activity Chart", no_login_activity="No login activity recorded yet" |
| `aboutContent` | ### MyPlanet\n\nmyPlanet is a learning tool that is designed to work with Planet web application. It has been used to improve early education, secondary schools, village health, youth workforce development, and economic and community development.\n\nPlanet houses is a repository of free, open access and public domain resources to benefit all learners.\n\nmyPlanet is designed to be available to everyone, everywhere, all the time. It is portable, affordable, scalable and sustainable. It runs on any android device such as tablets and mobile phones. It functions off, as well as on, the Internet.\n\nThis application enables schools and communities to have a complete multi-media library and learning system that periodically connects with Planet. Configured devices can contain the learners' personal dashboard. This ensures learners can read books on their shelf and take courses offline - i.e without connection to a central server. Learners are encouraged to rate from one to five stars the resources they use and the courses they take. Periodically learners can sync with a server. Activity data are uploaded and new resources are downloaded in a matter of a few minutes unto myPlanet for offline use.\n\nThe dashboard also contains a record of achievements, a calendar of events, and an internal chat system for communicating with fellow members.\n\nmyPlanet has been proven highly effective in improving learning opportunities for over fifty thousand learners in more than 100 locations, in schools throughout Nepal, Ghana, Kenya, and Rwanda, with Syrian refugees in Jordan, Somali refugees in Kenya, and village health workers in Uganda. | app_project_name="myPlanet", title_activity_dashboard="Dashboard", menu_myplanet="myPlanet", system_name="myPlanet", calendar="Calendar", download="Download", resources="Resources", location="Location", menu_community="Community", complete="complete", community="community", downloaded="Downloaded" |
| `disclaimerContent` | # Disclaimer\n\nLast updated: January 10, 2020\n\n# Interpretation and Definitions\n\n## Interpretation\n\nThe words of which the initial letter is capitalized have meanings defined under the following conditions.\n\nThe following definitions shall have the same meaning regardless of whether they appear in singular or in plural.\n\n## Definitions\n\nFor the purposes of this Disclaimer:\n\n- **Company** (referred to as either "the Company", "We", "Us" or "Our" in this Cookies Policy) refers to myPlanet.\n- **You** means the individual accessing the Service, or the company, or other legal entity on behalf of which such individual is accessing or using the Service, as applicable.\n- **Application** means the software program provided by the Company downloaded by You on any electronic device named myPlanet.\n- **Service** refers to the Application.\n\n# Disclaimer\n\nThe information contained on the Service is for general information purposes only.\n\nThe Company assumes no responsibility for errors or omissions in the contents of the Service.\n\nIn no event shall the Company be liable for any special, direct, indirect, consequential, or incidental damages or any damages whatsoever, whether in an action of contract, negligence or other tort, arising out of or in connection with the use of the Service or the contents of the Service. The Company reserves the right to make additions, deletions, or modifications to the contents on the Service at any time without prior notice.\n\nThe Company does not warrant that the Service is free of viruses or other harmful components.\n\n# Medical Information Disclaimer\n\nThe information about health provided by the Service is not intended to diagnose, treat, cure or prevent disease. Products, services, information, and other content provided by the Service, including information linking to third-party websites are provided for informational purposes only.\n\nInformation offered by the Service is not comprehensive and does not cover all diseases, ailments, physical conditions or their treatment.\n\nIndividuals are different and may react differently to different products. Comments made on the Service by employees or other users are strictly their own personal views made in their own personal capacity and are not claims made by the Company nor do they represent the position or view of the Company.\n\nThe Company is not liable for any information provided by the Service with regard to recommendations regarding supplements for any health purposes.\n\nThe Company makes no guarantee or warranty with respect to any products or services sold. The Company is not responsible for any damages for information or services provided even if the Company has been advised of the possibility of damages.\n\n# External Links Disclaimer\n\nThe Service may contain links to external websites that are not provided or maintained by or in any way affiliated with the Company.\n\nPlease note that the Company does not guarantee the accuracy, relevance, timeliness, or completeness of any information on these external websites.\n\n# Errors and Omissions Disclaimer\n\nThe information given by the Service is for general guidance on matters of interest only. Even if the Company takes every precaution to ensure that the content of the Service is both current and accurate, errors can occur. Plus, given the changing nature of laws, rules, and regulations, there may be delays, omissions, or inaccuracies in the information contained on the Service.\n\nThe Company is not responsible for any errors or omissions, or for the results obtained from the use of this information.\n\n# Fair Use Disclaimer\n\nThe Company may use copyrighted material which has not always been specifically authorized by the copyright owner. The Company is making such material available for criticism, comment, news reporting, teaching, scholarship, or research.\n\nThe Company believes this constitutes a "fair use" of any such copyrighted material as provided for in section 107 of the United States Copyright law.\n\nIf You wish to use copyrighted material from the Service for your own purposes that go beyond fair use, You must obtain permission from the copyright owner.\n\n# Views Expressed Disclaimer\n\nThe Service may contain views and opinions which are those of the authors and do not necessarily reflect the official policy or position of any other author, agency, organization, employer, or company, including the Company.\n\nComments published by users are their sole responsibility and the users will take full responsibility, liability, and blame for any libel or litigation that results from something written in or as a direct result of something written in a comment. The Company is not liable for any comment published by users and reserves the right to delete any comment for any reason whatsoever.\n\n# No Responsibility Disclaimer\n\nThe information on the Service is provided with the understanding that the Company is not herein engaged in rendering legal, accounting, tax, or other professional advice and services. As such, it should not be used as a substitute for consultation with professional accounting, tax, legal, or other competent advisers.\n\nIn no event shall the Company or its suppliers be liable for any special, incidental, indirect, or consequential damages whatsoever arising out of or in connection with your access or use or inability to access or use the Service.\n\n# "Use at Your Own Risk" Disclaimer\n\nAll information in the Service is provided "as is", with no guarantee of completeness, accuracy, timeliness, or of the results obtained from the use of this information, and without warranty of any kind, express or implied, including, but not limited to warranties of performance, merchantability, and fitness for a particular purpose.\n\nThe Company will not be liable to You or anyone else for any decision made or action taken in reliance on the information given by the Service or for any consequential, special, or similar damages, even if advised of the possibility of such damages.\n\n## Contact Us\n\nIf you have any questions about this Disclaimer, You can contact Us:\n\n- By email: myplanet@ole.org\n- By visiting this page on our website: [https://ole.org/contact](https://ole.org/contact) | app_project_name="myPlanet", menu_myplanet="myPlanet", system_name="myPlanet", question="Question", download="Download", https_protocol="https://", device_name="device name", action_disclaimer="Disclaimer", complete="complete", services="Services", downloaded="Downloaded" |
| `voiceShared` | Post shared with community | menu_community="Community", community="community" |
| `voiceUnshared` | Removed from community | removed_from="removed from", menu_community="Community", community="community" |
| `thankYouForTakingExam` | Thank you for taking this exam! We wish you all the best. | thank_you_for_taking_this="Thank you for taking this " |

## Wording for `CLAUDE.md`

The l10n paragraph's "231–256 of 858 keys per locale" and the open decision about
marking machine translations both move. Suggested replacement for the **Current
l10n state** passage:

> **Current l10n state, and one open decision.** The template `app_en.arb` has
> 858 keys; the five locale files carry 821–859 values each, of which 314–384 are
> human translations and 475–545 are machine output flagged `x-mt`. Phase 114
> raised the human share by 431 strings without translating anything: it read the
> Kotlin `values-*/strings.xml` — real human translations already shipping in the
> Android app — and preferred them wherever the port had minted its own English
> for a string Kotlin already had, or had let the machine pass fill a key whose
> English the Kotlin app matches exactly.
> `tool/arb_from_strings_xml.dart --candidates` reports the remaining
> candidates and `--adopt` applies the confident ones; the residue it will not
> touch (127 keys whose English differs, 33 where two Kotlin names disagree, 32
> where the ARB already holds someone else's translation) is listed in
> `flutter/PHASE_114_NOTES.md`. **Match on English text, never on the key name** —
> `achievements` and Kotlin's `myAchievements` are the same concept and different
> strings, and a name-driven derivation would swap words silently in five
> languages.

And a line for the Migration progress table's Localisation row, which was
"231–256 of 858 keys per locale" at ~30: the measurable figure is now 314–384
human of 858, with the rest present but machine-translated.
