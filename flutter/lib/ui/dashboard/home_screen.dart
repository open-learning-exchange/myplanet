import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../data/local/app_database.dart';
import '../../data/local/user_mapper.dart';
import '../../l10n/app_localizations.dart';
import '../../providers/app_providers.dart';
import '../../providers/challenge_provider.dart';
import '../../providers/courses_providers.dart';
import '../../providers/dashboard_providers.dart';
import '../../providers/health_provider.dart';
import '../../providers/life_provider.dart';
import '../../providers/network_status_provider.dart';
import '../../providers/notifications_provider.dart';
import '../../providers/resources_providers.dart';
import '../../providers/session_provider.dart';
import '../../providers/settings_provider.dart';
import '../../providers/sync_state.dart';
import '../../repository/notifications_repository.dart';
import '../components/challenge_dialog.dart';
import '../components/guest_dialog.dart';
import '../components/profile_avatar.dart';
import '../components/relative_time.dart';
import '../life/life_features.dart';
import '../router.dart';
import 'dashboard_drawer.dart';
import 'inactive_dashboard_screen.dart';

enum _HomeMenuAction {
  sync,
  feedback,
  settings,
  theme,
  language,
  about,
  disclaimer,
  logout,
}

/// Port of `ui/dashboard/BellDashboardFragment.kt` — the home ("bell")
/// dashboard: the profile card with its completed-course stars, network-status
/// ring and offline-login count, the four myLibrary / myCourses / myTeams /
/// myLife cards with team alert badges, the activity-chart action, and the
/// pending-survey dialog with its remind-later scheduler.
///
/// Still unported from the Kotlin home screen, tracked in the migration doc:
/// OS-scheduled background sync, which needs platform scheduling rather than a
/// screen (the `AutoSyncWorker` half landed in Phase 38 through the
/// `workmanager` plugin).
class HomeScreen extends ConsumerStatefulWidget {
  const HomeScreen({super.key});

  @override
  ConsumerState<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends ConsumerState<HomeScreen> {
  /// `BellDashboardFragment` throttles the dialog to one show per hour.
  static const Duration _surveyDialogInterval = Duration(hours: 1);

  bool _surveyCheckDone = false;

  @override
  void initState() {
    super.initState();
    // The session restores asynchronously, so a one-shot post-frame check
    // would race it and silently never run. The Kotlin fires this when the
    // user first loads (`wasUserNull` in `onViewCreated`); listening to the
    // session is the same trigger.
    ref.listenManual(sessionProvider, fireImmediately: true, (previous, next) {
      final user = next.valueOrNull;
      if (user != null) {
        WidgetsBinding.instance.addPostFrameCallback(
          (_) => _checkPendingSurveys(user),
        );
        // `syncKeyId()` from `BellDashboardFragment.onViewCreated`: a
        // non-guest user with no local health key fetches the one their
        // account published to its `userdb-*` database. The Kotlin gates on
        // `TextUtils.isEmpty(user.key)`; the notifier's SyncRunning check is
        // the `syncJob?.isActive` re-entrancy guard.
        if (!UserMapper.isGuest(user) && (user.key?.isEmpty ?? true)) {
          ref
              .read(healthKeyIvSyncProvider.notifier)
              .sync(user.rolesList.join(','));
        }
        // `DashboardActivity.evaluateChallengeDialog` — the challenge campaign
        // fires once on dashboard load when a non-guest user is on a
        // participating server.
        WidgetsBinding.instance.addPostFrameCallback(
          (_) => _evaluateChallenge(user),
        );
      }
    });

    // `observeSurveyReminders` — a snoozed set reappears when its time comes,
    // with the "Reminder:" title and no hourly throttle.
    ref.listenManual(dueSurveyRemindersProvider, (previous, next) {
      final due = next.valueOrNull;
      if (due == null || due.isEmpty) return;
      WidgetsBinding.instance.addPostFrameCallback((_) => _showDue(due));
    });
  }

  /// Port of `handleDueReminders` — resolves each snoozed id set back to the
  /// submissions that are *still* pending and re-shows the dialog for it.
  Future<void> _showDue(List<String> due) async {
    final session = ref.read(sessionProvider).valueOrNull;
    if (session == null || !mounted) return;

    // Re-reading the pending set rather than trusting the stored ids is what
    // the Kotlin does (`filter { it.status == "pending" }`): a survey answered
    // during the snooze must not be nagged about.
    final pending = await ref.read(pendingSurveysProvider(session.id).future);
    if (pending.isEmpty || !mounted) return;

    for (final surveyIds in due) {
      final ids = surveyIds.split(',').where((id) => id.isNotEmpty).toSet();
      final stillPending = pending
          .where((survey) => ids.contains(survey.submissionId))
          .toList(growable: false);
      if (stillPending.isEmpty) continue;
      if (!mounted) return;
      await _showSurveyDialog(
        stillPending,
        AppLocalizations.of(
          context,
        ).reminderSurveysToComplete(stillPending.length),
        // `showPendingSurveysReminder` passes `dismissOnNeutral = true`, so
        // snoozing a reminder closes it rather than leaving it stacked under the
        // remind-later sheet.
        dismissOnNeutral: true,
      );
    }
  }

  /// Port of `DashboardActivity.evaluateChallengeDialog` +
  /// `ChallengePrompter.showChallengeDialog`. The evaluator (see
  /// `challenge_provider.dart`) gathers the voice counts, course status, and
  /// sync state; this method shows the dialog and routes the action button to
  /// the screen the user needs next (course, voices, or sync center).
  Future<void> _evaluateChallenge(UserRow session) async {
    if (!mounted) return;
    final config = ref.read(serverConfigProvider);
    if (config == null) return;

    final evaluator = ref.read(challengeEvaluatorProvider);
    final data = await evaluator.evaluate(
      userId: session.id,
      isGuest: UserMapper.isGuest(session),
      serverUrl: config.serverUrl,
    );
    if (data == null || !mounted) return;

    // The congratulations variant fires once: once shown, the flag suppresses
    // every subsequent appearance, matching `ChallengePrompter`'s
    // `hasShownCongrats` guard.
    final isCompleted = _isChallengeCompleted(data);
    if (isCompleted) {
      final shown = ref.read(hasShownChallengeCongratsProvider);
      if (shown) return;
      await ref.read(hasShownChallengeCongratsProvider.notifier).setShown();
    }

    if (!mounted) return;
    await showDialog<void>(
      context: context,
      builder: (context) => ChallengeDialog(
        voiceCount: data.voiceCount,
        allVoiceCount: data.allVoiceCount,
        hasUnfinishedSurvey: data.hasUnfinishedSurvey,
        hasValidSync: data.hasValidSync,
        courseStatus: data.courseStatus,
        onStartCourse: () => context.push(
          '${Routes.courses}/${ChallengeEvaluator.challengeCourseId}/take',
        ),
        onNext: () => context.push(Routes.voices),
        onSync: () => context.push(Routes.syncCenter),
      ),
    );
  }

  bool _isChallengeCompleted(ChallengeDialogData data) {
    final voiceDone = data.voiceCount >= 5;
    final courseDone = data.courseStatus.toLowerCase().contains('terminado');
    final prereqsMet = courseDone && voiceDone;
    final syncDone = prereqsMet && data.hasValidSync;
    return voiceDone && courseDone && syncDone;
  }

  Future<void> _checkPendingSurveys(UserRow session) async {
    if (_surveyCheckDone || !mounted) return;
    _surveyCheckDone = true;

    // Guests never see the dialog, exactly as the Kotlin guards it.
    if (UserMapper.isGuest(session)) return;

    final prefs = ref.read(planetPrefsProvider);
    final now = DateTime.now().millisecondsSinceEpoch;
    if (now - prefs.lastSurveyDialogShown <
        _surveyDialogInterval.inMilliseconds) {
      return;
    }

    final pending = await ref.read(pendingSurveysProvider(session.id).future);
    if (pending.isEmpty || !mounted) return;

    // `checkPendingSurveys` skips a set the user has already snoozed, so the
    // hourly dialog cannot undo a remind-later.
    if (prefs.isReminderScheduled(_reminderKeyFor(pending))) return;

    // The "shown at" stamp is written when the dialog appears, not when it is
    // answered — dismissing it still starts the hour, as the Kotlin does.
    await prefs.setLastSurveyDialogShown(now);
    if (!mounted) return;

    await _showSurveyDialog(
      pending,
      AppLocalizations.of(context).surveysToComplete(pending.length),
    );
  }

  /// The comma-joined submission ids the Kotlin uses as its reminder key
  /// (`pendingSurveys.joinToString(",") { it.id }`).
  String _reminderKeyFor(List<PendingSurvey> surveys) =>
      surveys.map((survey) => survey.submissionId).join(',');

  /// Port of `showSurveyListDialog`. Shared by the hourly check and the
  /// reminder path, which differ only in title and in whether snoozing also
  /// dismisses this dialog.
  Future<void> _showSurveyDialog(
    List<PendingSurvey> pending,
    String title, {
    bool dismissOnNeutral = false,
  }) async {
    final l10n = AppLocalizations.of(context);
    await showDialog<void>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: Text(title),
        content: SizedBox(
          width: double.maxFinite,
          child: ListView(
            shrinkWrap: true,
            children: [
              for (final survey in pending)
                ListTile(
                  title: Text(survey.name),
                  onTap: () {
                    Navigator.of(dialogContext).pop();
                    context.push(
                      '${Routes.surveys}/${survey.surveyId}'
                      '?submission=${survey.submissionId}',
                    );
                  },
                ),
            ],
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(),
            child: Text(l10n.cancel),
          ),
          // The Kotlin's BUTTON_NEUTRAL, wired so it does *not* dismiss unless
          // `dismissOnNeutral` — the remind-later sheet opens over it.
          TextButton(
            onPressed: () async {
              if (dismissOnNeutral) Navigator.of(dialogContext).pop();
              await _showRemindLaterDialog(pending);
            },
            child: Text(l10n.remindLater),
          ),
          FilledButton(
            onPressed: () {
              Navigator.of(dialogContext).pop();
              context.push(Routes.surveys);
            },
            child: Text(l10n.ok),
          ),
        ],
      ),
    );
  }

  /// Port of `showRemindLaterDialog` — pick a unit and an amount, then store the
  /// reminder.
  ///
  /// The Kotlin uses a `RadioGroup` plus a `NumberPicker` whose max changes with
  /// the unit (60 minutes / 24 hours / 30 days) and resets nothing else. A
  /// segmented button and a slider express the same constrained pick with
  /// Material 3 widgets; the caps are the Kotlin's.
  Future<void> _showRemindLaterDialog(List<PendingSurvey> pending) async {
    if (!mounted) return;
    final l10n = AppLocalizations.of(context);
    final selection = await showDialog<Duration>(
      context: context,
      builder: (dialogContext) => _RemindLaterDialog(l10n: l10n),
    );
    if (selection == null) return;
    await ref
        .read(planetPrefsProvider)
        .scheduleSurveyReminder(_reminderKeyFor(pending), selection);
  }

  /// Port of `BellDashboardFragment`'s library-card navigation (`08e18ffdc`):
  /// if the user has shelf items, open the "My Library" (shelf) view; if not,
  /// open the full catalog so they can browse and join resources.
  void _openLibraryCard(BuildContext context, String userId) {
    final shelf =
        ref.read(myLibraryStreamProvider(userId)).valueOrNull ?? const [];
    ref.read(resourceShelfOnlyProvider.notifier).state = shelf.isNotEmpty;
    context.go(Routes.resources);
  }

  /// Port of `BellDashboardFragment`'s courses-card navigation (`034626415`,
  /// #15727): if the user has joined courses, open "My Courses" (shelf); if
  /// not, open the full catalog so they can browse and join. The library card
  /// got the same my/call split in `08e18ffdc` (see [_openLibraryCard]).
  void _openCoursesCard(BuildContext context, String userId) {
    final courses =
        ref.read(myCoursesStreamProvider(userId)).valueOrNull ?? const [];
    ref
        .read(courseFilterProvider.notifier)
        .setMyCoursesOnly(courses.isNotEmpty);
    context.go(Routes.courses);
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final session = ref.watch(sessionProvider).valueOrNull;
    final prefs = ref.watch(planetPrefsProvider);
    final unread = ref.watch(unreadNotificationCountProvider).valueOrNull ?? 0;
    final lastSync = ref.watch(lastSyncProvider);

    // `DashboardActivity.updateAppTitle`: "Planet <planetCode>", falling back
    // to the community name from preferences.
    final planetCode = session?.planetCode ?? '';
    final title = planetCode.isNotEmpty
        ? 'Planet $planetCode'
        : (prefs.communityName.isNotEmpty
              ? 'Planet ${prefs.communityName}'
              : l10n.appTitle);

    final isGuest = session != null && UserMapper.isGuest(session);

    // `DashboardActivity.handleGuestAccess`: a logged-in user with no roles
    // and no admin flag sees the inactive dashboard instead of the full
    // navigation. Guests fall through to the bell dashboard (their access is
    // gated per-action via `showGuestDialog`).
    final isInactive =
        session != null &&
        !isGuest &&
        session.rolesList.isEmpty &&
        !session.userAdmin;
    if (isInactive) {
      return const InactiveDashboardScreen();
    }

    return Scaffold(
      drawer: const DashboardDrawer(),
      // `binding.fabMyActivity` — opens the login-activity chart.
      floatingActionButton: FloatingActionButton.small(
        tooltip: l10n.myActivities,
        onPressed: () => context.push(Routes.activities),
        child: const Icon(Icons.insights_outlined),
      ),
      appBar: AppBar(
        title: Text(title),
        actions: [
          IconButton(
            tooltip: l10n.aiChat,
            onPressed: () => isGuest
                ? showGuestDialog(context)
                : context.push(Routes.chatHistory),
            icon: const Icon(Icons.forum_outlined),
          ),
          IconButton(
            tooltip: l10n.notifications,
            onPressed: () => context.push(Routes.notifications),
            icon: Badge.count(
              count: unread,
              isLabelVisible: unread > 0,
              child: const Icon(Icons.notifications_outlined),
            ),
          ),
          PopupMenuButton<_HomeMenuAction>(
            tooltip: l10n.moreOptions,
            onSelected: (action) =>
                _handleMenuAction(context, ref, action, isGuest: isGuest),
            itemBuilder: (context) => [
              PopupMenuItem(
                value: _HomeMenuAction.sync,
                child: ListTile(
                  leading: const Icon(Icons.sync),
                  title: Text(l10n.syncNow),
                  contentPadding: EdgeInsets.zero,
                ),
              ),
              PopupMenuItem(
                value: _HomeMenuAction.feedback,
                child: ListTile(
                  leading: const Icon(Icons.feedback_outlined),
                  title: Text(l10n.feedback),
                  contentPadding: EdgeInsets.zero,
                ),
              ),
              PopupMenuItem(
                value: _HomeMenuAction.settings,
                child: ListTile(
                  leading: const Icon(Icons.settings_outlined),
                  title: Text(l10n.settings),
                  contentPadding: EdgeInsets.zero,
                ),
              ),
              PopupMenuItem(
                value: _HomeMenuAction.theme,
                child: ListTile(
                  leading: const Icon(Icons.contrast_outlined),
                  title: Text(l10n.appTheme),
                  contentPadding: EdgeInsets.zero,
                ),
              ),
              PopupMenuItem(
                value: _HomeMenuAction.language,
                child: ListTile(
                  leading: const Icon(Icons.language),
                  title: Text(l10n.selectLanguage),
                  contentPadding: EdgeInsets.zero,
                ),
              ),
              PopupMenuItem(
                value: _HomeMenuAction.about,
                child: ListTile(
                  leading: const Icon(Icons.info_outline),
                  title: Text(l10n.about),
                  contentPadding: EdgeInsets.zero,
                ),
              ),
              PopupMenuItem(
                value: _HomeMenuAction.disclaimer,
                child: ListTile(
                  leading: const Icon(Icons.privacy_tip_outlined),
                  title: Text(l10n.disclaimer),
                  contentPadding: EdgeInsets.zero,
                ),
              ),
              PopupMenuItem(
                value: _HomeMenuAction.logout,
                child: ListTile(
                  leading: const Icon(Icons.logout),
                  title: Text(l10n.logOut),
                  contentPadding: EdgeInsets.zero,
                ),
              ),
            ],
          ),
        ],
      ),
      body: session == null
          ? const Center(child: CircularProgressIndicator())
          : Column(
              children: [
                _LastSyncStrip(timestamp: lastSync),
                _ProfileCard(session: session),
                Expanded(
                  child: _HomeCard(
                    color: Colors.red.shade700,
                    icon: Icons.local_library_outlined,
                    label: l10n.homeMyLibrary,
                    count: ref
                        .watch(myLibraryStreamProvider(session.id))
                        .valueOrNull
                        ?.length,
                    onHeaderTap: () => isGuest
                        ? showGuestDialog(context)
                        : _openLibraryCard(context, session.id),
                    child: _LibraryTiles(userId: session.id, isGuest: isGuest),
                  ),
                ),
                Expanded(
                  child: _HomeCard(
                    color: Colors.amber.shade800,
                    icon: Icons.school_outlined,
                    label: l10n.homeMyCourses,
                    count: ref
                        .watch(myCoursesStreamProvider(session.id))
                        .valueOrNull
                        ?.length,
                    onHeaderTap: () {
                      if (isGuest) {
                        showGuestDialog(context);
                        return;
                      }
                      _openCoursesCard(context, session.id);
                    },
                    child: _CourseTiles(userId: session.id, isGuest: isGuest),
                  ),
                ),
                Expanded(
                  child: _HomeCard(
                    color: Colors.green.shade700,
                    icon: Icons.groups_outlined,
                    label: l10n.homeMyTeams,
                    count: ref
                        .watch(myTeamsStreamProvider(session.id))
                        .valueOrNull
                        ?.length,
                    // The Kotlin myTeams header is not guest-gated.
                    onHeaderTap: () => context.push(Routes.teams),
                    child: _TeamTiles(userId: session.id),
                  ),
                ),
                Expanded(
                  child: _HomeCard(
                    color: Colors.purple.shade700,
                    icon: Icons.dashboard_customize_outlined,
                    label: l10n.homeMyLife,
                    // The Kotlin myLife card has no count badge.
                    count: null,
                    onHeaderTap: () => context.go(Routes.life),
                    child: _LifeTiles(isGuest: isGuest),
                  ),
                ),
              ],
            ),
    );
  }
}

Future<void> _handleMenuAction(
  BuildContext context,
  WidgetRef ref,
  _HomeMenuAction action, {
  required bool isGuest,
}) async {
  switch (action) {
    case _HomeMenuAction.sync:
      context.push(Routes.syncCenter);
      return;
    case _HomeMenuAction.feedback:
      if (isGuest) {
        showGuestDialog(context);
      } else {
        context.push(Routes.feedback);
      }
      return;
    case _HomeMenuAction.settings:
      context.push(Routes.settings);
      return;
    case _HomeMenuAction.theme:
      final current = ref.read(themeModeProvider);
      final next = switch (current) {
        ThemeMode.system => ThemeMode.light,
        ThemeMode.light => ThemeMode.dark,
        ThemeMode.dark => ThemeMode.system,
      };
      await ref.read(themeModeProvider.notifier).select(next);
      return;
    case _HomeMenuAction.language:
      await _showLanguageDialog(context, ref);
      return;
    case _HomeMenuAction.about:
      context.push(Routes.about);
      return;
    case _HomeMenuAction.disclaimer:
      context.push(Routes.disclaimer);
      return;
    case _HomeMenuAction.logout:
      await ref.read(sessionProvider.notifier).signOut();
      return;
  }
}

/// Port of `SettingsActivity.SettingFragment.languageChanger` — a single-choice
/// list of the six supported languages, applied immediately.
///
/// The Kotlin's list order and labels are kept (`english`, `español`, `somali`,
/// `नेपाली`, `عربى`, `français` — each in its own language, and each already
/// translated upstream).
Future<void> _showLanguageDialog(BuildContext context, WidgetRef ref) async {
  final l10n = AppLocalizations.of(context);
  final labels = <String, String>{
    'en': l10n.english,
    'es': l10n.spanish,
    'so': l10n.somali,
    'ne': l10n.nepali,
    'ar': l10n.arabic,
    'fr': l10n.french,
  };
  // `LocaleUtils.getLanguage` falls back to index 0 ("en") when nothing is
  // stored, so an unset override shows English checked.
  final current = ref.read(localeProvider)?.languageCode ?? 'en';

  final selected = await showDialog<String>(
    context: context,
    builder: (dialogContext) => SimpleDialog(
      title: Text(l10n.selectLanguage),
      children: [
        // A checked `ListTile` rather than `RadioListTile`: the radio's
        // `groupValue`/`onChanged` pair is deprecated in favour of a
        // `RadioGroup` ancestor, and `flutter analyze` fails the build on the
        // resulting info.
        for (final entry in labels.entries)
          ListTile(
            title: Text(entry.value),
            trailing: entry.key == current ? const Icon(Icons.check) : null,
            selected: entry.key == current,
            onTap: () => Navigator.of(dialogContext).pop(entry.key),
          ),
      ],
    ),
  );
  if (selected == null) return;
  await ref.read(localeProvider.notifier).select(selected);
}

/// The unit choices `showRemindLaterDialog` offers, with the Kotlin's per-unit
/// `numberPicker.maxValue`.
enum _RemindUnit {
  minutes(60),
  hours(24),
  days(30);

  const _RemindUnit(this.maxValue);

  final int maxValue;

  Duration durationFor(int value) => switch (this) {
    _RemindUnit.minutes => Duration(minutes: value),
    _RemindUnit.hours => Duration(hours: value),
    _RemindUnit.days => Duration(days: value),
  };
}

class _RemindLaterDialog extends StatefulWidget {
  const _RemindLaterDialog({required this.l10n});

  final AppLocalizations l10n;

  @override
  State<_RemindLaterDialog> createState() => _RemindLaterDialogState();
}

class _RemindLaterDialogState extends State<_RemindLaterDialog> {
  /// `radioGroup.check(R.id.radioButtonMinutes)` and `minValue = 1`.
  _RemindUnit _unit = _RemindUnit.minutes;
  int _value = 1;

  String _labelFor(_RemindUnit unit) => switch (unit) {
    _RemindUnit.minutes => widget.l10n.minutes,
    _RemindUnit.hours => widget.l10n.hours,
    _RemindUnit.days => widget.l10n.days,
  };

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: Text(widget.l10n.remindMeLater),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          SegmentedButton<_RemindUnit>(
            segments: [
              for (final unit in _RemindUnit.values)
                ButtonSegment(value: unit, label: Text(_labelFor(unit))),
            ],
            selected: {_unit},
            onSelectionChanged: (selected) => setState(() {
              _unit = selected.first;
              // Changing the unit lowers the cap, so an out-of-range value has
              // to come back inside it — the Kotlin's NumberPicker clamps for
              // the same reason.
              if (_value > _unit.maxValue) _value = _unit.maxValue;
            }),
          ),
          const SizedBox(height: 16),
          Text('$_value ${_labelFor(_unit)}'),
          Slider(
            value: _value.toDouble(),
            min: 1,
            max: _unit.maxValue.toDouble(),
            divisions: _unit.maxValue - 1,
            label: '$_value',
            onChanged: (value) => setState(() => _value = value.round()),
          ),
        ],
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(),
          child: Text(widget.l10n.cancel),
        ),
        FilledButton(
          onPressed: () => Navigator.of(context).pop(_unit.durationFor(_value)),
          child: Text(widget.l10n.setReminder),
        ),
      ],
    );
  }
}

class _LastSyncStrip extends StatelessWidget {
  const _LastSyncStrip({required this.timestamp});

  final int timestamp;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final value = timestamp <= 0
        ? l10n.neverSynced
        : relativeTimeLabel(
            l10n,
            DateTime.now().millisecondsSinceEpoch - timestamp,
          );
    return Semantics(
      liveRegion: true,
      child: Container(
        width: double.infinity,
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
        color: Theme.of(context).colorScheme.surfaceContainerHighest,
        child: Text(
          l10n.lastSynced(value),
          textAlign: TextAlign.center,
          style: Theme.of(context).textTheme.labelMedium,
        ),
      ),
    );
  }
}

/// The profile card: avatar ringed by the server-reachability colour, full name
/// with its offline-login count, role, planet code, and a star per completed
/// course. Port of `card_profile_bell.xml`.
class _ProfileCard extends ConsumerWidget {
  const _ProfileCard({required this.session});

  final UserRow session;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final fullName = [
      session.firstName,
      session.middleName,
      session.lastName,
    ].whereType<String>().where((part) => part.isNotEmpty).join(' ');
    final displayName = fullName.isNotEmpty ? fullName : (session.name ?? '');
    final role = session.rolesList.join(', ');
    // `getDashboardProfile` counts by user name, so a session with no name
    // contributes no count — and the Kotlin renders `user_name` regardless,
    // showing "(0)".
    final logins =
        ref.watch(offlineLoginCountProvider(session.name ?? '')).valueOrNull ??
        0;

    return Card(
      margin: const EdgeInsets.all(8),
      child: InkWell(
        onTap: () => context.go(Routes.profile),
        child: Padding(
          padding: const EdgeInsets.all(8),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  _NetworkRingAvatar(user: session),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          l10n.userNameWithLogins(displayName, logins),
                          style: Theme.of(context).textTheme.titleMedium,
                          overflow: TextOverflow.ellipsis,
                        ),
                        if (role.isNotEmpty)
                          Text(
                            '- $role',
                            style: Theme.of(context).textTheme.bodySmall,
                          ),
                      ],
                    ),
                  ),
                  if ((session.planetCode ?? '').isNotEmpty)
                    Text(
                      session.planetCode!,
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                ],
              ),
              _CompletedCourseStars(userId: session.id),
            ],
          ),
        ),
      ),
    );
  }
}

/// The avatar with `imageView.borderColor` set from the network status, as
/// `setNetworkIndicatorColor` does.
/// The avatar with the server-reachability ring around it.
///
/// Two ported details meet here: the ring colour is `MainApplication`'s
/// reachability probe (Phase 33), and the photo inside it is the `_users`
/// attachment [ProfileAvatar] fetches (harvested from `flutter-openhands4`).
/// The openhands branch replaced this avatar outright, which would have dropped
/// the ring; the photo belongs *inside* it, since the Kotlin draws both.
class _NetworkRingAvatar extends ConsumerWidget {
  const _NetworkRingAvatar({required this.user});

  final UserRow user;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final status = ref.watch(networkStatusProvider);
    // The Kotlin's three colour resources: md_red_700, md_yellow_600, green.
    final (color, label) = switch (status) {
      NetworkStatus.disconnected => (
        const Color(0xFFD32F2F),
        l10n.serverUnreachable,
      ),
      NetworkStatus.connecting => (
        const Color(0xFFFDD835),
        l10n.serverChecking,
      ),
      NetworkStatus.connected => (
        const Color(0xFF4CAF50),
        l10n.serverReachable,
      ),
    };

    return Semantics(
      label: label,
      child: Tooltip(
        message: label,
        child: Container(
          padding: const EdgeInsets.all(2),
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            border: Border.all(color: color, width: 3),
          ),
          child: ProfileAvatar(user: user, radius: 24),
        ),
      ),
    );
  }
}

/// The star row: one per completed course, tinted when a certification covers
/// it. Port of `showBadges`/`setColor`.
///
/// The Kotlin shows a spinner (`progressBarBadges`) until the completions
/// arrive, then hides it — and hides it unconditionally after two seconds so an
/// empty result does not spin forever. Riverpod's loading state expresses the
/// same thing without the timer: the spinner is the `loading` case, and an empty
/// list renders nothing.
class _CompletedCourseStars extends ConsumerWidget {
  const _CompletedCourseStars({required this.userId});

  final String userId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final completed = ref.watch(completedCoursesProvider(userId));

    return completed.when(
      loading: () => const Padding(
        padding: EdgeInsetsDirectional.only(top: 6, start: 4),
        child: SizedBox(
          height: 16,
          width: 16,
          child: CircularProgressIndicator(strokeWidth: 2),
        ),
      ),
      error: (_, _) => const SizedBox.shrink(),
      data: (courses) {
        if (courses.isEmpty) return const SizedBox.shrink();
        return Padding(
          padding: const EdgeInsets.only(top: 4),
          child: SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            child: Row(
              children: [
                for (final course in courses)
                  IconButton(
                    visualDensity: VisualDensity.compact,
                    constraints: const BoxConstraints(),
                    padding: const EdgeInsets.symmetric(horizontal: 2),
                    // `star.contentDescription = "completed course <title>"`.
                    tooltip: '${l10n.completedCourse} ${course.courseTitle}',
                    onPressed: () =>
                        context.push('${Routes.courses}/${course.courseId}'),
                    icon: Icon(
                      Icons.star,
                      size: 20,
                      // `colorPrimary` when certified, `md_blue_grey_300` when
                      // not — the star says "done", the colour says "certified".
                      color: course.certified
                          ? Theme.of(context).colorScheme.primary
                          : const Color(0xFF90A4AE),
                    ),
                  ),
              ],
            ),
          ),
        );
      },
    );
  }
}

/// One dashboard card: the coloured header block (icon, label, count badge)
/// beside a horizontally scrolling grid of tiles. Port of the shared
/// `home_card_*.xml` structure.
class _HomeCard extends StatelessWidget {
  const _HomeCard({
    required this.color,
    required this.icon,
    required this.label,
    required this.count,
    required this.onHeaderTap,
    required this.child,
  });

  final Color color;
  final IconData icon;
  final String label;
  final int? count;
  final VoidCallback onHeaderTap;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      clipBehavior: Clip.antiAlias,
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          SizedBox(
            width: 96,
            child: Material(
              color: color,
              child: InkWell(
                onTap: onHeaderTap,
                child: Stack(
                  children: [
                    Center(
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Icon(icon, color: Colors.white, size: 32),
                          const SizedBox(height: 4),
                          Text(
                            label,
                            textAlign: TextAlign.center,
                            style: const TextStyle(
                              color: Colors.white,
                              fontSize: 12,
                            ),
                          ),
                        ],
                      ),
                    ),
                    // The count circle, `GONE` at zero like `hideCountIfZero`.
                    if (count != null && count! > 0)
                      Positioned(
                        top: 4,
                        right: 4,
                        child: CircleAvatar(
                          radius: 12,
                          backgroundColor: Colors.white,
                          child: Text(
                            '$count',
                            style: TextStyle(fontSize: 11, color: color),
                          ),
                        ),
                      ),
                  ],
                ),
              ),
            ),
          ),
          Expanded(child: child),
        ],
      ),
    );
  }
}

/// The 250×100 tile grid the Kotlin builds in a FlexboxLayout inside a
/// HorizontalScrollView: fixed-size tiles wrap vertically to fill the card and
/// overflow horizontally.
class _TileGrid extends StatelessWidget {
  const _TileGrid({required this.children});

  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    return GridView.builder(
      scrollDirection: Axis.horizontal,
      padding: const EdgeInsets.all(4),
      gridDelegate: const SliverGridDelegateWithMaxCrossAxisExtent(
        maxCrossAxisExtent: 56,
        mainAxisExtent: 180,
        mainAxisSpacing: 4,
        crossAxisSpacing: 4,
      ),
      itemCount: children.length,
      itemBuilder: (context, index) => children[index],
    );
  }
}

/// One tile, background alternating by index as `setBackgroundColor` does.
class _Tile extends StatelessWidget {
  const _Tile({
    required this.index,
    required this.onTap,
    required this.child,
    this.placeholder = false,
  });

  final int index;
  final VoidCallback onTap;
  final Widget child;
  final bool placeholder;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final background = placeholder
        ? scheme.surface
        : index.isEven
        ? scheme.surfaceContainerLowest
        : scheme.surfaceContainerHigh;
    return Material(
      color: background,
      borderRadius: BorderRadius.circular(4),
      child: InkWell(
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
          child: Center(child: child),
        ),
      ),
    );
  }
}

class _LibraryTiles extends ConsumerWidget {
  const _LibraryTiles({required this.userId, required this.isGuest});

  final String userId;
  final bool isGuest;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final resources =
        ref.watch(myLibraryStreamProvider(userId)).valueOrNull ?? const [];

    if (resources.isEmpty) {
      return _TileGrid(
        children: [
          _Tile(
            index: 0,
            placeholder: true,
            onTap: () => isGuest
                ? showGuestDialog(context)
                : context.go(Routes.resources),
            child: Text(
              l10n.noResourcesAddedYet,
              style: TextStyle(color: Theme.of(context).hintColor),
            ),
          ),
        ],
      );
    }

    return _TileGrid(
      children: [
        for (final (index, resource) in resources.indexed)
          _Tile(
            index: index,
            onTap: () => context.push('/resources/detail/${resource.id}'),
            child: Text(
              resource.title ?? '',
              maxLines: 3,
              overflow: TextOverflow.ellipsis,
            ),
          ),
      ],
    );
  }
}

class _CourseTiles extends ConsumerWidget {
  const _CourseTiles({required this.userId, required this.isGuest});

  final String userId;
  final bool isGuest;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final courses =
        ref.watch(myCoursesStreamProvider(userId)).valueOrNull ?? const [];

    if (courses.isEmpty) {
      return _TileGrid(
        children: [
          _Tile(
            index: 0,
            placeholder: true,
            onTap: () {
              if (isGuest) {
                showGuestDialog(context);
                return;
              }
              context.go(Routes.courses);
            },
            child: Text(
              l10n.noCoursesJoinedYet,
              style: TextStyle(color: Theme.of(context).hintColor),
            ),
          ),
        ],
      );
    }

    return _TileGrid(
      children: [
        for (final (index, course) in courses.indexed)
          _Tile(
            index: index,
            onTap: () => context.push('${Routes.courses}/${course.id}'),
            child: Text(
              course.courseTitle ?? '',
              maxLines: 3,
              overflow: TextOverflow.ellipsis,
            ),
          ),
      ],
    );
  }
}

class _TeamTiles extends ConsumerWidget {
  const _TeamTiles({required this.userId});

  final String userId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final teams =
        ref.watch(myTeamsStreamProvider(userId)).valueOrNull ?? const [];

    if (teams.isEmpty) {
      return _TileGrid(
        children: [
          _Tile(
            index: 0,
            placeholder: true,
            onTap: () => context.push(Routes.teams),
            child: Text(
              l10n.noTeamsJoinedYet,
              style: TextStyle(color: Theme.of(context).hintColor),
            ),
          ),
        ],
      );
    }

    final notifications =
        ref.watch(teamNotificationsProvider(userId)).valueOrNull ?? const {};

    return _TileGrid(
      children: [
        for (final (index, team) in teams.indexed)
          _Tile(
            index: index,
            onTap: () => context.push('${Routes.teams}/${team.id}'),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  team.name ?? '',
                  maxLines: 3,
                  overflow: TextOverflow.ellipsis,
                  textAlign: TextAlign.center,
                  // A sync-type team renders bold, as `renderMyTeams` does.
                  style: team.type == 'sync'
                      ? const TextStyle(fontWeight: FontWeight.bold)
                      : null,
                ),
                _TeamAlertBadges(info: notifications[team.id]),
              ],
            ),
          ),
      ],
    );
  }
}

/// The `img_chat`/`img_task` icons on a team tile, shown or hidden by
/// `showNotificationIcons`.
class _TeamAlertBadges extends StatelessWidget {
  const _TeamAlertBadges({required this.info});

  final TeamNotificationInfo? info;

  @override
  Widget build(BuildContext context) {
    // No info yet (the query is still running, or the team is absent from the
    // map) draws nothing, matching a Kotlin tile before `updateTeamNotifications`
    // reaches it.
    if (info == null || !info!.hasAny) return const SizedBox.shrink();
    final l10n = AppLocalizations.of(context);
    final color = Theme.of(context).colorScheme.primary;
    return Padding(
      padding: const EdgeInsets.only(top: 2),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (info!.hasChat)
            Semantics(
              label: l10n.aiChat,
              child: Icon(Icons.chat_bubble, size: 12, color: color),
            ),
          if (info!.hasTask)
            Semantics(
              label: l10n.teamTasks,
              child: Icon(Icons.assignment_late, size: 12, color: color),
            ),
        ],
      ),
    );
  }
}

class _LifeTiles extends ConsumerWidget {
  const _LifeTiles({required this.isGuest});

  final bool isGuest;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final rows = (ref.watch(lifeItemsProvider).valueOrNull ?? const [])
        .where((row) => row.isVisible)
        .toList(growable: false);

    if (rows.isEmpty) {
      return _TileGrid(
        children: [
          _Tile(
            index: 0,
            placeholder: true,
            onTap: () => context.go(Routes.life),
            child: Text(
              l10n.noDataAvailable,
              style: TextStyle(color: Theme.of(context).hintColor),
            ),
          ),
        ],
      );
    }

    return _TileGrid(
      children: [
        for (final (index, row) in rows.indexed)
          _Tile(
            index: index,
            onTap: () {
              if (isGuest && guestGatedLifeFeatures.contains(row.feature)) {
                showGuestDialog(context);
                return;
              }
              openLifeFeature(context, row.feature);
            },
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(lifeFeatureIcon(row.feature), size: 24),
                const SizedBox(height: 2),
                Text(
                  lifeFeatureTitle(l10n, row.feature, row.title),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(fontSize: 11),
                ),
              ],
            ),
          ),
      ],
    );
  }
}
