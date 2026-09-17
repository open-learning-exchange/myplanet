import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../data/local/app_database.dart';
import '../../l10n/app_localizations.dart';
import '../../providers/app_providers.dart';

/// Port of `ui/surveys/SendSurveyFragment.kt`.
///
/// Dialog for selecting users to send a survey to.
class SendSurveyScreen extends ConsumerStatefulWidget {
  const SendSurveyScreen({required this.surveyId, super.key});

  final String surveyId;

  @override
  ConsumerState<SendSurveyScreen> createState() => _SendSurveyScreenState();
}

class _SendSurveyScreenState extends ConsumerState<SendSurveyScreen> {
  List<UserRow> _users = [];
  final Set<int> _selectedIndices = {};
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _loadUsers();
  }

  Future<void> _loadUsers() async {
    final users = await ref.read(userDaoProvider).getAllUsers();
    if (mounted) {
      setState(() {
        _users = users;
        _loading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);

    return AlertDialog(
      title: Text(l10n.sendSurveyTo),
      content: SizedBox(
        width: 400,
        height: 400,
        child: _loading
            ? const Center(child: CircularProgressIndicator())
            : _users.isEmpty
            ? Center(child: Text(l10n.noUsersAvailable))
            : ListView.builder(
                itemCount: _users.length,
                itemBuilder: (context, index) {
                  final user = _users[index];
                  return CheckboxListTile(
                    title: Text(user.name ?? l10n.unknown),
                    subtitle: Text(user.email ?? ''),
                    value: _selectedIndices.contains(index),
                    onChanged: (selected) {
                      setState(() {
                        if (selected == true) {
                          _selectedIndices.add(index);
                        } else {
                          _selectedIndices.remove(index);
                        }
                      });
                    },
                  );
                },
              ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: Text(l10n.cancel),
        ),
        FilledButton(
          onPressed: _selectedIndices.isEmpty
              ? null
              : () async {
                  await _sendSurvey();
                  if (context.mounted) {
                    Navigator.pop(context);
                  }
                },
          child: Text(l10n.sendSurvey),
        ),
      ],
    );
  }

  Future<void> _sendSurvey() async {
    final l10n = AppLocalizations.of(context);
    final selectedUserIds = _selectedIndices
        .map((i) => _users[i].id)
        .whereType<String>()
        .toList();

    if (selectedUserIds.isEmpty) return;

    await ref
        .read(submissionsRepositoryProvider)
        .createBulkSurveySubmissions(widget.surveyId, selectedUserIds);

    if (mounted) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(l10n.surveySentToUsers)));
    }
  }
}
