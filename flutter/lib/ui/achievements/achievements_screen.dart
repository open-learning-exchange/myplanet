import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../../core/files/achievement_files.dart';
import '../../data/local/app_database.dart';
import '../../l10n/app_localizations.dart';
import '../../providers/achievements_provider.dart';
import '../../repository/achievements_repository.dart';
import 'cv_viewer_dialog.dart';
import '../router.dart';

/// Port of `ui/user/AchievementFragment.kt` — the "My Achievements" read
/// screen the profile tile opens. The row is one ledger per user; the
/// achievements and references lists render from its JSON columns the way
/// the Kotlin reads them off the entity.
class AchievementsScreen extends ConsumerWidget {
  const AchievementsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final state = ref.watch(achievementEntryProvider);
    return Scaffold(
      appBar: AppBar(
        title: Text(l10n.myAchievements),
        actions: [
          IconButton(
            icon: const Icon(Icons.edit),
            tooltip: l10n.edit,
            onPressed: () => context.push(Routes.editAchievement),
          ),
        ],
      ),
      body: state.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(child: Text('$e')),
        data: (row) {
          if (row == null) {
            return _EmptyAchievements(
              onEdit: () => context.push(Routes.editAchievement),
            );
          }
          return _AchievementsBody(row: row);
        },
      ),
    );
  }
}

class _EmptyAchievements extends StatelessWidget {
  const _EmptyAchievements({required this.onEdit});

  final VoidCallback onEdit;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(l10n.noAchievementAdded),
          const SizedBox(height: 12),
          FilledButton(onPressed: onEdit, child: Text(l10n.addAchievement)),
        ],
      ),
    );
  }
}

class _AchievementsBody extends StatelessWidget {
  const _AchievementsBody({required this.row});

  final AchievementRow row;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final achievements = AchievementsRepository.achievementsArray(
      row.achievementsJson,
    );
    final references = AchievementsRepository.referencesArray(
      row.referencesJson,
    );
    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 32),
      children: [
        _HeaderCard(
          label: l10n.myGoals,
          value: row.goals.isEmpty ? l10n.noGoalAdded : row.goals,
        ),
        const SizedBox(height: 8),
        _HeaderCard(
          label: l10n.myPurpose,
          value: row.purpose.isEmpty ? l10n.noPurposeAdded : row.purpose,
        ),
        const SizedBox(height: 8),
        _HeaderCard(
          label: l10n.achievements,
          value: row.achievementsHeader.isEmpty
              ? l10n.noAchievementAdded
              : row.achievementsHeader,
        ),
        const SizedBox(height: 16),
        Text(
          l10n.myAchievements,
          style: Theme.of(context).textTheme.titleMedium,
        ),
        const SizedBox(height: 8),
        if (achievements.isEmpty)
          Text(l10n.noAchievementAdded)
        else
          for (final entry in achievements) _AchievementCard(entry: entry),
        const SizedBox(height: 16),
        Text(l10n.references, style: Theme.of(context).textTheme.titleMedium),
        const SizedBox(height: 8),
        if (references.isEmpty)
          Text(l10n.noReferencesAdded)
        else
          for (final reference in references)
            _ReferenceCard(reference: reference),
        const SizedBox(height: 16),
        _CvCard(resumeFileName: row.resumeFileName),
      ],
    );
  }
}

class _HeaderCard extends StatelessWidget {
  const _HeaderCard({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(label, style: Theme.of(context).textTheme.labelLarge),
            const SizedBox(height: 4),
            Text(value),
          ],
        ),
      ),
    );
  }
}

class _AchievementCard extends StatelessWidget {
  const _AchievementCard({required this.entry});

  final Map<String, dynamic> entry;

  @override
  Widget build(BuildContext context) {
    final title = (entry['title'] as String?) ?? '';
    final description = (entry['description'] as String?) ?? '';
    final rawDate = (entry['date'] as String?) ?? '';
    // The Kotlin row formats ISO-8601 through `getFormattedDateWithTime` and
    // falls back to the raw value when the parse fails.
    String date = rawDate;
    final parsed = DateTime.tryParse(rawDate);
    if (parsed != null) {
      date = DateFormat.yMMMd().add_jm().format(parsed);
    }
    final link = (entry['link'] as String?) ?? '';
    final resources = AchievementsRepository.resourcesOf(entry);
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(title, style: Theme.of(context).textTheme.titleSmall),
            if (date.isNotEmpty) ...[
              const SizedBox(height: 2),
              Text(date, style: Theme.of(context).textTheme.bodySmall),
            ],
            if (description.isNotEmpty) ...[
              const SizedBox(height: 6),
              Text(description),
            ],
            if (link.isNotEmpty) ...[
              const SizedBox(height: 6),
              Text(link, style: Theme.of(context).textTheme.bodySmall),
            ],
            if (resources.isNotEmpty) ...[
              const SizedBox(height: 8),
              Wrap(
                spacing: 6,
                children: [
                  for (final resource in resources)
                    Chip(label: Text((resource['title'] as String?) ?? '')),
                ],
              ),
            ],
          ],
        ),
      ),
    );
  }
}

class _ReferenceCard extends StatelessWidget {
  const _ReferenceCard({required this.reference});

  final Map<String, dynamic> reference;

  @override
  Widget build(BuildContext context) {
    final name = (reference['name'] as String?) ?? '';
    final relationship = (reference['relationship'] as String?) ?? '';
    final phone = (reference['phone'] as String?) ?? '';
    final email = (reference['email'] as String?) ?? '';
    return Card(
      child: ListTile(
        title: Text(name),
        subtitle: Text(
          [
            relationship,
            phone,
            email,
          ].where((part) => part.isNotEmpty).join('\n'),
        ),
      ),
    );
  }
}

/// The Kotlin shows the CV card only when the named file is on disk
/// (`AchievementFragment.setupCv`) — a row that survived a sync to a device
/// without the bytes shows nothing, the way it would there.
class _CvCard extends StatefulWidget {
  const _CvCard({required this.resumeFileName});

  final String resumeFileName;

  @override
  State<_CvCard> createState() => _CvCardState();
}

class _CvCardState extends State<_CvCard> {
  bool _available = false;

  @override
  void initState() {
    super.initState();
    _check();
  }

  Future<void> _check() async {
    if (widget.resumeFileName.isEmpty) return;
    final available = await AchievementFiles.hasResume(widget.resumeFileName);
    if (mounted && available) setState(() => _available = true);
  }

  @override
  Widget build(BuildContext context) {
    if (widget.resumeFileName.isEmpty || !_available) {
      return const SizedBox.shrink();
    }
    final l10n = AppLocalizations.of(context);
    return Card(
      child: ListTile(
        leading: const Icon(Icons.picture_as_pdf),
        title: Text(l10n.cvResume),
        subtitle: Text(widget.resumeFileName),
        onTap: () => _open(context),
      ),
    );
  }

  Future<void> _open(BuildContext context) =>
      showCvViewerDialog(context, widget.resumeFileName);
}
