import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../data/local/app_database.dart';
import '../../l10n/app_localizations.dart';
import '../../providers/activities_provider.dart';
import '../../providers/session_provider.dart';
import '../../providers/notifications_provider.dart';
import '../components/relative_time.dart';
import '../components/profile_avatar.dart';
import '../dashboard/dashboard_shell.dart';
import '../router.dart';

/// Port of the read-only state of `ui/user/UserProfileFragment.kt`.
///
/// Profile editing, photos, and server upload remain separate write paths. This
/// screen deliberately starts with the locally cached [UserRow], so a learner
/// can inspect their identity and account metadata without a network request.
class ProfileScreen extends ConsumerWidget {
  const ProfileScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final session = ref.watch(sessionProvider);
    final unread = ref.watch(unreadNotificationCountProvider).valueOrNull ?? 0;

    return Scaffold(
      appBar: AppBar(
        title: Text(l10n.profile),
        actions: [
          IconButton(
            tooltip: l10n.notifications,
            onPressed: () => context.push(Routes.notifications),
            icon: Badge(
              isLabelVisible: unread > 0,
              label: Text(unread > 99 ? '99+' : '$unread'),
              child: const Icon(Icons.notifications_outlined),
            ),
          ),
          IconButton(
            tooltip: l10n.settings,
            onPressed: () => context.push(Routes.settings),
            icon: const Icon(Icons.settings_outlined),
          ),
          if (session.valueOrNull != null)
            IconButton(
              tooltip: l10n.editProfile,
              onPressed: () =>
                  _showProfileEditor(context, ref, session.valueOrNull!),
              icon: const Icon(Icons.edit_outlined),
            ),
          const LogoutAction(),
        ],
      ),
      body: session.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => Center(child: Text(l10n.profileUnavailable)),
        data: (user) => user == null
            ? Center(child: Text(l10n.profileUnavailable))
            : _ProfileBody(user: user),
      ),
    );
  }
}

Future<void> _showProfileEditor(
  BuildContext context,
  WidgetRef ref,
  UserRow user,
) async {
  final result = await showDialog<_ProfileFormValue>(
    context: context,
    builder: (context) => _EditProfileDialog(user: user),
  );
  if (result == null || !context.mounted) return;

  await ref
      .read(sessionProvider.notifier)
      .updateProfile(
        firstName: result.firstName,
        middleName: result.middleName,
        lastName: result.lastName,
        email: result.email,
        phoneNumber: result.phoneNumber,
        level: result.level,
        language: result.language,
        gender: result.gender,
        dateOfBirth: result.dateOfBirth,
      );
  if (context.mounted) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(AppLocalizations.of(context).profileSaved)),
    );
  }
}

class _EditProfileDialog extends StatefulWidget {
  const _EditProfileDialog({required this.user});

  final UserRow user;

  @override
  State<_EditProfileDialog> createState() => _EditProfileDialogState();
}

class _EditProfileDialogState extends State<_EditProfileDialog> {
  final _formKey = GlobalKey<FormState>();
  late final Map<String, TextEditingController> _controllers;

  @override
  void initState() {
    super.initState();
    final user = widget.user;
    _controllers = {
      'firstName': TextEditingController(text: user.firstName),
      'middleName': TextEditingController(text: user.middleName),
      'lastName': TextEditingController(text: user.lastName),
      'email': TextEditingController(text: user.email),
      'phone': TextEditingController(text: user.phoneNumber),
      'level': TextEditingController(text: user.level),
      'language': TextEditingController(text: user.language),
      'gender': TextEditingController(text: user.gender),
      'dob': TextEditingController(text: user.dob),
    };
  }

  @override
  void dispose() {
    for (final controller in _controllers.values) {
      controller.dispose();
    }
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return AlertDialog(
      title: Text(l10n.editProfile),
      content: SizedBox(
        width: 480,
        child: Form(
          key: _formKey,
          child: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                _field('firstName', l10n.firstName, autofocus: true),
                _field('middleName', l10n.middleName),
                _field('lastName', l10n.lastName),
                _field(
                  'email',
                  l10n.email,
                  keyboardType: TextInputType.emailAddress,
                  validator: _validateEmail,
                ),
                _field('phone', l10n.phone, keyboardType: TextInputType.phone),
                _field('level', l10n.level),
                _field('language', l10n.language),
                _field('gender', l10n.gender),
                _field(
                  'dob',
                  l10n.dateOfBirth,
                  keyboardType: TextInputType.datetime,
                ),
              ],
            ),
          ),
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: Text(l10n.cancel),
        ),
        FilledButton(onPressed: _save, child: Text(l10n.save)),
      ],
    );
  }

  Widget _field(
    String key,
    String label, {
    bool autofocus = false,
    TextInputType? keyboardType,
    String? Function(String?)? validator,
  }) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: TextFormField(
        controller: _controllers[key],
        autofocus: autofocus,
        keyboardType: keyboardType,
        decoration: InputDecoration(labelText: label),
        textInputAction: TextInputAction.next,
        validator: validator,
      ),
    );
  }

  String? _validateEmail(String? value) {
    final email = value?.trim() ?? '';
    if (email.isEmpty ||
        RegExp(r'^[^@\s]+@[^@\s]+\.[^@\s]+$').hasMatch(email)) {
      return null;
    }
    return AppLocalizations.of(context).invalidEmail;
  }

  void _save() {
    if (!(_formKey.currentState?.validate() ?? false)) return;
    Navigator.pop(
      context,
      _ProfileFormValue(
        firstName: _controllers['firstName']!.text,
        middleName: _controllers['middleName']!.text,
        lastName: _controllers['lastName']!.text,
        email: _controllers['email']!.text,
        phoneNumber: _controllers['phone']!.text,
        level: _controllers['level']!.text,
        language: _controllers['language']!.text,
        gender: _controllers['gender']!.text,
        dateOfBirth: _controllers['dob']!.text,
      ),
    );
  }
}

class _ProfileFormValue {
  const _ProfileFormValue({
    required this.firstName,
    required this.middleName,
    required this.lastName,
    required this.email,
    required this.phoneNumber,
    required this.level,
    required this.language,
    required this.gender,
    required this.dateOfBirth,
  });

  final String firstName;
  final String middleName;
  final String lastName;
  final String email;
  final String phoneNumber;
  final String level;
  final String language;
  final String gender;
  final String dateOfBirth;
}

class _ProfileBody extends ConsumerWidget {
  const _ProfileBody({required this.user});

  final UserRow user;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final name = _displayName(user);
    final details = <_ProfileDetail>[
      _ProfileDetail(Icons.alternate_email, l10n.username, user.name),
      _ProfileDetail(Icons.email_outlined, l10n.email, user.email),
      _ProfileDetail(Icons.phone_outlined, l10n.phone, user.phoneNumber),
      _ProfileDetail(Icons.school_outlined, l10n.level, user.level),
      _ProfileDetail(Icons.language_outlined, l10n.language, user.language),
      _ProfileDetail(Icons.person_outline, l10n.gender, user.gender),
      _ProfileDetail(Icons.cake_outlined, l10n.dateOfBirth, user.dob),
      _ProfileDetail(
        Icons.hub_outlined,
        l10n.planetCode,
        user.planetCode ?? user.parentCode,
      ),
    ].where((detail) => detail.value?.trim().isNotEmpty ?? false).toList();

    return ListView(
      padding: const EdgeInsets.fromLTRB(20, 32, 20, 40),
      children: [
        Center(
          child: Semantics(
            label: l10n.profilePhotoFor(name),
            image: true,
            child: ProfileAvatar(user: user, radius: 48),
          ),
        ),
        const SizedBox(height: 16),
        Text(
          name,
          textAlign: TextAlign.center,
          style: theme.textTheme.headlineSmall,
        ),
        if (user.rolesList.isNotEmpty) ...[
          const SizedBox(height: 8),
          Wrap(
            alignment: WrapAlignment.center,
            spacing: 8,
            runSpacing: 4,
            children: user.rolesList
                .map((role) => Chip(label: Text(role)))
                .toList(),
          ),
        ],
        const SizedBox(height: 28),
        Text(l10n.accountDetails, style: theme.textTheme.titleMedium),
        const SizedBox(height: 8),
        if (details.isEmpty)
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 24),
            child: Text(l10n.noProfileDetails),
          )
        else
          _DetailCard(details: details),
        ..._activitySection(context, ref),
      ],
    );
  }

  /// Port of `UserProfileFragment.createStatsMap`, minus its community-name row
  /// — the port already shows the planet code among the account details above.
  ///
  /// The Kotlin renders every row unconditionally, showing "N/A" through
  /// `Utilities.checkNA` when a value is missing; here an absent value drops the
  /// row, which is what the rest of this screen already does with account
  /// details. The whole block is absent until the activity log has something in
  /// it, rather than showing a card of zeroes and N/As on a fresh install.
  List<Widget> _activitySection(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final stats = ref.watch(profileActivityStatsProvider).valueOrNull;
    if (stats == null) return const [];

    final lastVisit = stats.lastVisit;
    final mostOpened = stats.mostOpened;
    final rows = <_ProfileDetail>[
      if (lastVisit != null)
        _ProfileDetail(
          Icons.login_outlined,
          l10n.lastLogin,
          relativeTimeLabel(
            l10n,
            DateTime.now().millisecondsSinceEpoch - lastVisit,
          ),
        ),
      if (stats.offlineVisits > 0)
        _ProfileDetail(
          Icons.history_outlined,
          l10n.totalVisits,
          '${stats.offlineVisits}',
        ),
      if (mostOpened != null)
        _ProfileDetail(
          Icons.star_outline,
          l10n.mostOpenedResource,
          l10n.resourceOpenedTimes(mostOpened.title, mostOpened.count),
        ),
      if (stats.resourceOpenCount > 0)
        _ProfileDetail(
          Icons.menu_book_outlined,
          l10n.numberOfResourcesOpened,
          l10n.resourcesOpenedTimes(stats.resourceOpenCount),
        ),
    ];
    if (rows.isEmpty) return const [];

    return [
      const SizedBox(height: 28),
      Text(l10n.activitySummary, style: theme.textTheme.titleMedium),
      const SizedBox(height: 8),
      _DetailCard(details: rows),
    ];
  }
}

class _DetailCard extends StatelessWidget {
  const _DetailCard({required this.details});

  final List<_ProfileDetail> details;

  @override
  Widget build(BuildContext context) {
    return Card(
      clipBehavior: Clip.antiAlias,
      child: Column(
        children: [
          for (var index = 0; index < details.length; index++) ...[
            _DetailTile(detail: details[index]),
            if (index != details.length - 1)
              const Divider(height: 1, indent: 56),
          ],
        ],
      ),
    );
  }
}

class _ProfileDetail {
  const _ProfileDetail(this.icon, this.label, this.value);

  final IconData icon;
  final String label;
  final String? value;
}

class _DetailTile extends StatelessWidget {
  const _DetailTile({required this.detail});

  final _ProfileDetail detail;

  @override
  Widget build(BuildContext context) {
    return ListTile(
      leading: Icon(detail.icon),
      title: Text(detail.label),
      subtitle: Text(detail.value!.trim()),
    );
  }
}

String _displayName(UserRow user) {
  final parts = [user.firstName, user.middleName, user.lastName]
      .whereType<String>()
      .map((part) => part.trim())
      .where((part) => part.isNotEmpty);
  final fullName = parts.join(' ');
  if (fullName.isNotEmpty) return fullName;
  final username = user.name?.trim();
  return username == null || username.isEmpty ? 'myPlanet learner' : username;
}
