# Dead Code Analysis Report

_Generated on 2025-11-11T08:39:50Z_

## Tooling and Inputs
- `./gradlew lint` (defaultDebug variant) to detect unused resources and namespaces.
- Custom Python scan for Kotlin/Java type definitions to flag unreferenced classes/objects.
- Manual `rg` queries to confirm dependency usage signals.

## Kotlin / Java Findings
- No top-level classes were reported unused. A custom scan highlighted the following Hilt modules, but they are retained because the Hilt annotation processor loads them reflectively:
  - `org.ole.planet.myplanet.di.DatabaseModule`
  - `org.ole.planet.myplanet.di.NetworkModule`
  - `org.ole.planet.myplanet.di.RepositoryModule`
  - `org.ole.planet.myplanet.di.ServiceModule`
  - `org.ole.planet.myplanet.di.SharedPreferencesModule`
- No Kotlin/Java members were flagged by lint tooling; further IDE-based inspection may be required for fine-grained method/property pruning.

## XML Resource Findings
The following resources were reported unused by Android Lint (defaultDebug):

| Resource | File | Line |
| --- | --- | --- |
| `R.array.dark_mode_options` | `app/src/main/res/values/strings.xml` | 1122 |
| `R.array.info_type` | `app/src/main/res/values/strings.xml` | 203 |
| `R.array.material_calendar_months_array` | `app/src/main/res/values/strings.xml` | 1171 |
| `R.string.actions_menu` | `app/src/main/res/values/strings.xml` | 1042 |
| `R.string.add_link` | `app/src/main/res/values/strings.xml` | 373 |
| `R.string.add_profile_picture` | `app/src/main/res/values/strings.xml` | 630 |
| `R.string.add_story` | `app/src/main/res/values/strings.xml` | 511 |
| `R.string.age` | `app/src/main/res/values/strings.xml` | 1237 |
| `R.string.autosync_off` | `app/src/main/res/values/strings.xml` | 586 |
| `R.string.autosync_on` | `app/src/main/res/values/strings.xml` | 587 |
| `R.string.available` | `app/src/main/res/values/strings.xml` | 681 |
| `R.string.available_please_free_up_space` | `app/src/main/res/values/strings.xml` | 679 |
| `R.string.axillary` | `app/src/main/res/values/strings.xml` | 291 |
| `R.string.below_min_apk` | `app/src/main/res/values/strings.xml` | 1086 |
| `R.string.bulk_resource_download` | `app/src/main/res/values/strings.xml` | 674 |
| `R.string.by_ear` | `app/src/main/res/values/strings.xml` | 292 |
| `R.string.by_skin` | `app/src/main/res/values/strings.xml` | 293 |
| `R.string.chats` | `app/src/main/res/values/strings.xml` | 364 |
| `R.string.checking_server_availability` | `app/src/main/res/values/strings.xml` | 1325 |
| `R.string.community_board` | `app/src/main/res/values/strings.xml` | 1315 |
| `R.string.config_not_available` | `app/src/main/res/values/strings.xml` | 1002 |
| `R.string.connecting_to_server` | `app/src/main/res/values/strings.xml` | 830 |
| `R.string.date_n_a` | `app/src/main/res/values/strings.xml` | 723 |
| `R.string.date_range` | `app/src/main/res/values/strings.xml` | 1097 |
| `R.string.diagnosis_colon` | `app/src/main/res/values/strings.xml` | 770 |
| `R.string.diastolic` | `app/src/main/res/values/strings.xml` | 298 |
| `R.string.download_news_images` | `app/src/main/res/values/strings.xml` | 676 |
| `R.string.due_tasks` | `app/src/main/res/values/strings.xml` | 693 |
| `R.string.enter_message` | `app/src/main/res/values/strings.xml` | 162 |
| `R.string.enter_message_here` | `app/src/main/res/values/strings.xml` | 142 |
| `R.string.enter_password` | `app/src/main/res/values/strings.xml` | 148 |
| `R.string.enter_title` | `app/src/main/res/values/strings.xml` | 372 |
| `R.string.examination` | `app/src/main/res/values/strings.xml` | 343 |
| `R.string.exit` | `app/src/main/res/values/strings.xml` | 526 |
| `R.string.failed_to_get_configuration_id` | `app/src/main/res/values/strings.xml` | 1075 |
| `R.string.feature_not` | `app/src/main/res/values/strings.xml` | 276 |
| `R.string.feature_not_available_for_guest_user` | `app/src/main/res/values/strings.xml` | 694 |
| `R.string.filter_by_date` | `app/src/main/res/values/strings.xml` | 334 |
| `R.string.for_ambulance` | `app/src/main/res/values/strings.xml` | 606 |
| `R.string.for_emergency` | `app/src/main/res/values/strings.xml` | 608 |
| `R.string.for_police` | `app/src/main/res/values/strings.xml` | 607 |
| `R.string.got_it` | `app/src/main/res/values/strings.xml` | 698 |
| `R.string.gps_is_not_enabled_do_you_want_to_go_to_settings_menu` | `app/src/main/res/values/strings.xml` | 987 |
| `R.string.gps_is_settings` | `app/src/main/res/values/strings.xml` | 986 |
| `R.string.graded` | `app/src/main/res/values/strings.xml` | 719 |
| `R.string.health_record_not_available_click_to_sync` | `app/src/main/res/values/strings.xml` | 683 |
| `R.string.health_record_not_available_sync_health_data` | `app/src/main/res/values/strings.xml` | 696 |
| `R.string.hide_add_story` | `app/src/main/res/values/strings.xml` | 799 |
| `R.string.immunizations_colon` | `app/src/main/res/values/strings.xml` | 773 |
| `R.string.invalid_input` | `app/src/main/res/values/strings.xml` | 774 |
| `R.string.loading_enterprises` | `app/src/main/res/values/strings.xml` | 1327 |
| `R.string.login_password` | `app/src/main/res/values/strings.xml` | 544 |
| `R.string.login_user` | `app/src/main/res/values/strings.xml` | 543 |
| `R.string.managerial_login` | `app/src/main/res/values/strings.xml` | 149 |
| `R.string.markdown_filename` | `app/src/main/res/values/strings.xml` | 574 |
| `R.string.medications_colon` | `app/src/main/res/values/strings.xml` | 772 |
| `R.string.member_of_planet` | `app/src/main/res/values/strings.xml` | 1240 |
| `R.string.member_only_allowed` | `app/src/main/res/values/strings.xml` | 1147 |
| `R.string.message_is_required` | `app/src/main/res/values/strings.xml` | 838 |
| `R.string.messages` | `app/src/main/res/values/strings.xml` | 316 |
| `R.string.myhealth_synced_failed` | `app/src/main/res/values/strings.xml` | 691 |
| `R.string.myhealth_synced_successfully` | `app/src/main/res/values/strings.xml` | 690 |
| `R.string.name_colon` | `app/src/main/res/values/strings.xml` | 729 |
| `R.string.nation_board` | `app/src/main/res/values/strings.xml` | 1316 |
| `R.string.new_not_available` | `app/src/main/res/values/strings.xml` | 798 |
| `R.string.new_patient` | `app/src/main/res/values/strings.xml` | 345 |
| `R.string.new_request_to_join` | `app/src/main/res/values/strings.xml` | 1059 |
| `R.string.news` | `app/src/main/res/values/strings.xml` | 135 |
| `R.string.no_due_tasks` | `app/src/main/res/values/strings.xml` | 692 |
| `R.string.no_images_to_download` | `app/src/main/res/values/strings.xml` | 641 |
| `R.string.no_records` | `app/src/main/res/values/strings.xml` | 363 |
| `R.string.no_resources_to_download` | `app/src/main/res/values/strings.xml` | 1254 |
| `R.string.no_stories` | `app/src/main/res/values/strings.xml` | 1050 |
| `R.string.normal_mode` | `app/src/main/res/values/strings.xml` | 576 |
| `R.string.notifications` | `app/src/main/res/values/strings.xml` | 366 |
| `R.string.number_of_visits` | `app/src/main/res/values/strings.xml` | 1207 |
| `R.string.offer` | `app/src/main/res/values/strings.xml` | 639 |
| `R.string.orally` | `app/src/main/res/values/strings.xml` | 300 |
| `R.string.pending` | `app/src/main/res/values/strings.xml` | 720 |
| `R.string.pending_survey` | `app/src/main/res/values/strings.xml` | 675 |
| `R.string.permissions_denied` | `app/src/main/res/values/strings.xml` | 658 |
| `R.string.permissions_granted` | `app/src/main/res/values/strings.xml` | 657 |
| `R.string.phone_number_colon` | `app/src/main/res/values/strings.xml` | 731 |
| `R.string.planet_name` | `app/src/main/res/values/strings.xml` | 1089 |
| `R.string.please_enter_a_username` | `app/src/main/res/values/strings.xml` | 864 |
| `R.string.please_enter_your_password` | `app/src/main/res/values/strings.xml` | 823 |
| `R.string.please_select_link_item_from_list` | `app/src/main/res/values/strings.xml` | 660 |
| `R.string.rectally` | `app/src/main/res/values/strings.xml` | 290 |
| `R.string.reminder_set_for` | `app/src/main/res/values/strings.xml` | 1265 |
| `R.string.request_for_advice` | `app/src/main/res/values/strings.xml` | 640 |
| `R.string.resource_not_downloaded` | `app/src/main/res/values/strings.xml` | 673 |
| `R.string.resource_saved_successfully` | `app/src/main/res/values/strings.xml` | 732 |
| `R.string.resources_colon` | `app/src/main/res/values/strings.xml` | 853 |
| `R.string.respiration_rate` | `app/src/main/res/values/strings.xml` | 296 |
| `R.string.returning_user` | `app/src/main/res/values/strings.xml` | 1007 |
| `R.string.search_user` | `app/src/main/res/values/strings.xml` | 146 |
| `R.string.select_login_mode` | `app/src/main/res/values/strings.xml` | 575 |
| `R.string.select_user_to_login` | `app/src/main/res/values/strings.xml` | 984 |
| `R.string.show_filter` | `app/src/main/res/values/strings.xml` | 128 |
| `R.string.show_main_conversation` | `app/src/main/res/values/strings.xml` | 170 |
| `R.string.show_replies` | `app/src/main/res/values/strings.xml` | 539 |
| `R.string.show_reply` | `app/src/main/res/values/strings.xml` | 169 |
| `R.string.sign_up_to_chat` | `app/src/main/res/values/strings.xml` | 1081 |
| `R.string.start_time_is_required` | `app/src/main/res/values/strings.xml` | 705 |
| `R.string.storage_critically_low` | `app/src/main/res/values/strings.xml` | 678 |
| `R.string.survey_taken` | `app/src/main/res/values/strings.xml` | 991 |
| `R.string.sync_egdirbmac` | `app/src/main/res/values/strings.xml` | 1142 |
| `R.string.syncing_health_please_wait` | `app/src/main/res/values/strings.xml` | 689 |
| `R.string.systolic` | `app/src/main/res/values/strings.xml` | 297 |
| `R.string.tasks_due` | `app/src/main/res/values/strings.xml` | 677 |
| `R.string.team_name` | `app/src/main/res/values/strings.xml` | 284 |
| `R.string.temperature_taken` | `app/src/main/res/values/strings.xml` | 294 |
| `R.string.this_device_not_configured_properly_please_check_and_sync` | `app/src/main/res/values/strings.xml` | 814 |
| `R.string.time` | `app/src/main/res/values/strings.xml` | 997 |
| `R.string.times` | `app/src/main/res/values/strings.xml` | 996 |
| `R.string.treatments_colon` | `app/src/main/res/values/strings.xml` | 771 |
| `R.string.txt_myprogress` | `app/src/main/res/values/strings.xml` | 512 |
| `R.string.type_asterisk` | `app/src/main/res/values/strings.xml` | 579 |
| `R.string.type_name_to_search` | `app/src/main/res/values/strings.xml` | 278 |
| `R.string.unable_to_connect_to_planet_wifi` | `app/src/main/res/values/strings.xml` | 805 |
| `R.string.unable_to_play_audio` | `app/src/main/res/values/strings.xml` | 875 |
| `R.string.unable_to_upload_resource` | `app/src/main/res/values/strings.xml` | 659 |
| `R.string.uploading_activities_to_server_please_wait` | `app/src/main/res/values/strings.xml` | 829 |
| `R.string.user_not_available_in_our_database` | `app/src/main/res/values/strings.xml` | 873 |
| `R.string.user_removed_from_team` | `app/src/main/res/values/strings.xml` | 309 |
| `R.string.visit_count` | `app/src/main/res/values/strings.xml` | 1112 |
| `R.string.visits` | `app/src/main/res/values/strings.xml` | 684 |
| `R.string.vital_sign` | `app/src/main/res/values/strings.xml` | 342 |
| `R.string.vital_signs_record` | `app/src/main/res/values/strings.xml` | 616 |
| `R.string.welcome_back` | `app/src/main/res/values/strings.xml` | 1232 |
| `R.string.width` | `app/src/main/res/values/strings.xml` | 347 |

Total unused resources flagged: **131**

### Additional XML Warnings
- `app/src/main/res/layout/add_meetup.xml`: redundant `xmlns:android` declaration.
- `app/src/main/res/layout/fragment_edit_achievement.xml`: redundant `xmlns:android` and `xmlns:app` declarations.

## Gradle Dependency/Plugin Review
| Dependency | Status | Notes |
| --- | --- | --- |
| `androidx.multidex:multidex:2.0.1` | Unused | No direct references; minSdk 26 enables native multidex support, so the runtime dependency is redundant. |
| `org.jetbrains:annotations:26.0.2` | Unused | No `org.jetbrains.annotations.*` imports found; Kotlin code already ships nullability metadata. |
| `androidx.legacy:legacy-support-v4:1.0.0` | Partially used | Only required for `LocalBroadcastManager`; replace with `androidx.localbroadcastmanager:localbroadcastmanager` before removal. |

## Potential False Positives / Dynamic References
- Hilt modules above are instantiated via annotation processing and must be retained despite lacking direct call sites.
- String resources are not referenced through `getIdentifier` or reflection in the codebase, reducing the risk of hidden usages. Still, review with feature owners before removing messaging-related strings.

## Next Steps
- Delete the unused string and array resources listed above (including localized translations).
- Remove redundant namespace declarations from the noted layout files.
- Drop `androidx.multidex` and `org.jetbrains:annotations` dependencies; swap `androidx.legacy:legacy-support-v4` with `androidx.localbroadcastmanager:localbroadcastmanager` if legacy APIs are otherwise unused.