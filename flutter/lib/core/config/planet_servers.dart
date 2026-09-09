import 'package:meta/meta.dart';

/// The preconfigured Planet servers, port of `utils/ServerConfigUtils.kt`.
///
/// ## Why this exists, and what it fixes
///
/// The Kotlin app never asks a user to type a PIN for a known server.
/// `SyncActivity` lists these eleven (`ServerConfigUtils.getServerAddresses`)
/// and tapping a row fills **both** fields — the host, and
/// `getPinForUrl(host)` out of `BuildConfig`. The port shipped two empty text
/// boxes instead, so the only way to connect it was to already know a
/// four-character PIN that lives in `gradle.properties`. Found by installing
/// the first APK the Flutter workflow ever handed out: the server, the URL
/// derivation and the `minapk` check were all correct, and the app still could
/// not be used.
///
/// ## Why the values arrive as `--dart-define`s
///
/// `app/build.gradle` reads eleven URL/PIN pairs from `gradle.properties` and
/// bakes each into `BuildConfig`. Dart cannot read `BuildConfig` without a
/// platform channel, so the port takes the same values as compile-time
/// environment defines. `tool/planet_server_defines.dart` derives the JSON
/// from that same `gradle.properties`, so the two apps cannot drift.
///
/// **These PINs are recoverable from the built APK.** So are the Kotlin app's
/// — `minifyEnabled = false` there, and a `--dart-define` is a constant in the
/// snapshot here — and they are the same already-public `satellite`
/// credentials, so this adds no exposure the project does not already have.
/// It is emphatically *not* a secret store: **do not put anything here that is
/// not already in `gradle.properties`**, and see `CLAUDE.md`'s security note —
/// a PIN wants runtime authentication, which neither app has.
///
/// A build that passes no defines yields an empty list and the screen falls
/// back to manual entry, which is exactly how the port behaved before this
/// existed. That is why the tests can run without any of these values.
@immutable
class PlanetServer {
  const PlanetServer({
    required this.name,
    required this.host,
    required this.pin,
  });

  /// The row label. Byte-identical in all five `values-*/strings.xml`, flag
  /// emoji included — these are proper nouns, never translated — so they live
  /// here as data rather than as eleven ARB keys per locale.
  final String name;

  /// Host with no scheme, the shape `ServerConfigUtils` stores.
  final String host;

  final String pin;

  /// Port of `getDefaultProtocol`, **computed** rather than stored.
  ///
  /// An earlier cut of this file hardcoded a scheme per row and claimed
  /// `isLocalNetwork` "cannot fire for any row here (every host is a public
  /// domain)". That was asserted, not checked, and it is false: **six** of the
  /// eleven hosts in `gradle.properties` are private addresses
  /// (`192.168.48.253`, `10.82.1.31`, `192.168.1.73`, `192.168.1.66`,
  /// `192.168.1.148`, `192.168.68.126`). Four of those are *also* named
  /// explicitly in `getDefaultProtocol`; ruiru and cambridge are not, so only
  /// the `isLocalNetwork` arm makes them http — and hardcoding them https
  /// offered two LAN servers over TLS, failing the handshake and reporting
  /// "couldn't reach the server", which is the exact mystery this file exists
  /// to end.
  ///
  /// Computing it also closes the drift the literals created: the hosts arrive
  /// as `--dart-define`s while a literal scheme sits in source, so editing
  /// `gradle.properties` could change a host to a LAN box with nothing here
  /// noticing. Kotlin computes the protocol from the host at tap time; so does
  /// this.
  String get scheme => defaultProtocolFor(host);

  String get url => '$scheme://$host';
}

const String _learningHost = String.fromEnvironment('PLANET_LEARNING_URL');
const String _learningPin = String.fromEnvironment('PLANET_LEARNING_PIN');
const String _guatemalaHost = String.fromEnvironment('PLANET_GUATEMALA_URL');
const String _guatemalaPin = String.fromEnvironment('PLANET_GUATEMALA_PIN');
const String _sanPabloHost = String.fromEnvironment('PLANET_SANPABLO_URL');
const String _sanPabloPin = String.fromEnvironment('PLANET_SANPABLO_PIN');
const String _earthHost = String.fromEnvironment('PLANET_EARTH_URL');
const String _earthPin = String.fromEnvironment('PLANET_EARTH_PIN');
const String _somaliaHost = String.fromEnvironment('PLANET_SOMALIA_URL');
const String _somaliaPin = String.fromEnvironment('PLANET_SOMALIA_PIN');
const String _viHost = String.fromEnvironment('PLANET_VI_URL');
const String _viPin = String.fromEnvironment('PLANET_VI_PIN');
const String _xelaHost = String.fromEnvironment('PLANET_XELA_URL');
const String _xelaPin = String.fromEnvironment('PLANET_XELA_PIN');
const String _uriurHost = String.fromEnvironment('PLANET_URIUR_URL');
const String _uriurPin = String.fromEnvironment('PLANET_URIUR_PIN');
const String _ruiruHost = String.fromEnvironment('PLANET_RUIRU_URL');
const String _ruiruPin = String.fromEnvironment('PLANET_RUIRU_PIN');
const String _embakasiHost = String.fromEnvironment('PLANET_EMBAKASI_URL');
const String _embakasiPin = String.fromEnvironment('PLANET_EMBAKASI_PIN');
const String _cambridgeHost = String.fromEnvironment('PLANET_CAMBRIDGE_URL');
const String _cambridgePin = String.fromEnvironment('PLANET_CAMBRIDGE_PIN');

/// Hosts `getDefaultProtocol` names explicitly, regardless of address shape.
///
/// Kotlin compares against `BuildConfig.PLANET_XELA_URL`,
/// `PLANET_SANPABLO_URL`, `PLANET_URIUR_URL` and `PLANET_EMBAKASI_URL`, so the
/// comparison is against whatever those properties hold — which is why this
/// reads the same defines rather than listing addresses. Empty entries are
/// dropped so a build with no defines cannot match a bare `''` host.
final Set<String> _explicitHttpHosts = <String>[
  _xelaHost,
  _sanPabloHost,
  _uriurHost,
  _embakasiHost,
].where((host) => host.isNotEmpty).toSet();

/// Port of `ServerConfigUtils.isLocalNetwork`.
///
/// Kotlin takes `url.substringBefore(':').substringBefore('/')`, so a port or
/// path is stripped before the tests. Called with a bare host from
/// `ServerDialogExtensions`' `onItemClick`, which has already removed the
/// scheme.
bool isLocalNetwork(String url) {
  final host = url.split(':').first.split('/').first;
  return host.startsWith('192.168.') ||
      host.startsWith('10.') ||
      _carrierGradeLocal.hasMatch(host) ||
      host == 'localhost' ||
      host == '127.0.0.1' ||
      host.endsWith('.local');
}

/// Kotlin's `^172\.(1[6-9]|2[0-9]|3[0-1])\..*`, the private 172.16/12 block.
final RegExp _carrierGradeLocal = RegExp(r'^172\.(1[6-9]|2[0-9]|3[0-1])\..*');

/// Port of `ServerConfigUtils.getDefaultProtocol`, both arms.
String defaultProtocolFor(String host) =>
    _explicitHttpHosts.contains(host) || isLocalNetwork(host)
    ? 'http'
    : 'https';

/// Every row `getServerAddresses` declares, in its order.
const List<PlanetServer> allPlanetServers = <PlanetServer>[
  PlanetServer(
    name: '🌎 planet learning',
    host: _learningHost,
    pin: _learningPin,
  ),
  PlanetServer(
    name: '🇬🇹 planet guatemala',
    host: _guatemalaHost,
    pin: _guatemalaPin,
  ),
  PlanetServer(
    name: '🇬🇹 planet san pablo',
    host: _sanPabloHost,
    pin: _sanPabloPin,
  ),
  PlanetServer(name: '🌎 planet earth', host: _earthHost, pin: _earthPin),
  PlanetServer(
    name: '🇸🇴 planet somalia',
    host: _somaliaHost,
    pin: _somaliaPin,
  ),
  PlanetServer(name: '🌎 planet vi', host: _viHost, pin: _viPin),
  PlanetServer(name: '🇬🇹 planet xela', host: _xelaHost, pin: _xelaPin),
  PlanetServer(name: '🇰🇪 planet uriur', host: _uriurHost, pin: _uriurPin),
  PlanetServer(name: '🇰🇪 planet ruiru', host: _ruiruHost, pin: _ruiruPin),
  PlanetServer(
    name: '🇰🇪 planet embakasi',
    host: _embakasiHost,
    pin: _embakasiPin,
  ),
  PlanetServer(
    name: '🇺🇸 planet cambridge',
    host: _cambridgeHost,
    pin: _cambridgePin,
  ),
];

/// The rows this build can actually offer.
///
/// A host is empty when its define was not passed, which `app/build.gradle`
/// mirrors with `findProperty(...) ?: ""`. Filtering on the **host** rather
/// than the pin is what Kotlin does: `getPinForUrl` returns `""` for a server
/// it has no PIN for and the row is still listed, so a row that cannot work
/// is Kotlin's behaviour too rather than something to improve on here.
List<PlanetServer> get configuredPlanetServers =>
    allPlanetServers.where((server) => server.host.isNotEmpty).toList();

/// Port of `getFilteredList` **and** the reordering half of
/// `ServerDialogExtensions.refreshServerList`.
///
/// Both arms keep the configured server visible, and they do it differently,
/// which is the part an audit of `getFilteredList` alone cannot see:
///
/// * **Collapsed** — `getFilteredList` takes the first three rows and, when the
///   configured server is not among them, prepends it, giving four
///   (`ServerConfigUtils.kt:56-62`). No de-duplication is needed because the
///   `contains` check is what gates the prepend.
/// * **Expanded** — `getFilteredList` returns the list untouched, and then
///   `refreshServerList` hoists the configured server to index 0 and drops the
///   duplicate further down (`ServerDialogExtensions.kt:128-135`). This half
///   had never been ported: expanding the list left the configured server
///   wherever `getServerAddresses` declares it, so on a device configured for
///   cambridge — the eleventh row — "show more" put the row the user is
///   already using last.
///
/// One knowing simplification. Kotlin reads **two** preferences here:
/// `getPinnedServerUrl()` feeds the collapsed prepend (written only after a
/// successful sync, `SyncActivity.kt:529`) while `getServerUrl()` feeds the
/// expanded hoist. The port has no separate pinned-after-sync preference, so
/// one [configuredHost] serves both roles; the caller supplies the persisted
/// `ServerConfig`'s host, which is the analogue of `getServerUrl()`.
List<PlanetServer> planetServersToShow({
  required List<PlanetServer> servers,
  required bool showAdditional,
  String? configuredHost,
}) {
  final configured = configuredHost ?? '';

  if (showAdditional) {
    if (configured.isEmpty) return servers;
    final pinned = servers.where((s) => s.host == configured);
    if (pinned.isEmpty) return servers;
    return <PlanetServer>[
      pinned.first,
      ...servers.where((s) => s.host != configured),
    ];
  }

  final topThree = servers.take(3).toList();
  if (configured.isEmpty) return topThree;
  if (topThree.any((s) => s.host == configured)) return topThree;
  final pinned = servers.where((s) => s.host == configured);
  if (pinned.isEmpty) return topThree;
  return <PlanetServer>[pinned.first, ...topThree];
}

/// Port of `ServerConfigUtils.removeProtocol`.
///
/// The list stores bare hosts while the screen's field holds a full URL, so
/// matching the configured server against a row needs one of the two reduced
/// to the other's shape.
///
/// Kotlin **chains** the two strips —
/// `url.removePrefix("https://").removePrefix("http://")`
/// (`ServerConfigUtils.kt:64-66`) — where this returned on the first match, so
/// `https://http://host` reduced to `http://host` there and to `host` in the
/// Kotlin. The chain is asymmetric and that is reproduced rather than tidied:
/// `https://` is stripped first, so `http://https://host` keeps its `https://`
/// in both apps. Nothing else is removed: no trailing slash, no port, no
/// `www.`, no credentials.
///
/// Kotlin has a second, *unchained* reducer for the same job — the
/// `^https?://` regex `ServerDialogExtensions` matches list rows with — and the
/// two disagree only on a doubled scheme. This follows `removeProtocol`, the
/// one it is named after.
String hostWithoutScheme(String url) =>
    _withoutPrefix(_withoutPrefix(url, 'https://'), 'http://');

String _withoutPrefix(String value, String prefix) =>
    value.startsWith(prefix) ? value.substring(prefix.length) : value;
