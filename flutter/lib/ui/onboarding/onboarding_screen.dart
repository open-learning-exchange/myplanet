import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../l10n/app_localizations.dart';
import '../../providers/app_providers.dart';

/// Port of `ui/onboarding/OnboardingActivity.kt` and `OnboardingAdapter.kt`.
class OnboardingScreen extends ConsumerStatefulWidget {
  const OnboardingScreen({super.key, this.onFinished});

  /// Test hook. Production navigation is driven by the router redirect after
  /// [onboardingProvider] changes, so no imperative route push is necessary.
  final VoidCallback? onFinished;

  @override
  ConsumerState<OnboardingScreen> createState() => _OnboardingScreenState();
}

class _OnboardingScreenState extends ConsumerState<OnboardingScreen> {
  final PageController _controller = PageController();
  int _page = 0;
  bool _finishing = false;

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  Future<void> _finish() async {
    if (_finishing) return;
    setState(() => _finishing = true);
    try {
      await ref.read(onboardingProvider.notifier).complete();
      if (mounted) widget.onFinished?.call();
    } finally {
      // Persistence can throw; leaving _finishing set would disable both Skip
      // and Get started and strand the user on onboarding.
      if (mounted) setState(() => _finishing = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final pages = <_OnboardingPageData>[
      _OnboardingPageData(
        icon: Icons.public,
        title: l10n.welcomeToMyPlanet,
        description: l10n.onboardingWelcomeDescription,
      ),
      _OnboardingPageData(
        icon: Icons.offline_pin,
        title: l10n.learnOffline,
        description: l10n.onboardingOfflineDescription,
      ),
      _OnboardingPageData(
        icon: Icons.auto_stories,
        title: l10n.openLearning,
        description: l10n.onboardingOpenDescription,
      ),
      _OnboardingPageData(
        icon: Icons.rocket_launch,
        title: l10n.unleashLearningPower,
        description: l10n.onboardingPowerDescription,
      ),
    ];
    final lastPage = _page == pages.length - 1;

    return Scaffold(
      body: SafeArea(
        child: Column(
          children: [
            Align(
              alignment: AlignmentDirectional.centerEnd,
              child: AnimatedOpacity(
                opacity: lastPage ? 0 : 1,
                duration: const Duration(milliseconds: 200),
                // Opacity 0 still leaves the button in the semantics tree, so
                // a screen reader would announce a dead "Skip" on the last page.
                child: ExcludeSemantics(
                  excluding: lastPage,
                  child: TextButton(
                    onPressed: lastPage || _finishing ? null : _finish,
                    child: Text(l10n.skip),
                  ),
                ),
              ),
            ),
            Expanded(
              child: PageView.builder(
                controller: _controller,
                itemCount: pages.length,
                onPageChanged: (page) => setState(() => _page = page),
                itemBuilder: (context, index) => _OnboardingPage(
                  data: pages[index],
                  serverHint: index == pages.length - 1
                      ? l10n.onboardingServerPrepHint
                      : null,
                ),
              ),
            ),
            Semantics(
              label: l10n.pageProgress(_page + 1, pages.length),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: List.generate(
                  pages.length,
                  (index) => AnimatedContainer(
                    duration: const Duration(milliseconds: 200),
                    margin: const EdgeInsets.all(4),
                    width: index == _page ? 24 : 8,
                    height: 8,
                    decoration: BoxDecoration(
                      color: index == _page
                          ? Theme.of(context).colorScheme.primary
                          : Theme.of(context).colorScheme.outlineVariant,
                      borderRadius: BorderRadius.circular(4),
                    ),
                  ),
                ),
              ),
            ),
            Padding(
              padding: const EdgeInsets.all(24),
              child: FilledButton(
                onPressed: _finishing
                    ? null
                    : lastPage
                    ? _finish
                    : () => _controller.nextPage(
                        duration: const Duration(milliseconds: 300),
                        curve: Curves.easeOut,
                      ),
                child: Text(lastPage ? l10n.getStarted : l10n.next),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _OnboardingPageData {
  const _OnboardingPageData({
    required this.icon,
    required this.title,
    required this.description,
  });

  final IconData icon;
  final String title;
  final String description;
}

class _OnboardingPage extends StatelessWidget {
  const _OnboardingPage({required this.data, this.serverHint});

  final _OnboardingPageData data;
  final String? serverHint;

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      padding: const EdgeInsets.symmetric(horizontal: 32, vertical: 16),
      child: Column(
        children: [
          const SizedBox(height: 32),
          Icon(
            data.icon,
            size: 128,
            color: Theme.of(context).colorScheme.primary,
          ),
          const SizedBox(height: 40),
          Text(data.title, style: Theme.of(context).textTheme.headlineMedium),
          const SizedBox(height: 20),
          Text(
            data.description,
            textAlign: TextAlign.center,
            style: Theme.of(context).textTheme.bodyLarge,
          ),
          if (serverHint != null) ...[
            const SizedBox(height: 32),
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Row(
                  children: [
                    const Icon(Icons.info_outline),
                    const SizedBox(width: 12),
                    Expanded(child: Text(serverHint!)),
                  ],
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }
}
