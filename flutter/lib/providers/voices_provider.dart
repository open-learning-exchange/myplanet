import 'dart:convert';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/config/server_config.dart';
import '../core/sync/sync_result.dart';
import '../data/local/app_database.dart';
import '../repository/voices_uploader.dart';
import 'app_providers.dart';
import 'session_provider.dart';
import 'sync_state.dart';

final voiceSearchProvider = StateProvider<String>((ref) => '');

/// How a `viewIn` entry names the viewer: `"<planetCode>@<parentCode>"`.
///
/// Port of `VoicesFragment.getUserIdentifier()` (`:157-165`), and the same
/// string `shareToCommunity` writes into the entry it creates — which is the
/// point. This was `user.couchId ?? user.id`, the CouchDB *user* id, so the
/// port's reader and its own writer disagreed about the key and no shared post
/// could ever reach the feed. The comment that justified it said `viewIn`
/// entries carry server ids; they do, but a planet's, not a user's.
///
/// A blank half is kept rather than trimmed: `isVisibleToUser` treats an empty
/// or `"@"` id as the planet-wide wildcard on both sides.
String communityViewerIdentifier({
  required String? planetCode,
  required String? parentCode,
}) => '${planetCode ?? ''}@${parentCode ?? ''}';

/// The community feed for the signed-in user.
///
/// Visibility depends on who is asking — `isVisibleToUser` matches the viewer
/// against each post's `viewIn` — so this watches the session rather than
/// taking an identifier, and re-filters when the user changes.
final communityFeedProvider = StreamProvider<List<NewsRow>>((ref) async* {
  final user = ref.watch(sessionProvider).valueOrNull;
  if (user == null) {
    yield const [];
    return;
  }
  final query = ref.watch(voiceSearchProvider).trim().toLowerCase();
  final repository = ref.watch(voicesRepositoryProvider);

  await for (final rows in repository.watchCommunityFeed(
    communityViewerIdentifier(
      planetCode: user.planetCode,
      parentCode: user.parentCode,
    ),
  )) {
    if (query.isEmpty) {
      yield rows;
      continue;
    }
    yield rows
        .where(
          (row) =>
              (row.message ?? '').toLowerCase().contains(query) ||
              (row.userName ?? '').toLowerCase().contains(query),
        )
        .toList(growable: false);
  }
});

final voiceProvider = FutureProvider.family<NewsRow?, String>(
  (ref, id) => ref.watch(voicesRepositoryProvider).getById(id),
);

final voiceRepliesProvider = StreamProvider.family<List<NewsRow>, String>((
  ref,
  id,
) async* {
  final repository = ref.watch(voicesRepositoryProvider);
  final row = await repository.getById(id);
  // Replies key on the parent's server id once it has one, so a thread opened
  // on a synced post has to be looked up by `_id`, not by the local row id.
  yield* repository.watchReplies(row?.docId ?? id);
});

final voiceReplyCountProvider = FutureProvider.family<int, String>((
  ref,
  id,
) async {
  final repository = ref.watch(voicesRepositoryProvider);
  final row = await repository.getById(id);
  return repository.replyCount(row?.docId ?? id);
});

/// The voice posts for a specific team.
///
/// Filters `watchTopLevelMessages` to those whose `viewIn` contains the team id.
final teamVoicesProvider = StreamProvider.family<List<NewsRow>, String>((
  ref,
  teamId,
) {
  final dao = ref.watch(newsDaoProvider);
  return dao.watchTopLevelMessages().map((rows) {
    final filtered = rows.where((row) {
      final viewIn = row.viewIn;
      if (viewIn == null || viewIn.isEmpty) return false;
      try {
        final decoded = jsonDecode(viewIn);
        if (decoded is! List) return false;
        return decoded.any((element) {
          if (element is! Map<String, dynamic>) return false;
          final id = element['_id'];
          return id == teamId;
        });
      } catch (_) {
        return false;
      }
    }).toList();

    // Sort newest first using the team post's time (not shared date).
    filtered.sort((a, b) => b.time.compareTo(a.time));
    return filtered;
  });
});

class VoicesSyncNotifier extends SyncNotifier {
  @override
  Future<SyncResult> runSync(
    ServerConfig config,
    void Function(SyncProgress) onProgress,
  ) => ref
      .read(voicesRepositoryProvider)
      .sync(config: config, onProgress: onProgress);
}

final voicesSyncProvider = NotifierProvider<VoicesSyncNotifier, SyncUiState>(
  VoicesSyncNotifier.new,
);

/// The write path for voices, keeping "save locally" and "queue for upload"
/// together so a post cannot be composed and then silently never sent.
class VoicesActions {
  VoicesActions(this.ref);

  final Ref ref;

  Future<String?> createPost(String message) async {
    final user = ref.read(sessionProvider).valueOrNull;
    if (user == null) return null;
    final id = await ref
        .read(voicesRepositoryProvider)
        .createPost(
          message: message,
          userId: user.couchId ?? user.id,
          userName: user.name ?? '',
          planetCode: user.planetCode,
          parentCode: user.parentCode,
        );
    await queuePending();
    return id;
  }

  /// Creates a voice post visible only to members of the specified team.
  Future<String?> createTeamPost({
    required String teamId,
    required String teamName,
    required String message,
  }) async {
    final user = ref.read(sessionProvider).valueOrNull;
    if (user == null) return null;
    final id = await ref
        .read(voicesRepositoryProvider)
        .createPost(
          message: message,
          userId: user.couchId ?? user.id,
          userName: user.name ?? '',
          planetCode: user.planetCode,
          parentCode: user.parentCode,
          messageType: 'team',
          messagePlanetCode: user.planetCode,
          viewInId: teamId,
          viewInSection: 'teams',
          viewInName: teamName,
        );
    await queuePending();
    return id;
  }

  Future<String?> postReply({
    required String parentId,
    required String message,
  }) async {
    final user = ref.read(sessionProvider).valueOrNull;
    if (user == null) return null;
    final id = await ref
        .read(voicesRepositoryProvider)
        .postReply(
          parentId: parentId,
          message: message,
          userId: user.couchId ?? user.id,
          userName: user.name ?? '',
          planetCode: user.planetCode,
          parentCode: user.parentCode,
        );
    await queuePending();
    ref.invalidate(voiceRepliesProvider(parentId));
    ref.invalidate(voiceReplyCountProvider(parentId));
    return id;
  }

  Future<bool> editPost({
    required String newsId,
    required String message,
  }) async {
    final edited = await ref
        .read(voicesRepositoryProvider)
        .editPost(newsId: newsId, message: message);
    if (!edited) return false;
    // `editPost` sets `isEdited`, which is what puts the post back into
    // `pendingUploads` — without re-queuing here the change would sit locally
    // until some other write happened to trigger a queue pass.
    await queuePending();
    ref.invalidate(voiceProvider(newsId));
    return true;
  }

  /// Deleting from the community feed (no [teamName]) un-shares when the post
  /// still has another audience; a team screen passes its team name and the
  /// post dies outright. The repository's doc comment covers the exact rule.
  Future<int> deletePost(String newsId, {String teamName = ''}) async {
    // Withdraw first. A post deleted while its upload is still queued would
    // otherwise be POSTed on the next drain and reappear on the server, with
    // no local row left to record the result against. Done before the
    // repository decides between delete and un-share, so the un-share path
    // pays the small cost of cancelling a queue entry it then re-adds.
    final repository = ref.read(voicesRepositoryProvider);
    final outbox = ref.read(outboxRepositoryProvider);
    for (final id in await repository.collectThreadIds(newsId)) {
      await outbox.cancel(VoicesUploader.type, id);
    }
    final deleted = await repository.deletePost(newsId, teamName: teamName);
    if (deleted == 0) {
      // Un-shared rather than deleted: queue the updated post so the share
      // withdrawal reaches the server too.
      await queuePending();
    }
    ref.invalidate(voiceProvider(newsId));
    return deleted;
  }

  /// Shares the post to the community feed, mirroring
  /// `VoicesViewModel.shareNewsToCommunity`. The session's codes win, with the
  /// configured community as the fallback — the same effective-code rule the
  /// Kotlin applies against its preferences.
  Future<bool> shareToCommunity(String newsId, {String teamName = ''}) async {
    final user = ref.read(sessionProvider).valueOrNull;
    if (user == null) return false;
    final config = ref.read(serverConfigProvider);
    final shared = await ref
        .read(voicesRepositoryProvider)
        .shareToCommunity(
          newsId: newsId,
          userId: user.couchId ?? user.id,
          planetCode: user.planetCode ?? config?.code ?? '',
          parentCode: user.parentCode ?? config?.parentCode ?? '',
          teamName: teamName,
        );
    if (!shared) return false;
    await queuePending();
    ref.invalidate(voiceProvider(newsId));
    return true;
  }

  Future<int> queuePending() async {
    final config = ref.read(serverConfigProvider);
    if (config == null) return 0;
    return ref
        .read(voicesUploaderProvider)
        .queuePending(
          config: config,
          userId: ref.read(sessionProvider).valueOrNull?.id,
        );
  }
}

final voicesActionsProvider = Provider<VoicesActions>(VoicesActions.new);

/// Stream of inline comments on a team task or meetup. Comments are `News`
/// rows with `messageType = 'comment'` and `replyTo = parentId`.
final commentsForParentProvider = StreamProvider.family<List<NewsRow>, String>(
  (ref, parentId) =>
      ref.watch(appDatabaseProvider).newsDao.watchCommentsForParent(parentId),
);
