import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../l10n/app_localizations.dart';
import '../../providers/app_providers.dart';

/// Port of `ui/community/LeadersFragment.kt` and `CommunityLeadersAdapter`.
///
/// Displays community/nation leaders in a grid, parsed from the JSON stored
/// in [PlanetPrefs.communityLeaders].
class LeadersScreen extends ConsumerWidget {
  const LeadersScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final leaders = _parseLeaders(ref);

    if (leaders.isEmpty) {
      return Scaffold(
        body: Center(child: Text(l10n.noLeadersAvailable)),
      );
    }

    return Scaffold(
      body: GridView.builder(
        padding: const EdgeInsets.all(16),
        gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
          crossAxisCount: 2,
          crossAxisSpacing: 12,
          mainAxisSpacing: 12,
          childAspectRatio: 1.5,
        ),
        itemCount: leaders.length,
        itemBuilder: (context, index) => LeaderCard(leader: leaders[index]),
      ),
    );
  }

  List<_LeaderInfo> _parseLeaders(WidgetRef ref) {
    final json = ref.read(planetPrefsProvider).communityLeaders;
    if (json.isEmpty) return [];

    try {
      final doc = jsonDecode(json) as Map<String, dynamic>;
      final docs = doc['docs'] as List<dynamic>? ?? [];
      return docs.map((d) {
        final map = d as Map<String, dynamic>;
        return _LeaderInfo(
          id: (map['_id'] as String?) ?? 'org.couchdb.user:${map['name'] ?? ''}',
          name: map['name'] as String? ?? '',
          firstName: map['firstName'] as String?,
          lastName: map['lastName'] as String?,
          email: map['email'] as String?,
        );
      }).toList();
    } catch (_) {
      return [];
    }
  }
}

/// Minimal leader info parsed from the community leaders JSON.
/// Mirrors the fields `LeadersFragment` reads from `UserEntity`.
class _LeaderInfo {
  final String id;
  final String name;
  final String? firstName;
  final String? lastName;
  final String? email;

  const _LeaderInfo({
    required this.id,
    required this.name,
    this.firstName,
    this.lastName,
    this.email,
  });

  String get displayName {
    if (firstName != null || lastName != null) {
      return '${firstName ?? ''} ${lastName ?? ''}'.trim();
    }
    return name;
  }
}

/// One leader card in the grid. Mirrors `RowJoinedUserBinding` from the
/// Kotlin adapter.
class LeaderCard extends StatelessWidget {
  const LeaderCard({required this.leader, super.key});

  final _LeaderInfo leader;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Card(
      child: InkWell(
        onTap: () => _showLeaderDetails(context),
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              CircleAvatar(
                radius: 28,
                backgroundColor: theme.colorScheme.primaryContainer,
                child: Text(
                  leader.displayName.isNotEmpty
                      ? leader.displayName[0].toUpperCase()
                      : '?',
                  style: theme.textTheme.titleLarge?.copyWith(
                    color: theme.colorScheme.onPrimaryContainer,
                  ),
                ),
              ),
              const SizedBox(height: 8),
              Text(
                leader.displayName,
                style: theme.textTheme.titleSmall,
                textAlign: TextAlign.center,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
              if (leader.email != null && leader.email!.isNotEmpty)
                Text(
                  leader.email!,
                  style: theme.textTheme.bodySmall,
                  textAlign: TextAlign.center,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
            ],
          ),
        ),
      ),
    );
  }

  void _showLeaderDetails(BuildContext context) {
    showModalBottomSheet(
      context: context,
      builder: (context) => Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            CircleAvatar(
              radius: 40,
              child: Text(
                leader.displayName.isNotEmpty
                    ? leader.displayName[0].toUpperCase()
                    : '?',
              ),
            ),
            const SizedBox(height: 16),
            Text(
              leader.displayName,
              style: Theme.of(context).textTheme.headlineSmall,
            ),
            if (leader.email != null && leader.email!.isNotEmpty) ...[
              const SizedBox(height: 8),
              Text(leader.email!),
            ],
            const SizedBox(height: 24),
          ],
        ),
      ),
    );
  }
}
