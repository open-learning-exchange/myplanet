import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/providers/provider_retry.dart';
import 'package:myplanet/core/system/device_identity.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/ratings_provider.dart';
import 'package:myplanet/providers/session_provider.dart';
import 'package:myplanet/repository/ratings_repository.dart';
import 'package:myplanet/repository/ratings_uploader.dart';

/// `RatingActions.submit` used to return `void`, so [RatingDialog] had nothing
/// to branch on and popped `true` for every outcome.
///
/// The dialog's own tests drive a fake, which is the right shape there — they
/// are about what the dialog does with a verdict. This file is about the
/// verdict itself, and specifically about the two inputs that produce a
/// *failure with nothing thrown*: a session that never resolves to a user, and
/// one whose future rejects. Both used to fall out of `submit` as a bare
/// `return`, indistinguishable from a rating that was written.
///
/// `RatingsViewModel.submitRating` (`RatingsViewModel.kt:81-108`) reports both:
/// the null user as `SubmitState.Error("User not found")` (`:87-90`) and
/// anything thrown as `SubmitState.Error(e.message)` (`:105-107`).
void main() {
  const target = (type: 'course', itemId: 'course-1');
  const config = ServerConfig(
    serverUrl: 'https://planet.example',
    couchDbUrl: 'https://satellite:1234@planet.example:443',
    pin: '1234',
  );

  ProviderContainer containerWith(
    AppDatabase database,
    SessionNotifier Function() session,
  ) {
    final container = ProviderContainer(
      retry: noProviderRetry,
      overrides: [
        appDatabaseProvider.overrideWith((ref) => database),
        sessionProvider.overrideWith(session),
        // Overridden rather than left to resolve, because
        // `serverConfigProvider` reaches `planetPrefsProvider`, which is an
        // `UnimplementedError` in this harness — the Phase 75 trap. Left
        // un-overridden it throws out of `queuePending`, `submit` catches it
        // and answers `false`, and the happy-path assertion below fails for a
        // reason that has nothing to do with ratings.
        serverConfigProvider.overrideWith(() => _TestServerConfig(config)),
        deviceIdentitySourceProvider.overrideWithValue(
          const FixedDeviceIdentitySource(
            DeviceIdentity(
              androidId: 'android-1',
              deviceName: 'Pixel',
              customDeviceName: 'test-phone',
            ),
          ),
        ),
      ],
    );
    addTearDown(container.dispose);
    return container;
  }

  Future<bool> submit(ProviderContainer container) => container
      .read(ratingActionsProvider)
      .submit(
        target: target,
        title: 'Open learning',
        rate: 4,
        comment: 'useful',
      );

  test('a rating with nobody signed in is reported, not swallowed', () async {
    final database = AppDatabase.memory();
    addTearDown(database.close);

    final container = containerWith(database, _NoSession.new);

    expect(await submit(container), isFalse);
    expect(
      await database.ratingDao.pendingUploads(),
      isEmpty,
      reason: 'nothing was written, and the caller was told otherwise',
    );
  });

  test('a session whose future rejects is reported, not swallowed', () async {
    // The Phase 100 amendment in miniature: a future can reject where `.value`
    // could only have been null, so the `await` has to sit inside the
    // enclosing `try`.
    //
    // Be clear about what this does and does not pin, because it is one
    // `resolveSession` edit away from being a fixture that cannot
    // distinguish: today `resolveSession` swallows the rejection to `null`, so
    // this input reaches exactly the same `user == null` branch as the test
    // above and mutating `submit`'s `try` alone leaves it green. It is a guard
    // on the *pair* — the day someone lets `resolveSession` propagate, this is
    // what says `submit` must still answer rather than throw at the dialog.
    final database = AppDatabase.memory();
    addTearDown(database.close);

    final container = containerWith(database, _RejectingSession.new);

    expect(await submit(container), isFalse);
  });

  test('a write that throws is reported, not swallowed', () async {
    // This is the input the `try` actually catches, and the one the dialog's
    // old unconditional `pop(true)` was worst for: the rating reaches no row
    // at all and the person is shown a closed dialog.
    //
    // `RatingsRepositoryImpl.submitRating` has several of these — the
    // `require` on a blank identifier (`:57-58`) and the SQLite family on its
    // four DAO calls, one of which the repository's own comment records as
    // throwing `SQLiteBlobTooBigException` on this very table (`:83-86`).
    // `RatingsViewModel.kt:105-107` catches all of them into
    // `SubmitState.Error`.
    final database = AppDatabase.memory();
    addTearDown(database.close);

    final container = ProviderContainer(
      retry: noProviderRetry,
      overrides: [
        appDatabaseProvider.overrideWith((ref) => database),
        sessionProvider.overrideWith(_ResolvedSession.new),
        serverConfigProvider.overrideWith(() => _TestServerConfig(config)),
        ratingsRepositoryProvider.overrideWith(
          (ref) => _ThrowingRatingsRepository(
            ref.watch(planetApiProvider),
            ref.watch(ratingDaoProvider),
            ref.watch(userDaoProvider),
          ),
        ),
      ],
    );
    addTearDown(container.dispose);

    expect(await submit(container), isFalse);
    expect(await database.ratingDao.pendingUploads(), isEmpty);
  });

  test('a rating that was written reports true', () async {
    final database = AppDatabase.memory();
    addTearDown(database.close);

    final container = containerWith(database, _ResolvedSession.new);

    expect(await submit(container), isTrue);
    final pending = await database.ratingDao.pendingUploads();
    expect(pending, hasLength(1));
    expect(pending.single.rate, 4);
    expect(pending.single.userId, 'user-1');

    // `true` means written *and* handed over, which is the whole reason
    // `queuePending` is inside `submit`: `RatingsRepository.pendingUploads`
    // and `RatingsUploader` existed with no caller between them for several
    // phases, so a rating was saved locally and stopped there.
    final queued = await database.outboxDao.due(
      DateTime.now().millisecondsSinceEpoch + 1000,
    );
    expect(queued.map((e) => e.uploadType), [RatingsUploader.type]);
  });
}

class _NoSession extends SessionNotifier {
  @override
  Future<UserRow?> build() async => null;
}

class _RejectingSession extends SessionNotifier {
  @override
  Future<UserRow?> build() async => throw StateError('no session');
}

class _ResolvedSession extends SessionNotifier {
  @override
  Future<UserRow?> build() async => UserRow(
    id: 'user-1',
    name: 'ada',
    rolesList: const ['learner'],
    userAdmin: false,
    joinDate: 0,
    isArchived: false,
    isUpdated: false,
  );
}

class _TestServerConfig extends ServerConfigNotifier {
  _TestServerConfig(this.config);
  final ServerConfig? config;

  @override
  ServerConfig? build() => config;
}

class _ThrowingRatingsRepository extends RatingsRepository {
  _ThrowingRatingsRepository(super.api, super.dao, super.userDao);

  @override
  Future<void> submit({
    required String type,
    required String itemId,
    required String title,
    required String userId,
    required int rate,
    String? comment,
    String? parentCode,
    String? planetCode,
  }) async => throw StateError('disk full');
}
