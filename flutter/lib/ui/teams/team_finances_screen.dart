import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:image_picker/image_picker.dart';
import 'package:path/path.dart' as p;

import '../../core/files/team_attachments.dart';
import '../../data/local/user_mapper.dart';
import '../../l10n/app_localizations.dart';
import '../../providers/session_provider.dart';
import '../../providers/teams_provider.dart';
import 'team_reports_screen.dart';

/// Port of `ui/enterprises/EnterprisesFinancesFragment.kt`.
///
/// Displays a list of financial transactions (debits and credits) for a team,
/// with the ability to add new transactions.
class TeamFinancesScreen extends ConsumerStatefulWidget {
  const TeamFinancesScreen({
    required this.teamId,
    this.fromCommunity = false,
    super.key,
  });
  final String teamId;

  /// Mirrors the `"fromCommunity"` fragment argument
  /// `EnterprisesFinancesFragment` reads — see [TeamReportsScreen].
  final bool fromCommunity;

  @override
  ConsumerState<TeamFinancesScreen> createState() => _TeamFinancesScreenState();
}

class _TeamFinancesScreenState extends ConsumerState<TeamFinancesScreen> {
  int? _startDate;
  int? _endDate;
  bool _ascending = false;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final params = (
      teamId: widget.teamId,
      startDate: _startDate,
      endDate: _endDate,
      ascending: _ascending,
    );
    final transactions = ref.watch(teamTransactionsProvider(params));
    // Port of `EnterprisesFinancesFragment.onViewCreated`:
    //   val canManage = if (fromCommunity) user?.isManager() == true else isMember
    // `isMemberFlow` is `TeamsRepositoryImpl.isMember` — plain membership, not
    // leadership. Kotlin has a separate `isTeamLeader` it deliberately does not
    // use here, so any member of an enterprise may add a transaction.
    final canManage = widget.fromCommunity
        ? _isManager()
        : ref.watch(teamMembershipsProvider).valueOrNull?[widget.teamId] !=
              null;

    return Scaffold(
      appBar: AppBar(
        title: Text(l10n.finances),
        actions: [
          IconButton(
            icon: Icon(_ascending ? Icons.arrow_upward : Icons.arrow_downward),
            tooltip: _ascending ? 'Sort: Oldest first' : 'Sort: Newest first',
            onPressed: () => setState(() => _ascending = !_ascending),
          ),
        ],
      ),
      body: Column(
        children: [
          // Date filter row
          Padding(
            padding: const EdgeInsets.all(8.0),
            child: Row(
              children: [
                Expanded(
                  child: _DateFilterButton(
                    label: l10n.fromDate,
                    value: _startDate,
                    onTap: () => _selectDate(context, isStart: true),
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: _DateFilterButton(
                    label: l10n.toDate,
                    value: _endDate,
                    enabled: _startDate != null,
                    onTap: () => _selectDate(context, isStart: false),
                  ),
                ),
                const SizedBox(width: 8),
                TextButton(
                  onPressed: () => setState(() {
                    _startDate = null;
                    _endDate = null;
                    _ascending = false;
                  }),
                  child: Text(l10n.reset),
                ),
              ],
            ),
          ),
          Expanded(
            child: transactions.when(
              loading: () => const Center(child: CircularProgressIndicator()),
              error: (e, s) => Center(child: Text(l10n.unavailable)),
              data: (rows) {
                var totalDebit = 0;
                var totalCredit = 0;
                for (final tx in rows) {
                  if (tx.row.type?.toLowerCase() == 'debit') {
                    totalDebit += tx.row.amount;
                  } else {
                    totalCredit += tx.row.amount;
                  }
                }
                final balance = totalCredit - totalDebit;

                return Column(
                  children: [
                    _FinanceSummary(
                      debit: totalDebit,
                      credit: totalCredit,
                      balance: balance,
                    ),
                    Expanded(
                      child: rows.isEmpty
                          ? Center(child: Text(l10n.noTransactions))
                          : ListView.separated(
                              padding: const EdgeInsets.all(12),
                              itemCount: rows.length,
                              separatorBuilder: (context, index) =>
                                  const SizedBox(height: 8),
                              itemBuilder: (context, index) =>
                                  _TransactionCard(transaction: rows[index]),
                            ),
                    ),
                  ],
                );
              },
            ),
          ),
        ],
      ),
      floatingActionButton: canManage
          ? FloatingActionButton.extended(
              onPressed: () => _showAddTransactionDialog(context),
              icon: const Icon(Icons.add),
              label: Text(l10n.addTransaction),
            )
          : null,
    );
  }

  /// `user?.isManager()` — the `manager` role or the admin flag. Only read on
  /// the community path, so the team path never builds [sessionProvider].
  bool _isManager() {
    final session = ref.watch(sessionProvider).valueOrNull;
    return session != null && UserMapper.isManager(session) == true;
  }

  Future<void> _selectDate(
    BuildContext context, {
    required bool isStart,
  }) async {
    final now = DateTime.now();
    final date = await showDatePicker(
      context: context,
      firstDate: DateTime(2000),
      // #15766: don't let the user pick a future date.
      lastDate: now,
      initialDate: isStart
          ? (_startDate != null
                ? DateTime.fromMillisecondsSinceEpoch(_startDate!)
                : now)
          // The "to" picker falls back to the selected "from" date before
          // today, as the Kotlin's does (437a3d28a).
          : (_endDate != null
                ? DateTime.fromMillisecondsSinceEpoch(_endDate!)
                : (_startDate != null
                      ? DateTime.fromMillisecondsSinceEpoch(_startDate!)
                      : now)),
    );
    if (date != null) {
      setState(() {
        if (isStart) {
          _startDate = date.millisecondsSinceEpoch;
          if (_endDate != null && _endDate! < _startDate!) {
            _endDate = null;
          }
        } else {
          _endDate = date.millisecondsSinceEpoch;
        }
      });
    }
  }

  Future<void> _showAddTransactionDialog(BuildContext context) async {
    final l10n = AppLocalizations.of(context);
    final noteController = TextEditingController();
    final amountController = TextEditingController();
    String selectedType = 'credit';
    var selectedDate = DateTime.now().millisecondsSinceEpoch;
    XFile? selectedImage;

    await showDialog<void>(
      context: context,
      builder: (dialogContext) => StatefulBuilder(
        builder: (context, setDialogState) => AlertDialog(
          title: Text(l10n.addTransaction),
          content: SizedBox(
            width: 400,
            child: SingleChildScrollView(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  DropdownButtonFormField<String>(
                    initialValue: selectedType,
                    decoration: InputDecoration(labelText: l10n.type),
                    items: [
                      DropdownMenuItem(
                        value: 'credit',
                        child: Text(l10n.credit),
                      ),
                      DropdownMenuItem(value: 'debit', child: Text(l10n.debit)),
                    ],
                    onChanged: (value) {
                      if (value != null) {
                        setDialogState(() => selectedType = value);
                      }
                    },
                  ),
                  const SizedBox(height: 16),
                  TextField(
                    controller: noteController,
                    decoration: InputDecoration(labelText: l10n.note),
                    maxLines: 2,
                  ),
                  const SizedBox(height: 16),
                  TextField(
                    controller: amountController,
                    decoration: InputDecoration(labelText: l10n.amount),
                    keyboardType: TextInputType.number,
                    inputFormatters: [
                      FilteringTextInputFormatter.allow(RegExp(r'^\d*')),
                    ],
                  ),
                  const SizedBox(height: 16),
                  ListTile(
                    contentPadding: EdgeInsets.zero,
                    title: Text(l10n.date),
                    subtitle: Text(
                      MaterialLocalizations.of(context).formatShortDate(
                        DateTime.fromMillisecondsSinceEpoch(selectedDate),
                      ),
                    ),
                    trailing: const Icon(Icons.calendar_today),
                    onTap: () async {
                      final date = await showDatePicker(
                        context: context,
                        firstDate: DateTime(2000),
                        lastDate: DateTime(2100),
                        initialDate: DateTime.fromMillisecondsSinceEpoch(
                          selectedDate,
                        ),
                      );
                      if (date != null) {
                        setDialogState(
                          () => selectedDate = date.millisecondsSinceEpoch,
                        );
                      }
                    },
                  ),
                  // Port of `EnterprisesFinancesFragment`'s receipt picker:
                  // `pickImageLauncher` launches `GetContent("image/*")`,
                  // and the picked uri supplies both the name and the bytes.
                  // The picker reads the file into the app cache and hands
                  // back a path; the bytes are read at submit time, the way
                  // `FileUtils.readBytesFromUri` reads them just before the
                  // repository call.
                  ListTile(
                    contentPadding: EdgeInsets.zero,
                    leading: Icon(
                      selectedImage == null
                          ? Icons.add_photo_alternate_outlined
                          : Icons.image_outlined,
                    ),
                    title: Text(
                      selectedImage == null
                          ? l10n.addReceipt
                          : l10n.receiptSelected,
                    ),
                    subtitle: selectedImage == null
                        ? Text(l10n.noReceipt)
                        : Text(p.basename(selectedImage!.name)),
                    onTap: () async {
                      final picker = ImagePicker();
                      final picked = await picker.pickImage(
                        source: ImageSource.gallery,
                        imageQuality: 85,
                      );
                      if (picked != null) {
                        setDialogState(() => selectedImage = picked);
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
              onPressed: () async {
                final note = noteController.text.trim();
                final amount = int.tryParse(amountController.text) ?? 0;
                if (note.isEmpty) {
                  ScaffoldMessenger.of(
                    context,
                  ).showSnackBar(SnackBar(content: Text(l10n.noteRequired)));
                  return;
                }
                if (amount <= 0) {
                  ScaffoldMessenger.of(
                    context,
                  ).showSnackBar(SnackBar(content: Text(l10n.amountRequired)));
                  return;
                }
                String? imageName;
                List<int>? imageBytes;
                if (selectedImage != null) {
                  imageName = p.basename(selectedImage!.name);
                  try {
                    imageBytes = await selectedImage!.readAsBytes();
                  } on Exception {
                    if (!context.mounted) return;
                    ScaffoldMessenger.of(
                      context,
                    ).showSnackBar(SnackBar(content: Text(l10n.unavailable)));
                    return;
                  }
                }
                final ok = await ref
                    .read(teamFinancesActionsProvider)
                    .createTransaction(
                      teamId: widget.teamId,
                      type: selectedType,
                      note: note,
                      amount: amount,
                      date: selectedDate,
                      imageName: imageName,
                      imageBytes: imageBytes,
                    );
                if (ok && dialogContext.mounted) {
                  Navigator.pop(dialogContext);
                }
              },
              child: Text(l10n.submit),
            ),
          ],
        ),
      ),
    );
    noteController.dispose();
    amountController.dispose();
  }
}

class _FinanceSummary extends StatelessWidget {
  const _FinanceSummary({
    required this.debit,
    required this.credit,
    required this.balance,
  });

  final int debit;
  final int credit;
  final int balance;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      color: Theme.of(context).colorScheme.surfaceContainerHighest,
      child: Column(
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceAround,
            children: [
              _SummaryItem(label: l10n.debit, value: debit),
              _SummaryItem(label: l10n.credit, value: credit),
              _SummaryItem(label: l10n.balance, value: balance, bold: true),
            ],
          ),
          // `balance_caution` in `header_finance.xml`, shown when
          // `FinanceHeaderState.isCautionVisible` — i.e. `total < 0`.
          if (balance < 0)
            Padding(
              padding: const EdgeInsets.only(top: 8),
              child: Text(
                l10n.negativeBalance,
                style: TextStyle(color: Theme.of(context).colorScheme.error),
              ),
            ),
        ],
      ),
    );
  }
}

class _DateFilterButton extends StatelessWidget {
  const _DateFilterButton({
    required this.label,
    required this.value,
    this.enabled = true,
    required this.onTap,
  });

  final String label;
  final int? value;
  final bool enabled;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final displayText = value != null
        ? MaterialLocalizations.of(
            context,
          ).formatShortDate(DateTime.fromMillisecondsSinceEpoch(value!))
        : label;
    return Opacity(
      opacity: enabled ? 1.0 : 0.5,
      child: InkWell(
        onTap: enabled ? onTap : null,
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
          decoration: BoxDecoration(
            border: Border.all(color: Theme.of(context).colorScheme.outline),
            borderRadius: BorderRadius.circular(8),
          ),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(Icons.calendar_today, size: 16),
              const SizedBox(width: 8),
              Text(displayText),
            ],
          ),
        ),
      ),
    );
  }
}

class _SummaryItem extends StatelessWidget {
  const _SummaryItem({
    required this.label,
    required this.value,
    this.bold = false,
  });
  final String label;
  final int value;
  final bool bold;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Text(label, style: Theme.of(context).textTheme.bodySmall),
        Text(
          '$value',
          style: bold
              ? const TextStyle(fontWeight: FontWeight.bold, fontSize: 18)
              : null,
        ),
      ],
    );
  }
}

class _TransactionCard extends StatelessWidget {
  const _TransactionCard({required this.transaction});
  final TransactionRow transaction;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final isDebit = transaction.row.type?.toLowerCase() == 'debit';
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Row(
          children: [
            // Date
            SizedBox(
              width: 80,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    MaterialLocalizations.of(context).formatShortDate(
                      DateTime.fromMillisecondsSinceEpoch(transaction.row.date),
                    ),
                    style: Theme.of(context).textTheme.bodySmall,
                  ),
                ],
              ),
            ),
            const SizedBox(width: 12),
            // Type, Note, Amount
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Icon(
                        isDebit ? Icons.arrow_downward : Icons.arrow_upward,
                        size: 16,
                        color: isDebit ? Colors.red : Colors.green,
                      ),
                      const SizedBox(width: 4),
                      Text(
                        isDebit ? l10n.debit : l10n.credit,
                        style: TextStyle(
                          color: isDebit ? Colors.red : Colors.green,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ],
                  ),
                  if (transaction.row.description?.isNotEmpty == true)
                    Text(transaction.row.description!),
                  if (transaction.row.imageName?.isNotEmpty == true)
                    _ReceiptThumb(transaction: transaction),
                ],
              ),
            ),
            // Amount and Balance
            Column(
              crossAxisAlignment: CrossAxisAlignment.end,
              children: [
                Text(
                  isDebit
                      ? '-${transaction.row.amount}'
                      : '+${transaction.row.amount}',
                  style: TextStyle(
                    color: isDebit ? Colors.red : Colors.green,
                    fontWeight: FontWeight.bold,
                  ),
                ),
                Text(
                  '= ${transaction.balance}',
                  style: Theme.of(context).textTheme.bodySmall,
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

/// Port of `EnterprisesFinancesAdapter.bindFinanceImage`: loads the local
/// attachment file (when present) and shows a tappable thumbnail that opens a
/// zoomable view, the way `ImageViewerUtils.showZoomableImage` does.
class _ReceiptThumb extends StatelessWidget {
  const _ReceiptThumb({required this.transaction});
  final TransactionRow transaction;

  @override
  Widget build(BuildContext context) {
    final imageName = transaction.row.imageName;
    final docId = transaction.row.id;
    return FutureBuilder<File?>(
      future: imageName == null || imageName.isEmpty || docId.isEmpty
          ? Future.value(null)
          : TeamAttachments.existingFileFor(docId: docId, filename: imageName),
      builder: (context, snapshot) {
        final file = snapshot.data;
        if (file == null) {
          return const SizedBox.shrink();
        }
        return Padding(
          padding: const EdgeInsets.only(top: 8),
          child: InkWell(
            onTap: () => _openViewer(context, file),
            child: ClipRRect(
              borderRadius: BorderRadius.circular(6),
              child: Image.file(
                file,
                width: 72,
                height: 72,
                fit: BoxFit.cover,
                errorBuilder: (context, error, stack) =>
                    const SizedBox.shrink(),
              ),
            ),
          ),
        );
      },
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
