import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../data/local/app_database.dart';
import '../../data/local/user_mapper.dart';
import '../../l10n/app_localizations.dart';
import '../../providers/app_providers.dart';
import '../../providers/session_provider.dart';
import '../../providers/settings_provider.dart';
import '../components/guest_dialog.dart';
import '../router.dart';

/// First vertical slice of `ui/settings/SettingsActivity.kt`.
///
/// Theme selection is fully persisted. Server identity is intentionally
/// read-only here: changing it must continue through the configuration flow so
/// credentials and cached CouchDB URLs are replaced atomically.
class SettingsScreen extends ConsumerWidget {
  const SettingsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final selectedTheme = ref.watch(themeModeProvider);
    final server = ref.watch(serverConfigProvider);
    final background = ref.watch(backgroundSettingsProvider);
    final backgroundRun = ref.watch(planetPrefsProvider).lastBackgroundRun;
    final versionInfo = ref.watch(appVersionInfoProvider).valueOrNull;
    final textScale = ref.watch(textScaleProvider);
    final clearState = ref.watch(clearDataProvider);
    // Watched, not read. Both guest gates below used to call
    // `ref.read(sessionProvider).valueOrNull` from inside their `onTap`, and
    // this screen watches `sessionProvider` nowhere else — so the session was
    // `null` until something outside the screen resolved it, the gate fell
    // through, and a guest reached the reset-app confirmation. Latent in the
    // shipping app only because the router holds a `ref.listen` on the same
    // provider; the fifth instance of that shape in this port.
    final session = ref.watch(sessionProvider).valueOrNull;

    return Scaffold(
      appBar: AppBar(title: Text(l10n.settings)),
      body: ListView(
        children: [
          _SectionHeader(title: l10n.appearance),
          RadioGroup<ThemeMode>(
            groupValue: selectedTheme,
            onChanged: (mode) {
              if (mode != null) {
                ref.read(themeModeProvider.notifier).select(mode);
              }
            },
            child: Column(
              children: [
                RadioListTile<ThemeMode>(
                  value: ThemeMode.system,
                  title: Text(l10n.systemTheme),
                  secondary: const Icon(Icons.brightness_auto_outlined),
                ),
                RadioListTile<ThemeMode>(
                  value: ThemeMode.light,
                  title: Text(l10n.lightTheme),
                  secondary: const Icon(Icons.light_mode_outlined),
                ),
                RadioListTile<ThemeMode>(
                  value: ThemeMode.dark,
                  title: Text(l10n.darkTheme),
                  secondary: const Icon(Icons.dark_mode_outlined),
                ),
              ],
            ),
          ),
          ListTile(
            leading: const Icon(Icons.format_size_outlined),
            title: Text(l10n.textSize),
            subtitle: Text(_textScaleLabel(l10n, textScale)),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => _showTextSizeDialog(context, l10n, ref, textScale),
          ),
          const Divider(),
          _SectionHeader(title: l10n.server),
          ListTile(
            leading: const Icon(Icons.dns_outlined),
            title: Text(l10n.serverUrl),
            subtitle: Text(server?.serverUrl ?? l10n.notConfigured),
          ),
          if (server != null && server.code.isNotEmpty)
            ListTile(
              leading: const Icon(Icons.location_city_outlined),
              title: Text(l10n.communityCode),
              subtitle: Text(server.code),
            ),
          const Divider(),
          _SectionHeader(title: l10n.backgroundSync),
          SwitchListTile(
            value: background.enabled,
            onChanged: (enabled) async {
              try {
                await ref
                    .read(backgroundSettingsProvider.notifier)
                    .setEnabled(enabled);
              } catch (_) {
                if (context.mounted) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(content: Text(l10n.backgroundScheduleFailed)),
                  );
                }
              }
            },
            secondary: const Icon(Icons.sync_outlined),
            title: Text(l10n.backgroundSync),
            subtitle: Text(l10n.backgroundSyncDescription),
          ),
          ListTile(
            enabled: background.enabled,
            leading: const Icon(Icons.schedule_outlined),
            title: Text(l10n.syncFrequency),
            trailing: DropdownButton<Duration>(
              value: _supportedInterval(background.interval),
              onChanged: background.enabled
                  ? (interval) async {
                      if (interval != null) {
                        try {
                          await ref
                              .read(backgroundSettingsProvider.notifier)
                              .setInterval(interval);
                        } catch (_) {
                          if (context.mounted) {
                            ScaffoldMessenger.of(context).showSnackBar(
                              SnackBar(
                                content: Text(l10n.backgroundScheduleFailed),
                              ),
                            );
                          }
                        }
                      }
                    }
                  : null,
              items: [
                DropdownMenuItem(
                  value: const Duration(minutes: 15),
                  child: Text(l10n.every15Minutes),
                ),
                DropdownMenuItem(
                  value: const Duration(minutes: 30),
                  child: Text(l10n.every30Minutes),
                ),
                DropdownMenuItem(
                  value: const Duration(hours: 1),
                  child: Text(l10n.everyHour),
                ),
                DropdownMenuItem(
                  value: const Duration(hours: 6),
                  child: Text(l10n.every6Hours),
                ),
              ],
            ),
          ),
          ListTile(
            leading: Icon(
              _backgroundRunIcon(backgroundRun),
              color: _backgroundRunColor(context, backgroundRun),
            ),
            title: Text(l10n.lastBackgroundRun),
            subtitle: Text(_backgroundRunSummary(context, l10n, backgroundRun)),
            isThreeLine: backgroundRun != null,
          ),
          const Divider(),
          _SectionHeader(title: l10n.learningTools),
          ListTile(
            leading: const Icon(Icons.menu_book_outlined),
            title: Text(l10n.dictionary),
            subtitle: Text(l10n.dictionaryDescription),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => context.push(Routes.dictionary),
          ),
          ListTile(
            leading: const Icon(Icons.storage_outlined),
            title: Text(l10n.storageManagement),
            trailing: const Icon(Icons.chevron_right),
            // Guest-gated, matching `SettingsActivity`: a guest is offered
            // membership instead of the storage tools.
            onTap: () {
              if (session != null && UserMapper.isGuest(session)) {
                showGuestDialog(context);
                return;
              }
              context.push(Routes.storageManagement);
            },
          ),
          const Divider(),
          _SectionHeader(title: l10n.about),
          ListTile(
            leading: const Icon(Icons.info_outline),
            title: Text(l10n.appTitle),
            subtitle: Text(l10n.offlineLearningApp),
          ),
          ListTile(
            leading: const Icon(Icons.new_releases_outlined),
            title: Text(l10n.appVersion(versionInfo?.version ?? '…')),
            subtitle: Text(l10n.buildNumber(versionInfo?.buildNumber ?? '…')),
          ),
          const Divider(),
          _SectionHeader(title: l10n.dataManagement),
          ListTile(
            leading: const Icon(Icons.delete_outline),
            title: Text(l10n.resetApp),
            subtitle: Text(l10n.clearAllDataConfirm),
            enabled: !clearState.isLoading,
            onTap: () => _confirmClearData(context, l10n, ref, session),
          ),
        ],
      ),
    );
  }
}

Duration _supportedInterval(Duration interval) {
  const supported = [
    Duration(minutes: 15),
    Duration(minutes: 30),
    Duration(hours: 1),
    Duration(hours: 6),
  ];
  return supported.contains(interval) ? interval : const Duration(hours: 1);
}

IconData _backgroundRunIcon(Map<String, dynamic>? run) =>
    switch (run?['status']) {
      'succeeded' => Icons.check_circle_outline,
      'retryRequested' => Icons.error_outline,
      'skipped' => Icons.pause_circle_outline,
      _ => Icons.history,
    };

Color? _backgroundRunColor(BuildContext context, Map<String, dynamic>? run) =>
    switch (run?['status']) {
      'succeeded' => Colors.green,
      'retryRequested' => Theme.of(context).colorScheme.error,
      _ => null,
    };

String _backgroundRunSummary(
  BuildContext context,
  AppLocalizations l10n,
  Map<String, dynamic>? run,
) {
  if (run == null) return l10n.backgroundNeverRun;
  final status = switch (run['status']) {
    'succeeded' => l10n.backgroundSucceeded,
    'retryRequested' => l10n.backgroundRetryRequested,
    'skipped' => l10n.backgroundSkipped,
    _ => l10n.backgroundUnknown,
  };
  final attempted = DateTime.tryParse('${run['attemptedAt'] ?? ''}')?.toLocal();
  final timestamp = attempted == null
      ? ''
      : ' • ${MaterialLocalizations.of(context).formatShortDate(attempted)} '
            '${TimeOfDay.fromDateTime(attempted).format(context)}';
  final failures = run['failedSteps'];
  final failedText = failures is List && failures.isNotEmpty
      ? '\n${l10n.backgroundFailedSteps(failures.join(', '))}'
      : '';
  return '$status$timestamp$failedText';
}

class _SectionHeader extends StatelessWidget {
  const _SectionHeader({required this.title});

  final String title;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 24, 16, 8),
      child: Text(
        title,
        style: Theme.of(context).textTheme.titleSmall?.copyWith(
          color: Theme.of(context).colorScheme.primary,
        ),
      ),
    );
  }
}

/// Port of `SettingsActivity.SettingFragment.textSizeChanger` — a
/// single-choice dialog of the three text scales, applied immediately.
void _showTextSizeDialog(
  BuildContext context,
  AppLocalizations l10n,
  WidgetRef ref,
  double currentScale,
) {
  final scales = TextScaleNotifier.supportedScales;
  final labels = [l10n.textSizeSmall, l10n.textSizeMedium, l10n.textSizeLarge];

  showDialog<void>(
    context: context,
    builder: (context) => SimpleDialog(
      title: Text(l10n.selectTextSize),
      children: [
        RadioGroup<double>(
          groupValue: currentScale,
          onChanged: (value) {
            if (value != null) {
              ref.read(textScaleProvider.notifier).select(value);
            }
            if (context.mounted) Navigator.of(context).pop();
          },
          child: Column(
            children: [
              for (var i = 0; i < scales.length; i++)
                RadioListTile<double>(value: scales[i], title: Text(labels[i])),
            ],
          ),
        ),
        TextButton(
          onPressed: () => Navigator.of(context).pop(),
          child: Text(MaterialLocalizations.of(context).cancelButtonLabel),
        ),
      ],
    ),
  );
}

String _textScaleLabel(AppLocalizations l10n, double scale) {
  final scales = TextScaleNotifier.supportedScales;
  final i = scales.indexOf(scale);
  if (i == 0) return l10n.textSizeSmall;
  if (i == 2) return l10n.textSizeLarge;
  return l10n.textSizeMedium;
}

/// Port of `SettingsActivity.SettingFragment.clearDataButtonInit` — a
/// confirmation dialog that calls `clearAllData()` on "Yes". Guest users are
/// offered membership instead, matching the Kotlin's `guestDialog` gate.
Future<void> _confirmClearData(
  BuildContext context,
  AppLocalizations l10n,
  WidgetRef ref,
  UserRow? session,
) async {
  if (session != null && UserMapper.isGuest(session)) {
    showGuestDialog(context);
    return;
  }
  await showDialog<void>(
    context: context,
    builder: (context) => AlertDialog(
      title: Text(l10n.areYouSure),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(),
          child: Text(l10n.no),
        ),
        TextButton(
          onPressed: () {
            Navigator.of(context).pop();
            ref.read(clearDataProvider.notifier).clearAllData();
          },
          child: Text(l10n.yes),
        ),
      ],
    ),
  );
}
