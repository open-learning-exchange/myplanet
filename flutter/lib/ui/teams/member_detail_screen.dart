import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../l10n/app_localizations.dart';
import '../../providers/teams_provider.dart';
import '../components/profile_avatar.dart';

/// Port of `ui/teams/members/MembersDetailFragment.kt`.
///
/// Reached by tapping a member in the team members list. Shows the member's
/// profile photo, full name, and the fields the Kotlin card does: email, date
/// of birth, language, phone, level, number of (team) visits, and last login.
/// Empty/blank fields are hidden — the Kotlin's `setFieldOrHide` does the same,
/// so a member with no email set never shows an empty "Email" row.
class MemberDetailScreen extends ConsumerWidget {
  const MemberDetailScreen({
    required this.teamId,
    required this.userId,
    super.key,
  });

  final String teamId;
  final String userId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final detail = ref.watch(
      memberDetailProvider((teamId: teamId, userId: userId)),
    );

    return Scaffold(
      appBar: AppBar(title: Text(l10n.memberDetail)),
      body: detail.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (_, _) => Center(child: Text(l10n.membersUnavailable)),
        data: (data) => data == null
            ? Center(child: Text(l10n.unknownMember))
            : _MemberDetailBody(detail: data),
      ),
    );
  }
}

class _MemberDetailBody extends ConsumerWidget {
  const _MemberDetailBody({required this.detail});
  final MemberDetail detail;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final user = detail.user;

    final fullName = [
      user.firstName,
      user.lastName,
    ].where((p) => p != null && p.trim().isNotEmpty).join(' ');
    final displayName = fullName.isEmpty ? (user.name ?? user.id) : fullName;

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Center(
          child: Column(
            children: [
              ProfileAvatar(user: user, radius: 48),
              const SizedBox(height: 12),
              Text(
                displayName,
                style: theme.textTheme.headlineSmall,
                textAlign: TextAlign.center,
              ),
              if (detail.isLeader) ...[
                const SizedBox(height: 4),
                Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(
                      Icons.star,
                      size: 18,
                      color: theme.colorScheme.primary,
                    ),
                    const SizedBox(width: 4),
                    Text(l10n.leader),
                  ],
                ),
              ],
            ],
          ),
        ),
        const SizedBox(height: 24),
        _DetailField(
          icon: Icons.alternate_email,
          label: l10n.username,
          value: _clean(user.name),
        ),
        _DetailField(
          icon: Icons.email_outlined,
          label: l10n.email,
          value: _clean(user.email),
        ),
        _DetailField(
          icon: Icons.cake_outlined,
          label: l10n.dateOfBirth,
          value: _cleanDate(user.dob),
        ),
        _DetailField(
          icon: Icons.language,
          label: l10n.language,
          value: _clean(user.language),
        ),
        _DetailField(
          icon: Icons.phone_outlined,
          label: l10n.phoneNumber,
          value: _clean(user.phoneNumber),
        ),
        _DetailField(
          icon: Icons.bar_chart_outlined,
          label: l10n.level,
          value: _clean(user.level),
        ),
        _DetailField(
          icon: Icons.visibility_outlined,
          label: l10n.numberOfVisits,
          value: detail.visitCount.toString(),
        ),
        _DetailField(
          icon: Icons.login_outlined,
          label: l10n.lastLogin,
          value: detail.lastLogin == null
              ? l10n.noLogoutRecord
              : _formatTimestamp(detail.lastLogin!, l10n),
        ),
      ],
    );
  }

  String _clean(String? value) {
    if (value == null) return '';
    final trimmed = value.trim();
    if (trimmed.isEmpty || trimmed == 'null') return '';
    return trimmed;
  }

  String _cleanDate(String? value) {
    if (value == null) return '';
    final trimmed = value.trim();
    if (trimmed.isEmpty || trimmed == 'null') return '';
    // The Kotlin passes `dob.substringBefore("T")`, so a full ISO timestamp
    // is cut to its date. Same here.
    return trimmed.split('T').first;
  }

  String _formatTimestamp(int millis, AppLocalizations l10n) {
    final dt = DateTime.fromMillisecondsSinceEpoch(millis, isUtc: true);
    String two(int n) => n.toString().padLeft(2, '0');
    return '${dt.year}-${two(dt.month)}-${two(dt.day)} '
        '${two(dt.hour)}:${two(dt.minute)}';
  }
}

/// A labelled row that hides itself entirely when its value is empty — the
/// port of `MembersDetailFragment.setFieldOrHide`, which also hides the
/// wrapping parent. Hiding the row (rather than rendering a blank value)
/// matches the Kotlin exactly, so a member with no email set shows no email
/// row at all.
class _DetailField extends StatelessWidget {
  const _DetailField({
    required this.icon,
    required this.label,
    required this.value,
  });

  final IconData icon;
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    if (value.isEmpty) return const SizedBox.shrink();
    final theme = Theme.of(context);
    return Card(
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        child: Row(
          children: [
            Icon(icon, color: theme.colorScheme.primary),
            const SizedBox(width: 16),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    label,
                    style: theme.textTheme.labelSmall?.copyWith(
                      color: theme.colorScheme.onSurfaceVariant,
                    ),
                  ),
                  const SizedBox(height: 2),
                  Text(value, style: theme.textTheme.bodyLarge),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
