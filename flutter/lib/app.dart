import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'l10n/app_localizations.dart';
import 'providers/settings_provider.dart';
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

    return MaterialApp.router(
      onGenerateTitle: (context) => AppLocalizations.of(context).appTitle,
      routerConfig: router,
      localizationsDelegates: const [
        AppLocalizations.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
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
      // Wraps the whole navigator so the drain follows the app's lifecycle
      // rather than any one screen's.
      builder: (context, child) =>
          OutboxDrainScope(child: child ?? const SizedBox.shrink()),
    );
  }
}
