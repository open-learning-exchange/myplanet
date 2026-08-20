import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

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
              final session = ref.read(sessionProvider).valueOrNull;
              if (session != null && session.id.startsWith('guest')) {
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
