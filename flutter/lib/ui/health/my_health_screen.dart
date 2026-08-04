import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../data/local/app_database.dart';
import '../../l10n/app_localizations.dart';
import '../../providers/health_provider.dart';
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
    final healthData = ref.watch(healthDataProvider);

    return Scaffold(
      appBar: AppBar(
        title: Text(l10n.myHealth),
        actions: [
          IconButton(
            icon: const Icon(Icons.edit),
            tooltip: l10n.updateHealth,
            onPressed: () => context.push(Routes.addHealth),
          ),
        ],
      ),
      body: healthData.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => Center(child: Text('${l10n.error}: $error')),
        data: (data) {
          if (data == null) {
            return Center(child: Text(l10n.healthRecordNotAvailable));
          }
          return _HealthContent(data: data);
        },
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => context.push(Routes.addExamination),
        icon: const Icon(Icons.add),
        label: Text(l10n.addHealthRecord),
      ),
    );
  }
}

class _HealthContent extends StatelessWidget {
  const _HealthContent({required this.data});
  final HealthData data;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final user = data.user;
    final examination = data.examination;
    final myHealth = data.myHealth;
    final examinations = data.examinations;

    if (user == null) {
      return Center(child: Text(l10n.healthRecordNotAvailable));
    }

    return RefreshIndicator(
      onRefresh: () async {
        // Refresh health data
      },
      child: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          // User profile card
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      CircleAvatar(
                        radius: 32,
                        backgroundImage: user.userImage != null
                            ? NetworkImage(user.userImage!)
                            : null,
                        child: user.userImage == null
                            ? Text(_getInitials(user))
                            : null,
                      ),
                      const SizedBox(width: 16),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              _getDisplayName(user),
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
                      height: 120,
                      child: ListView.separated(
                        scrollDirection: Axis.horizontal,
                        itemCount: examinations.length,
                        separatorBuilder: (context, index) =>
                            const SizedBox(width: 8),
                        itemBuilder: (context, index) {
                          final exam = examinations[index];
                          return _ExaminationCard(exam: exam);
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

  String _getDisplayName(UserRow user) {
    final parts = [
      user.firstName?.trim(),
      user.middleName?.trim(),
      user.lastName?.trim(),
    ].where((p) => p != null && p.isNotEmpty).toList();
    if (parts.isEmpty) return user.name ?? 'Unknown';
    return parts.join(' ');
  }

  String _getInitials(UserRow user) {
    final name = _getDisplayName(user);
    final parts = name.split(' ');
    if (parts.isEmpty) return '?';
    if (parts.length == 1) return parts[0][0].toUpperCase();
    return '${parts[0][0]}${parts.last[0]}'.toUpperCase();
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

class _ExaminationCard extends StatelessWidget {
  const _ExaminationCard({required this.exam});
  final HealthExaminationRow exam;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final date =
        exam.date > 0 ? DateTime.fromMillisecondsSinceEpoch(exam.date) : null;

    return Container(
      width: 140,
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        border: Border.all(color: Theme.of(context).colorScheme.outline),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            date != null
                ? '${date.day}/${date.month}/${date.year}'
                : l10n.unknown,
            style: Theme.of(context).textTheme.labelMedium,
          ),
          const SizedBox(height: 8),
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
    );
  }
}
