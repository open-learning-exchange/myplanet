import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/network/network_result.dart';
import 'app_providers.dart';

/// Port of `ui/dashboard/BellDashboardViewModel.kt`'s `NetworkStatus` — the
/// colour of the ring drawn around the dashboard avatar.
enum NetworkStatus {
  /// Red in the Kotlin (`md_red_700`): nothing answered at all.
  disconnected,

  /// Yellow (`md_yellow_600`): a probe is in flight, or the server answered but
  /// not with success.
  connecting,

  /// Green: the server answered 2xx.
  connected,
}

/// Probes the configured server and reports it as a ring colour.
///
/// `BellDashboardViewModel` drives this from `NetworkUtils.isNetworkConnectedFlow`:
/// connectivity events move it to `Connecting`, then `checkServerConnection`
/// resolves to `Connected`/`Disconnected`, and `handleConnectingState` probes
/// the primary URL followed by the mapped alternative. Reachability itself is
/// `MainApplication.isServerReachable` — a plain GET, success being any 2xx,
/// cached for 30 seconds.
///
/// Two deliberate differences, both from the port having no connectivity
/// plugin (no `connectivity_plus`, and adding one for a status ring did not
/// earn its keep):
///
/// * **There is no connectivity trigger.** The Kotlin re-probes whenever the OS
///   reports a connectivity change; this probes when the dashboard builds and
///   whenever [refresh] is called. It does not poll on a timer — a background
///   probe every few seconds costs battery to colour one ring.
/// * **The three states are inferred from the failure kind rather than from the
///   radio.** The Kotlin shows red only when the device has no network, and
///   yellow when it has one but the server did not answer well. Here a
///   transport-level failure (socket error, timeout — [NetworkException]) reads
///   as red, and a response that arrived but was not 2xx ([NetworkError]) reads
///   as yellow. That lands on the same colour as the Kotlin in the ordinary
///   cases: no network at all fails at the transport, and a reachable-but-sick
///   server answers with a status.
class NetworkStatusNotifier extends Notifier<NetworkStatus> {
  @override
  NetworkStatus build() {
    // The first probe is fired and not awaited: `build` cannot be async and the
    // ring should render immediately in its "checking" colour, exactly as the
    // Kotlin paints yellow before `handleConnectingState` returns.
    Future.microtask(refresh);
    return NetworkStatus.connecting;
  }

  Future<void> refresh() async {
    final config = ref.read(serverConfigProvider);
    final primary = config?.serverUrl ?? '';
    if (primary.isEmpty) {
      // `handleConnectingState` returns early on an empty URL, leaving the ring
      // yellow rather than claiming the server is down.
      state = NetworkStatus.connecting;
      return;
    }

    final result = await _probe(primary);
    if (result == NetworkStatus.connected) {
      state = result;
      return;
    }

    // `isServerReachable` tries the mapped alternative before giving up.
    final alternative = config?.alternativeUrl;
    if (alternative != null && alternative.isNotEmpty) {
      final alternativeResult = await _probe(alternative);
      if (alternativeResult == NetworkStatus.connected) {
        state = alternativeResult;
        return;
      }
      // The worse of the two verdicts wins: if either hop got a real response,
      // the device clearly has a network, so yellow beats red.
      state =
          (result == NetworkStatus.connecting ||
              alternativeResult == NetworkStatus.connecting)
          ? NetworkStatus.connecting
          : NetworkStatus.disconnected;
      return;
    }
    state = result;
  }

  Future<NetworkStatus> _probe(String url) async {
    final result = await ref.read(planetApiProvider).getJsonObject(url);
    return switch (result) {
      NetworkSuccess<Map<String, dynamic>>() => NetworkStatus.connected,
      NetworkError<Map<String, dynamic>>() => NetworkStatus.connecting,
      NetworkException<Map<String, dynamic>>() => NetworkStatus.disconnected,
    };
  }
}

final networkStatusProvider =
    NotifierProvider<NetworkStatusNotifier, NetworkStatus>(
      NetworkStatusNotifier.new,
    );
