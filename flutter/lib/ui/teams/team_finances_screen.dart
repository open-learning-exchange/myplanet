import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../l10n/app_localizations.dart';
import '../../providers/teams_provider.dart';

/// Port of `ui/enterprises/EnterprisesFinancesFragment.kt`.
///
/// Displays a list of financial transactions (debits and credits) for a team,
/// with the ability to add new transactions.
class TeamFinancesScreen extends ConsumerStatefulWidget {
  const TeamFinancesScreen({required this.teamId, super.key});
  final String teamId;

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
    final canManage =
        ref
            .watch(teamMembershipsProvider)
            .valueOrNull?[widget.teamId]
            ?.isLeader ??
        false;

    int totalDebit = 0;
    int totalCredit = 0;
    int balance = 0;

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
                  }),
                  child: Text(l10n.reset),
                ),
              ],
            ),
          ),
          // Summary row
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            color: Theme.of(context).colorScheme.surfaceContainerHighest,
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceAround,
              children: [
                _SummaryItem(label: l10n.debit, value: totalDebit),
                _SummaryItem(label: l10n.credit, value: totalCredit),
                _SummaryItem(label: l10n.balance, value: balance, bold: true),
              ],
            ),
          ),
          // Transaction list
          Expanded(
            child: transactions.when(
              loading: () => const Center(child: CircularProgressIndicator()),
              error: (e, s) => Center(child: Text(l10n.unavailable)),
              data: (rows) {
                // Calculate totals
                for (final tx in rows) {
                  if (tx.row.type?.toLowerCase() == 'debit') {
                    totalDebit += tx.row.amount;
                  } else {
                    totalCredit += tx.row.amount;
                  }
                }
                balance = totalCredit - totalDebit;

                if (rows.isEmpty) {
                  return Center(child: Text(l10n.noTransactions));
                }
                return ListView.separated(
                  padding: const EdgeInsets.all(12),
                  itemCount: rows.length,
                  separatorBuilder: (context, index) =>
                      const SizedBox(height: 8),
                  itemBuilder: (context, index) =>
                      _TransactionCard(transaction: rows[index]),
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

  Future<void> _selectDate(
    BuildContext context, {
    required bool isStart,
  }) async {
    final now = DateTime.now();
    final date = await showDatePicker(
      context: context,
      firstDate: DateTime(2000),
      lastDate: DateTime(2100),
      initialDate: isStart
          ? (_startDate != null
                ? DateTime.fromMillisecondsSinceEpoch(_startDate!)
                : now)
          : (_endDate != null
                ? DateTime.fromMillisecondsSinceEpoch(_endDate!)
                : now),
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
                      if (value != null)
                        setDialogState(() => selectedType = value);
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
                final ok = await ref
                    .read(teamFinancesActionsProvider)
                    .createTransaction(
                      teamId: widget.teamId,
                      type: selectedType,
                      note: note,
                      amount: amount,
                      date: selectedDate,
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
