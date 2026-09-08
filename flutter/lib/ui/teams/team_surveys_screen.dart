import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../data/local/app_database.dart';
import '../../l10n/app_localizations.dart';
import '../../providers/app_providers.dart';
import '../../providers/session_provider.dart';
import '../../providers/team_surveys_provider.dart';
import '../../providers/teams_provider.dart';
import '../router.dart';
import '../surveys/send_survey_screen.dart';

/// Team-scoped port of `ui/surveys/SurveyFragment.kt`.
class TeamSurveysScreen extends ConsumerWidget {
  const TeamSurveysScreen({required this.teamId, super.key});

  final String teamId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final owned = ref.watch(teamOwnedSurveysProvider(teamId));
    final adoptable = ref.watch(teamAdoptableSurveysProvider(teamId));
    final team = ref.watch(teamProvider(teamId)).valueOrNull;
    final user = ref.watch(sessionProvider).valueOrNull;
    final userId = user?.id;
    final memberships =
        ref.watch(teamMembershipsProvider).valueOrNull ?? const {};
    final membership =
        memberships[teamId] ??
        (team?.teamId == null ? null : memberships[team!.teamId]);
    final canAdopt = membership?.isLeader == true;

    return Scaffold(
      appBar: AppBar(title: Text(l10n.teamSurveys)),
      body: RefreshIndicator(
        onRefresh: () async {
          ref.invalidate(teamOwnedSurveysProvider(teamId));
          ref.invalidate(teamAdoptableSurveysProvider(teamId));
        },
        child: ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.all(12),
          children: [
            _SurveySection(
              title: l10n.teamSurveys,
              rows: owned,
              emptyText: l10n.noSurveys,
              itemBuilder: (survey) => _SurveyTile(
                survey: survey,
                trailing: Wrap(
                  spacing: 4,
                  children: [
                    IconButton(
                      tooltip: l10n.sendSurvey,
                      icon: const Icon(Icons.send),
                      onPressed: () => showDialog<void>(
                        context: context,
                        // The survey on the card, never its source. Kotlin
                        // hands the bound exam straight through —
                        // `listener?.sendSurvey(current.exam)`
                        // (`SurveysAdapter:63-66`) ->
                        // `b.putString("surveyId", current?.id)`
                        // (`DashboardActivity:1008-1014`) ->
                        // `createBulkSurveySubmissions` — and an adopted team
                        // survey always has a `sourceSurveyId`, so preferring
                        // it sent every member a sheet keyed to the
                        // *un-adopted original*, so their answers filed
                        // against the source survey rather than against the
                        // team's own copy — mis-attributed at the parent id,
                        // which is what identifies a survey's responses.
                        // `surveys_screen.dart` already passes `row.id`.
                        //
                        // Note what this does *not* fix, because an earlier
                        // draft of this comment claimed it did:
                        // `getOrCreateSurveySubmission` writes neither a
                        // `teamId` column nor a `parent` document
                        // (`submissions_repository.dart:683-695`), and the
                        // team's two readers need one or the other —
                        // `submissionsForTeam` is `byTeam` on the column and
                        // `_teamSubmissionSurveyIds` reads `parent._id`. So a
                        // bulk-created sheet is invisible to both under
                        // either id. This is right because Kotlin does it,
                        // not because it makes those readers work.
                        builder: (context) =>
                            SendSurveyScreen(surveyId: survey.id),
                      ),
                    ),
                    const Icon(Icons.chevron_right),
                  ],
                ),
                // The team travels with the tap. `TeamPagerAdapter` puts
                // `isTeam`/`teamId` into the surveys tab's arguments
                // (`:89-93`) and every sheet opened from it is attributed to
                // the team; the port pushed the bare survey route, so the
                // whole chain below — route, screen, `createSurveyDraft` —
                // had a `teamId` nothing ever filled.
                //
                // Written as a literal rather than through a helper on
                // purpose: `route_reachability_test`'s scanner reads
                // `'${Routes.x}/…'` literals out of `lib/` and skips
                // `router.dart`, so moving this into a builder there would
                // make the one navigation this phase adds the one navigation
                // that guard cannot see.
                onTap: () => context.push(
                  '${Routes.surveys}/${survey.id}'
                  '?teamId=${Uri.encodeQueryComponent(teamId)}',
                ),
              ),
            ),
            const SizedBox(height: 16),
            _SurveySection(
              title: l10n.adoptableSurveys,
              rows: adoptable,
              emptyText: l10n.noAdoptableSurveys,
              itemBuilder: (survey) => _SurveyTile(
                survey: survey,
                trailing: FilledButton.icon(
                  onPressed: canAdopt && userId != null
                      ? () async {
                          await ref
                              .read(surveysRepositoryProvider)
                              .adoptSurvey(
                                surveyId: survey.id,
                                userId: userId,
                                // The marker's `user.doc.name`, which Kotlin
                                // takes off the same resolved session model
                                // it takes the id from
                                // (`createUserJsonString:142-143`).
                                userName: user?.name,
                                teamId: teamId,
                                teamName: team?.name,
                                isTeam: true,
                              );
                          ref.invalidate(teamOwnedSurveysProvider(teamId));
                          ref.invalidate(teamAdoptableSurveysProvider(teamId));
                          if (context.mounted) {
                            ScaffoldMessenger.of(context).showSnackBar(
                              SnackBar(content: Text(l10n.surveyAdopted)),
                            );
                          }
                        }
                      : null,
                  icon: const Icon(Icons.add),
                  label: Text(l10n.adoptSurvey),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _SurveySection extends StatelessWidget {
  const _SurveySection({
    required this.title,
    required this.rows,
    required this.emptyText,
    required this.itemBuilder,
  });

  final String title;
  final AsyncValue<List<SurveyRow>> rows;
  final String emptyText;
  final Widget Function(SurveyRow survey) itemBuilder;

  @override
  Widget build(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      Text(title, style: Theme.of(context).textTheme.titleMedium),
      const SizedBox(height: 8),
      rows.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (_, _) => Text(AppLocalizations.of(context).surveysUnavailable),
        data: (surveys) => surveys.isEmpty
            ? Card(
                child: ListTile(
                  leading: const Icon(Icons.info_outline),
                  title: Text(emptyText),
                ),
              )
            : Column(children: surveys.map(itemBuilder).toList()),
      ),
    ],
  );
}

class _SurveyTile extends StatelessWidget {
  const _SurveyTile({required this.survey, this.trailing, this.onTap});

  final SurveyRow survey;
  final Widget? trailing;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return Card(
      child: ListTile(
        leading: const Icon(Icons.poll_outlined),
        title: Text(survey.name ?? l10n.untitledSurvey),
        subtitle: Text(survey.description ?? ''),
        trailing: trailing,
        onTap: onTap,
      ),
    );
  }
}
