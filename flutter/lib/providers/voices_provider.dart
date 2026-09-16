import 'dart:convert';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_riverpod/legacy.dart';

import '../core/config/server_config.dart';
import '../core/sync/sync_result.dart';
import '../data/local/app_database.dart';
import '../repository/voices_repository.dart';
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
  final user = ref.watch(sessionProvider).value;
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

  /// The session is **awaited**, and the `await` sits inside the `try`.
  ///
  /// This provider never watches `sessionProvider`, so
  /// `ref.read(...).value` was null until something else resolved it and
  /// the composed post was dropped with no error, no snackbar and no row. In
  /// the shipping app the router's `ref.listen` keeps it resolved, which is
  /// what made this latent rather than visible. The `await` is inside the
  /// `try` because a future can reject where `value` could not.
  ///
  /// The screen with the live window is `VoicesScreen`, whose compose FAB is
  /// **ungated** and renders while `communityFeedProvider` is still loading.
  /// `TeamVoicesScreen` is not it, contrary to an earlier draft of this
  /// comment: it watches `teamMembershipsProvider`, which watches
  /// `sessionProvider`, and only renders its FAB once a membership resolved —
  /// which needs a resolved session. Both are safe now; only one ever wasn't.
  Future<UserRow?> _author() async {
    try {
      return await ref.read(sessionProvider.future);
    } catch (_) {
      return null;
    }
  }

  Future<String?> createPost(
    String message, {
    List<VoiceImageAttachment> attachments = const [],
  }) async {
    final user = await _author();
    if (user == null) return null;
    final id = await ref
        .read(voicesRepositoryProvider)
        .createPost(
          message: message,
          userId: user.couchId ?? user.id,
          userName: user.name ?? '',
          // The nested `user` object is the **only** author identity a news
          // document carries — there is no top-level `userId`/`userName` on
          // the wire — and `NewsMapper.fromDoc` reads all three local columns
          // back out of it. Without this the post uploads anonymously *and*
          // loses its author here at the next sync-in. Kotlin sets it on every
          // write path (`News.createNews`: `news.user = gson.toJson(...)`).
          userJson: VoicesRepository.authorJson(user),
          planetCode: user.planetCode,
          parentCode: user.parentCode,
          // The four keys `VoicesFragment.btnSubmit` builds before it calls
          // `createNews` (`ui/voices/VoicesFragment.kt:140-143`). Without them
          // `_viewInJson` writes the string `"[]"` — which is neither null nor
          // empty, so `isVisibleToUser` does not take its early guard: it
          // decodes to an empty list and `.any` returns false. Either way the
          // post the user just composed is listed by nobody — not in this
          // app's community feed, and not on Planet, where `serialize` omits
          // the key entirely for an empty list. Both halves of the identifier are interpolated unguarded, as
          // Kotlin does: an empty or `"@"` id is the planet-wide wildcard on
          // both sides, so a user missing a code still reaches everyone rather
          // than nobody.
          messageType: 'sync',
          messagePlanetCode: user.planetCode,
          viewInId: communityViewerIdentifier(
            planetCode: user.planetCode,
            parentCode: user.parentCode,
          ),
          viewInSection: 'community',
          attachments: attachments,
        );
    await queuePending();
    return id;
  }

  /// Creates a voice post visible only to members of the specified team.
  /// [teamType] is the port of `getEffectiveTeamType()`
  /// (`BaseTeamFragment.kt:94-96`), which `TeamsVoicesFragment.kt:80` writes
  /// straight into `messageType`. An enterprise is a team *type*, not a
  /// separate feature (Phase 99), so an enterprise's discussion posts are
  /// `"enterprise"` on Planet; hardcoding `'team'` mislabelled every one of
  /// them. Required rather than defaulted so a new caller has to decide: the
  /// Kotlin's own fallback is `""`, and inventing `'team'` for a team document
  /// that omits the field would send a value Kotlin never sends.
  Future<String?> createTeamPost({
    required String teamId,
    required String teamName,
    required String teamType,
    required String message,
    List<VoiceImageAttachment> attachments = const [],
  }) async {
    final user = await _author();
    if (user == null) return null;
    final id = await ref
        .read(voicesRepositoryProvider)
        .createPost(
          message: message,
          userId: user.couchId ?? user.id,
          userName: user.name ?? '',
          userJson: VoicesRepository.authorJson(user),
          planetCode: user.planetCode,
          parentCode: user.parentCode,
          messageType: teamType,
          // The **team's** planet code, not the author's:
          // `TeamsVoicesFragment.kt:81` writes `team?.teamPlanetCode ?: ""`.
          // The port's `Teams` table has no such column (reported against
          // `tables.dart`), and `MyTeam.kt:86` reads the field with
          // `JsonUtils.getString`, so a team document that omits it yields
          // `""` in Kotlin too — `''` is the faithful interim value.
          // `user.planetCode` was a claim about a team that may have been
          // created on another planet, and it disagreed with the port's own
          // chat-share writer, which already sends `''` here
          // (`chat_repository.dart:306-310`).
          messagePlanetCode: '',
          viewInId: teamId,
          viewInSection: 'teams',
          viewInName: teamName,
          attachments: attachments,
        );
    await queuePending();
    return id;
  }

  Future<String?> postReply({
    required String parentId,
    required String message,
    List<VoiceImageAttachment> attachments = const [],
  }) async {
    final user = await _author();
    if (user == null) return null;
    final id = await ref
        .read(voicesRepositoryProvider)
        .postReply(
          parentId: parentId,
          message: message,
          userId: user.couchId ?? user.id,
          userName: user.name ?? '',
          userJson: VoicesRepository.authorJson(user),
          planetCode: user.planetCode,
          parentCode: user.parentCode,
          attachments: attachments,
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
    final user = await _author();
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
    // Awaited like the writers', though this one is only the outbox row's tag
    // and nothing reads it back (`tables.dart:368`). `editPost` and
    // `deletePost` reach here without having resolved the session, so a plain
    // `.value` tagged those rows null.
    return ref
        .read(voicesUploaderProvider)
        .queuePending(config: config, userId: (await _author())?.id);
  }
}

final voicesActionsProvider = Provider<VoicesActions>(VoicesActions.new);

/// Stream of inline comments on a team task or meetup. Comments are `News`
/// rows with `messageType = 'comment'` and `replyTo = parentId`.
final commentsForParentProvider = StreamProvider.family<List<NewsRow>, String>(
  (ref, parentId) =>
      ref.watch(appDatabaseProvider).newsDao.watchCommentsForParent(parentId),
);
