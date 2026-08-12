import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../data/local/app_database.dart';
import '../../l10n/app_localizations.dart';
import '../../providers/app_providers.dart';
import '../../providers/courses_providers.dart';
import '../../providers/dashboard_providers.dart';
import '../../providers/life_provider.dart';
import '../../providers/notifications_provider.dart';
import '../../providers/session_provider.dart';
import '../components/guest_dialog.dart';
import '../life/life_features.dart';
import '../router.dart';
import 'dashboard_shell.dart';

/// Port of `ui/dashboard/BellDashboardFragment.kt` — the home ("bell")
/// dashboard: the profile card and the four myLibrary / myCourses / myTeams /
/// myLife cards, plus the pending-survey dialog.
///
/// Deliberately not (yet) ported from the Kotlin home screen, all tracked in
/// the migration doc: the completed-course star row (needs per-step progress
/// data the port does not sync), the network-status ring around the avatar
/// (needs a connectivity plugin), team chat/task alert badges (needs team
/// notifications), the offline-logins count in the name line and the activity
/// chart FAB (needs login activity tracking), the last-sync strip, and the
/// "remind later" survey scheduler.
class HomeScreen extends ConsumerStatefulWidget {
  const HomeScreen({super.key});

  @override
  ConsumerState<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends ConsumerState<HomeScreen> {
  /// `BellDashboardFragment` throttles the dialog to one show per hour.
  static const Duration _surveyDialogInterval = Duration(hours: 1);

  bool _surveyCheckDone = false;

  @override
  void initState() {
    super.initState();
    // The session restores asynchronously, so a one-shot post-frame check
    // would race it and silently never run. The Kotlin fires this when the
    // user first loads (`wasUserNull` in `onViewCreated`); listening to the
    // session is the same trigger.
    ref.listenManual(sessionProvider, fireImmediately: true, (previous, next) {
      final user = next.valueOrNull;
      if (user != null) {
        WidgetsBinding.instance.addPostFrameCallback(
          (_) => _checkPendingSurveys(user),
        );
      }
    });
  }

  Future<void> _checkPendingSurveys(UserRow session) async {
    if (_surveyCheckDone || !mounted) return;
    _surveyCheckDone = true;

    // Guests never see the dialog, exactly as the Kotlin guards it.
    if (session.id.startsWith('guest')) return;

    final prefs = ref.read(planetPrefsProvider);
    final now = DateTime.now().millisecondsSinceEpoch;
    if (now - prefs.lastSurveyDialogShown <
        _surveyDialogInterval.inMilliseconds) {
      return;
    }

    final pending = await ref.read(pendingSurveysProvider(session.id).future);
    if (pending.isEmpty || !mounted) return;

    // The "shown at" stamp is written when the dialog appears, not when it is
    // answered — dismissing it still starts the hour, as the Kotlin does.
    await prefs.setLastSurveyDialogShown(now);
    if (!mounted) return;

    final l10n = AppLocalizations.of(context);
    await showDialog<void>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: Text(l10n.surveysToComplete(pending.length)),
        content: SizedBox(
          width: double.maxFinite,
          child: ListView(
            shrinkWrap: true,
            children: [
              for (final survey in pending)
                ListTile(
                  title: Text(survey.name),
                  onTap: () {
                    Navigator.of(dialogContext).pop();
                    context.push(Routes.surveys);
                  },
                ),
            ],
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(),
            child: Text(l10n.cancel),
          ),
          FilledButton(
            onPressed: () {
              Navigator.of(dialogContext).pop();
              context.push(Routes.surveys);
            },
            child: Text(l10n.ok),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final session = ref.watch(sessionProvider).valueOrNull;
    final prefs = ref.watch(planetPrefsProvider);
    final unread = ref.watch(unreadNotificationCountProvider).valueOrNull ?? 0;

    // `DashboardActivity.updateAppTitle`: "Planet <planetCode>", falling back
    // to the community name from preferences.
    final planetCode = session?.planetCode ?? '';
    final title = planetCode.isNotEmpty
        ? 'Planet $planetCode'
        : (prefs.communityName.isNotEmpty
              ? 'Planet ${prefs.communityName}'
              : l10n.appTitle);

    final isGuest = session != null && session.id.startsWith('guest');

    return Scaffold(
      appBar: AppBar(
        title: Text(title),
        actions: [
          IconButton(
            tooltip: l10n.notifications,
            onPressed: () => context.push(Routes.notifications),
            icon: Badge.count(
              count: unread,
              isLabelVisible: unread > 0,
              child: const Icon(Icons.notifications_outlined),
            ),
          ),
          const LogoutAction(),
        ],
      ),
      body: session == null
          ? const Center(child: CircularProgressIndicator())
          : Column(
              children: [
                _ProfileCard(session: session),
                Expanded(
                  child: _HomeCard(
                    color: Colors.red.shade700,
                    icon: Icons.local_library_outlined,
                    label: l10n.homeMyLibrary,
                    count: ref
                        .watch(myLibraryStreamProvider(session.id))
                        .valueOrNull
                        ?.length,
                    onHeaderTap: () => isGuest
                        ? showGuestDialog(context)
                        : context.go(Routes.resources),
                    child: _LibraryTiles(userId: session.id, isGuest: isGuest),
                  ),
                ),
                Expanded(
                  child: _HomeCard(
                    color: Colors.amber.shade800,
                    icon: Icons.school_outlined,
                    label: l10n.homeMyCourses,
                    count: ref
                        .watch(myCoursesStreamProvider(session.id))
                        .valueOrNull
                        ?.length,
                    onHeaderTap: () {
                      if (isGuest) {
                        showGuestDialog(context);
                        return;
                      }
                      ref
                          .read(courseFilterProvider.notifier)
                          .setMyCoursesOnly(true);
                      context.go(Routes.courses);
                    },
                    child: _CourseTiles(userId: session.id, isGuest: isGuest),
                  ),
                ),
                Expanded(
                  child: _HomeCard(
                    color: Colors.green.shade700,
                    icon: Icons.groups_outlined,
                    label: l10n.homeMyTeams,
                    count: ref
                        .watch(myTeamsStreamProvider(session.id))
                        .valueOrNull
                        ?.length,
                    // The Kotlin myTeams header is not guest-gated.
                    onHeaderTap: () => context.push(Routes.teams),
                    child: _TeamTiles(userId: session.id),
                  ),
                ),
                Expanded(
                  child: _HomeCard(
                    color: Colors.purple.shade700,
                    icon: Icons.dashboard_customize_outlined,
                    label: l10n.homeMyLife,
                    // The Kotlin myLife card has no count badge.
                    count: null,
                    onHeaderTap: () => context.go(Routes.life),
                    child: _LifeTiles(isGuest: isGuest),
                  ),
                ),
              ],
            ),
    );
  }
}

/// The profile card: avatar, full name, role, planet code. Port of
/// `card_profile_bell.xml` minus the star row and network ring (see the
/// class comment on [HomeScreen]).
class _ProfileCard extends StatelessWidget {
  const _ProfileCard({required this.session});

  final UserRow session;

  @override
  Widget build(BuildContext context) {
    final fullName = [
      session.firstName,
      session.middleName,
      session.lastName,
    ].whereType<String>().where((part) => part.isNotEmpty).join(' ');
    final displayName = fullName.isNotEmpty ? fullName : (session.name ?? '');
    final role = session.rolesList.join(', ');

    return Card(
      margin: const EdgeInsets.all(8),
      child: InkWell(
        onTap: () => context.go(Routes.profile),
        child: Padding(
          padding: const EdgeInsets.all(8),
          child: Row(
            children: [
              const CircleAvatar(radius: 24, child: Icon(Icons.person)),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      displayName,
                      style: Theme.of(context).textTheme.titleMedium,
                      overflow: TextOverflow.ellipsis,
                    ),
                    if (role.isNotEmpty)
                      Text(
                        '- $role',
                        style: Theme.of(context).textTheme.bodySmall,
                      ),
                  ],
                ),
              ),
              if ((session.planetCode ?? '').isNotEmpty)
                Text(
                  session.planetCode!,
                  style: Theme.of(context).textTheme.bodySmall,
                ),
            ],
          ),
        ),
      ),
    );
  }
}

/// One dashboard card: the coloured header block (icon, label, count badge)
/// beside a horizontally scrolling grid of tiles. Port of the shared
/// `home_card_*.xml` structure.
class _HomeCard extends StatelessWidget {
  const _HomeCard({
    required this.color,
    required this.icon,
    required this.label,
    required this.count,
    required this.onHeaderTap,
    required this.child,
  });

  final Color color;
  final IconData icon;
  final String label;
  final int? count;
  final VoidCallback onHeaderTap;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      clipBehavior: Clip.antiAlias,
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          SizedBox(
            width: 96,
            child: Material(
              color: color,
              child: InkWell(
                onTap: onHeaderTap,
                child: Stack(
                  children: [
                    Center(
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Icon(icon, color: Colors.white, size: 32),
                          const SizedBox(height: 4),
                          Text(
                            label,
                            textAlign: TextAlign.center,
                            style: const TextStyle(
                              color: Colors.white,
                              fontSize: 12,
                            ),
                          ),
                        ],
                      ),
                    ),
                    // The count circle, `GONE` at zero like `hideCountIfZero`.
                    if (count != null && count! > 0)
                      Positioned(
                        top: 4,
                        right: 4,
                        child: CircleAvatar(
                          radius: 12,
                          backgroundColor: Colors.white,
                          child: Text(
                            '$count',
                            style: TextStyle(fontSize: 11, color: color),
                          ),
                        ),
                      ),
                  ],
                ),
              ),
            ),
          ),
          Expanded(child: child),
        ],
      ),
    );
  }
}

/// The 250×100 tile grid the Kotlin builds in a FlexboxLayout inside a
/// HorizontalScrollView: fixed-size tiles wrap vertically to fill the card and
/// overflow horizontally.
class _TileGrid extends StatelessWidget {
  const _TileGrid({required this.children});

  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    return GridView.builder(
      scrollDirection: Axis.horizontal,
      padding: const EdgeInsets.all(4),
      gridDelegate: const SliverGridDelegateWithMaxCrossAxisExtent(
        maxCrossAxisExtent: 56,
        mainAxisExtent: 180,
        mainAxisSpacing: 4,
        crossAxisSpacing: 4,
      ),
      itemCount: children.length,
      itemBuilder: (context, index) => children[index],
    );
  }
}

/// One tile, background alternating by index as `setBackgroundColor` does.
class _Tile extends StatelessWidget {
  const _Tile({
    required this.index,
    required this.onTap,
    required this.child,
    this.placeholder = false,
  });

  final int index;
  final VoidCallback onTap;
  final Widget child;
  final bool placeholder;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final background = placeholder
        ? scheme.surface
        : index.isEven
        ? scheme.surfaceContainerLowest
        : scheme.surfaceContainerHigh;
    return Material(
      color: background,
      borderRadius: BorderRadius.circular(4),
      child: InkWell(
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
          child: Center(child: child),
        ),
      ),
    );
  }
}

class _LibraryTiles extends ConsumerWidget {
  const _LibraryTiles({required this.userId, required this.isGuest});

  final String userId;
  final bool isGuest;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final resources =
        ref.watch(myLibraryStreamProvider(userId)).valueOrNull ?? const [];

    if (resources.isEmpty) {
      return _TileGrid(
        children: [
          _Tile(
            index: 0,
            placeholder: true,
            onTap: () => isGuest
                ? showGuestDialog(context)
                : context.go(Routes.resources),
            child: Text(
              l10n.noResourcesAddedYet,
              style: TextStyle(color: Theme.of(context).hintColor),
            ),
          ),
        ],
      );
    }

    return _TileGrid(
      children: [
        for (final (index, resource) in resources.indexed)
          _Tile(
            index: index,
            onTap: () => context.push('/resources/detail/${resource.id}'),
            child: Text(
              resource.title ?? '',
              maxLines: 3,
              overflow: TextOverflow.ellipsis,
            ),
          ),
      ],
    );
  }
}

class _CourseTiles extends ConsumerWidget {
  const _CourseTiles({required this.userId, required this.isGuest});

  final String userId;
  final bool isGuest;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final courses =
        ref.watch(myCoursesStreamProvider(userId)).valueOrNull ?? const [];

    if (courses.isEmpty) {
      return _TileGrid(
        children: [
          _Tile(
            index: 0,
            placeholder: true,
            onTap: () {
              if (isGuest) {
                showGuestDialog(context);
                return;
              }
              context.go(Routes.courses);
            },
            child: Text(
              l10n.noCoursesJoinedYet,
              style: TextStyle(color: Theme.of(context).hintColor),
            ),
          ),
        ],
      );
    }

    return _TileGrid(
      children: [
        for (final (index, course) in courses.indexed)
          _Tile(
            index: index,
            onTap: () => context.push('${Routes.courses}/${course.id}'),
            child: Text(
              course.courseTitle ?? '',
              maxLines: 3,
              overflow: TextOverflow.ellipsis,
            ),
          ),
      ],
    );
  }
}

class _TeamTiles extends ConsumerWidget {
  const _TeamTiles({required this.userId});

  final String userId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final teams =
        ref.watch(myTeamsStreamProvider(userId)).valueOrNull ?? const [];

    if (teams.isEmpty) {
      return _TileGrid(
        children: [
          _Tile(
            index: 0,
            placeholder: true,
            onTap: () => context.push(Routes.teams),
            child: Text(
              l10n.noTeamsJoinedYet,
              style: TextStyle(color: Theme.of(context).hintColor),
            ),
          ),
        ],
      );
    }

    return _TileGrid(
      children: [
        for (final (index, team) in teams.indexed)
          _Tile(
            index: index,
            onTap: () => context.push('${Routes.teams}/${team.id}'),
            child: Text(
              team.name ?? '',
              maxLines: 3,
              overflow: TextOverflow.ellipsis,
              // A sync-type team renders bold, as `renderMyTeams` does.
              style: team.type == 'sync'
                  ? const TextStyle(fontWeight: FontWeight.bold)
                  : null,
            ),
          ),
      ],
    );
  }
}

class _LifeTiles extends ConsumerWidget {
  const _LifeTiles({required this.isGuest});

  final bool isGuest;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final rows = (ref.watch(lifeItemsProvider).valueOrNull ?? const [])
        .where((row) => row.isVisible)
        .toList(growable: false);

    if (rows.isEmpty) {
      return _TileGrid(
        children: [
          _Tile(
            index: 0,
            placeholder: true,
            onTap: () => context.go(Routes.life),
            child: Text(
              l10n.noDataAvailable,
              style: TextStyle(color: Theme.of(context).hintColor),
            ),
          ),
        ],
      );
    }

    return _TileGrid(
      children: [
        for (final (index, row) in rows.indexed)
          _Tile(
            index: index,
            onTap: () {
              if (isGuest && guestGatedLifeFeatures.contains(row.feature)) {
                showGuestDialog(context);
                return;
              }
              openLifeFeature(context, row.feature);
            },
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(lifeFeatureIcon(row.feature), size: 24),
                const SizedBox(height: 2),
                Text(
                  lifeFeatureTitle(l10n, row.feature, row.title),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(fontSize: 11),
                ),
              ],
            ),
          ),
      ],
    );
  }
}
