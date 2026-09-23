import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/providers/provider_retry.dart';
import 'package:myplanet/core/system/device_identity.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/data/local/chat_mapper.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/chat_provider.dart';
import 'package:myplanet/providers/session_provider.dart';
import 'package:myplanet/repository/chat_repository.dart';

/// The verdict `ChatDetailScreen` clears the composer on.
///
/// The screen's own tests drive a fake notifier, which is the right division
/// of labour there — they are about what the screen does with an outcome. This
/// file is about the outcome itself, and it exists because without it the
/// whole provider half of the fix was held by nothing: `return
/// ChatSendOutcome.declined` could be changed to `sent` and the entire suite
/// stayed green, which re-opens the original data loss verbatim.
///
/// The contract under test is narrow on purpose. [ChatSendOutcome.declined]
/// means *the text exists nowhere but the field*, so the caller must keep it;
/// [ChatSendOutcome.failed] means the optimistic query bubble is on screen
/// holding the text, so clearing is safe. Everything else — a pending row, an
/// error banner — is a property of particular paths, not of the enum.
void main() {
  const config = ServerConfig(
    serverUrl: 'https://planet.example',
    couchDbUrl: 'https://satellite:1234@planet.example:443',
    pin: '1234',
  );

  ({ProviderContainer container, AppDatabase database}) harness({
    required SessionNotifier Function() session,
    ChatRepository? repository,
  }) {
    final database = AppDatabase.memory();
    addTearDown(database.close);
    final container = ProviderContainer(
      retry: noProviderRetry,
      overrides: [
        appDatabaseProvider.overrideWith((ref) => database),
        sessionProvider.overrideWith(session),
        serverConfigProvider.overrideWith(() => _TestServerConfig(config)),
        // A null [repository] means `ref.read(chatRepositoryProvider)` itself
        // throws — the one failure that lands *above* the optimistic bubble.
        if (repository == null)
          chatRepositoryProvider.overrideWith(
            (ref) => throw StateError('the repository could not be built'),
          )
        else
          chatRepositoryProvider.overrideWithValue(repository),
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
    return (container: container, database: database);
  }

  Future<ChatSendOutcome> send(
    ProviderContainer container, [
    String message = 'Capital of Iceland?',
  ]) => container.read(chatConversationProvider.notifier).sendMessage(message);

  test('a send with nobody to send as is declined, not failed', () async {
    // The route the soft-keyboard Send key used to take. `declined` is what
    // tells the screen to put nothing where the person's text was.
    final h = harness(session: _NoSession.new, repository: _NeverCalled());

    expect(await send(h.container), ChatSendOutcome.declined);
    expect(
      h.container.read(chatConversationProvider).messages,
      isEmpty,
      reason: 'a refused send must leave no bubble to be mistaken for one',
    );
  });

  test('a blank message is declined', () async {
    final h = harness(
      session: _ResolvedSession.new,
      repository: _NeverCalled(),
    );

    expect(await send(h.container, '   '), ChatSendOutcome.declined);
  });

  test('an answered send is sent, with both bubbles', () async {
    final h = harness(
      session: _ResolvedSession.new,
      repository: _FakeChatRepository(
        const ChatSuccess(response: 'Reykjavik.', id: 'chat-1', rev: '1-a'),
      ),
    );

    expect(await send(h.container), ChatSendOutcome.sent);
    final state = h.container.read(chatConversationProvider);
    expect(state.messages.map((m) => m.content), [
      'Capital of Iceland?',
      'Reykjavik.',
    ]);
    expect(state.id, 'chat-1');
    expect(state.isLoading, isFalse);
  });

  test(
    'a refused request is failed: bubble, banner and a row to retry',
    () async {
      final repository = _FakeChatRepository(const ChatError('no provider'));
      final h = harness(session: _ResolvedSession.new, repository: repository);

      expect(await send(h.container), ChatSendOutcome.failed);
      final state = h.container.read(chatConversationProvider);
      expect(state.messages.map((m) => m.content), ['Capital of Iceland?']);
      expect(state.error, 'no provider');
      expect(
        repository.saved,
        ['Capital of Iceland?'],
        reason: 'nothing else queues a failed chat, so this is the only retry',
      );
    },
  );

  test('a throw before the bubble is declined, not failed', () async {
    // The finding the second audit pass produced against this round's own
    // first cut, and the reason `sendMessage` tracks whether the bubble
    // landed. `ref.read(chatRepositoryProvider)` runs one line above the
    // optimistic bubble and reaches `appDatabaseProvider`, so a
    // `ProviderException` there leaves no bubble, no row and no text — and the
    // first cut answered `failed`, which tells the screen to clear the
    // composer. That is the round's own defect relocated rather than removed.
    //
    // Mutation: make the catch arm return `failed` unconditionally and this
    // goes red on the outcome, while every other test here stays green.
    final h = harness(session: _ResolvedSession.new);

    expect(await send(h.container), ChatSendOutcome.declined);
    expect(h.container.read(chatConversationProvider).messages, isEmpty);
  });

  test(
    'a throw after the bubble is failed, because the bubble holds it',
    () async {
      // `savePendingChat` failing (a full or locked database) leaves no retry
      // row — which is why `failed` promises the bubble and nothing more.
      final h = harness(
        session: _ResolvedSession.new,
        repository: _FakeChatRepository(
          const ChatError('no provider'),
          throwOnSave: true,
        ),
      );

      expect(await send(h.container), ChatSendOutcome.failed);
      final state = h.container.read(chatConversationProvider);
      expect(state.messages.map((m) => m.content), ['Capital of Iceland?']);
      expect(
        state.isLoading,
        isFalse,
        reason: 'the Send button spins for ever',
      );
      expect(state.error, isNotNull);
    },
  );

  test('a rejecting session is declined, and does not escape', () async {
    final h = harness(
      session: _RejectingSession.new,
      repository: _NeverCalled(),
    );

    expect(await send(h.container), ChatSendOutcome.declined);
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

/// Everything a [ChatRepository] must declare, defaulting to "this test should
/// not have got here". Subclasses override only what they are about.
class _NeverCalled implements ChatRepository {
  @override
  dynamic noSuchMethod(Invocation invocation) =>
      throw StateError('unexpected ${invocation.memberName}');
}

class _FakeChatRepository extends _NeverCalled {
  _FakeChatRepository(this.result, {this.throwOnSave = false});

  final ChatResult result;
  final bool throwOnSave;
  final saved = <String>[];

  @override
  Future<ChatResult> sendNewChatRequest({
    required String query,
    required String user,
    required AiProviderConfig aiProvider,
  }) async => result;

  @override
  Future<ChatResult> sendContinueChatRequest({
    required String query,
    required String user,
    required AiProviderConfig aiProvider,
    required String id,
    required String rev,
  }) async => result;

  @override
  Future<String> savePendingChat({
    required String user,
    required String query,
    required AiProviderConfig aiProvider,
    String? existingId,
    String? existingRev,
  }) async {
    if (throwOnSave) throw StateError('database is locked');
    saved.add(query);
    return 'pending-1';
  }
}
