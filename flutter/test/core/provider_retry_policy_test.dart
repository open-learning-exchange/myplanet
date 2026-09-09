import 'dart:io';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/core/providers/provider_retry.dart';

/// Riverpod 3 retries a failing provider by default. This port opts out —
/// see `lib/core/providers/provider_retry.dart` for why — and the opt-out is
/// only worth anything if it is at every root.
///
/// `retry` is inherited from a *parent* container
/// (`provider_container.dart:902`, `retry = retry ?? parent?.retry`), and
/// every container a test builds is a root. So the policy is not something
/// the framework can carry for us: it is written at each construction site,
/// and without a guard the next site added silently gets the default back.
///
/// The source-text half of this file is that guard. The behavioural half
/// below it is what makes the guard mean something: mutate `noProviderRetry`
/// to return a `Duration` and `is not retried` fails.
void main() {
  group('the policy is applied at every root', () {
    test('every ProviderContainer and ProviderScope passes retry:', () {
      final offenders = <String>[];
      for (final dir in ['lib', 'test']) {
        for (final entity in Directory(dir).listSync(recursive: true)) {
          if (entity is! File || !entity.path.endsWith('.dart')) continue;
          // This file carries deliberate counterexamples in `the guard can
          // actually fail`, so it cannot scan itself.
          if (entity.path.endsWith('provider_retry_policy_test.dart')) continue;
          final source = entity.readAsStringSync();
          for (final match in RegExp(
            r'(?<![A-Za-z0-9_])(ProviderContainer|ProviderScope)\(',
          ).allMatches(source)) {
            // `UncontrolledProviderScope` takes a container that already
            // carries the policy, and the negative lookbehind above lets it
            // through; nothing else may be exempt.
            final args = _balanced(source, match.end - 1);
            if (args.contains('retry:')) continue;
            final line =
                '\n'.allMatches(source.substring(0, match.start)).length + 1;
            offenders.add('${entity.path}:$line  ${match.group(1)}(');
          }
        }
      }
      expect(
        offenders,
        isEmpty,
        reason:
            'These construct a Riverpod root without the port\'s retry policy, '
            'so a failing provider there is retried 11 times over 38 s. Pass '
            '`retry: noProviderRetry`.\n${offenders.join('\n')}',
      );
    });

    test('the guard can actually fail', () {
      // Phase 122's rule: a migration test that passes unconditionally reads
      // as coverage. The scan is only evidence if a site without the policy
      // is detected, so feed it one.
      const withoutPolicy = 'final c = ProviderContainer(overrides: []);';
      const withPolicy =
          'final c = ProviderContainer(retry: noProviderRetry, overrides: []);';
      final pattern = RegExp(
        r'(?<![A-Za-z0-9_])(ProviderContainer|ProviderScope)\(',
      );
      expect(
        _balanced(withoutPolicy, pattern.firstMatch(withoutPolicy)!.end - 1),
        isNot(contains('retry:')),
      );
      expect(
        _balanced(withPolicy, pattern.firstMatch(withPolicy)!.end - 1),
        contains('retry:'),
      );
    });
  });

  group('the policy behaves', () {
    test('noProviderRetry declines every shape defaultRetry would retry', () {
      // `ProviderContainer.defaultRetry` retries anything that is neither an
      // `Error` nor a `ProviderException`, for the first 10 attempts.
      expect(ProviderContainer.defaultRetry(0, _Boom()), isNotNull);
      expect(noProviderRetry(0, _Boom()), isNull);
      expect(noProviderRetry(9, _Boom()), isNull);
      expect(noProviderRetry(0, StateError('an Error')), isNull);
    });

    test('a failing provider is not retried', () async {
      var builds = 0;
      final failing = FutureProvider<int>((ref) async {
        builds++;
        throw _Boom();
      });
      final container = ProviderContainer(retry: noProviderRetry);
      addTearDown(container.dispose);

      // Under the default policy this future never rejects: `triggerRetry`
      // returns `AsyncLoading(retrying: true)` and leaves the completer
      // pending, so this line would hang to the suite timeout instead.
      await expectLater(container.read(failing.future), throwsA(isA<_Boom>()));
      expect(builds, 1);

      // Past the first two default delays (200 ms, 400 ms). Under the default
      // policy `builds` would be 3 by now.
      await Future<void>.delayed(const Duration(milliseconds: 700));
      expect(builds, 1, reason: 'the build was re-run after it failed');
    });

    test('a failing provider settles on AsyncError, not a retrying load', () {
      final failing = FutureProvider<int>((ref) => throw _Boom());
      final container = ProviderContainer(retry: noProviderRetry);
      addTearDown(container.dispose);

      expect(container.read(failing), isA<AsyncError<int>>());
    });
  });
}

/// The text between a `(` at [open] and its matching `)`.
String _balanced(String source, int open) {
  var depth = 0;
  for (var i = open; i < source.length; i++) {
    final char = source[i];
    if (char == '(') depth++;
    if (char == ')') {
      depth--;
      if (depth == 0) return source.substring(open + 1, i);
    }
  }
  return source.substring(open);
}

class _Boom implements Exception {
  @override
  String toString() => 'boom';
}
