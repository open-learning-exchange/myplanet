import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'app.dart';
import 'core/prefs/planet_prefs.dart';
import 'providers/app_providers.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();

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
