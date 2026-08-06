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

  @override
  void dispose() {
    _comment.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final summary = ref.watch(ratingSummaryProvider(widget.target));
    final existing = summary.valueOrNull;
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

  Future<void> _submit() async {
    setState(() => _submitting = true);
    await ref
        .read(ratingActionsProvider)
        .submit(
          target: widget.target,
          title: widget.title,
          rate: _rating,
          comment: _comment.text,
        );
    if (mounted) Navigator.pop(context, true);
  }
}
