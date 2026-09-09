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
    required this.scheme,
  });

  /// The row label. Byte-identical in all five `values-*/strings.xml`, flag
  /// emoji included — these are proper nouns, never translated — so they live
  /// here as data rather than as eleven ARB keys per locale.
  final String name;

  /// Host with no scheme, the shape `ServerConfigUtils` stores.
  final String host;

  final String pin;

  /// Port of `getDefaultProtocol`, resolved per server rather than computed.
  ///
  /// **Four of the eleven are plain `http`** — xela, san pablo, uriur and
  /// embakasi are named explicitly in that function — and building them all as
  /// `https` would break those four. The function's other arm,
  /// `isLocalNetwork`, cannot fire for any row here (every host is a public
  /// domain, not a `192.168.`/`10.`/`.local` address), so it is deliberately
  /// **not** ported: it would be a helper with no caller, which is the dead
  /// plumbing this project keeps finding. Port it alongside a caller that
  /// needs it.
  final String scheme;

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

/// Every row `getServerAddresses` declares, in its order.
const List<PlanetServer> allPlanetServers = <PlanetServer>[
  PlanetServer(
    name: '🌎 planet learning',
    host: _learningHost,
    pin: _learningPin,
    scheme: 'https',
  ),
  PlanetServer(
    name: '🇬🇹 planet guatemala',
    host: _guatemalaHost,
    pin: _guatemalaPin,
    scheme: 'https',
  ),
  PlanetServer(
    name: '🇬🇹 planet san pablo',
    host: _sanPabloHost,
    pin: _sanPabloPin,
    scheme: 'http',
  ),
  PlanetServer(
    name: '🌎 planet earth',
    host: _earthHost,
    pin: _earthPin,
    scheme: 'https',
  ),
  PlanetServer(
    name: '🇸🇴 planet somalia',
    host: _somaliaHost,
    pin: _somaliaPin,
    scheme: 'https',
  ),
  PlanetServer(
    name: '🌎 planet vi',
    host: _viHost,
    pin: _viPin,
    scheme: 'https',
  ),
  PlanetServer(
    name: '🇬🇹 planet xela',
    host: _xelaHost,
    pin: _xelaPin,
    scheme: 'http',
  ),
  PlanetServer(
    name: '🇰🇪 planet uriur',
    host: _uriurHost,
    pin: _uriurPin,
    scheme: 'http',
  ),
  PlanetServer(
    name: '🇰🇪 planet ruiru',
    host: _ruiruHost,
    pin: _ruiruPin,
    scheme: 'https',
  ),
  PlanetServer(
    name: '🇰🇪 planet embakasi',
    host: _embakasiHost,
    pin: _embakasiPin,
    scheme: 'http',
  ),
  PlanetServer(
    name: '🇺🇸 planet cambridge',
    host: _cambridgeHost,
    pin: _cambridgePin,
    scheme: 'https',
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

/// Port of `getFilteredList`: three rows, expandable to all of them, with the
/// already-configured server kept visible even when it sits further down.
List<PlanetServer> planetServersToShow({
  required List<PlanetServer> servers,
  required bool showAdditional,
  String? configuredHost,
}) {
  if (showAdditional) return servers;

  final topThree = servers.take(3).toList();
  if (configuredHost == null || configuredHost.isEmpty) return topThree;
  if (topThree.any((s) => s.host == configuredHost)) return topThree;
  final pinned = servers.where((s) => s.host == configuredHost);
  if (pinned.isEmpty) return topThree;
  return <PlanetServer>[pinned.first, ...topThree];
}

/// Port of `ServerConfigUtils.removeProtocol`.
///
/// The list stores bare hosts while the screen's field holds a full URL, so
/// matching the configured server against a row needs one of the two reduced
/// to the other's shape.
String hostWithoutScheme(String url) {
  for (final prefix in const <String>['https://', 'http://']) {
    if (url.startsWith(prefix)) return url.substring(prefix.length);
  }
  return url;
}
