import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../data/local/app_database.dart';
import '../../l10n/app_localizations.dart';
import '../../providers/app_providers.dart';
import '../router.dart';

/// Port of `ui/community/CommunityServicesFragment.kt`.
///
/// Displays community services/links that navigate to other teams or external URLs.
class ServicesScreen extends ConsumerWidget {
  const ServicesScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final links = ref.watch(teamLinksStreamProvider);

    return Scaffold(
      body: links.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (_, _) => Center(child: Text(l10n.unavailable)),
        data: (rows) {
          if (rows.isEmpty) {
            return Center(child: Text(l10n.noServicesAvailable));
          }

          return ListView.builder(
            padding: const EdgeInsets.all(16),
            itemCount: rows.length,
            itemBuilder: (context, index) {
              final team = rows[index];
              final title = team.title ?? team.name ?? l10n.untitledTeam;
              final route = team.route ?? '';

              return Card(
                child: ListTile(
                  leading: const Icon(Icons.link),
                  title: Text(title),
                  trailing: const Icon(Icons.chevron_right),
                  onTap: () => _handleServiceTap(context, ref, route, title),
                ),
              );
            },
          );
        },
      ),
    );
  }

  void _handleServiceTap(
    BuildContext context,
    WidgetRef ref,
    String route,
    String title,
  ) {
    final l10n = AppLocalizations.of(context);
    if (route.startsWith('http://') || route.startsWith('https://')) {
      context.push(
        '${Routes.webView}?url=${Uri.encodeComponent(route)}&title=${Uri.encodeComponent(title)}',
      );
      return;
    }

    // Internal team route
    final segments = route.split('/');
    if (segments.length >= 4) {
      final teamId = segments[3];
      context.push('${Routes.teams}/$teamId');
    } else {
      // Fallback: try opening as external URL
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(l10n.openingResource(title))));
    }
  }
}

/// Provider for team links/services stream.
final teamLinksStreamProvider = StreamProvider<List<TeamRow>>((ref) {
  return ref.watch(teamsRepositoryProvider).watchTeamLinks();
});
