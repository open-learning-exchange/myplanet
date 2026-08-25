import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'l10n/app_localizations.dart';
import 'l10n/framework_fallback_delegates.dart';
import 'providers/settings_provider.dart';
import 'ui/deep_link_scope.dart';
import 'ui/outbox_drain_scope.dart';
import 'ui/router.dart';

/// Port of `MainApplication.kt`'s theme and locale setup.
class MyPlanetApp extends ConsumerWidget {
  const MyPlanetApp({super.key});

  /// Approximates the app's existing primary colour in `res/values/colors.xml`.
  static const Color _seedColor = Color(0xFF00A0DF);

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final router = ref.watch(routerProvider);
    final themeMode = ref.watch(themeModeProvider);
    final locale = ref.watch(localeProvider);
    final textScale = ref.watch(textScaleProvider);

    return MaterialApp.router(
      onGenerateTitle: (context) => AppLocalizations.of(context).appTitle,
      routerConfig: router,
      // Null follows the device, which is what the app did before the language
      // override existed.
      locale: locale,
      localizationsDelegates: const [
        AppLocalizations.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
        // Last, so they only answer for locales the global delegates decline —
        // Somali, which Flutter does not translate. See the file's header.
        ...frameworkFallbackDelegates,
      ],
      supportedLocales: AppLocalizations.supportedLocales,
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: _seedColor),
      ),
      darkTheme: ThemeData(
        colorScheme: ColorScheme.fromSeed(
          seedColor: _seedColor,
          brightness: Brightness.dark,
        ),
      ),
      themeMode: themeMode,
      // Both wrap the whole navigator so they follow the app's lifecycle
      // rather than any one screen's: the drain on resume, and the deep-link
      // listener for the launch link and anything that arrives afterwards.
      // The `MediaQuery` override applies `LocaleUtils.setTextScale` — the
      // Kotlin recreates the activity to re-`Configuration.fontScale`; here
      // rebuilding this builder on a `textScaleProvider` change does the same.
      builder: (context, child) => MediaQuery(
        data: MediaQuery.of(
          context,
        ).copyWith(textScaler: TextScaler.linear(textScale)),
        child: OutboxDrainScope(
          child: DeepLinkScope(child: child ?? const SizedBox.shrink()),
        ),
      ),
    );
  }
}
