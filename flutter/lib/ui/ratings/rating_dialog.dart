import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../l10n/app_localizations.dart';
import '../../providers/ratings_provider.dart';

/// Port of the rating dialog `ui/ratings/RatingsFragment.kt` inflates.
///
/// Kotlin builds this from `alert_rating.xml` inside an `AlertDialog.Builder`;
/// the layout and its wiring collapse into this one widget.
class RatingDialog extends ConsumerStatefulWidget {
  const RatingDialog({required this.target, required this.title, super.key});
  final RatingTarget target;
  final String title;

  @override
  ConsumerState<RatingDialog> createState() => _RatingDialogState();
}

class _RatingDialogState extends ConsumerState<RatingDialog> {
  final _comment = TextEditingController();
  int _rating = 0;
  bool _initialized = false;
  bool _submitting = false;
  bool _failed = false;

  @override
  void dispose() {
    _comment.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final summary = ref.watch(ratingSummaryProvider(widget.target));
    final existing = summary.value;
    if (!_initialized && existing != null) {
      _initialized = true;
      _rating = existing.userRating ?? 0;
      _comment.text = existing.userComment ?? '';
    }
    return AlertDialog(
      title: Text(l10n.rateTitle(widget.title)),
      content: summary.when(
        loading: () => const SizedBox(
          height: 120,
          child: Center(child: CircularProgressIndicator()),
        ),
        error: (_, _) => Text(l10n.ratingsUnavailable),
        data: (data) => Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (data.total > 0)
              Text(l10n.ratingSummary(data.average, data.total)),
            const SizedBox(height: 12),
            Semantics(
              label: l10n.ratingOutOfFive(_rating),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  for (var value = 1; value <= 5; value++)
                    IconButton(
                      tooltip: l10n.setRating(value),
                      onPressed: _submitting
                          ? null
                          : () => setState(() => _rating = value),
                      icon: Icon(
                        value <= _rating ? Icons.star : Icons.star_border,
                        color: Theme.of(context).colorScheme.primary,
                      ),
                    ),
                ],
              ),
            ),
            if (_rating == 0)
              Text(
                l10n.ratingRequired,
                style: const TextStyle(color: Colors.red),
              ),
            // `RatingsFragment:115-117` toasts `SubmitState.Error` and leaves
            // the dialog up — only `Success` reaches `dismiss()`. Drawn inline
            // rather than as a toast/snackbar because this dialog already
            // reports its other refusal that way (`ratingRequired` above), and
            // because a four-second snackbar behind a modal is the wrong
            // lifetime for a message whose whole job is to explain why the
            // dialog did not close.
            if (_failed)
              Padding(
                padding: const EdgeInsets.only(top: 8),
                child: Text(
                  l10n.ratingSubmitFailed,
                  style: TextStyle(color: Theme.of(context).colorScheme.error),
                ),
              ),
            const SizedBox(height: 12),
            TextField(
              controller: _comment,
              enabled: !_submitting,
              minLines: 2,
              maxLines: 4,
              decoration: InputDecoration(
                labelText: l10n.commentOptional,
                border: const OutlineInputBorder(),
              ),
            ),
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: _submitting ? null : () => Navigator.pop(context),
          child: Text(l10n.cancel),
        ),
        FilledButton(
          onPressed: _submitting || _rating == 0 ? null : _submit,
          child: _submitting
              ? const SizedBox.square(
                  dimension: 18,
                  child: CircularProgressIndicator(strokeWidth: 2),
                )
              : Text(l10n.submitRating),
        ),
      ],
    );
  }

  /// Port of `RatingsFragment.observeViewModel`'s `submitState` arm
  /// (`RatingsFragment.kt:101-122`).
  ///
  /// The result used to be thrown away and `Navigator.pop(context, true)`
  /// called unconditionally, so a rating that never reached the database — a
  /// still-unresolved session, a failing write, an outbox that could not take
  /// it — closed the dialog with the same gesture as one that had. Kotlin
  /// dismisses on `Success` only.
  ///
  /// `_submitting` is cleared before the message renders so Submit comes back:
  /// `updateSubmitButtonState` (`:147-151`) re-enables on anything that is not
  /// `Submitting`, so `Error` leaves the button live and the typed rating and
  /// comment in place for a retry.
  Future<void> _submit() async {
    setState(() {
      _submitting = true;
      _failed = false;
    });
    final saved = await ref
        .read(ratingActionsProvider)
        .submit(
          target: widget.target,
          title: widget.title,
          rate: _rating,
          comment: _comment.text,
        );
    if (!mounted) return;
    if (saved) {
      Navigator.pop(context, true);
      return;
    }
    setState(() {
      _submitting = false;
      _failed = true;
    });
  }
}
