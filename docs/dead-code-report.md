# Dead Code Report

Generated on 2026-07-13 for the Android app defaultDebug variant.

## Scope and tooling

- Ran Android Lint with `./gradlew :app:lintDefaultDebug`.
- Reviewed Lint `UnusedResources` findings and repository references with resource-aware Android Lint.
- Kotlin/Java declaration and Gradle dependency removal were not performed automatically because this repository uses Android framework entry points, Hilt, Realm annotation processing, generated binding classes, and manifest/navigation/resource references that can make whole-program text searches unsafe.

## Confirmed dead XML resources removed

Android Lint reported 55 unused resources. These were confirmed by the defaultDebug resource graph and removed from default, night, and localized resource sets where present:

### Colors

- `R.color.background_color`
- `R.color.md_blue_200`

### Layouts

- `R.layout.item_voice_label`

### Styles

- `R.style.Header`

### Strings and plurals

- `R.string.last_syncs`
- `R.string.csv_filename`
- `R.string.image_filename`
- `R.string.this_file_type_is_currently_unsupported`
- `R.string.no_course_matched_filter`
- `R.string.do_you_want_to_stay_online`
- `R.string.all_files_downloaded_successfully`
- `R.string.downloading_started_please_check_notification`
- `R.string.file_already_exists`
- `R.string.thank_you_your_feedback_has_been_submitted`
- `R.string.wifi_is_turned_off_saving_battery_power`
- `R.string.turning_on_wifi_please_wait`
- `R.string.you_are_now_connected`
- `R.string.check_the_server_address_again_what_i_connected_to_wasn_t_the_planet_server`
- `R.string.unable_to_load`
- `R.string.audio_file_saved_in_database`
- `R.string.connection_failed_reason`
- `R.string.beta_function_for_wifi_switch`
- `R.string.image_resource`
- `R.string.checking_server`
- `R.string.three_strings`
- `R.string.dark_mode_off`
- `R.string.dark_mode_on`
- `R.string.survey_adopted_successfully`
- `R.string.server_sync_has_failed`
- `R.plurals.minutes`
- `R.plurals.hours`
- `R.plurals.days`
- `R.string.syncing_team_data`
- `R.string.syncing_chat_history`
- `R.string.syncing_courses_data`
- `R.string.loading_courses`
- `R.string.syncing_feedback`
- `R.string.syncing_health_data`
- `R.string.syncing_resources`
- `R.string.syncing_achievements`
- `R.string.failed_to_adopt_survey`
- `R.string.failed_to_save_chat`
- `R.string.failed_to_delete_report`
- `R.string.stop_reading`
- `R.string.tts_not_available`
- `R.string.pdf_no_text_layer`
- `R.string.pdf_extracting_text`
- `R.string.label_relationship`
- `R.string.label_phone_no`
- `R.string.label_email_with_colon`
- `R.string.track_title`
- `R.string.unknown_artist`
- `R.string.markdown_filename`
- `R.string.link`
- `R.string.enterprise_reports`

## Reflection and dynamic-loading risk flags

- Realm model classes, Hilt-injected classes, Android components declared in the manifest, ViewBinding/DataBinding-generated references, and WorkManager/service/receiver entry points may be reached without direct source references. No Kotlin/Java classes were removed in this pass.
- Resource names can be loaded dynamically via `Resources.getIdentifier`, server-provided names, or reflection. The removed resources are ordinary app resources reported unused by Android Lint for the defaultDebug variant; no dynamic lookup evidence was found during this pass.

## Potential false positives intentionally left in place

- Kotlin/Java classes, methods, and properties that appear unused by text search but may be framework, annotation processor, test, serialization, or reflection entry points.
- Gradle dependencies and plugins. Android builds frequently require dependencies only for annotation processors, generated code, variant-specific code, or transitive API contracts. No dependency was removed without dependency-analysis tooling.
- Test-only helpers and fixtures under `app/src/test` and `app/src/androidTest`.
- Future-feature strings or resources not reported by Android Lint remain untouched.
