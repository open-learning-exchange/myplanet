/// The port's provider-retry policy.
///
/// Riverpod 3 retries a provider whose `build` throws, automatically, with
/// nobody asking. `ProviderContainer.defaultRetry` (riverpod 3.4.3,
/// `src/core/provider_container.dart:983`) waits 200 ms and doubles to a
/// 6400 ms cap over `maxRetries` 10 — 11 attempts spread across 38.2 s — and
/// declines only an `Error` or a `ProviderException`. Anything that
/// `implements Exception` is retried, which in this tree includes
/// `MissingPluginException`, `SqliteException` and `FileSystemException`.
/// `ProviderElement.buildState` (`src/core/element.dart:734`, the retry call at
/// `:757`) is on the common base, so this covers synchronous `Provider`s too,
/// not only the async ones.
///
/// This port opts out, globally, and preserves the 2.6.1 behaviour it was
/// written against. The reason is a rule this codebase already has, from
/// Phase 148:
///
/// > An `outbox` item owns exactly one row, for ever. A terminal row is a
/// > memo: the request it holds has been answered, and nothing re-asks the
/// > identical question unattended. What re-arms it is a *changed request* —
/// > a different payload, endpoint or verb — or a person explicitly asking
/// > for a retry.
///
/// A framework default that re-runs a failed build eleven times is that
/// sentence's opposite, applied a layer below where the rule was written and
/// invisibly. No provider in this port writes to a server from its `build`
/// today — that was enumerated by hand, not by grep, when this landed — so
/// the switch buys no immediate bug fix. What it buys is that the *next*
/// provider to write from a build is not retried by a default nobody chose,
/// on a codebase where eleven uploaders append with server-assigned ids and a
/// duplicate is undetectable afterwards.
///
/// It also keeps a failure visible. The port renders an error state for a
/// failed read; under the default that state is replaced by 38 s of spinner
/// first.
///
/// **To opt one provider back in**, pass `retry:` to that provider's own
/// constructor — `origin.retry` wins over the container's
/// (`src/core/element.dart:772`). Do that deliberately, per provider, with a
/// reason at the call site. Do not remove this policy to get it.
///
/// Applied at every `ProviderContainer`/`ProviderScope` construction in the
/// app and in the test tree, so tests reproduce production semantics rather
/// than diverging from them. `test/core/provider_retry_policy_test.dart`
/// is the guard that keeps it that way.
Duration? noProviderRetry(int retryCount, Object error) => null;
