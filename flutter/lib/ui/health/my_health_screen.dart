import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../data/local/app_database.dart';
import '../../data/local/health_models.dart';
import '../../l10n/app_localizations.dart';
import '../../providers/app_providers.dart';
import '../../providers/health_provider.dart';
import '../../providers/sync_state.dart';
import '../../repository/personals_uploader.dart';
import '../components/profile_avatar.dart';
import '../router.dart';

/// Port of `ui/health/MyHealthFragment.kt`.
///
/// Displays the current user's health profile with vital signs and examination
/// history. Health providers can also select other patients.
class MyHealthScreen extends ConsumerWidget {
  const MyHealthScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final detail = ref.watch(patientDetailProvider);
    final syncState = ref.watch(healthSyncProvider);
    final isHealthProvider =
        ref.watch(isHealthProviderProvider).valueOrNull ?? false;
    // Both editors take the *selected* patient, as `MyHealthFragment` passes
    // it (`putExtra("userId", userId)`); without it they fell back to the
    // signed-in user, so a health provider read and wrote their own record.
    final patientId = detail.user == null ? null : patientIdOf(detail.user!);
    final patientQuery = patientId == null || patientId.isEmpty
        ? ''
        : '?userId=${Uri.encodeQueryComponent(patientId)}';

    return Scaffold(
      appBar: AppBar(
        title: Text(l10n.myHealth),
        actions: [
          IconButton(
            icon: const Icon(Icons.sync),
            tooltip: l10n.sync,
            onPressed: syncState is SyncRunning
                ? null
                : () async {
                    await ref.read(healthSyncProvider.notifier).sync();
                    // `refresh`, not `invalidate`: see
                    // `PatientDetailNotifier.refresh` — invalidating rebuilds
                    // the notifier and resets a provider's chosen patient.
                    await ref.read(patientDetailProvider.notifier).refresh();
                  },
          ),
          IconButton(
            icon: const Icon(Icons.edit),
            tooltip: l10n.updateHealth,
            onPressed: () => context.push('${Routes.addHealth}$patientQuery'),
          ),
        ],
      ),
      body: detail.isLoading
          ? const Center(child: CircularProgressIndicator())
          : _buildBody(context, ref, detail, l10n),
      // `btnnewPatient` is the health provider's own button and `addNewRecord`
      // is everyone's — the layout carries both, so a provider who has just
      // picked a patient can record an examination for them. Offering one
      // *or* the other left the health role, whose whole purpose is recording
      // other people's examinations, with no way to record one.
      floatingActionButton: Column(
        mainAxisAlignment: MainAxisAlignment.end,
        crossAxisAlignment: CrossAxisAlignment.end,
        children: [
          if (isHealthProvider) ...[
            FloatingActionButton.extended(
              heroTag: 'health-new-patient',
              onPressed: () => _showPatientPicker(context),
              icon: const Icon(Icons.person_search),
              label: Text(l10n.newPatient),
            ),
            const SizedBox(height: 12),
          ],
          FloatingActionButton.extended(
            heroTag: 'health-add-record',
            onPressed: () =>
                context.push('${Routes.addExamination}$patientQuery'),
            icon: const Icon(Icons.add),
            label: Text(l10n.addHealthRecord),
          ),
        ],
      ),
    );
  }

  Widget _buildBody(
    BuildContext context,
    WidgetRef ref,
    PatientDetailState detail,
    AppLocalizations l10n,
  ) {
    final user = detail.user;
    final record = detail.record;
    if (user == null) {
      // The banner rides above this branch as well: a record the server refused
      // is a device-level fact, and "no patient resolves" is exactly the state
      // a clinician might be in while wondering where a reading went.
      return ListView(
        padding: const EdgeInsets.all(16),
        children: [
          const _RejectedUploadsBanner(),
          Center(child: Text(l10n.healthRecordNotAvailable)),
        ],
      );
    }
    final data = HealthData(
      user: user,
      examination: record?.healthPojo,
      myHealth: record?.healthProfile,
      examinations: record?.examinations ?? const [],
      userMap: record?.userMap ?? const {},
      createdByOf: record?.createdByOf ?? const {},
    );
    return _HealthContent(data: data);
  }

  void _showPatientPicker(BuildContext context) {
    showDialog<void>(
      context: context,
      builder: (dialogContext) => const _PatientPickerDialog(),
    );
  }
}

class _HealthContent extends ConsumerWidget {
  const _HealthContent({required this.data});
  final HealthData data;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final user = data.user;
    final examination = data.examination;
    final myHealth = data.myHealth;
    final examinations = data.examinations;

    if (user == null) {
      return Center(child: Text(l10n.healthRecordNotAvailable));
    }

    return RefreshIndicator(
      // Kotlin's `MyHealthFragment` has no `SwipeRefreshLayout`, so this
      // gesture is the port's own addition — which is why its handler was
      // left empty, advertising a refresh that did nothing. Re-reading the
      // selected patient is what the affordance already promises.
      onRefresh: () async {
        // The banner is a `FutureProvider`, so the pull that re-reads the
        // patient is also what re-reads whether anything is still stranded.
        ref.invalidate(rejectedHealthRecordCountProvider);
        await ref.read(patientDetailProvider.notifier).refresh();
      },
      child: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          const _RejectedUploadsBanner(),
          // User profile card
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      // `users.userImage` is a CouchDB attachment *name*, not a
                      // URL, and the attachment sits behind Basic auth — this
                      // used to hand the bare name to `NetworkImage`, so the
                      // photo could never load. `ProfileAvatar` resolves it
                      // through the authenticated bytes path (and handles a
                      // locally-picked file path), falling back to initials.
                      ProfileAvatar(user: user, radius: 32),
                      const SizedBox(width: 16),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              displayName(user),
                              style: Theme.of(context).textTheme.titleLarge,
                            ),
                            if (user.email != null)
                              Text(
                                user.email!,
                                style: Theme.of(context).textTheme.bodyMedium,
                              ),
                          ],
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 16),
                  const Divider(),
                  const SizedBox(height: 8),
                  _InfoRow(label: l10n.birthDate, value: user.dob ?? '-'),
                  if (user.birthPlace != null && user.birthPlace!.isNotEmpty)
                    _InfoRow(label: l10n.birthPlace, value: user.birthPlace!),
                  if (user.language != null && user.language!.isNotEmpty)
                    _InfoRow(label: l10n.language, value: user.language!),
                  if (user.gender != null)
                    _InfoRow(label: l10n.gender, value: user.gender!),
                  if (user.phoneNumber != null)
                    _InfoRow(label: l10n.phone, value: user.phoneNumber!),
                ],
              ),
            ),
          ),

          // Health profile card
          if (myHealth?.profile != null) ...[
            const SizedBox(height: 16),
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      l10n.healthProfile,
                      style: Theme.of(context).textTheme.titleMedium,
                    ),
                    const SizedBox(height: 12),
                    _InfoRow(
                      label: l10n.specialNeeds,
                      value: myHealth!.profile!.specialNeeds.isEmpty
                          ? '-'
                          : myHealth.profile!.specialNeeds,
                    ),
                    _InfoRow(
                      label: l10n.otherNeeds,
                      value: myHealth.profile!.notes.isEmpty
                          ? '-'
                          : myHealth.profile!.notes,
                    ),
                    if (myHealth.profile!.emergencyContactName.isNotEmpty)
                      _InfoRow(
                        label: l10n.emergencyContact,
                        value:
                            '${myHealth.profile!.emergencyContactName} (${myHealth.profile!.emergencyContactType})',
                      ),
                    if (myHealth.profile!.emergencyContact.isNotEmpty)
                      _InfoRow(
                        label: l10n.contactNumber,
                        value: myHealth.profile!.emergencyContact,
                      ),
                  ],
                ),
              ),
            ),
          ],

          // Current vital signs
          if (examination != null) ...[
            const SizedBox(height: 16),
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      l10n.vitalSigns,
                      style: Theme.of(context).textTheme.titleMedium,
                    ),
                    const SizedBox(height: 12),
                    Wrap(
                      spacing: 24,
                      runSpacing: 12,
                      children: [
                        _VitalSignChip(
                          icon: Icons.thermostat,
                          label: l10n.temperature,
                          value: '${examination.temperature}°C',
                        ),
                        _VitalSignChip(
                          icon: Icons.favorite,
                          label: l10n.pulse,
                          value: '${examination.pulse} bpm',
                        ),
                        _VitalSignChip(
                          icon: Icons.straighten,
                          label: l10n.height,
                          value: '${examination.height} cm',
                        ),
                        _VitalSignChip(
                          icon: Icons.monitor_weight,
                          label: l10n.weight,
                          value: '${examination.weight} kg',
                        ),
                        if (examination.bp != null &&
                            examination.bp!.isNotEmpty)
                          _VitalSignChip(
                            icon: Icons.bloodtype,
                            label: l10n.bloodPressure,
                            value: examination.bp!,
                          ),
                        if (examination.vision != null &&
                            examination.vision!.isNotEmpty)
                          _VitalSignChip(
                            icon: Icons.visibility,
                            label: l10n.vision,
                            value: examination.vision!,
                          ),
                        if (examination.hearing != null &&
                            examination.hearing!.isNotEmpty)
                          _VitalSignChip(
                            icon: Icons.hearing,
                            label: l10n.hearing,
                            value: examination.hearing!,
                          ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
          ],

          // Examination history
          if (examinations.isNotEmpty) ...[
            const SizedBox(height: 16),
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      l10n.examinationHistory,
                      style: Theme.of(context).textTheme.titleMedium,
                    ),
                    const SizedBox(height: 12),
                    SizedBox(
                      // A horizontal list has to be given a bounded height, so
                      // this cannot size to its content. 140 was 8px short of
                      // an examination carrying date, examiner, temperature,
                      // pulse, blood pressure *and* the has-info icon, which
                      // struck a RenderFlex overflow — a yellow-and-black
                      // stripe on a device. Sized for that fullest card with
                      // headroom; the card body scrolls if a large text scale
                      // still outgrows it.
                      height: 168,
                      child: ListView.separated(
                        scrollDirection: Axis.horizontal,
                        itemCount: examinations.length,
                        separatorBuilder: (context, index) =>
                            const SizedBox(width: 8),
                        itemBuilder: (context, index) {
                          final exam = examinations[index];
                          return _ExaminationCard(
                            exam: exam,
                            userId: data.user?.id ?? '',
                            userMap: data.userMap,
                            createdBy: data.createdByOf[exam.id],
                            profileRowId: data.examination?.id,
                          );
                        },
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ],

          const SizedBox(height: 80), // Space for FAB
        ],
      ),
    );
  }
}

/// The examiner's display name for [createdBy].
///
/// A known user renders as their name; an unknown creator id falls back to the
/// text after the `:` (`org.couchdb.user:provider-1` reads as `provider-1`),
/// and then to the raw id. Kept as one function because the card and its
/// detail dialog both need it and had identical copies.
String resolveCreatorName(String createdBy, Map<String, UserRow> userMap) {
  final user = userMap[createdBy];
  if (user != null) {
    final parts = [user.firstName, user.middleName, user.lastName]
        .whereType<String>()
        .map((part) => part.trim())
        .where((part) => part.isNotEmpty);
    final fullName = parts.join(' ');
    if (fullName.isNotEmpty) return fullName;
    return user.name ?? createdBy;
  }
  final colonIndex = createdBy.indexOf(':');
  if (colonIndex >= 0 && colonIndex + 1 < createdBy.length) {
    return createdBy.substring(colonIndex + 1).trim();
  }
  return createdBy;
}

/// Says so when the server has permanently refused a health record.
///
/// The port's own addition — Kotlin has no counterpart, because it has no
/// permanent-failure state to report: `uploadHealthData` swallows the response
/// and leaves `isUpdated` set, so the next sync tries again forever. The outbox
/// classifies a 409 as permanent instead, which is a better model of what a
/// conflict means and a worse one to keep silent about. A reading that never
/// reached the server, on a screen that shows it as recorded, is the failure
/// this app can least afford.
class _RejectedUploadsBanner extends ConsumerWidget {
  const _RejectedUploadsBanner();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final count = ref.watch(rejectedHealthRecordCountProvider).valueOrNull ?? 0;
    if (count == 0) return const SizedBox.shrink();

    final theme = Theme.of(context);
    final l10n = AppLocalizations.of(context);
    return Card(
      color: theme.colorScheme.errorContainer,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Row(
          children: [
            Icon(Icons.cloud_off, color: theme.colorScheme.onErrorContainer),
            const SizedBox(width: 12),
            Expanded(
              child: Text(
                l10n.healthRecordsRejected(count),
                style: theme.textTheme.bodyMedium?.copyWith(
                  color: theme.colorScheme.onErrorContainer,
                ),
              ),
            ),
            // Without this the banner names a problem and offers nothing to do
            // about it. Kotlin re-attempts every failed health upload on every
            // sync (`UploadToShelfService.uploadHealth`, run from
            // `AutoSyncWorker`); the port re-queues only from the examination
            // form's own save, so a stranded record has no other way back onto
            // the wire. `enqueue` ignores the abandoned row and writes a fresh
            // pending one, so this is a real retry rather than a nudge.
            TextButton(onPressed: () => _retry(ref), child: Text(l10n.retry)),
          ],
        ),
      ),
    );
  }

  Future<void> _retry(WidgetRef ref) async {
    await ref.read(healthQueueProvider).queuePending();
    final config = ref.read(serverConfigProvider);
    if (config != null) {
      await ref
          .read(outboxDrainerProvider)
          .drain(authHeader: PersonalsUploader.authHeaderFor(config));
    }
    ref.invalidate(rejectedHealthRecordCountProvider);
  }
}

class _InfoRow extends StatelessWidget {
  const _InfoRow({required this.label, required this.value});
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 120,
            child: Text(
              label,
              style: Theme.of(context).textTheme.bodySmall?.copyWith(
                color: Theme.of(context).colorScheme.outline,
              ),
            ),
          ),
          Expanded(child: Text(value)),
        ],
      ),
    );
  }
}

class _VitalSignChip extends StatelessWidget {
  const _VitalSignChip({
    required this.icon,
    required this.label,
    required this.value,
  });
  final IconData icon;
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.surfaceContainerHighest,
        borderRadius: BorderRadius.circular(8),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 16),
          const SizedBox(width: 4),
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(label, style: Theme.of(context).textTheme.labelSmall),
              Text(
                value,
                style: Theme.of(
                  context,
                ).textTheme.bodyMedium?.copyWith(fontWeight: FontWeight.bold),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _ExaminationCard extends ConsumerWidget {
  const _ExaminationCard({
    required this.exam,
    required this.userId,
    required this.userMap,
    this.createdBy,
    this.profileRowId,
  });
  final HealthExaminationRow exam;
  final String userId;
  final Map<String, UserRow> userMap;

  /// The examiner from the record's decrypted `data`, not the `creatorId`
  /// column — see [HealthRecord.createdByOf].
  final String? createdBy;

  /// The patient's profile row id, which the Edit action passes on as the
  /// examination form's patient: `showAlert`'s Edit is
  /// `putExtra("userId", mh._id)`, the id that row was created under. The
  /// user row's `id` is not interchangeable with it — for a member registered
  /// on this device the two differ, and the form would then mint a second
  /// profile row under a new key and drop the examination it was editing out
  /// of the patient's record.
  final String? profileRowId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final date = exam.date > 0
        ? DateTime.fromMillisecondsSinceEpoch(exam.date)
        : null;

    final isSelfExam =
        createdBy == null || createdBy!.isEmpty || createdBy == userId;
    final creatorName = isSelfExam
        ? l10n.selfExamination
        : resolveCreatorName(createdBy!, userMap);

    final isDark = Theme.of(context).brightness == Brightness.dark;
    final selfColor = isDark
        ? Theme.of(context).colorScheme.surfaceContainerHighest
        : const Color(0xFFE8F5E9);
    final providerColor = isDark
        ? Theme.of(context).colorScheme.surfaceContainerLow
        : const Color(0xFFFAFAFA);

    return InkWell(
      borderRadius: BorderRadius.circular(8),
      onTap: () => _showDetail(context, ref),
      child: Container(
        width: 160,
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: isSelfExam ? selfColor : providerColor,
          border: Border.all(color: Theme.of(context).colorScheme.outline),
          borderRadius: BorderRadius.circular(8),
        ),
        // The strip's height is fixed, so a text scale larger than the one the
        // height was measured against would overflow the card. Scrolling the
        // body degrades that into a scrollable card instead of a striped one.
        child: SingleChildScrollView(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                date != null
                    ? '${date.day}/${date.month}/${date.year}'
                    : l10n.unknown,
                style: Theme.of(context).textTheme.labelMedium,
              ),
              const SizedBox(height: 4),
              Text(
                creatorName,
                style: Theme.of(context).textTheme.labelSmall?.copyWith(
                  color: Theme.of(context).colorScheme.outline,
                ),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
              const SizedBox(height: 6),
              if (exam.temperature > 0)
                Text(
                  '${l10n.temp}: ${exam.temperature}°C',
                  style: Theme.of(context).textTheme.bodySmall,
                ),
              if (exam.pulse > 0)
                Text(
                  '${l10n.pulse}: ${exam.pulse} bpm',
                  style: Theme.of(context).textTheme.bodySmall,
                ),
              if (exam.bp != null && exam.bp!.isNotEmpty)
                Text(
                  '${l10n.bp}: ${exam.bp}',
                  style: Theme.of(context).textTheme.bodySmall,
                ),
              if (exam.hasInfo)
                Padding(
                  padding: const EdgeInsets.only(top: 4),
                  child: Icon(
                    Icons.info_outline,
                    size: 16,
                    color: Theme.of(context).colorScheme.primary,
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }

  void _showDetail(BuildContext context, WidgetRef ref) {
    showDialog<void>(
      context: context,
      builder: (_) => _ExaminationDetailDialog(
        exam: exam,
        userId: userId,
        userMap: userMap,
        createdBy: createdBy,
        profileRowId: profileRowId,
      ),
    );
  }
}

/// Detail dialog for a single examination — port of
/// `HealthExaminationAdapter.showAlert`.
///
/// Shows the vitals, the checked conditions, the encrypted notes/diagnosis/
/// medications/etc. (decrypted with the patient's key/iv), and an Edit button
/// that opens the examination form pre-loaded with the row.
class _ExaminationDetailDialog extends ConsumerWidget {
  const _ExaminationDetailDialog({
    required this.exam,
    required this.userId,
    required this.userMap,
    this.createdBy,
    this.profileRowId,
  });
  final HealthExaminationRow exam;
  final String userId;
  final Map<String, UserRow> userMap;

  /// See [HealthRecord.createdByOf].
  final String? createdBy;

  /// See [_ExaminationCard.profileRowId].
  final String? profileRowId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final date = exam.date > 0
        ? DateTime.fromMillisecondsSinceEpoch(exam.date)
        : null;
    final dateText = date != null
        ? '${date.day}/${date.month}/${date.year}'
        : l10n.unknown;

    final isSelfExam =
        createdBy == null || createdBy!.isEmpty || createdBy == userId;
    final creatorName = isSelfExam
        ? l10n.selfExamination
        : resolveCreatorName(createdBy!, userMap);

    final title = '$dateText — $creatorName';

    final detailAsync = ref.watch(
      examinationDetailProvider((userId: userId, examId: exam.id)),
    );

    final conditions = ref
        .read(healthRepositoryProvider)
        .parseConditions(exam.conditions);

    return AlertDialog(
      title: Text(title),
      content: SizedBox(
        width: double.maxFinite,
        child: SingleChildScrollView(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                l10n.vitalSigns,
                style: Theme.of(context).textTheme.titleSmall,
              ),
              const SizedBox(height: 8),
              _VitalRow(
                label: l10n.temperature,
                value: _formatDouble(exam.temperature),
              ),
              _VitalRow(label: l10n.pulse, value: _formatInt(exam.pulse)),
              _VitalRow(label: l10n.bloodPressure, value: exam.bp),
              _VitalRow(label: l10n.height, value: _formatDouble(exam.height)),
              _VitalRow(label: l10n.weight, value: _formatDouble(exam.weight)),
              _VitalRow(label: l10n.vision, value: exam.vision),
              _VitalRow(label: l10n.hearing, value: exam.hearing),
              if (conditions.entries.any((e) => e.value)) ...[
                const SizedBox(height: 12),
                Text(
                  l10n.conditions,
                  style: Theme.of(context).textTheme.titleSmall,
                ),
                const SizedBox(height: 4),
                Wrap(
                  spacing: 8,
                  runSpacing: 4,
                  children: conditions.entries
                      .where((e) => e.value)
                      .map((e) => Chip(label: Text(e.key)))
                      .toList(),
                ),
              ],
              const SizedBox(height: 12),
              Text(
                l10n.examinationDetails,
                style: Theme.of(context).textTheme.titleSmall,
              ),
              const SizedBox(height: 8),
              switch (detailAsync) {
                AsyncData(:final value) when value != null => _EncryptedFields(
                  exam: value,
                  l10n: l10n,
                ),
                AsyncLoading() => const Center(
                  child: Padding(
                    padding: EdgeInsets.all(16),
                    child: CircularProgressIndicator(),
                  ),
                ),
                _ => Padding(
                  padding: const EdgeInsets.symmetric(vertical: 8),
                  child: Text(
                    l10n.healthRecordNotAvailable,
                    style: Theme.of(context).textTheme.bodySmall,
                  ),
                ),
              },
            ],
          ),
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(),
          child: Text(l10n.ok),
        ),
        TextButton(
          onPressed: () {
            Navigator.of(context).pop();
            context.push(
              '${Routes.addExamination}?id=${Uri.encodeQueryComponent(exam.id)}'
              '&userId=${Uri.encodeQueryComponent(profileRowId ?? userId)}',
            );
          },
          child: Text(l10n.edit),
        ),
      ],
    );
  }

  String _formatDouble(double v) => v == 0 ? '' : v.toString();
  String _formatInt(int v) => v == 0 ? '' : v.toString();
}

class _VitalRow extends StatelessWidget {
  const _VitalRow({required this.label, required this.value});
  final String label;
  final String? value;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final display = (value == null || value!.isEmpty) ? l10n.nA : value!;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 2),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 120,
            child: Text(
              label,
              style: Theme.of(context).textTheme.bodySmall?.copyWith(
                color: Theme.of(context).colorScheme.outline,
              ),
            ),
          ),
          Expanded(child: Text(display)),
        ],
      ),
    );
  }
}

class _EncryptedFields extends StatelessWidget {
  const _EncryptedFields({required this.exam, required this.l10n});
  final Examination exam;
  final AppLocalizations l10n;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _DetailRow(label: l10n.observations, value: exam.notes),
        _DetailRow(label: l10n.diagnosis, value: exam.diagnosis),
        _DetailRow(label: l10n.treatments, value: exam.treatments),
        _DetailRow(label: l10n.medications, value: exam.medications),
        _DetailRow(label: l10n.immunizations, value: exam.immunizations),
        _DetailRow(label: l10n.allergies, value: exam.allergies),
        _DetailRow(label: l10n.xrays, value: exam.xrays),
        _DetailRow(label: l10n.labTests, value: exam.tests),
        _DetailRow(label: l10n.referrals, value: exam.referrals),
      ],
    );
  }
}

class _DetailRow extends StatelessWidget {
  const _DetailRow({required this.label, required this.value});
  final String label;
  final String? value;

  @override
  Widget build(BuildContext context) {
    if (value == null || value!.isEmpty) return const SizedBox.shrink();
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 2),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 120,
            child: Text(
              label,
              style: Theme.of(context).textTheme.bodySmall?.copyWith(
                color: Theme.of(context).colorScheme.outline,
              ),
            ),
          ),
          Expanded(child: Text(value!)),
        ],
      ),
    );
  }
}

/// Patient selection dialog for health providers — port of the
/// `selectPatient()` / `setTextWatcher()` / `sortList()` flow in Kotlin's
/// `MyHealthFragment`.
class _PatientPickerDialog extends ConsumerStatefulWidget {
  const _PatientPickerDialog();

  @override
  ConsumerState<_PatientPickerDialog> createState() =>
      _PatientPickerDialogState();
}

class _PatientPickerDialogState extends ConsumerState<_PatientPickerDialog> {
  final _searchController = TextEditingController();
  PatientSort _sort = PatientSort.joinDateDesc;
  bool _searching = false;

  @override
  void initState() {
    super.initState();
    _searchController.addListener(_onSearchChanged);
  }

  @override
  void dispose() {
    _searchController.removeListener(_onSearchChanged);
    _searchController.dispose();
    super.dispose();
  }

  Timer? _debounce;

  void _onSearchChanged() {
    _debounce?.cancel();
    _debounce = Timer(const Duration(milliseconds: 300), () {
      if (!mounted) return;
      final query = _searchController.text;
      if (query.isEmpty) {
        ref.read(patientListProvider.notifier).sort(_sort);
      } else {
        setState(() => _searching = true);
        ref.read(patientListProvider.notifier).search(query, sort: _sort).then((
          _,
        ) {
          if (mounted) setState(() => _searching = false);
        });
      }
    });
  }

  void _onSortChanged(PatientSort? sort) {
    if (sort == null) return;
    setState(() => _sort = sort);
    if (_searchController.text.isEmpty) {
      ref.read(patientListProvider.notifier).sort(sort);
    } else {
      ref
          .read(patientListProvider.notifier)
          .search(_searchController.text, sort: sort);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final patientsAsync = ref.watch(patientListProvider);

    return AlertDialog(
      title: Text(l10n.selectHealthMember),
      content: SizedBox(
        width: double.maxFinite,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
              controller: _searchController,
              decoration: InputDecoration(
                hintText: l10n.searchMembers,
                prefixIcon: const Icon(Icons.search),
                suffixIcon: _searching
                    ? const SizedBox(
                        width: 20,
                        height: 20,
                        child: Padding(
                          padding: EdgeInsets.all(12),
                          child: CircularProgressIndicator(strokeWidth: 2),
                        ),
                      )
                    : null,
              ),
            ),
            const SizedBox(height: 8),
            DropdownButton<PatientSort>(
              value: _sort,
              isExpanded: true,
              items: [
                DropdownMenuItem(
                  value: PatientSort.joinDateDesc,
                  child: Text(l10n.sortJoinDateDesc),
                ),
                DropdownMenuItem(
                  value: PatientSort.joinDateAsc,
                  child: Text(l10n.sortJoinDateAsc),
                ),
                DropdownMenuItem(
                  value: PatientSort.nameAsc,
                  child: Text(l10n.sortNameAsc),
                ),
                DropdownMenuItem(
                  value: PatientSort.nameDesc,
                  child: Text(l10n.sortNameDesc),
                ),
              ],
              onChanged: _onSortChanged,
            ),
            const SizedBox(height: 8),
            Flexible(
              child: patientsAsync.when(
                loading: () => const SizedBox(
                  height: 200,
                  child: Center(child: CircularProgressIndicator()),
                ),
                error: (e, _) => SizedBox(
                  height: 200,
                  child: Center(child: Text('${l10n.error}: $e')),
                ),
                data: (patients) {
                  if (patients.isEmpty) {
                    return SizedBox(
                      height: 100,
                      child: Center(child: Text(l10n.noMembers)),
                    );
                  }
                  return ListView.builder(
                    shrinkWrap: true,
                    itemCount: patients.length,
                    itemBuilder: (context, index) {
                      final user = patients[index];
                      return ListTile(
                        leading: ProfileAvatar(user: user, radius: 20),
                        title: Text(displayName(user)),
                        subtitle: user.email != null ? Text(user.email!) : null,
                        onTap: () {
                          ref
                              .read(patientDetailProvider.notifier)
                              .selectPatient(patientIdOf(user));
                          Navigator.of(context).pop();
                        },
                      );
                    },
                  );
                },
              ),
            ),
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(),
          child: Text(l10n.dismiss),
        ),
      ],
    );
  }
}
