import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Awaits a [StreamProvider]'s first value from a bare [ProviderContainer].
///
/// Riverpod 2 completed `container.read(streamProvider.future)` on its own.
/// Riverpod 3 does not: with no listener the element's subscription is never
/// driven, so the future stays pending, the test times out at 30 s, and the
/// teardown's `container.dispose()` then reports
/// `disposed during loading state, yet no value could be emitted` — which
/// reads like a disposal bug and is really the missing listener.
///
/// Production is unaffected, and that is worth knowing before anyone
/// "fixes" a provider over this: both `.future` reads on a stream in `lib/`
/// (`teamNotificationsProvider` on `myTeamsStreamProvider`,
/// `notificationFormatContextProvider` on `notificationsProvider`) are
/// `ref.watch(...)` from inside another provider, and watching does listen.
/// It is only the container-level `read` — a shape that exists in tests — that
/// changed.
/// The listener is closed again once the value arrives. Leaving it open would
/// be worse than the bug it works around: an `autoDispose` provider with a
/// listener is never swept, and this port has providers whose correctness
/// depends on that sweep — `courseProgressStreamProvider` yields once and
/// completes, so a leaked listener freezes it for the rest of the process.
Future<T> readStreamValue<T>(
  ProviderContainer container,
  StreamProvider<T> provider,
) async {
  final subscription = container.listen<AsyncValue<T>>(provider, (_, _) {});
  try {
    return await container.read(provider.future);
  } finally {
    subscription.close();
  }
}
