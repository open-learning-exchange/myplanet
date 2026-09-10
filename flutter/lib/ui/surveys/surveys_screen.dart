import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../l10n/app_localizations.dart';
import '../../providers/surveys_provider.dart';
import '../../providers/sync_state.dart';
import '../router.dart';
import 'send_survey_screen.dart';

/// Port of `ui/surveys/SurveyFragment.kt` for individual offline surveys.
class SurveysScreen extends ConsumerWidget {
  const SurveysScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final surveys = ref.watch(surveysProvider);
    final sync = ref.watch(surveysSyncProvider);
    return Scaffold(
      appBar: AppBar(
        title: Text(l10n.mySurveys),
        actions: [
          IconButton(
            tooltip: l10n.syncSurveys,
            onPressed: sync is SyncRunning
                ? null
                : () => ref.read(surveysSyncProvider.notifier).sync(),
            icon: const Icon(Icons.sync),
          ),
        ],
        bottom: PreferredSize(
          preferredSize: Size.fromHeight(sync is SyncRunning ? 76 : 72),
          child: Column(
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(12, 0, 12, 8),
                child: Row(
                  children: [
                    Expanded(
                      child: SearchBar(
                        hintText: l10n.searchSurveys,
                        leading: const Icon(Icons.search),
                        onChanged: (value) =>
                            ref.read(surveySearchProvider.notifier).state =
                                value,
                      ),
                    ),
                    PopupMenuButton<SurveySort>(
                      tooltip: l10n.sortSurveys,
                      onSelected: (value) =>
                          ref.read(surveySortProvider.notifier).state = value,
                      itemBuilder: (_) => [
                        PopupMenuItem(
                          value: SurveySort.newest,
                          child: Text(l10n.newestFirst),
                        ),
                        PopupMenuItem(
                          value: SurveySort.oldest,
                          child: Text(l10n.oldestFirst),
                        ),
                        PopupMenuItem(
                          value: SurveySort.titleAscending,
                          child: Text(l10n.titleAscending),
                        ),
                        PopupMenuItem(
                          value: SurveySort.titleDescending,
                          child: Text(l10n.titleDescending),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
              if (sync is SyncRunning)
                LinearProgressIndicator(
                  value: sync.progress.total == 0
                      ? null
                      : sync.progress.fraction,
                ),
            ],
          ),
        ),
      ),
      body: RefreshIndicator(
        onRefresh: () => ref.read(surveysSyncProvider.notifier).sync(),
        child: surveys.when(
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (_, _) => Center(child: Text(l10n.surveysUnavailable)),
          data: (rows) => ListView.separated(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.all(12),
            itemCount: rows.isEmpty ? 1 : rows.length,
            separatorBuilder: (_, _) => const SizedBox(height: 6),
            itemBuilder: (context, index) {
              if (rows.isEmpty) {
                return SizedBox(
                  height: 300,
                  child: Center(child: Text(l10n.noSurveys)),
                );
              }
              final row = rows[index];
              return Card(
                child: ListTile(
                  leading: const Icon(Icons.poll_outlined),
                  title: Text(row.name ?? l10n.untitledSurvey),
                  subtitle: Text(row.description ?? ''),
                  trailing: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      IconButton(
                        icon: const Icon(Icons.send),
                        tooltip: l10n.sendSurvey,
                        onPressed: () {
                          showDialog(
                            context: context,
                            builder: (context) =>
                                SendSurveyScreen(surveyId: row.id),
                          );
                        },
                      ),
                      const Icon(Icons.chevron_right),
                    ],
                  ),
                  onTap: () => context.push('${Routes.surveys}/${row.id}'),
                ),
              );
            },
          ),
        ),
      ),
    );
  }
}
