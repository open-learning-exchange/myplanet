import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../data/local/app_database.dart';
import '../../l10n/app_localizations.dart';
import '../../providers/teams_provider.dart';
import '../../repository/teams_repository.dart';

class TeamReportsScreen extends ConsumerWidget {
  const TeamReportsScreen({required this.teamId, super.key});
  final String teamId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final reports = ref.watch(teamReportsProvider(teamId));
    final canManage =
        ref.watch(teamMembershipsProvider).valueOrNull?[teamId]?.isLeader ??
        false;
    return Scaffold(
      appBar: AppBar(title: Text(l10n.financialReports)),
      body: reports.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (_, _) => Center(child: Text(l10n.reportsUnavailable)),
        data: (rows) => rows.isEmpty
            ? Center(child: Text(l10n.noReports))
            : ListView.separated(
                padding: const EdgeInsets.all(12),
                itemCount: rows.length,
                separatorBuilder: (_, _) => const SizedBox(height: 8),
                itemBuilder: (context, index) => _ReportCard(
                  report: rows[index],
                  canManage: canManage,
                  onEdit: () => _editReport(context, ref, report: rows[index]),
                  onArchive: () => ref
                      .read(teamReportActionsProvider)
                      .archive(rows[index].id),
                ),
              ),
      ),
      floatingActionButton: canManage
          ? FloatingActionButton.extended(
              onPressed: () => _editReport(context, ref),
              icon: const Icon(Icons.add_chart),
              label: Text(l10n.addReport),
            )
          : null,
    );
  }

  Future<void> _editReport(
    BuildContext context,
    WidgetRef ref, {
    TeamRow? report,
  }) async {
    final l10n = AppLocalizations.of(context);
    final description = TextEditingController(text: report?.description);
    final values = <String, TextEditingController>{
      'beginning': TextEditingController(
        text: '${report?.beginningBalance ?? 0}',
      ),
      'sales': TextEditingController(text: '${report?.sales ?? 0}'),
      'income': TextEditingController(text: '${report?.otherIncome ?? 0}'),
      'wages': TextEditingController(text: '${report?.wages ?? 0}'),
      'expenses': TextEditingController(text: '${report?.otherExpenses ?? 0}'),
    };
    var start = report?.startDate ?? DateTime.now().millisecondsSinceEpoch;
    var end = report?.endDate ?? start;
    await showDialog<void>(
      context: context,
      builder: (dialogContext) => StatefulBuilder(
        builder: (context, setState) => AlertDialog(
          title: Text(report == null ? l10n.addReport : l10n.editReport),
          content: SizedBox(
            width: 500,
            child: SingleChildScrollView(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  TextField(
                    controller: description,
                    decoration: InputDecoration(labelText: l10n.description),
                    maxLines: 2,
                  ),
                  Row(
                    children: [
                      Expanded(
                        child: _DateField(
                          label: l10n.startDate,
                          value: start,
                          onChanged: (value) => setState(() => start = value),
                        ),
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: _DateField(
                          label: l10n.endDate,
                          value: end,
                          onChanged: (value) => setState(() => end = value),
                        ),
                      ),
                    ],
                  ),
                  _MoneyField(
                    controller: values['beginning']!,
                    label: l10n.beginningBalance,
                  ),
                  _MoneyField(controller: values['sales']!, label: l10n.sales),
                  _MoneyField(
                    controller: values['income']!,
                    label: l10n.otherIncome,
                  ),
                  _MoneyField(controller: values['wages']!, label: l10n.wages),
                  _MoneyField(
                    controller: values['expenses']!,
                    label: l10n.otherExpenses,
                  ),
                ],
              ),
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(dialogContext),
              child: Text(l10n.cancel),
            ),
            FilledButton(
              onPressed: start > end
                  ? null
                  : () async {
                      final ok = await ref
                          .read(teamReportActionsProvider)
                          .save(
                            id: report?.id,
                            teamId: teamId,
                            description: description.text,
                            startDate: start,
                            endDate: end,
                            beginningBalance:
                                int.tryParse(values['beginning']!.text) ?? 0,
                            sales: int.tryParse(values['sales']!.text) ?? 0,
                            otherIncome:
                                int.tryParse(values['income']!.text) ?? 0,
                            wages: int.tryParse(values['wages']!.text) ?? 0,
                            otherExpenses:
                                int.tryParse(values['expenses']!.text) ?? 0,
                          );
                      if (ok && dialogContext.mounted) {
                        Navigator.pop(dialogContext);
                      }
                    },
              child: Text(l10n.save),
            ),
          ],
        ),
      ),
    );
    description.dispose();
    for (final controller in values.values) {
      controller.dispose();
    }
  }
}

class _ReportCard extends StatelessWidget {
  const _ReportCard({
    required this.report,
    required this.canManage,
    required this.onEdit,
    required this.onArchive,
  });
  final TeamRow report;
  final bool canManage;
  final VoidCallback onEdit;
  final VoidCallback onArchive;
  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final dates =
        '${MaterialLocalizations.of(context).formatShortDate(DateTime.fromMillisecondsSinceEpoch(report.startDate))} – ${MaterialLocalizations.of(context).formatShortDate(DateTime.fromMillisecondsSinceEpoch(report.endDate))}';
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(dates, style: Theme.of(context).textTheme.titleMedium),
            if (report.description?.isNotEmpty == true)
              Text(report.description!),
            const Divider(),
            _Total(label: l10n.totalIncome, value: report.totalIncome),
            _Total(label: l10n.totalExpenses, value: report.totalExpenses),
            _Total(label: l10n.profitLoss, value: report.profitLoss),
            _Total(
              label: l10n.endingBalance,
              value: report.endingBalance,
              bold: true,
            ),
            if (canManage)
              Row(
                mainAxisAlignment: MainAxisAlignment.end,
                children: [
                  TextButton(onPressed: onEdit, child: Text(l10n.editReport)),
                  TextButton(
                    onPressed: onArchive,
                    child: Text(l10n.archiveReport),
                  ),
                ],
              ),
          ],
        ),
      ),
    );
  }
}

class _Total extends StatelessWidget {
  const _Total({required this.label, required this.value, this.bold = false});
  final String label;
  final int value;
  final bool bold;
  @override
  Widget build(BuildContext context) => Row(
    mainAxisAlignment: MainAxisAlignment.spaceBetween,
    children: [
      Text(label),
      Text(
        '$value',
        style: bold ? const TextStyle(fontWeight: FontWeight.bold) : null,
      ),
    ],
  );
}

class _MoneyField extends StatelessWidget {
  const _MoneyField({required this.controller, required this.label});
  final TextEditingController controller;
  final String label;
  @override
  Widget build(BuildContext context) => TextField(
    controller: controller,
    decoration: InputDecoration(labelText: label),
    keyboardType: TextInputType.number,
    inputFormatters: [FilteringTextInputFormatter.allow(RegExp(r'^-?\d*'))],
  );
}

class _DateField extends StatelessWidget {
  const _DateField({
    required this.label,
    required this.value,
    required this.onChanged,
  });
  final String label;
  final int value;
  final ValueChanged<int> onChanged;
  @override
  Widget build(BuildContext context) => ListTile(
    contentPadding: EdgeInsets.zero,
    title: Text(label),
    subtitle: Text(
      MaterialLocalizations.of(
        context,
      ).formatShortDate(DateTime.fromMillisecondsSinceEpoch(value)),
    ),
    onTap: () async {
      final date = await showDatePicker(
        context: context,
        firstDate: DateTime(2000),
        lastDate: DateTime(2100),
        initialDate: DateTime.fromMillisecondsSinceEpoch(value),
      );
      if (date != null) onChanged(date.millisecondsSinceEpoch);
    },
  );
}
