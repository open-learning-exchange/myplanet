import 'package:app_links/app_links.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/deeplinks/deep_link.dart';
import '../ui/router.dart';
import 'app_providers.dart';
import 'session_provider.dart';

/// Where incoming links come from.
///
/// An interface rather than `AppLinks` directly so the routing logic can be
/// driven from a test without a platform channel — `AppLinks` calls into a
/// method channel on construction, which under `flutter test` throws
/// `MissingPluginException`.
abstract interface class DeepLinkSource {
  /// The link that launched the app, if it was launched by one.
  Future<Uri?> initialLink();

  /// Links delivered while the app is already running.
  Stream<Uri> links();
}

/// `app_links`-backed [DeepLinkSource], the production implementation.
class AppLinksSource implements DeepLinkSource {
  AppLinksSource([AppLinks? links]) : _links = links ?? AppLinks();

  final AppLinks _links;

  @override
  Future<Uri?> initialLink() => _links.getInitialLink();

  @override
  Stream<Uri> links() => _links.uriLinkStream;
}

final deepLinkSourceProvider = Provider<DeepLinkSource>(
  (ref) => AppLinksSource(),
);

/// Where a [SectionDeepLink] lands, porting `DashboardActivity`'s
/// `when (fragmentToOpen)`.
///
/// The Kotlin's set is exactly these five plus a fall-through to the bell
/// dashboard, and an unrecognised section is *not* an error — it opens the
/// dashboard. Returning null for one keeps the app where it is instead, which
/// is the same outcome for a link that arrives while the user is already on the
/// dashboard and avoids yanking them off whatever screen they were on.
String? deepLinkRoute(String section) => switch (section) {
  // `feedbackList`, not `feedback`: the Kotlin's `when` names the fragment, and
  // accepting a friendlier spelling here would make the two apps disagree about
  // the same link.
  'feedbackList' => Routes.feedback,
  'courses' => Routes.courses,
  'resources' => Routes.resources,
  'teams' => Routes.teams,
  'surveys' => Routes.surveys,
  _ => null,
};

/// Resolves incoming links and routes them.
///
/// Port of `OnboardingActivity.handleDeepLinkIntent` plus the consumption half
/// in `DashboardActivity.openFragmentFromIntent`. The Kotlin splits the two
/// because an intent arrives at whichever activity is launched and the
/// destination may not exist yet; here the router is always mounted, so the
/// split survives only where it carries behaviour — a section link that arrives
/// before sign-in is *persisted*, not dropped, and applied once the session
/// exists.
class DeepLinkHandler {
  DeepLinkHandler(this.ref);

  final Ref ref;

  /// Handles one link. Returns the location navigated to, or null if the link
  /// was ignored or only stored for later.
  Future<String?> handle(Uri uri) async {
    final isSignedIn = ref.read(sessionProvider).valueOrNull != null;
    final link = parseDeepLink(uri, isSignedIn: isSignedIn);
    return switch (link) {
      PublicSurveyDeepLink() => publicSurveyLocation(link),
      SectionDeepLink() => _sectionLocation(link, isSignedIn: isSignedIn),
      null => null,
    };
  }

  /// The in-app location for a public-survey link.
  ///
  /// The origin travels as a query parameter because that is the only part of
  /// the link the route cannot recover for itself: go_router hands the builder
  /// a location that may be scheme- and host-less, and the origin *is* the
  /// server the screen fetches the survey from.
  static String publicSurveyLocation(PublicSurveyDeepLink link) {
    final path = '/survey/${link.teamId}/${link.surveyId}';
    // An empty `queryParameters` map still renders a trailing `?`, which makes
    // the location for a link with no origin differ from the same route reached
    // in-app for no reason.
    if (link.origin.isEmpty) return path;
    return Uri(path: path, queryParameters: {'origin': link.origin}).toString();
  }

  Future<String?> _sectionLocation(
    SectionDeepLink link, {
    required bool isSignedIn,
  }) async {
    final route = deepLinkRoute(link.section);
    if (route == null) return null;
    if (!isSignedIn) {
      // `prefData.setRawString(DEEP_LINK_SECTION_KEY, section)` — the link
      // outlives the login it triggers.
      await ref
          .read(planetPrefsProvider)
          .setPendingDeepLink(link.section, link.contentId);
      return null;
    }
    return route;
  }

  /// Port of the `fragmentToOpen == null` branch in
  /// `DashboardActivity.openFragmentFromIntent`: reads the stored section,
  /// clears it, and returns where to go. Clearing on read is what stops the
  /// link reopening on every subsequent launch.
  Future<String?> takePendingLocation() async {
    final prefs = ref.read(planetPrefsProvider);
    final section = prefs.pendingDeepLinkSection;
    if (section.isEmpty) return null;
    await prefs.clearPendingDeepLink();
    return deepLinkRoute(section);
  }
}

final deepLinkHandlerProvider = Provider(DeepLinkHandler.new);
