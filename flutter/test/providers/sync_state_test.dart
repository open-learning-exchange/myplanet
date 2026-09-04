import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/prefs/planet_prefs.dart';
import 'package:myplanet/core/sync/sync_result.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/sync_state.dart';
import 'package:shared_preferences/shared_preferences.dart';

const _config = ServerConfig(
  serverUrl: 'https://planet.example.org',
  pin: '1234',
  couchDbUrl: 'https://satellite:1234@planet.example.org/db',
);

class _ConfiguredServerNotifier extends ServerConfigNotifier {
  @override
  ServerConfig? build() => _config;
}

class _ResultSyncNotifier extends SyncNotifier {
  _ResultSyncNotifier(this.result);

  final SyncResult result;

  @override
  Future<SyncResult> runSync(
    ServerConfig config,
    void Function(SyncProgress) onProgress,
  ) async => result;
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  Future<({ProviderContainer container, PlanetPrefs prefs})> harness(
    SyncResult result,
  ) async {
    SharedPreferences.setMockInitialValues({});
    final prefs = PlanetPrefs(await SharedPreferences.getInstance());
    final syncProvider = NotifierProvider<_ResultSyncNotifier, SyncUiState>(
      () => _ResultSyncNotifier(result),
    );
    final container = ProviderContainer(
      overrides: [
        planetPrefsProvider.overrideWithValue(prefs),
        serverConfigProvider.overrideWith(_ConfiguredServerNotifier.new),
      ],
    );
    addTearDown(container.dispose);
    container.read(syncProvider);
    await container.read(syncProvider.notifier).sync();
    return (container: container, prefs: prefs);
  }

  test('a successful foreground sync records and publishes its time', () async {
    final (:container, :prefs) = await harness(const SyncComplete(4));

    expect(prefs.lastSync, greaterThan(0));
    expect(container.read(lastSyncProvider), prefs.lastSync);
  });

  test('a failed foreground sync does not advance last sync', () async {
    final (:container, :prefs) = await harness(const SyncFailed('offline'));

    expect(prefs.lastSync, 0);
    expect(container.read(lastSyncProvider), 0);
  });
}
