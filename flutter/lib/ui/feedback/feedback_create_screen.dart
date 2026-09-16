import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../l10n/app_localizations.dart';
import '../../providers/feedback_provider.dart';

/// Port of `ui/feedback/FeedbackFragment.kt`.
///
/// Dialog/screen for creating new feedback.
class FeedbackCreateScreen extends ConsumerStatefulWidget {
  const FeedbackCreateScreen({super.key, this.item, this.state});

  final String? item;
  final String? state;

  @override
  ConsumerState<FeedbackCreateScreen> createState() =>
      _FeedbackCreateScreenState();
}

class _FeedbackCreateScreenState extends ConsumerState<FeedbackCreateScreen> {
  final _messageController = TextEditingController();
  String _priority = 'No';
  String _type = '';
  String? _priorityError;
  String? _typeError;
  String? _messageError;

  @override
  void dispose() {
    _messageController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);

    return Scaffold(
      appBar: AppBar(
        title: Text(l10n.feedback),
        actions: [
          TextButton(onPressed: () => context.pop(), child: Text(l10n.cancel)),
        ],
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Priority section
            Text(l10n.isUrgent, style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 8),
            // `RadioGroup` rather than per-tile `groupValue`/`onChanged`, which
            // are deprecated — and which duplicated the same setState twice.
            RadioGroup<String>(
              groupValue: _priority,
              onChanged: (value) {
                if (value == null) return;
                setState(() {
                  _priority = value;
                  _priorityError = null;
                });
              },
              child: Row(
                children: [
                  Expanded(
                    child: RadioListTile<String>(
                      title: Text(l10n.yes),
                      value: 'Yes',
                    ),
                  ),
                  Expanded(
                    child: RadioListTile<String>(
                      title: Text(l10n.no),
                      value: 'No',
                    ),
                  ),
                ],
              ),
            ),
            if (_priorityError != null)
              Padding(
                padding: const EdgeInsetsDirectional.only(start: 16),
                child: Text(
                  _priorityError!,
                  style: TextStyle(color: Theme.of(context).colorScheme.error),
                ),
              ),

            const SizedBox(height: 24),

            // Type section
            Text(
              l10n.feedbackType,
              style: Theme.of(context).textTheme.titleMedium,
            ),
            const SizedBox(height: 8),
            Wrap(
              spacing: 8,
              children: [
                ChoiceChip(
                  label: Text(l10n.question),
                  selected: _type == 'Question',
                  onSelected: (selected) {
                    setState(() {
                      _type = selected ? 'Question' : '';
                      _typeError = null;
                    });
                  },
                ),
                ChoiceChip(
                  label: Text(l10n.bug),
                  selected: _type == 'Bug',
                  onSelected: (selected) {
                    setState(() {
                      _type = selected ? 'Bug' : '';
                      _typeError = null;
                    });
                  },
                ),
                ChoiceChip(
                  label: Text(l10n.suggestion),
                  selected: _type == 'Suggestion',
                  onSelected: (selected) {
                    setState(() {
                      _type = selected ? 'Suggestion' : '';
                      _typeError = null;
                    });
                  },
                ),
              ],
            ),
            if (_typeError != null)
              Padding(
                padding: const EdgeInsetsDirectional.only(start: 16, top: 8),
                child: Text(
                  _typeError!,
                  style: TextStyle(color: Theme.of(context).colorScheme.error),
                ),
              ),

            const SizedBox(height: 24),

            // Message section
            TextField(
              controller: _messageController,
              decoration: InputDecoration(
                labelText: l10n.yourFeedback,
                border: const OutlineInputBorder(),
                errorText: _messageError,
              ),
              maxLines: 5,
              onChanged: (value) {
                if (_messageError != null && value.trim().isNotEmpty) {
                  setState(() => _messageError = null);
                }
              },
            ),

            const SizedBox(height: 32),

            // Submit button
            SizedBox(
              width: double.infinity,
              // Enabled with no session, deliberately. This screen is one of
              // the two things a user who cannot sign in can reach at all
              // (`login_screen.dart`, and `inactive_dashboard_screen` for an
              // account nobody has activated), and Kotlin puts no user test
              // anywhere on the path — `FeedbackComposerViewModel.kt:38`
              // defaults the name to `""` and files the document. Disabling
              // the button here was the port's own invention, and it made the
              // front door's feedback button lead to a form with a dead
              // Submit.
              child: FilledButton(
                onPressed: _submit,
                child: Padding(
                  padding: const EdgeInsets.symmetric(vertical: 12),
                  child: Text(l10n.submit),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _submit() async {
    final l10n = AppLocalizations.of(context);
    final message = _messageController.text.trim();

    // Validate
    bool hasError = false;

    if (message.isEmpty) {
      setState(() => _messageError = l10n.pleaseEnterFeedback);
      hasError = true;
    }

    if (_type.isEmpty) {
      setState(() => _typeError = l10n.feedbackTypeRequired);
      hasError = true;
    }

    if (hasError) return;

    // No session read here at all — not a fixed one, an absent one. This used
    // to be `ref.read(sessionProvider).value` followed by a bare `return`, on
    // a screen that never watched the provider: the port's standing trap,
    // where a still-loading session silently discarded the form with no
    // snackbar, no error and no row (Phase 100's `_submitExam`, same shape).
    // The session is a *value on the document* rather than a precondition, so
    // the notifier resolves it with `await ...future` — see
    // `FeedbackCreateNotifier.submit`, which holds the Kotlin citations.
    final messenger = ScaffoldMessenger.of(context);

    // Going through the notifier rather than calling `createFeedback` directly
    // is what gets the feedback off the device: `submit` follows the write with
    // `queuePending()`, and nothing else in the app hands feedback to the
    // outbox. Calling the repository straight from here saved the row to disk
    // and left it there forever.
    final notifier = ref.read(feedbackCreateProvider.notifier)
      ..setPriority(_priority)
      ..setType(_type)
      ..setMessage(message);

    final saved = await notifier.submit(
      item: widget.item,
      feedbackState: widget.state,
    );
    if (!mounted) return;

    if (saved) {
      messenger.showSnackBar(SnackBar(content: Text(l10n.feedbackSaved)));
      context.pop();
      return;
    }

    final error = ref.read(feedbackCreateProvider).error;
    messenger.showSnackBar(
      SnackBar(content: Text('${l10n.error}: ${error ?? ''}')),
    );
  }
}
