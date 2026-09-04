import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../l10n/app_localizations.dart';
import '../router.dart';

/// Port of `DialogUtils.guestDialog` — a guest tapping a members-only feature
/// is offered membership instead.
void showGuestDialog(BuildContext context) {
  final l10n = AppLocalizations.of(context);
  showDialog<void>(
    context: context,
    builder: (dialogContext) => AlertDialog(
      title: Text(l10n.becomeMember),
      content: Text(l10n.toAccessThisFeatureBecomeAMember),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(dialogContext).pop(),
          child: Text(l10n.cancel),
        ),
        FilledButton(
          onPressed: () {
            Navigator.of(dialogContext).pop();
            context.push(Routes.becomeMember);
          },
          child: Text(l10n.becomeMember),
        ),
      ],
    ),
  );
}
