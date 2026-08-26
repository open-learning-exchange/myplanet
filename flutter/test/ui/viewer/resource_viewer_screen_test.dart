import 'dart:io';

import 'package:drift/drift.dart' show Value;
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/files/resource_files.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/l10n/app_localizations.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/ui/viewer/resource_viewer_screen.dart';

import '../../support/widget_harness.dart';

/// First coverage for `ResourceViewerScreen` — at 1052 lines the largest screen
/// in the port, and until now the only one with none at all.
///
/// What is covered: the load / missing-resource states, the app-bar title and
/// its untitled fallback, the whole download-prompt state machine, which is
/// the part a user meets when a resource has not been fetched yet, and the
/// text/CSV/markdown renderers.
///
/// The renderers (`_TextViewer`, `_CsvContent`, `_MarkdownViewer`) read their
/// bytes through `resourceContentReaderProvider` (added for these tests) rather
/// than calling `File.readAsString` in their own `initState`, which a widget
/// test's fake clock cannot drive. A real file still has to exist on disk for
/// the screen's `_getLocalFilePath` to route into the viewer rather than the
/// download prompt, but that file write runs inside `runAsync` and the bytes
/// the renderer displays come from the override.
///
/// The three media renderers (`video_player`, `pdfx`, `webview_flutter`) are
/// out of reach for a different and more permanent reason: each wants a texture
/// or a platform view that no widget test can serve.
void main() {
  late AppDatabase db;
  late Directory tempDir;
  late Future<Directory> Function() savedBaseDirectory;

  const server = ServerConfig(
    serverUrl: 'https://planet.example.org',
    pin: 'secret-pin',
    couchDbUrl: 'https://satellite:secret-pin@planet.example.org',
    id: 'config-1',
    code: 'community-a',
    parentCode: 'nation',
  );

  setUp(() async {
    db = AppDatabase.memory();
    tempDir = await Directory.systemTemp.createTemp('viewer_test');
    savedBaseDirectory = ResourceFiles.baseDirectory;
    ResourceFiles.baseDirectory = () async => tempDir;
  });

  tearDown(() async {
    ResourceFiles.baseDirectory = savedBaseDirectory;
    await db.close();
    // Best-effort: a renderer may still hold the file open, and a failed
    // cleanup of a temp directory must not fail or stall the test.
    try {
      if (tempDir.existsSync()) tempDir.deleteSync(recursive: true);
    } catch (_) {}
  });

  /// Seeds one library row. [filename] is what drives the type routing, and
  /// [couchId] is the directory the attachment is looked for under.
  Future<void> seedResource({
    String id = 'res-1',
    String? title = 'Test Resource',
    String? filename,
    String? couchId = 'res-1',
    String? mediaType,
    bool offline = false,
  }) async {
    await db
        .into(db.myLibraryTable)
        .insert(
          MyLibraryTableCompanion.insert(
            id: id,
            couchId: Value(couchId),
            title: Value(title),
            filename: Value(filename),
            mediaType: Value(mediaType),
            resourceOffline: Value(offline),
          ),
        );
  }

  /// Lets the screen's real disk work finish, then rebuilds.
  ///
  /// This screen resolves its attachment through `ResourceFiles`, which is
  /// genuine `dart:io`, and a widget test's default zone is fake-async — real
  /// I/O never progresses there, so the screen sits on its
  /// `CircularProgressIndicator` and `pumpAndSettle` spins on that indefinite
  /// animation until its ten-minute default expires. `runAsync` is the escape
  /// hatch: it yields actual wall-clock time so the pending file futures
  /// complete, and the `pump` afterwards rebuilds with the resolved state.
  /// Pumping *inside* `runAsync` does not work — the two clocks fight.
  ///
  /// (The harness prefers provider overrides to `runAsync`, but `ResourceFiles`
  /// is a static seam rather than a provider, and a real file on disk is
  /// precisely what these tests are here to exercise.)
  Future<void> settleViewer(WidgetTester tester, {int rounds = 5}) async {
    for (var round = 0; round < rounds; round++) {
      await tester.runAsync(
        () => Future<void>.delayed(const Duration(milliseconds: 50)),
      );
      await tester.pump();
    }
  }

  Future<void> pumpViewer(
    WidgetTester tester, {
    String resourceId = 'res-1',
    ServerConfig? config = server,
    bool settle = true,
  }) async {
    await tester.pumpWidget(
      wrapScreen(
        ResourceViewerScreen(resourceId: resourceId),
        overrides: [
          appDatabaseProvider.overrideWith((ref) => db),
          serverConfigProvider.overrideWith(() => _TestServerConfig(config)),
        ],
      ),
    );
    if (settle) await settleViewer(tester);
  }

  /// Writes an attachment to the temp directory the screen resolves through.
  Future<void> writeAttachment(
    String docId,
    String filename,
    String content,
  ) async {
    final file = await ResourceFiles.fileFor(docId: docId, filename: filename);
    await file.parent.create(recursive: true);
    await file.writeAsString(content);
  }

  /// Pumps the viewer with a [resourceContentReaderProvider] override that
  /// hands the text/CSV/markdown renderers their content directly, so the
  /// rendering pipeline is exercised without a real `File.readAsString` (which
  /// a widget test's fake clock cannot drive). A real file still has to exist
  /// on disk for `_getLocalFilePath` to route the screen into the viewer rather
  /// than the download prompt — see [writeAttachment].
  Future<void> pumpViewerWithContent(
    WidgetTester tester, {
    required String content,
    String filename = 'notes.txt',
    String resourceId = 'res-1',
    String? title,
  }) async {
    // `writeAttachment` does real `dart:io`, which a `testWidgets` body's
    // fake-async zone cannot drive — it must run inside `runAsync`, or the
    // write Future never completes and the test hangs.
    await tester.runAsync(() => writeAttachment(resourceId, filename, content));
    await seedResource(
      id: resourceId,
      couchId: resourceId,
      title: title,
      filename: filename,
      offline: true,
    );
    await tester.pumpWidget(
      wrapScreen(
        ResourceViewerScreen(resourceId: resourceId),
        overrides: [
          appDatabaseProvider.overrideWith((ref) => db),
          serverConfigProvider.overrideWith(() => _TestServerConfig(server)),
          resourceContentReaderProvider.overrideWith(
            (_) =>
                ({required docId, required filename}) async => content,
          ),
        ],
      ),
    );
    await settleViewer(tester);
  }

  AppLocalizations l10nOf(WidgetTester tester) =>
      AppLocalizations.of(tester.element(find.byType(Scaffold).first));

  group('load states', () {
    testWidgets('shows a spinner before the resource resolves', (tester) async {
      await seedResource();
      // One frame only: the row read has been started but not awaited, so this
      // is the state a user on a slow disk actually sees.
      await pumpViewer(tester, settle: false);

      expect(find.byType(CircularProgressIndicator), findsOneWidget);

      await settleViewer(tester);
    });

    testWidgets('says so when the resource id matches nothing', (tester) async {
      await pumpViewer(tester, resourceId: 'missing');

      expect(find.text(l10nOf(tester).noDataAvailable), findsOneWidget);
    });
  });

  group('title', () {
    testWidgets('uses the resource title', (tester) async {
      await seedResource(title: 'Algebra Basics');
      await pumpViewer(tester);

      expect(find.text('Algebra Basics'), findsOneWidget);
    });

    testWidgets('falls back to the untitled label for a null title', (
      tester,
    ) async {
      await seedResource(title: null);
      await pumpViewer(tester);

      // The title-case key, not the sentence-case list fallback — the two were
      // one duplicated key until Phase 59 split them.
      expect(find.text(l10nOf(tester).untitledResourceTitle), findsOneWidget);
    });
  });

  group('download prompt', () {
    testWidgets('offers a download when the attachment is not on disk', (
      tester,
    ) async {
      await seedResource(filename: 'notes.txt');
      await pumpViewer(tester);

      final l10n = l10nOf(tester);
      expect(find.text(l10n.resourceNotDownloaded), findsOneWidget);
      expect(find.widgetWithText(FilledButton, l10n.download), findsOneWidget);
      expect(find.byIcon(Icons.cloud_download_outlined), findsOneWidget);
    });

    testWidgets('offers nothing to press when no server is configured', (
      tester,
    ) async {
      // `urlFor` needs a config, so with none there is nothing to fetch from.
      // Showing a dead button would be worse than saying so.
      await seedResource(filename: 'notes.txt');
      await pumpViewer(tester, config: null);

      expect(find.byType(FilledButton), findsNothing);
      expect(find.text(l10nOf(tester).resourceNotDownloaded), findsOneWidget);
    });

    testWidgets('offers nothing to press when the row names no attachment', (
      tester,
    ) async {
      // A row with no filename resolves to no URL even with a server.
      await seedResource(filename: null);
      await pumpViewer(tester);

      expect(find.byType(FilledButton), findsNothing);
    });

    testWidgets('clears a stale offline flag when the file is gone', (
      tester,
    ) async {
      // A row flagged downloaded whose file has since been deleted must stop
      // claiming to be downloaded, or the viewer offers no way to get it back.
      await seedResource(filename: 'notes.txt', offline: true);
      await pumpViewer(tester);

      expect(find.text(l10nOf(tester).resourceNotDownloaded), findsOneWidget);
      final row = await db.myLibraryDao.getById('res-1');
      expect(row!.resourceOffline, isFalse);
    });
  });

  group('text/CSV/markdown renderers', () {
    // These were the four withdrawn tests from Phase 91. They now run because
    // the renderers take their content from `resourceContentReaderProvider`
    // (an injectable seam) rather than calling `File.readAsString` in their own
    // `initState`, which the test binding's fake clock cannot drive. A real file
    // still has to exist on disk — the screen's `_getLocalFilePath` checks it to
    // decide between the viewer and the download prompt — but the bytes the
    // renderer displays come from the override.

    testWidgets('renders a text attachment as selectable text', (tester) async {
      await pumpViewerWithContent(
        tester,
        content: 'Hello, plain text.',
        filename: 'notes.txt',
      );

      expect(find.text('Hello, plain text.'), findsOneWidget);
    });

    testWidgets('renders the title above a text attachment', (tester) async {
      await pumpViewerWithContent(
        tester,
        content: 'body',
        filename: 'notes.txt',
        title: 'Field Notes',
      );

      // The title shows in the app bar and again in the renderer's header, so
      // both are present.
      expect(find.text('Field Notes'), findsNWidgets(2));
    });

    testWidgets('renders a CSV attachment as a data table', (tester) async {
      await pumpViewerWithContent(
        tester,
        content: 'name,age\nAda,36\nAlan,41',
        filename: 'people.csv',
      );

      expect(find.byType(DataTable), findsOneWidget);
      // Header + two rows.
      expect(find.text('name'), findsOneWidget);
      expect(find.text('Ada'), findsOneWidget);
      expect(find.text('Alan'), findsOneWidget);
    });

    testWidgets('renders a markdown attachment as markdown', (tester) async {
      await pumpViewerWithContent(
        tester,
        content: '# Heading',
        filename: 'readme.md',
      );

      // flutter_markdown renders the heading text in a larger style, not the
      // raw "# Heading" markup.
      expect(find.text('# Heading'), findsNothing);
      expect(find.text('Heading'), findsOneWidget);
    });
  });
}

class _TestServerConfig extends ServerConfigNotifier {
  _TestServerConfig(this.config);

  final ServerConfig? config;

  @override
  ServerConfig? build() => config;
}
