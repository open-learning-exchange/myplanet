import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../l10n/app_localizations.dart';
import '../../providers/app_providers.dart';
import '../router.dart';
import '../calendar/calendar_screen.dart';
import '../voices/voices_screen.dart';
import 'leaders_screen.dart';
import 'services_screen.dart';

/// Port of `ui/community/CommunityTabFragment.kt` and
/// `ui/community/HomeCommunityDialogFragment`.
///
/// A tabbed community view showing Voices, Leaders, Calendar, and optionally
/// Services, Finances, and Reports. The tabs vary based on whether the user
/// is logging in (3 tabs) or already logged in (6 tabs).
class CommunityScreen extends ConsumerStatefulWidget {
  const CommunityScreen({
    this.fromLogin = false,
    this.communityId,
    super.key,
  });

  /// Whether this screen is shown during login flow.
  final bool fromLogin;

  /// The community identifier (e.g., "planetcode@parentcode").
  final String? communityId;

  @override
  ConsumerState<CommunityScreen> createState() => _CommunityScreenState();
}

class _CommunityScreenState extends ConsumerState<CommunityScreen>
    with SingleTickerProviderStateMixin {
  late TabController _tabController;
  late int _tabCount;
  late List<Widget> _tabs;
  late List<Widget> _tabViews;

  @override
  void initState() {
    super.initState();
    _buildTabs();
    _tabController = TabController(length: _tabCount, vsync: this);
  }

  void _buildTabs() {
    final l10n = AppLocalizations.of(context);
    final prefs = ref.read(planetPrefsProvider);
    final isCommunity = prefs.planetType == 'community';

    _tabs = [];
    _tabViews = [];

    // Voices tab
    _tabs.add(Text(l10n.ourVoices));
    _tabViews.add(const VoicesScreen());

    // Leaders tab
    final leadersLabel = isCommunity ? l10n.communityLeaders : l10n.nationLeaders;
    _tabs.add(Text(leadersLabel));
    _tabViews.add(const LeadersScreen());

    // Calendar tab
    _tabs.add(Text(l10n.calendar));
    _tabViews.add(const CalendarScreen());

    // Additional tabs only shown when not from login
    if (!widget.fromLogin) {
      // Services tab
      _tabs.add(Text(l10n.services));
      _tabViews.add(const ServicesScreen());

      // Finances tab
      _tabs.add(Text(l10n.finances));
      _tabViews.add(const _PlaceholderTab(label: l10n.finances));

      // Reports tab
      _tabs.add(Text(l10n.reports));
      _tabViews.add(const _PlaceholderTab(label: l10n.reports));
    }

    _tabCount = _tabs.length;
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final prefs = ref.watch(planetPrefsProvider);
    final communityName = prefs.communityName;
    final planetType = prefs.planetType;

    return Scaffold(
      appBar: AppBar(
        title: Text(communityName.isNotEmpty ? communityName : l10n.community),
        bottom: TabBar(
          isScrollable: true,
          controller: _tabController,
          tabs: _tabs,
        ),
      ),
      body: TabBarView(
        controller: _tabController,
        children: _tabViews,
      ),
    );
  }
}

/// Placeholder for tabs not yet fully implemented.
class _PlaceholderTab extends ConsumerWidget {
  const _PlaceholderTab({required this.label});

  final String label;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(
            Icons.construction_outlined,
            size: 64,
            color: Theme.of(context).colorScheme.primary,
          ),
          const SizedBox(height: 16),
          Text(
            '$label ${AppLocalizations.of(context).unavailable}',
            style: Theme.of(context).textTheme.titleMedium,
          ),
        ],
      ),
    );
  }
}

/// Bottom sheet variant shown from the home/dashboard.
///
/// Port of `ui/community/HomeCommunityDialogFragment.kt`.
class CommunityBottomSheet extends ConsumerStatefulWidget {
  const CommunityBottomSheet({super.key});

  @override
  ConsumerState<CommunityBottomSheet> createState() =>
      _CommunityBottomSheetState();
}

class _CommunityBottomSheetState extends ConsumerState<CommunityBottomSheet>
    with SingleTickerProviderStateMixin {
  late TabController _tabController;

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 3, vsync: this);
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final prefs = ref.watch(planetPrefsProvider);
    final isCommunity = prefs.planetType == 'community';
    final communityName = prefs.communityName;
    final planetType = prefs.planetType;

    return DraggableScrollableSheet(
      initialChildSize: 0.15,
      minChildSize: 0.1,
      maxChildSize: 0.9,
      builder: (context, scrollController) {
        return Container(
          decoration: BoxDecoration(
            color: Theme.of(context).colorScheme.surface,
            borderRadius: const BorderRadius.vertical(top: Radius.circular(16)),
            boxShadow: [
              BoxShadow(
                color: Colors.black.withValues(alpha: 0.1),
                blurRadius: 10,
                offset: const Offset(0, -2),
              ),
            ],
          ),
          child: Column(
            children: [
              // Handle bar
              Container(
                margin: const EdgeInsets.symmetric(vertical: 12),
                width: 40,
                height: 4,
                decoration: BoxDecoration(
                  color: Theme.of(context).colorScheme.outline,
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
              // Header
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16),
                child: Row(
                  children: [
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            communityName.isNotEmpty
                                ? communityName
                                : l10n.community,
                            style: Theme.of(context).textTheme.titleLarge,
                          ),
                          if (planetType.isNotEmpty)
                            Text(
                              planetType,
                              style: Theme.of(context).textTheme.bodySmall,
                            ),
                        ],
                      ),
                    ),
                    IconButton(
                      icon: const Icon(Icons.open_in_full),
                      onPressed: () {
                        Navigator.of(context).pop();
                        context.push(Routes.community);
                      },
                    ),
                  ],
                ),
              ),
              // Tabs
              TabBar(
                isScrollable: true,
                controller: _tabController,
                tabs: [
                  Text(l10n.ourVoices),
                  Text(isCommunity ? l10n.communityLeaders : l10n.nationLeaders),
                  Text(l10n.calendar),
                ],
              ),
              // Tab content
              Expanded(
                child: TabBarView(
                  controller: _tabController,
                  children: const [
                    VoicesScreen(),
                    LeadersScreen(),
                    CalendarScreen(),
                  ],
                ),
              ),
            ],
          ),
        );
      },
    );
  }
}
