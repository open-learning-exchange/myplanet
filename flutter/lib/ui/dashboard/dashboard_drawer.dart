import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../data/local/app_database.dart';
import '../../data/local/user_mapper.dart';
import '../../l10n/app_localizations.dart';
import '../../providers/session_provider.dart';
import '../components/guest_dialog.dart';
import '../router.dart';

/// Navigation drawer counterpart of `DashboardActivity`'s side navigation.
///
/// The bottom bar keeps the six primary destinations close at hand; the drawer
/// exposes the wider set of learning and community destinations without
/// forcing users to discover them through several nested screens.
class DashboardDrawer extends ConsumerWidget {
  const DashboardDrawer({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final session = ref.watch(sessionProvider).valueOrNull;
    final isGuest = session != null && UserMapper.isGuest(session);

    return NavigationDrawer(
      children: [
        _DrawerHeader(session: session),
        _destination(context, Icons.home_outlined, l10n.home, Routes.home),
        // The full Library and Courses lists are open to guests, exactly as
        // the Kotlin drawer's `menu_library`/`menu_courses` entries are —
        // browsing is what a guest account is for. Only the *my*-filtered
        // variants are members-only there.
        _destination(
          context,
          Icons.local_library_outlined,
          l10n.resources,
          Routes.resources,
        ),
        _destination(
          context,
          Icons.school_outlined,
          l10n.courses,
          Routes.courses,
        ),
        _destination(context, Icons.groups_outlined, l10n.teams, Routes.teams),
        _destination(
          context,
          Icons.calendar_month_outlined,
          l10n.calendar,
          Routes.calendar,
        ),
        _destination(
          context,
          Icons.dashboard_customize_outlined,
          l10n.myLife,
          Routes.life,
        ),
        const Divider(indent: 28, endIndent: 28),
        _destination(context, Icons.sync, l10n.syncCenter, Routes.syncCenter),
        _destination(
          context,
          Icons.public_outlined,
          l10n.community,
          Routes.community,
        ),
        _destination(
          context,
          Icons.forum_outlined,
          l10n.aiChat,
          Routes.chatHistory,
          guestBlocked: isGuest,
        ),
        _destination(
          context,
          Icons.feedback_outlined,
          l10n.feedback,
          Routes.feedback,
          guestBlocked: isGuest,
        ),
        _destination(
          context,
          Icons.bookmarks_outlined,
          l10n.references,
          Routes.references,
        ),
        _destination(
          context,
          Icons.settings_outlined,
          l10n.settings,
          Routes.settings,
        ),
      ],
    );
  }

  Widget _destination(
    BuildContext context,
    IconData icon,
    String label,
    String route, {
    bool guestBlocked = false,
  }) => ListTile(
    leading: Icon(icon),
    title: Text(label),
    onTap: () {
      Navigator.of(context).pop();
      if (guestBlocked) {
        showGuestDialog(context);
      } else {
        context.go(route);
      }
    },
  );
}

class _DrawerHeader extends StatelessWidget {
  const _DrawerHeader({required this.session});

  final UserRow? session;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final name = [
      session?.firstName,
      session?.lastName,
    ].whereType<String>().where((part) => part.isNotEmpty).join(' ');
    return DrawerHeader(
      child: Row(
        children: [
          const CircleAvatar(radius: 28, child: Icon(Icons.person_outline)),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  name.isEmpty ? (session?.name ?? l10n.appTitle) : name,
                  style: Theme.of(context).textTheme.titleMedium,
                ),
                if ((session?.planetCode ?? '').isNotEmpty)
                  Text(session!.planetCode!),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
