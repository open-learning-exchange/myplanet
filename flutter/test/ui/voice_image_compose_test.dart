import 'dart:io';
import 'dart:typed_data';

import 'package:drift/drift.dart' show Value;
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/files/voice_images.dart';
import 'package:myplanet/core/prefs/planet_prefs.dart';
import 'package:myplanet/core/providers/provider_retry.dart';
import 'package:myplanet/core/system/voice_image_picker.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/session_provider.dart';
import 'package:myplanet/providers/voices_provider.dart';
import 'package:myplanet/repository/voices_repository.dart';
import 'package:myplanet/ui/voices/voice_thread_screen.dart';
import 'package:myplanet/ui/voices/voices_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../repository/device_identity_fixture.dart';
import '../support/widget_harness.dart';

class _MockPlanetApi extends Mock implements PlanetApi {}

class _MockVoicesActions extends Mock implements VoicesActions {}

class _FakePicker implements VoiceImagePicker {
  _FakePicker(this.images);
  final List<PickedVoiceImage> images;
  var calls = 0;
  @override
  Future<List<PickedVoiceImage>> pick() async {
    calls++;
    return images;
  }
}

/// Phase 147, Job 1 — the **reachability** half.
///
/// `test/repository/voice_image_upload_test.dart` proves the uploader does the
/// right thing with a pending image. This file proves a pending image can
/// exist at all, because before this phase the port had no writer for
/// `imageUrls`: every `createPost`/`postReply` caller passed nothing, no
/// screen offered an attach affordance, and the column's only other writer
/// (`NewsMapper`) merely preserved whatever was already there. An upload path
/// with no writer is the failure class Phases 113, 116 and 119 kept finding —
/// ported, tested, green and dead — and it is invisible to a test that seeds
/// its own row.
///
/// **Split at the provider boundary on purpose, not for convenience.**
/// `VoiceImages.write` is real `dart:io` and a widget test's zone is
/// fake-async, so a chain that starts in a tap handler and ends in a file
/// write never completes: the post silently never appears, which looks exactly
/// like the defect. The two halves meet at [VoiceImageAttachment] — the screen
/// tests assert the attachment reaches `VoicesActions`, and the container test
/// takes it from there to disk.
void main() {
  setUpAll(() {
    registerFallbackValue(<String, dynamic>{});
    registerFallbackValue(const <VoiceImageAttachment>[]);
  });

  final picked = PickedVoiceImage(
    bytes: Uint8List.fromList([1, 2, 3, 4]),
    filename: 'well.png',
  );

  group('the screens offer the affordance and pass what it produces', () {
    late _MockVoicesActions actions;
    late _FakePicker picker;

    setUp(() {
      actions = _MockVoicesActions();
      picker = _FakePicker([picked]);
      VoiceImagePicker.instance = picker;
    });

    /// The composer sheet's own text field. The feed behind it has a search
    /// field of its own, so a bare `find.byType(TextField)` matches two.
    Finder composerField() => find.descendant(
      of: find.byType(BottomSheet),
      matching: find.byType(TextField),
    );

    Future<void> attachAndPost(WidgetTester tester, String message) async {
      expect(
        find.text('Add Image'),
        findsOneWidget,
        reason: 'the screen did not pass `allowImages: true`',
      );
      await tester.tap(find.text('Add Image'));
      await tester.pumpAndSettle();
      expect(picker.calls, 1);
      // Shown as a removable chip, so the user can see what they attached.
      expect(find.text('well.png'), findsOneWidget);

      await tester.enterText(composerField(), message);
      await tester.tap(find.text('Post'));
      await tester.pumpAndSettle();
    }

    testWidgets('the community composer hands the image to createPost', (
      tester,
    ) async {
      when(
        () => actions.createPost(any(), attachments: any(named: 'attachments')),
      ).thenAnswer((_) async => 'news-1');

      await tester.pumpWidget(
        wrapScreen(
          const VoicesScreen(),
          overrides: [
            voicesActionsProvider.overrideWithValue(actions),
            communityFeedProvider.overrideWith((ref) => Stream.value(const [])),
            sessionProvider.overrideWith(
              () => _TestSessionNotifier(buildUserRow(id: 'ada', name: 'ada')),
            ),
          ],
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.byType(FloatingActionButton));
      await tester.pumpAndSettle();

      await attachAndPost(tester, 'the well is dry');

      final captured =
          verify(
                () => actions.createPost(
                  'the well is dry',
                  attachments: captureAny(named: 'attachments'),
                ),
              ).captured.single
              as List<VoiceImageAttachment>;
      expect(captured, hasLength(1));
      expect(captured.single.filename, 'well.png');
      expect(captured.single.bytes, [1, 2, 3, 4]);
    });

    testWidgets('a reply hands the image to postReply', (tester) async {
      // `ReplyActivity.kt:226-233` builds the same `imageUrls` entry the two
      // compose screens do, so the reply composer carries the picker too.
      when(
        () => actions.postReply(
          parentId: any(named: 'parentId'),
          message: any(named: 'message'),
          attachments: any(named: 'attachments'),
        ),
      ).thenAnswer((_) async => 'reply-1');

      await tester.pumpWidget(
        wrapScreen(
          const VoiceThreadScreen(newsId: 'post-1'),
          overrides: [
            voicesActionsProvider.overrideWithValue(actions),
            sessionProvider.overrideWith(
              () => _TestSessionNotifier(buildUserRow(id: 'ada', name: 'ada')),
            ),
            voiceProvider.overrideWith((ref, id) async => _post(id)),
            voiceRepliesProvider.overrideWith(
              (ref, id) => Stream.value(const []),
            ),
            voiceReplyCountProvider.overrideWith((ref, id) async => 0),
          ],
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.byType(FloatingActionButton));
      await tester.pumpAndSettle();

      await attachAndPost(tester, 'since when?');

      final captured =
          verify(
                () => actions.postReply(
                  parentId: 'post-1',
                  message: 'since when?',
                  attachments: captureAny(named: 'attachments'),
                ),
              ).captured.single
              as List<VoiceImageAttachment>;
      expect(captured.single.filename, 'well.png');
    });

    testWidgets('the edit composer offers no attach affordance', (
      tester,
    ) async {
      // A decision, not an omission: Kotlin's `editPost` does accept
      // `newImages`, and wiring it needs the second send to carry the existing
      // `images` alongside the new ones. Reported, not built.
      //
      // Driven through the card's own edit button rather than by calling
      // `showVoiceComposer` here: a test that builds its own call cannot see
      // `_edit` gaining `allowImages`, which is the change it exists to
      // forbid. Mutating that line left the first draft of this test green.
      final ada = buildUserRow(id: 'ada', name: 'ada');
      await tester.pumpWidget(
        wrapScreen(
          Scaffold(
            body: VoiceCard(
              row: _post('post-1').copyWith(userId: const Value('ada')),
            ),
          ),
          overrides: [
            voicesActionsProvider.overrideWithValue(actions),
            sessionProvider.overrideWith(() => _TestSessionNotifier(ada)),
            voiceReplyCountProvider.overrideWith((ref, id) async => 0),
          ],
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.byIcon(Icons.edit_outlined));
      await tester.pumpAndSettle();

      expect(
        find.byType(BottomSheet),
        findsOneWidget,
        reason:
            'the edit composer did not open, so the assertion below '
            'would pass for the wrong reason',
      );
      expect(find.text('Add Image'), findsNothing);
    });
  });

  test('createPost puts the bytes where the uploader will look', () async {
    // The other half of the chain, in a plain zone so the real file write can
    // complete. Ends at the same question `VoicesUploader` asks: given only
    // the row, can the bytes be found again?
    SharedPreferences.setMockInitialValues({});
    final db = AppDatabase.memory();
    addTearDown(db.close);
    final tempDir = Directory.systemTemp.createTempSync('voice_compose_test');
    VoiceImages.baseDirectory = () async => tempDir;
    addTearDown(() {
      if (tempDir.existsSync()) tempDir.deleteSync(recursive: true);
    });

    final container = ProviderContainer(
      retry: noProviderRetry,
      overrides: [
        appDatabaseProvider.overrideWithValue(db),
        planetApiProvider.overrideWithValue(_MockPlanetApi()),
        planetPrefsProvider.overrideWithValue(
          PlanetPrefs(await SharedPreferences.getInstance()),
        ),
        deviceIdentitySourceProvider.overrideWithValue(testDeviceIdentity),
        serverConfigProvider.overrideWith(_TestServerConfigNotifier.new),
        sessionProvider.overrideWith(
          () => _TestSessionNotifier(buildUserRow(id: 'ada', name: 'ada')),
        ),
      ],
    );
    addTearDown(container.dispose);

    final id = await container
        .read(voicesActionsProvider)
        .createPost(
          'the well is dry',
          attachments: [
            VoiceImageAttachment(bytes: picked.bytes, filename: 'well.png'),
          ],
        );

    final pending = await container
        .read(voicesRepositoryProvider)
        .pendingImagesFor(id!);
    expect(pending, hasLength(1));
    expect(pending.single.fileName, 'well.png');

    final file = await VoiceImages.existingFileFor(
      newsId: id,
      filename: pending.single.fileName,
    );
    expect(
      file,
      isNotNull,
      reason:
          'the row names an image whose bytes are not on disk — the '
          'uploader would take its "missing file" branch and the post would '
          'go up without it',
    );
    expect(await file!.readAsBytes(), [1, 2, 3, 4]);
  });
}

NewsRow _post(String id) => NewsRow(
  id: id,
  message: 'the well is dry',
  docType: 'message',
  updatedDate: 0,
  time: 0,
  imageUrls: const [],
  labels: const [],
  newsCreatedDate: 0,
  newsUpdatedDate: 0,
  chat: false,
  isEdited: false,
  editedTime: 0,
);

class _TestServerConfigNotifier extends ServerConfigNotifier {
  @override
  ServerConfig? build() => const ServerConfig(
    serverUrl: 'https://planet.example.org',
    pin: '1234',
    couchDbUrl: 'https://satellite:1234@planet.example.org:443',
  );
}

class _TestSessionNotifier extends SessionNotifier {
  _TestSessionNotifier(this._user);

  final UserRow? _user;

  @override
  Future<UserRow?> build() async => _user;
}
