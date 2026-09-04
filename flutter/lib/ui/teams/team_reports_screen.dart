import 'dart:convert';
import 'dart:io';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:image_picker/image_picker.dart';
import 'package:path/path.dart' as p;

import '../../core/files/team_attachments.dart';
import '../../data/local/app_database.dart';
import '../../data/local/user_mapper.dart';
import '../../l10n/app_localizations.dart';
import '../../providers/app_providers.dart';
import '../../providers/session_provider.dart';
import '../../providers/teams_provider.dart';
import '../../repository/teams_repository.dart';

class TeamReportsScreen extends ConsumerWidget {
  const TeamReportsScreen({
    required this.teamId,
    this.fromCommunity = false,
    super.key,
  });
  final String teamId;

  /// Mirrors the `"fromCommunity"` fragment argument
  /// `EnterprisesReportsFragment` reads: the community tabs host the same
  /// screen against a community document rather than a team the user can be a
  /// member of, and there the manage gate is the manager role instead.
  final bool fromCommunity;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final reports = ref.watch(teamReportsProvider(teamId));
    // Port of `EnterprisesReportsFragment.onViewCreated`:
    //   val canManage = if (fromCommunity) user?.isManager() == true else isMember
    // `isMemberFlow` is `TeamsRepositoryImpl.isMember` — plain membership, not
    // leadership. Kotlin has a separate `isTeamLeader` it deliberately does not
    // use here, so any member of an enterprise may add and edit its reports.
    final canManage = fromCommunity
        ? _isManager(ref)
        : ref.watch(teamMembershipsProvider).valueOrNull?[teamId] != null;
    return Scaffold(
      appBar: AppBar(title: Text(l10n.financialReports)),
      body: reports.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (_, _) => Center(child: Text(l10n.reportsUnavailable)),
        data: (rows) => rows.isEmpty
            ? Center(child: Text(l10n.noReports))
            : Column(
                children: [
                  // Port of `EnterprisesReportsFragment`'s `exportCSV` button:
                  // a toolbar action that opens the platform save-file
                  // picker (the SAF `ACTION_CREATE_DOCUMENT` equivalent) and
                  // writes the CSV produced by `exportReportsAsCsv` to the
                  // chosen location. The Kotlin hides the button when the list
                  // is empty; the port does the same by only rendering the
                  // list (this row) when `rows` is non-empty.
                  Padding(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 12,
                      vertical: 8,
                    ),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.end,
                      children: [
                        OutlinedButton.icon(
                          onPressed: () => _exportCsv(context, ref, rows),
                          icon: const Icon(Icons.download),
                          label: Text(l10n.exportCsv),
                        ),
                      ],
                    ),
                  ),
                  Expanded(
                    child: ListView.separated(
                      padding: const EdgeInsets.fromLTRB(12, 0, 12, 12),
                      itemCount: rows.length,
                      separatorBuilder: (_, _) => const SizedBox(height: 8),
                      itemBuilder: (context, index) => _ReportCard(
                        report: rows[index],
                        canManage: canManage,
                        onEdit: () =>
                            _editReport(context, ref, report: rows[index]),
                        onArchive: () => ref
                            .read(teamReportActionsProvider)
                            .archive(rows[index].id),
                      ),
                    ),
                  ),
                ],
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

  /// `user?.isManager()` — the `manager` role or the admin flag. Only read on
  /// the community path, so the team path never builds [sessionProvider] (and
  /// the team screen tests need no session override).
  bool _isManager(WidgetRef ref) {
    final session = ref.watch(sessionProvider).valueOrNull;
    return session != null && UserMapper.isManager(session) == true;
  }

  Future<void> _exportCsv(
    BuildContext context,
    WidgetRef ref,
    List<TeamRow> rows,
  ) async {
    final l10n = AppLocalizations.of(context);
    final repo = ref.read(teamsRepositoryProvider);
    final team = await repo.getById(teamId);
    final teamName = (team?.name ?? '').replaceAll(' ', '_');
    final csv = repo.exportReportsAsCsv(rows, team?.name ?? '');
    final formattedDate = reportExportDateSuffix(DateTime.now());
    final defaultName =
        'Report_of_${teamName}_Financial_Report_Summary_on_$formattedDate.csv';
    if (!context.mounted) return;
    final messenger = ScaffoldMessenger.of(context);
    try {
      final path = await FilePicker.saveFile(
        dialogTitle: l10n.exportCsv,
        fileName: defaultName,
        bytes: Uint8List.fromList(utf8.encode(csv)),
        type: FileType.custom,
        allowedExtensions: ['csv'],
      );
      if (path == null) {
        messenger.showSnackBar(SnackBar(content: Text(l10n.exportCancelled)));
        return;
      }
      messenger.showSnackBar(
        SnackBar(content: Text(l10n.csvFileSavedSuccessfully)),
      );
    } on Exception {
      messenger.showSnackBar(SnackBar(content: Text(l10n.failedToSaveCsvFile)));
    }
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
    XFile? selectedImage;
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
                  // Port of the report image picker in
                  // `EnterprisesReportsFragment`/`EnterprisesReportsAdapter`:
                  // a report can carry one receipt image, attached on create
                  // or replace. Editing without picking a new image leaves the
                  // existing attachment in place — the repository only attaches
                  // when both name and bytes are supplied.
                  ListTile(
                    contentPadding: EdgeInsets.zero,
                    leading: Icon(
                      selectedImage == null
                          ? Icons.add_photo_alternate_outlined
                          : Icons.image_outlined,
                    ),
                    title: Text(
                      selectedImage == null
                          ? (report?.imageName?.isNotEmpty == true
                                ? l10n.viewReceipt
                                : l10n.addReceipt)
                          : l10n.receiptSelected,
                    ),
                    subtitle: selectedImage == null
                        ? (report?.imageName?.isNotEmpty == true
                              ? Text(p.basename(report!.imageName!))
                              : Text(l10n.noReceipt))
                        : Text(p.basename(selectedImage!.name)),
                    onTap: () async {
                      final picker = ImagePicker();
                      final picked = await picker.pickImage(
                        source: ImageSource.gallery,
                        imageQuality: 85,
                      );
                      if (picked != null) {
                        setState(() => selectedImage = picked);
                      }
                    },
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
                      String? imageName;
                      List<int>? imageBytes;
                      if (selectedImage != null) {
                        imageName = p.basename(selectedImage!.name);
                        try {
                          imageBytes = await selectedImage!.readAsBytes();
                        } on Exception {
                          if (!context.mounted) return;
                          ScaffoldMessenger.of(context).showSnackBar(
                            SnackBar(content: Text(l10n.unavailable)),
                          );
                          return;
                        }
                      }
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
                            imageName: imageName,
                            imageBytes: imageBytes,
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
            if (report.imageName?.isNotEmpty == true)
              _ReportReceiptThumb(report: report),
            const Divider(),
            // The nine value rows `EnterprisesReportsAdapter.onBindViewHolder`
            // binds, in the order `report_list_item.xml` lays them out: the
            // five figures the report was authored from, each followed by the
            // total it feeds. Showing only the totals (as this card used to)
            // leaves a reader unable to see where a profit or loss came from.
            _Total(
              label: l10n.beginningBalance,
              value: report.beginningBalance,
            ),
            _Total(label: l10n.sales, value: report.sales),
            _Total(label: l10n.otherIncome, value: report.otherIncome),
            _Total(label: l10n.totalIncome, value: report.totalIncome),
            _Total(label: l10n.wages, value: report.wages),
            _Total(label: l10n.otherExpenses, value: report.otherExpenses),
            _Total(label: l10n.totalExpenses, value: report.totalExpenses),
            _Total(label: l10n.profitLoss, value: report.profitLoss),
            _Total(
              label: l10n.endingBalance,
              value: report.endingBalance,
              bold: true,
            ),
            // `createUpdate` in the Kotlin adapter (R.string.report_date_details).
            Padding(
              padding: const EdgeInsets.only(top: 8),
              child: Text(
                l10n.reportDateDetails(
                  MaterialLocalizations.of(context).formatShortDate(
                    DateTime.fromMillisecondsSinceEpoch(report.createdDate),
                  ),
                  MaterialLocalizations.of(context).formatShortDate(
                    DateTime.fromMillisecondsSinceEpoch(report.updatedDate),
                  ),
                ),
                style: Theme.of(context).textTheme.bodySmall,
              ),
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

/// Port of `EnterprisesReportsAdapter.bindReportImage`: shows the local
/// attachment thumbnail and opens a zoomable view on tap.
class _ReportReceiptThumb extends StatelessWidget {
  const _ReportReceiptThumb({required this.report});
  final TeamRow report;

  @override
  Widget build(BuildContext context) {
    final imageName = report.imageName;
    final docId = report.id;
    return Padding(
      padding: const EdgeInsets.only(top: 8),
      child: FutureBuilder<File?>(
        future: imageName == null || imageName.isEmpty || docId.isEmpty
            ? Future.value(null)
            : TeamAttachments.existingFileFor(
                docId: docId,
                filename: imageName,
              ),
        builder: (context, snapshot) {
          final file = snapshot.data;
          if (file == null) return const SizedBox.shrink();
          return InkWell(
            onTap: () => _openViewer(context, file),
            child: ClipRRect(
              borderRadius: BorderRadius.circular(6),
              child: Image.file(
                file,
                width: 96,
                height: 96,
                fit: BoxFit.cover,
                errorBuilder: (context, error, stack) =>
                    const SizedBox.shrink(),
              ),
            ),
          );
        },
      ),
    );
  }

  void _openViewer(BuildContext context, File file) {
    final l10n = AppLocalizations.of(context);
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => Scaffold(
          backgroundColor: Colors.black,
          appBar: AppBar(
            title: Text(l10n.receipt),
            backgroundColor: Colors.black,
            foregroundColor: Colors.white,
          ),
          body: Center(
            child: InteractiveViewer(
              child: Image.file(
                file,
                errorBuilder: (context, error, stack) {
                  final l = AppLocalizations.of(context);
                  return Padding(
                    padding: const EdgeInsets.all(24),
                    child: Text(
                      l.unavailable,
                      style: const TextStyle(color: Colors.white),
                    ),
                  );
                },
              ),
            ),
          ),
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

/// The date suffix of the exported CSV's default filename.
///
/// `LocalDate.now().format(dateFormatter)` in the Kotlin source, where
/// `dateFormatter` is `"EEE_MMM_dd_yyyy"` (e.g. `Wed_Aug_20_2026`). Top-level
/// and public so the format is pinned by a test rather than only reachable
/// through the platform save-file picker.
String reportExportDateSuffix(DateTime dt) {
  final weekdays = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
  final months = [
    'Jan',
    'Feb',
    'Mar',
    'Apr',
    'May',
    'Jun',
    'Jul',
    'Aug',
    'Sep',
    'Oct',
    'Nov',
    'Dec',
  ];
  final weekday = weekdays[dt.weekday - 1];
  final month = months[dt.month - 1];
  final day = dt.day.toString().padLeft(2, '0');
  return '${weekday}_${month}_${day}_${dt.year}';
}
