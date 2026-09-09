import 'dart:developer';

import 'package:flutter/foundation.dart' show visibleForTesting;
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:mime/mime.dart';
import 'package:path/path.dart' as p;

import '../core/config/server_config.dart';
import '../core/files/resource_files.dart';
import '../core/network/network_result.dart';
import '../core/system/device_identity.dart';
import '../core/utils/url_utils.dart';
import '../data/api/planet_api.dart';
import '../data/local/app_database.dart';
import '../providers/app_providers.dart';
import 'outbox_drainer.dart';
import 'outbox_repository.dart';
import 'resources_repository.dart';

/// Durable two-step write-back for resources this device authored, porting
/// `UploadManager.uploadResource` (`:167-216`) together with
/// `UploadConfigs.getResourcesConfig` (`:270-290`) and
/// `FileUploader.uploadAttachment(id, rev, personal: MyLibrary)` (`:36-42`).
///
/// **This direction did not exist in the port at all.** A Phase 119-class
/// reachability gap: [ResourcesRepository.saveLocalResource] has been a
/// careful port for several phases — it writes the `my_library` row, copies the
/// picked file to `<base>/ole/<id>/<basename>`, marks the row offline and adds
/// it to the user's shelf — and nothing ever sent it anywhere. A resource the
/// user created existed on that handset and nowhere else: absent from Planet,
/// absent from their other devices, and destroyed outright by the reset-app
/// action. `my_library` is a preserved table, so a schema bump spared it; that
/// is the only reason this was survivable rather than routinely lossy.
///
/// The Kotlin shape is a per-document POST to the `resources` database
/// (`RoomUploadConfig(endpoint = "resources", …)` through
/// `UploadCoordinator.uploadRoom`), then, for each document CouchDB accepted,
/// a PUT of the file to `resources/<id>/<name>` carrying the returned revision
/// as `If-Match`. Routed here through the [OutboxDrainer] like every other
/// append in the port, so a create survives process death.
///
/// ## Three deliberate divergences, each because the Kotlin is wrong
///
/// **1. The pending predicate.** See [MyLibraryDao.pendingUploads]. Kotlin's is
/// `_rev IS NULL`, which in *this* codebase also matches every course-embedded
/// resource and would file a second copy of documents that already exist on
/// the server.
///
/// **2. In Kotlin the attachment never uploads at all, for two independent
/// reasons — so the port must not treat that path as a working reference.**
///
/// *The lookup finds nothing.* `uploadResource` maps its succeeded items back
/// to library rows with `resourcesRepository.getLibraryItemsByIds(localIds)`
/// (`UploadManager.kt:177`), which is `getByUnderscoreIds` — `WHERE _id IN
/// (:ids)` (`MyLibraryDao.kt:40`) — while `localId` is `config.idExtractor`,
/// i.e. the **local** primary key (`UploadConfigs.kt:278`). Worse, by the time
/// this runs `markResourceUploaded` has already overwritten `_id` with the
/// CouchDB id inside the coordinator's batch loop, before `uploadRoom`
/// returns. So the query is local ids against a column now holding remote
/// ones, `libMap` is empty, `uploadAttachment` is never invoked — and
/// `notifyListener(…, "Uploaded N resources successfully")` fires anyway
/// (`:187`). The map is even built on `it.id` while the query is on `_id`
/// (`:178`), which is the tell.
///
/// *And the file handle would be wrong if it did.*
/// `FileUploader.uploadAttachment` builds the file as
/// `File(personal.resourceLocalAddress)` (`FileUploader.kt:37`), but
/// `saveLocalResource` stores a **basename** in that column
/// (`ResourcesRepositoryImpl.kt:278`) and copies the bytes to
/// `FileUtils.getLibraryFile(dir, id, filename)` — `<ext>/ole/<id>/<name>`.
/// `File("mybook.pdf")` is relative, resolved against the process working
/// directory, so it does not exist; `asRequestBody` throws `FileNotFoundException`
/// and `uploadDoc` catches it and reports "Unable to upload resource" through
/// `onSuccess`, which has no failure channel.
///
/// Either way the document lands with no `_attachments`, the bytes stay on the
/// handset, the row is no longer pending so nothing retries, and **nothing
/// fails loudly**. The second half is precisely the Phase 100
/// verification-photo shape — bytes written under one key and read back under
/// another — reached through the Kotlin rather than through the port.
/// [_uploadAttachment] resolves through [ResourceFiles], the same helper and
/// the same `docId` the writer used, and looks the row up by its **local** id,
/// which is what `outbox.itemId` holds.
///
/// **3. The payload must not drift between sweeps.** Kotlin serializes at send
/// time and so may write `System.currentTimeMillis()` freely; the port stores
/// the payload in `outbox` at enqueue time and re-serializes on every sweep. A
/// field that moves makes the request differ from the one already recorded
/// against this row, so [OutboxRepository.enqueue]'s memo can never match and
/// a document the server refused is POSTed again on every sweep — a fresh
/// duplicate each time. [serialize] takes its timestamps as an argument for
/// that reason, and [queuePending] passes the row's own `createdDate`. Same
/// fix, same reasoning, as `PersonalsUploader.queuePending`'s `uploadedAt`.
class ResourcesUploader {
  ResourcesUploader(this._api, this._resources, this._outbox, this._identity);

  /// The `uploadType` these operations carry in the outbox.
  static const String type = 'resources';

  final PlanetApi _api;
  final ResourcesRepository _resources;
  final OutboxRepository _outbox;
  final DeviceIdentitySource _identity;

  /// Credential-free: this string is persisted in `outbox.endpoint`. The PIN
  /// travels as the `Authorization` header at send time instead.
  static String endpointFor(ServerConfig config) =>
      '${UrlUtils.credentialFreeDbUrl(config)}/resources';

  /// Port of `MyLibrary.serialize(personal, user)` (`MyLibrary.kt:154-189`).
  ///
  /// This is the **create** payload and is deliberately not
  /// [ResourcesRepository.serializeResource] (the port of `serializeResource`
  /// at `MyLibrary.kt:81`), which describes a fully synced resource and carries
  /// `_id`, `_rev` and `_attachments`. Nothing here carries an id: the document
  /// is new, and CouchDB assigns one.
  ///
  /// Field-for-field against the Kotlin, including its quirks:
  ///
  /// * `author`, `publisher` and `linkToLicense` coalesce a null to `''`,
  ///   while `medium`, `description`, `year`, `language`, `resourceType` and
  ///   `openWith` are sent as JSON `null`. That asymmetry is Kotlin's, and it
  ///   is reproduced rather than tidied: which keys Planet sees is the
  ///   contract, and Phase 103 is the standing reminder that a payload the
  ///   server cannot parse is not a cosmetic defect. `Gson.addProperty(String,
  ///   String?)` writes `JsonNull` for a null, so those keys really are
  ///   present-with-null on the wire, not omitted.
  /// * `mediaType` defaults to `'other'`.
  /// * `addedBy` is the **user's id**, not the row's `addedBy` column — which
  ///   holds the user's *name*, because `saveLocalResource` puts `user?.name`
  ///   there (`add_resource_screen`, matching `AddResourceActivity`). The
  ///   column is not read here at all.
  /// * `filename` is the basename of `resourceLocalAddress`, `''` when the
  ///   column is null — and this is **not** a line-for-line copy, because
  ///   Kotlin derives the same field twice, differently. The document gets
  ///   `FileUtils.getFileNameFromUrl` (`MyLibrary.kt:159`), which parses the
  ///   value as a `Uri` and `URLDecoder.decode`s the last segment, while the
  ///   attachment PUT names the file with `getFileNameFromLocalAddress`
  ///   (`FileUploader.kt:38`), a plain `substringAfterLast('/')`. Those
  ///   disagree for any name the first one transforms: `a+b.pdf` becomes
  ///   `a b.pdf`, `50%.pdf` throws inside `URLDecoder` and yields `''`, and
  ///   `notes#1.pdf` truncates at the fragment delimiter to `notes`. A
  ///   document whose `filename` does not match its attachment's name is a
  ///   resource Planet cannot resolve, so the two are unified here on the
  ///   plain basename — the attachment side, since that one names bytes that
  ///   really exist. [_uploadAttachment] derives it the same way.
  ///
  ///   One consequence, stated rather than hidden: for those names the port's
  ///   document carries a different `filename` string than Kotlin's would.
  ///   That is the point — Kotlin's is the one the attachment contradicts.
  /// * `privateFor` is a nested object `{'teams': …}`, and **only present**
  ///   when the resource is private *and* has a team. Both halves of that
  ///   condition are Kotlin's.
  /// * `isDownloadable` is a literal boolean `true`.
  /// * `sourcePlanet` and `resideOn` both carry the user's planet code.
  /// * Kotlin writes `createdDate` twice (`:157` and `:186`) with the same
  ///   expression, so one key here is not a loss.
  ///
  /// [uploadedAt] supplies both `uploadDate` and `updatedDate`, which Kotlin
  /// reads from the clock at send time. See the class doc for why a caller has
  /// to pass a stable value instead.
  static Map<String, dynamic> serialize(
    MyLibraryRow row, {
    required int uploadedAt,
    String? addedById,
    String? planetCode,
  }) {
    final localAddress = row.resourceLocalAddress;
    return {
      'title': row.title,
      'uploadDate': uploadedAt,
      'createdDate': row.createdDate,
      'filename': (localAddress == null || localAddress.isEmpty)
          ? ''
          : p.basename(localAddress.replaceAll(r'\', '/')),
      'author': row.author ?? '',
      'addedBy': addedById,
      'medium': row.medium,
      'description': row.description,
      'year': row.year,
      'language': row.language,
      'publisher': row.publisher ?? '',
      'linkToLicense': row.linkToLicense ?? '',
      'subject': row.subject,
      'level': row.level,
      'resourceType': row.resourceType,
      'openWith': row.openWith,
      'mediaType': row.mediaType ?? 'other',
      'resourceFor': row.resourceFor,
      'private': row.isPrivate,
      if (row.isPrivate && row.privateFor != null)
        'privateFor': {'teams': row.privateFor},
      'isDownloadable': true,
      'sourcePlanet': planetCode,
      'resideOn': planetCode,
      'updatedDate': uploadedAt,
    };
  }

  /// Queues every resource this device authored that has not reached the
  /// server.
  ///
  /// Safe to call repeatedly: [OutboxRepository.enqueue] keys on
  /// `(uploadType, itemId)`, so a resource already queued has its payload
  /// refreshed rather than being POSTed twice. That refresh is what carries a
  /// later [ResourcesRepository.updateLocalResource] edit onto the wire — see
  /// [queueOne].
  ///
  /// [user] is the signed-in account, and it is a parameter rather than a
  /// dependency because Kotlin resolves it the same way, once per upload pass
  /// (`UploadManager.uploadResource:169` — `userRepository.getUserModel()`).
  /// A null user costs `addedBy`, `sourcePlanet` and `resideOn`; it does not
  /// stop the resource being sent, matching Kotlin's `user?.id` /
  /// `user?.planetCode`.
  Future<int> queuePending({
    required ServerConfig config,
    UserRow? user,
  }) async {
    final pending = await _resources.pendingUploads();
    if (pending.isEmpty) return 0;
    final endpoint = endpointFor(config);
    final identity = await _identity.read();
    for (final row in pending) {
      await _enqueue(row, endpoint, identity, user);
    }
    return pending.length;
  }

  Future<void> _enqueue(
    MyLibraryRow row,
    String endpoint,
    DeviceIdentity identity,
    UserRow? user,
  ) => _outbox.enqueue(
    uploadType: type,
    itemId: row.id,
    endpoint: endpoint,
    payload: {
      ...serialize(
        row,
        // Deterministic, and the row's creation date is the honest value: the
        // moment of the POST is what Kotlin sends, and this app cannot know it
        // at enqueue time because the drain may be days later. See the class
        // doc for what a drifting field costs here.
        uploadedAt: row.createdDate,
        addedById: user?.id,
        planetCode: user?.planetCode,
      ),
      // `MyLibrary.serialize` carries `addDocumentOrigin()` plus both device
      // names (`:187-189`), which is exactly [DeviceIdentity.documentFields].
      // Layered at queue time rather than stored on the row, so a resource
      // created under one device name and drained under another reports the
      // current one — the Phase 44 convention.
      ...identity.documentFields,
    },
    userId: user?.id,
  );

  /// The [OutboxHandler] for [type].
  ///
  /// Port of `UploadConfigs.getResourcesConfig`'s `markUploaded` followed by
  /// `UploadManager.uploadResource`'s per-item `uploadAttachment` call. The
  /// ordering is Kotlin's: the document is marked uploaded first, and the
  /// attachment is attempted afterwards and best-effort.
  ///
  /// **Marking and sending are one step here, which closes a Kotlin hole.**
  /// `RetryQueueWorker.processOperationInternal` re-POSTs a queued resource
  /// document and calls only `retryQueue.markCompleted`
  /// (`RetryQueueWorker.kt:231`) — never `markResourceUploaded` — so the local
  /// row keeps `_rev = NULL`, stays inside `getPendingUploads()`, and the next
  /// `uploadResource` POSTs it a second time. The same happens whenever
  /// Kotlin's `markUploaded` reports a local failure: the document is filed and
  /// the row is still pending. In this handler the mark is part of the send, so
  /// a delivered document is always recorded as delivered.
  ///
  /// A 2xx whose body carries no usable `id`/`rev` is reported as a failure so
  /// the row is not deleted while `_id` stays null — otherwise the next sweep
  /// would find the resource pending again and POST a duplicate on every
  /// drain, forever. [OutboxRepository] classifies that as
  /// [OutboxRefusal.indeterminate] and does not retry it, which is the right
  /// bound: this is an append with a server-assigned id, so a duplicate would
  /// be undetectable afterwards and the transport already said the write
  /// landed.
  OutboxHandler get handler => (row, payload, authHeader) async {
    final result = await _api.postJsonObject(
      row.endpoint,
      payload,
      authHeader: authHeader,
    );

    if (result case NetworkSuccess<Map<String, dynamic>>(:final data)) {
      final couchId = data['id'];
      final rev = data['rev'];
      if (couchId is! String || rev is! String) {
        return const NetworkError<Map<String, dynamic>>(
          null,
          'Upload response carried no id/rev',
        );
      }
      // Port of `ResourcesRepositoryImpl.markResourceUploaded:803-806`. Its
      // false return means the local row vanished between the POST and now,
      // which Kotlin surfaces as a failed item via `markUploaded`'s
      // `results.filter { !markResourceUploaded(…) }`. Here the document is
      // already filed and there is no row left to attach bytes to or to
      // re-POST, so the operation is finished: reporting failure would only
      // keep an un-actionable row in the outbox.
      final marked = await _resources.markResourceUploaded(
        row.itemId,
        couchId,
        rev,
      );
      if (!marked) {
        log('Resource ${row.itemId} disappeared before its id could be stored');
        return result;
      }
      await _uploadAttachment(row, couchId, rev, authHeader);
    }
    return result;
  };

  /// PUTs the picked file to `resources/<id>/<name>` once the document POST
  /// lands — the port of `FileUploader.uploadAttachment(id, rev, personal:
  /// MyLibrary)`, with its path bug fixed.
  ///
  /// **The one key.** The bytes were written by
  /// [ResourcesRepository.saveLocalResource] through
  /// [ResourceFiles.fileFor] under the row's **local** id, and they are read
  /// back here through [ResourceFiles.existingFileFor] under
  /// `row.itemId` — which *is* that local id, because
  /// [OutboxRepository.enqueue] is keyed on `MyLibraryRow.id`. One derivation,
  /// one helper, both sides. Phase 100's verification photo is what happens
  /// when the two sides each pick their own key, and
  /// `resource_upload_round_trip_test.dart` drives the writer and this method
  /// together rather than trusting either half's own fixture.
  ///
  /// A row with **no file** is a no-op, not an error, and that case is real
  /// here rather than defensive: Kotlin refuses to save a resource without one
  /// (`ResourcesRepositoryImpl.kt:235-238`) while the port's form does not, so
  /// a metadata-only row can exist and its document is still worth filing.
  /// Kotlin reaches the same outcome by accident — its `File(basename)` never
  /// exists either, so every attachment took this branch.
  Future<void> _uploadAttachment(
    OutboxRow row,
    String couchId,
    String rev,
    String? authHeader,
  ) async {
    final localAddress = (await _resources.getLibraryItemById(
      row.itemId,
    ))?.resourceLocalAddress;
    if (localAddress == null || localAddress.isEmpty) return;

    // No second `filename.isEmpty` guard, because there was one and mutation
    // testing showed it could not fail: [ResourceFiles.existingFileFor]
    // already returns null for an empty filename, so it — not this method —
    // is what guarantees the no-op. The early return above is an optimisation
    // that skips a pointless disk lookup, not the guarantee, and writing it as
    // though it were the guarantee invites the next reader to stop checking.
    final filename = p.basename(localAddress.replaceAll(r'\', '/'));

    final file = await ResourceFiles.existingFileFor(
      docId: row.itemId,
      filename: filename,
    );
    if (file == null) return;

    late final List<int> bytes;
    try {
      bytes = await file.readAsBytes();
    } on Exception catch (e, stack) {
      log('Could not read local resource file', error: e, stackTrace: stack);
      return;
    }

    // `row.endpoint` is already `<db>/resources`, so the document id and the
    // attachment name append directly — the same construction
    // `SubmitPhotosUploader` uses, and the same
    // `String.format("%s/resources/%s/%s", url, id, name)` Kotlin builds.
    final attachmentUrl =
        '${row.endpoint}/${Uri.encodeComponent(couchId)}'
        '/${Uri.encodeComponent(filename)}';
    final contentType = lookupMimeType(filename) ?? 'application/octet-stream';

    final attachResult = await _api.uploadAttachment(
      attachmentUrl,
      bytes: bytes,
      authHeader: authHeader,
      // Kotlin passes the rev from the POST response as `If-Match`
      // (`FileUploader.getHeaderMap:85`), which is how CouchDB accepts an
      // attachment onto an existing document.
      ifMatch: rev,
      contentType: contentType,
    );
    if (attachResult case NetworkSuccess<Map<String, dynamic>>(:final data)) {
      // CouchDB bumped the revision to accept these bytes. Kotlin discards
      // that response, which leaves the local row a revision behind and makes
      // the next sync ask the user to re-download their own file; see
      // [MyLibraryDao.adoptAttachmentRev].
      final newRev = data['rev'];
      if (newRev is String && newRev.isNotEmpty) {
        await _resources.adoptAttachmentRev(row.itemId, newRev);
      }
    } else {
      log('Resource attachment upload failed: $attachResult');
    }
  }

  static String authHeaderFor(ServerConfig config) =>
      UrlUtils.authHeader(config);
}

/// The safety net, and **it is not wired up** — see the note below.
///
/// Kotlin's `uploadResource` has exactly the unconditional-sweep property
/// Phase 134 was about: three callers with no precondition on how the row got
/// there (`AutoSyncWorker.kt:129`, `UserDataWorker.kt:84`,
/// `TeamsRepositoryImpl.kt:928`). The port has **one** enqueue site, in
/// `add_resource_screen._save`, so a resource whose enqueue never ran has
/// nothing to deliver it.
///
/// That window is narrower than the submissions one Phase 134 closed, because
/// the write site calls [ResourcesUploader.queuePending] — an *unscoped* sweep
/// — rather than enqueuing the one row it just wrote, so saving any resource
/// rescues every stranded one. It is not closed. A user who creates a resource
/// with no server configured (`serverConfigProvider` null, which is the whole
/// offline-first premise), or whose process dies between the row write and the
/// enqueue, and who never returns to that screen, never uploads it — and the
/// row is invisible in Planet for ever, with the reset-app action able to
/// destroy it in the meantime.
///
/// **`lib/background_entrypoint.dart` belongs to another lane this round**, so
/// the two calls that close it are reported rather than made. They belong
/// beside the existing voices and submissions sweeps: this one in
/// `drainOutbox`, for the reason `sweepPendingVoices` documents at length —
/// `syncSteps` runs only for a due `autoSync` task with auto-sync enabled,
/// which is precisely not the user most likely to have an undelivered write.
/// Ordering against the other sweeps is free: no sync step writes `my_library`
/// rows over a locally authored one ([MyLibraryMapper.fromDoc] keys on the
/// CouchDB `_id`, and a pending resource has none), and the port's own
/// `deleteNotIn` already spares a row with no `_rev`.
///
/// [userId] is nullable and a null one is not an early return: Kotlin passes
/// `user?.id` and `user?.planetCode` straight through, so a handset whose
/// session has gone still sends the resource, minus its attribution. A handset
/// whose session has gone is exactly the one with a stranded write on it.
///
/// Exposed because `executeBackgroundTask` needs a Flutter binding, real
/// preferences and a WorkManager engine, so a body written inline in that
/// closure is unreachable from a unit test — the same reason the sweeps it
/// sits beside are.
@visibleForTesting
Future<void> sweepPendingResources(
  ProviderContainer container, {
  required ServerConfig config,
  required String? userId,
}) async {
  try {
    final user = userId == null
        ? null
        : await container.read(userDaoProvider).getById(userId);
    await container
        .read(resourcesUploaderProvider)
        .queuePending(config: config, user: user);
  } catch (_) {
    // Swallowed for the reason the sweeps beside it are: a throwing
    // `drainOutbox` adds `outboxDrain` to the runner's `failedSteps` and asks
    // the OS to retry the whole task, and no Kotlin caller of `uploadResource`
    // does that — `UserDataWorker` wraps it and still returns
    // `Result.success()`. Not hypothetical: `queuePending` reads device
    // identity, which rethrows on an engine with no channel and no primed
    // cache.
  }
}
