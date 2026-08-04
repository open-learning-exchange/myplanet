import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../l10n/app_localizations.dart';
import '../../providers/app_providers.dart';
import '../../providers/settings_provider.dart';
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
            onTap: () => context.push(Routes.storageManagement),
          ),
          const Divider(),
          _SectionHeader(title: l10n.about),
          ListTile(
            leading: const Icon(Icons.info_outline),
            title: Text(l10n.appTitle),
            subtitle: Text(l10n.offlineLearningApp),
          ),
        ],
      ),
    );
  }
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
