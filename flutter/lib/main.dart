import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:media_kit/media_kit.dart';

import 'app.dart';
import 'core/prefs/planet_prefs.dart';
import 'providers/app_providers.dart';

/// Entry point, replacing `MainApplication.kt` and the launcher Activity.
///
/// Only the bootstrap lives here. Theme, locale and routing belong to
/// `app.dart`, and the background work `MainApplication` schedules through
/// WorkManager has no equivalent yet — see
/// `docs/kotlin-to-flutter-migration.md`.
Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // Initialize media_kit for video/audio playback.
  MediaKit.init();

  // SharedPreferences is loaded up front so the rest of the graph can read the
  // server config synchronously — the same reason MainApplication initialises
  // SharedPrefManager before anything else.
  final prefs = await PlanetPrefs.load();

  runApp(
    ProviderScope(
      overrides: [planetPrefsProvider.overrideWithValue(prefs)],
      child: const MyPlanetApp(),
    ),
  );
}
