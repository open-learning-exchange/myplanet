import 'dart:developer';

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
import 'teams_repository.dart';
import 'teams_uploader.dart';

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
  ResourcesUploader(
    this._api,
    this._resources,
    this._teams,
    this._outbox,
    this._identity,
  );

  /// The `uploadType` these operations carry in the outbox.
  static const String type = 'resources';

  /// The `uploadType` of the **attachment** retry row, filed only when a PUT
  /// is refused — see [attachmentHandler]. Keyed on the document's CouchDB id
  /// rather than the local one, because by the time it is written the row has
  /// adopted that identity.
  static const String attachmentType = 'resource_attachment';

  final PlanetApi _api;
  final ResourcesRepository _resources;
  final TeamsRepository _teams;
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
  /// [_enqueue], reached through this method on every path.
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
    final endpoint = endpointFor(config);

    // **Unconditional, and ahead of the early return below.** The two sweeps
    // are independent in reachability: a handset can have every document filed
    // and still owe an attachment, which is precisely the state this round
    // exists to close, and gating the attachment sweep on `pending.isNotEmpty`
    // would make it reachable only for a user who happens to have a second
    // undelivered resource. Placed here rather than in `sweepPendingResources`
    // so it also reaches `DashboardSyncNotifier.queuePendingResources` and
    // `add_resource_screen._save`, the other two callers — one sweep, three
    // call sites, no other lane's file touched.
    //
    // **The `try` makes them independent in *failure* too, and its absence was
    // a defect the audit caught.** The attachment sweep reaches `dart:io`
    // through [ResourceFiles.existingFileFor], so a `FileSystemException` or a
    // `MissingPluginException` from the documents-directory lookup escaped
    // here and took the **document** sweep with it — the one that is the only
    // thing getting a user-authored resource to the server at all. Every
    // caller swallows the throw, so the failure was silent, and it was
    // *persistent* rather than transient: `attachment_pending` survives a
    // schema bump, so on a handset where that lookup fails the document sweep
    // was blocked on every pass, for ever.
    //
    // A smaller, newer safety net must never gate an older, larger one. Same
    // reasoning as `sweepPendingResources`' own catch, quoted there.
    try {
      await queuePendingAttachments(config: config, userId: user?.id);
    } catch (e, stack) {
      log('The attachment sweep failed', error: e, stackTrace: stack);
    }

    final pending = await _resources.pendingUploads();
    if (pending.isEmpty) return 0;
    final identity = await _identity.read();
    var queued = 0;
    for (final row in pending) {
      // **A resource POST is an append, so an in-flight row must be left
      // alone.** [OutboxRepository.enqueue] deliberately puts an `in_progress`
      // row back to `pending` so a payload edited mid-flight is not lost with
      // the row `markCompleted` deletes — and `markCompleted` is
      // `deleteIfInProgress`, so the send that succeeds moments later deletes
      // nothing, the row survives `pending` with the same body, and the next
      // drain POSTs a **second CouchDB document**. Nothing downstream catches
      // it: the handler carries no `_id`, so the server mints a fresh one, and
      // `markResourceUploaded` then points the local row at the duplicate
      // while the first document becomes an unidentifiable orphan in the
      // shared Planet catalog.
      //
      // Reached without any exotic timing: a drain claims this row on a slow
      // link while the user saves a second resource in `AddResourceScreen`,
      // whose `_save` sweeps every pending row including this one. Also
      // cross-isolate, between the headless drain and the UI — the case
      // [OutboxRepository.isInFlight] documents — and more reachable once
      // [sweepPendingResources] is wired.
      //
      // Same guard, same reasoning, as `SubmissionsUploader.queuePending`,
      // `VoicesUploader` and `AdoptedSurveysUploader`. The shelf can skip it
      // because its payload is derived state a replay recomputes; an append
      // cannot.
      if (await _outbox.isInFlight(type, row.id)) continue;
      queued++;
      await _enqueue(row, endpoint, identity, user);
    }
    return queued;
  }

  /// Re-arms every resource whose attachment PUT has not been delivered.
  ///
  /// **The safety net the attachment half never had, and the reason it is a
  /// sweep rather than a longer retry ladder.** Two routes lose a file and
  /// neither is reachable from the retry row: the process dies between
  /// [MyLibraryDao.markUploaded] and [_uploadAttachment], so no retry row is
  /// ever filed; or the row spends its five-attempt ladder (~15 minutes) on a
  /// handset that is offline for longer. Both leave the same state, and until
  /// [MyLibraryTable.attachmentPending] existed that state was
  /// indistinguishable from success — so there was nothing to sweep *from*.
  /// Now there is, and this reads it.
  ///
  /// Shaped to the Phase 148 policy rather than around it:
  ///
  ///  * **One row per `(uploadType, itemId)`, for ever.**
  ///    [OutboxRepository.enqueue] keys on the pair, so calling this on every
  ///    sync pass refreshes one row rather than accreting one per pass. The
  ///    accretion the policy stopped was unbounded *rows*; this adds none.
  ///  * **A terminal row is a memo, and the memo is respected.** A PUT the
  ///    server refused on the bytes (400, 413, 415, 422) stays terminal and
  ///    this sweep cannot re-ask, which is right: resending identical bytes to
  ///    a server that has already rejected them cannot help. The one 4xx that
  ///    is *not* a verdict on the bytes — a 409 against a stale `If-Match` —
  ///    is answered inline instead, in [_sendAttachment].
  ///  * **[OutboxRepository.rearm] is deliberately not used**, though it would
  ///    be the shorter route past that memo. Its own dartdoc reserves it for a
  ///    person tapping *Retry*, and the distinction holds here: this is the
  ///    app deciding, unattended, that something might have changed.
  ///  * **An in-flight row is left alone**, the guard [queuePending] documents
  ///    at length for the document POST. It matters less here — a duplicate
  ///    PUT to a known URL writes the same bytes under the same name rather
  ///    than filing a second document — but a refreshed payload can still lose
  ///    a `fileDocId` a live drain is about to read.
  ///
  /// **The disk check is what keeps this from churning.** A row flagged with
  /// no bytes under its id is not enqueued at all, so the sweep does not file
  /// a row per pass for a resource whose file the user deleted, or whose
  /// [ResourceFiles.moveResourceDirectory] failed — those cost one `stat` per
  /// pass and no outbox row, no request and no state change. The flag stays
  /// set rather than being cleared, because "the bytes are not where the row
  /// says they are" is not evidence the attachment was delivered, and this
  /// class's whole defect was reporting delivery it had not established.
  ///
  /// [fileDocId] is the row's own id, which is the CouchDB id after
  /// [MyLibraryDao.markUploaded] rekeys it, and is what every file reader in
  /// the port resolves. The retry row written by [_enqueueAttachmentRetry] can
  /// carry a *different* one when the directory move failed; the in-flight
  /// guard plus the disk check keep this sweep from overwriting that with a
  /// worse answer, since a row whose bytes are not under its id is skipped
  /// here entirely.
  ///
  /// Returns how many rows were queued, for the tests and for symmetry with
  /// [queuePending].
  Future<int> queuePendingAttachments({
    required ServerConfig config,
    String? userId,
  }) async {
    final pending = await _resources.pendingAttachments();
    if (pending.isEmpty) return 0;
    final endpoint = endpointFor(config);
    var queued = 0;
    for (final row in pending) {
      final localAddress = row.resourceLocalAddress;
      if (localAddress == null || localAddress.isEmpty) continue;
      final filename = p.basename(localAddress.replaceAll(r'\', '/'));
      // Where the writer put them and where every reader looks. Checked before
      // enqueueing so a row with nothing to send never becomes an outbox row
      // the drain would file and delete again on every pass.
      final file = await ResourceFiles.existingFileFor(
        docId: row.id,
        filename: filename,
      );
      if (file == null) continue;
      if (await _outbox.isInFlight(attachmentType, row.id)) continue;
      await _outbox.enqueue(
        uploadType: attachmentType,
        itemId: row.id,
        endpoint: endpoint,
        payload: {'filename': filename, 'fileDocId': row.id},
        userId: userId,
      );
      queued++;
    }
    return queued;
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
    // **The replay guard, and it closes a duplicate-document window that has
    // been open since this uploader landed.**
    //
    // A drain killed after the POST but before `markCompleted` leaves the
    // outbox row `in_progress`; `OutboxRepository.recoverStuck` — which
    // `OutboxDrainScope` and `background_entrypoint` both run at startup —
    // returns it to `pending`, and the handler runs again. This endpoint
    // carries no `_id` (see [serialize]), so CouchDB mints a fresh one and the
    // shared catalog gains a **second, indistinguishable document**. The
    // `isInFlight` guard in [queuePending] does not cover this: it stops a
    // concurrent sweep re-enqueueing, not a replay of the row itself.
    //
    // A missing local row is the evidence, and it is exact rather than
    // heuristic. [MyLibraryDao.markUploaded] rekeys the row onto the CouchDB
    // id, so `row.itemId` — the local uuid this operation was filed under —
    // stops resolving the moment the mark lands. The only other way it goes
    // missing is the user deleting the resource before the drain, and skipping
    // the POST is right there too: today that case files a document with no
    // local row to point at it, which `markUploaded`'s `false` return then
    // reports while the orphan stays on the server.
    //
    // It cannot be a *pending* row that a sync pruned: `deleteNotIn` spares
    // rows with no `_rev`, which is exactly what pending means here.
    if (await _resources.getLibraryItemById(row.itemId) == null) {
      log('Resource ${row.itemId} is already filed or gone; not re-POSTing');
      return const NetworkSuccess<Map<String, dynamic>>(<String, dynamic>{});
    }

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
      //
      // **The mark comes before the byte move, and the order is load-bearing.**
      // It is what arms the replay guard at the top of this handler: until the
      // row has adopted the document's id, a killed drain re-POSTs and files a
      // duplicate. Moving the files first would widen that window by exactly
      // the duration of a directory rename — and worse, a replay would then
      // find the source gone, take
      // [ResourceFiles.moveResourceDirectory]'s "nothing to move" answer, and
      // look for the attachment under the *second* document's id, so the bytes
      // would be orphaned under the first one with nothing left that knows
      // where they are.
      //
      // A throw here is caught rather than propagated for the same reason, and
      // the choice is a genuine tie rather than an obvious one: a failed mark
      // leaves a filed document with a still-pending row, so it is re-POSTed
      // either by the replay (if this reports failure) or by the next
      // `queuePending` sweep (if it reports success). Success is chosen to
      // match the `!marked` branch and because it does not re-POST
      // *immediately*. A drift transaction failing on a local database is
      // "the database is broken", and no ordering rescues that.
      bool marked;
      try {
        marked = await _resources.markResourceUploaded(
          row.itemId,
          couchId,
          rev,
        );
      } catch (e, stack) {
        log('Could not record $couchId locally', error: e, stackTrace: stack);
        return result;
      }
      if (!marked) {
        log('Resource ${row.itemId} disappeared before its id could be stored');
        return result;
      }

      // **The bytes move with the key.** Every file reader in the port
      // resolves `couchId ?? id`, so leaving the files under the old uuid
      // would hand the viewer a directory that does not exist — the Phase 100
      // shape, with each half correct and the pair wrong.
      //
      // A false answer is not fatal: the document is filed and the row is
      // marked, so nothing will re-POST it. [_uploadAttachment] is told where
      // the files actually are, and that answer is carried into the retry row
      // rather than re-derived there.
      final bytesMoved = await ResourceFiles.moveResourceDirectory(
        fromDocId: row.itemId,
        toDocId: couchId,
      );
      if (!bytesMoved) {
        log(
          'Resource ${row.itemId} kept its files under the old id: '
          'the move to $couchId failed',
        );
      }
      // The row answers to [couchId] from here on, not to `row.itemId`, which
      // is the local uuid the outbox row was keyed on and which no longer
      // names a `my_library` row.
      //
      // **Nothing after the POST may throw out of this handler.** The drainer
      // records a throw as a failed send and puts the row back to `pending`,
      // so the next drain POSTs the same body again — and this endpoint mints
      // a fresh `_id` each time, leaving two indistinguishable documents in
      // the shared catalog. The document is already filed by the time we get
      // here, so every remaining step is a best-effort follow-up: a team link
      // that cannot be written, or bytes that cannot be read, are worth less
      // than the duplicate they would cost.
      try {
        await _linkPrivateResourceToTeam(row, couchId, payload);
        await _uploadAttachment(
          row,
          couchId,
          rev,
          authHeader,
          fileDocId: bytesMoved ? couchId : row.itemId,
        );
      } catch (e, stack) {
        log(
          'Resource $couchId was filed but its follow-up steps did not finish',
          error: e,
          stackTrace: stack,
        );
      }
    }
    return result;
  };

  /// Port of the second half of `ResourcesRepositoryImpl.markResourceUploaded`
  /// (`:808-816`): a private team resource also gets a local `resourceLink`
  /// team document, so the resource appears under that team.
  ///
  /// **This was omitted in this phase's first cut, on a false premise, and the
  /// premise is worth recording.** Two doc comments claimed
  /// `TeamsRepository.createLocalResourceLink` "does not exist anywhere in the
  /// port". It existed — as `addResourceLink`, a match for the Kotlin on every
  /// field it wrote (blank guard, generated id, `docType` `'resourceLink'`,
  /// `teamType` `'local'`, `isUpdated` true) plus a duplicate check Kotlin
  /// lacks. The search was for the Kotlin *name* rather than the behaviour,
  /// which is the same mistake as matching `<basename>_test.dart` instead of
  /// grepping for the symbol.
  ///
  /// **And "a field-for-field match" was itself too strong**, which schema v50
  /// exposed: the one method stood for *both* Kotlin producers, which stamp
  /// different planet-code fields, and matched neither on those. It is now
  /// split, and this call goes to [TeamsRepository.createLocalResourceLink] —
  /// the Kotlin name after all. A correction that lands on the right symbol
  /// can still overstate how closely it matches. Without this, a team leader's private
  /// resource uploads its own document and **no team ever links to it**: the
  /// team's Resources tab is empty on Planet and on every other member's
  /// handset, for bytes that are already on the server.
  ///
  /// **It has to run here, after the POST**, because the link must carry the
  /// **CouchDB** id. `markUploaded` leaves `resourceId` at the local uuid, so
  /// `TeamResourceActions.add`'s `resource.resourceId` would name a document
  /// that does not exist. Kotlin puts it inside `markResourceUploaded` for
  /// exactly this reason.
  ///
  /// The link is then enqueued the way `TeamResourceActions.add` enqueues one
  /// — `TeamsUploader.resourceType` against `<db>/teams` — because writing the
  /// row alone would leave it local. Kotlin needs no equivalent: its
  /// `updated = true` is swept by the team upload pass.
  ///
  /// `planetCode` comes out of the payload rather than a session read: the
  /// drain may run days later in a headless isolate, and `sourcePlanet` is the
  /// planet code this very document was serialized with, so it cannot drift
  /// from it.
  ///
  /// **It now reaches the row**, which it did not when this paragraph was
  /// written — it said `addResourceLink` *"currently accepts `planetCode` and
  /// ignores it"*, and closing that needed the schema bump this call was
  /// waiting on. It also needed a **different method**: Kotlin's two
  /// resource-link producers stamp different fields, and the one this path
  /// ports is `createLocalResourceLink` (`sourcePlanet` and `teamPlanetCode`),
  /// not `addResourceLinks` (`teamPlanetCode` and `userPlanetCode`), which is
  /// what [TeamsRepository.addResourceLink] is. Calling the wrong one would
  /// have put `userPlanetCode` on the wire and left `sourcePlanet` off it.
  ///
  /// Best-effort, like the attachment: the resource document is already filed,
  /// and reporting failure would re-POST it and duplicate it to fix a missing
  /// link.
  Future<void> _linkPrivateResourceToTeam(
    OutboxRow row,
    String couchId,
    Map<String, dynamic> payload,
  ) async {
    // Read under the **CouchDB** id: [ResourcesRepository.markResourceUploaded]
    // has already rekeyed the row, and `row.itemId` is the local uuid the
    // outbox entry was filed under.
    final resource = await _resources.getLibraryItemById(couchId);
    final teamId = resource?.privateFor;
    // Kotlin's condition is `library.isPrivate && !library.privateFor
    // .isNullOrBlank()` (`ResourcesRepositoryImpl.kt:809`), and
    // `createLocalResourceLink` re-checks both ids for blankness (`:725`).
    if (resource == null || !resource.isPrivate) return;
    if (teamId == null || teamId.isEmpty) return;

    try {
      final link = await _teams.createLocalResourceLink(
        teamId: teamId,
        resourceId: couchId,
        title: resource.title ?? '',
        planetCode: payload['sourcePlanet'] as String?,
      );
      if (link == null) return;
      await _outbox.enqueue(
        uploadType: TeamsUploader.resourceType,
        itemId: link.id,
        endpoint: _siblingDatabase(row.endpoint, 'teams'),
        payload: TeamsRepository.serializeTeamDocument(link),
        userId: row.userId,
      );
    } on Exception catch (e, stack) {
      log('Could not link resource to team', error: e, stackTrace: stack);
    }
  }

  /// `<db>/resources` -> `<db>/<name>`.
  ///
  /// Derived from the stored endpoint rather than a `ServerConfig` read,
  /// because a handler runs at drain time with no config in scope — the same
  /// reason `PersonalsUploader._uploadAttachment` rebuilds its base this way.
  static String _siblingDatabase(String endpoint, String name) {
    const suffix = '/resources';
    final base = endpoint.endsWith(suffix)
        ? endpoint.substring(0, endpoint.length - suffix.length)
        : endpoint;
    return '$base/$name';
  }

  /// PUTs the picked file to `resources/<id>/<name>` once the document POST
  /// lands — the port of `FileUploader.uploadAttachment(id, rev, personal:
  /// MyLibrary)`, with its path bug fixed.
  ///
  /// **The one key.** The bytes were written by
  /// [ResourcesRepository.saveLocalResource] through [ResourceFiles.fileFor]
  /// under the row's **local** id, and [handler] has just moved them to the
  /// CouchDB id along with the row itself
  /// ([ResourceFiles.moveResourceDirectory]), so [fileDocId] is normally
  /// [couchId]. It is a parameter rather than an assumption because the move
  /// can fail: the bytes are then still under the old uuid, and sending them
  /// from there is better than filing a document with no attachment. One
  /// derivation, handed down, never re-guessed — Phase 100's verification
  /// photo is what happens when the two sides each pick their own key, and
  /// `resources_uploader_test.dart`'s *the round trip* group drives the writer
  /// and this method together rather than trusting either half's own fixture.
  ///
  /// A row with **no file** is a no-op, not an error, and that case is real
  /// here rather than defensive: Kotlin refuses to save a resource without one
  /// (`ResourcesRepositoryImpl.kt:235-238`) while the port's form does not, so
  /// a metadata-only row can exist and its document is still worth filing.
  /// Kotlin reaches the same outcome by accident — its `File(basename)` never
  /// exists either, so every attachment took this branch.
  ///
  /// **A refusal is no longer the end of it**, which is the hole this closes.
  /// Kotlin's attachment PUT is best-effort with no failure channel at all
  /// (`uploadDoc` reports "Unable to upload resource" through `onSuccess`), and
  /// so was the port's: `adoptAttachmentRev` keeps `rev == downloadedRev` on
  /// success and [MyLibraryDao.markUploaded] keeps them equal when there is
  /// nothing to send, so **nothing downstream can even tell the difference**
  /// between an attachment that landed and one that never will. The document
  /// sits on Planet with no `_attachments` for ever, the bytes sit on the one
  /// handset that authored them, and the user is told the resource uploaded.
  /// A refused PUT files its own outbox row ([attachmentType]) so the next
  /// drain tries again. See [attachmentHandler] for what that row is allowed
  /// to do.
  ///
  /// **That row alone only narrowed the hole, and schema v50 is what closed
  /// it.** The paragraph this replaces ended: *"there is no sweep to re-arm
  /// it, because there is nothing to re-arm it from — after
  /// [MyLibraryDao.markUploaded] a resource whose attachment landed and one
  /// whose attachment never will are the same row, byte for byte. So a device
  /// offline for longer than the ladder still loses the attachment silently.
  /// Closing it properly needs a column recording delivery."* It does, and
  /// that column is [MyLibraryTable.attachmentPending]. Both losing routes are
  /// re-armable by [queuePendingAttachments]: the ladder spent while offline
  /// (a transport failure classifies `transient`, which
  /// [OutboxRepository.enqueue] re-arms with a fresh ladder), and the process
  /// killed before any retry row was filed at all — which no outbox row could
  /// ever have covered, because it died before there was one.
  ///
  /// **One residual case is not**, and saying so is the point: a row whose
  /// [ResourceFiles.moveResourceDirectory] failed has its bytes under the old
  /// uuid, so the sweep's disk check — which looks under the row's own id —
  /// misses and skips it. If that row's ladder is then spent, nothing re-arms
  /// it. The flag stays set, so nothing reports a delivery that did not
  /// happen; the attachment simply stays on the handset. Closing it means
  /// falling back to the retry row's stored `fileDocId` when the row's own id
  /// has no bytes. Note the row is already broken for *reading* too — every
  /// file reader resolves the same id — so this is the narrow end of a wider
  /// pre-existing failure.
  Future<void> _uploadAttachment(
    OutboxRow row,
    String couchId,
    String rev,
    String? authHeader, {
    required String fileDocId,
  }) async {
    // Read under the CouchDB id: the row was rekeyed a few lines up.
    final resource = await _resources.getLibraryItemById(couchId);
    final localAddress = resource?.resourceLocalAddress;
    if (localAddress == null || localAddress.isEmpty) {
      // A row with no address owes no attachment, and [MyLibraryDao
      // .markUploaded] would not have flagged it — `hasBytes` reads the same
      // two columns. Clearing anyway costs one statement and makes the
      // invariant hold under any caller, including a row edited between the
      // mark and this call.
      if (resource != null) {
        await _resources.clearAttachmentPending(couchId);
      }
      return;
    }

    // No second `filename.isEmpty` guard, because there was one and mutation
    // testing showed it could not fail: [ResourceFiles.existingFileFor]
    // already returns null for an empty filename, so it — not this method —
    // is what guarantees the no-op. The early return above is an optimisation
    // that skips a pointless disk lookup, not the guarantee, and writing it as
    // though it were the guarantee invites the next reader to stop checking.
    final filename = p.basename(localAddress.replaceAll(r'\', '/'));

    final result = await _sendAttachment(
      endpoint: row.endpoint,
      couchId: couchId,
      fileDocId: fileDocId,
      filename: filename,
      rev: rev,
      authHeader: authHeader,
    );

    // Null is "there are no bytes under either key", which is not a failure
    // and must not queue a retry that can never find anything to send.
    if (result == null) return;
    if (result is NetworkSuccess<Map<String, dynamic>>) return;

    log('Resource attachment upload failed: $result');
    await _enqueueAttachmentRetry(
      row: row,
      couchId: couchId,
      filename: filename,
      fileDocId: fileDocId,
    );
  }

  /// The PUT itself, shared by the inline attempt and by [attachmentHandler]
  /// so a retry cannot drift from the send it is retrying.
  ///
  /// Returns null when there is no usable file — distinct from a failed send,
  /// because only one of the two is worth queueing.
  Future<NetworkResult<Map<String, dynamic>>?> _sendAttachment({
    required String endpoint,
    required String couchId,
    required String fileDocId,
    required String filename,
    required String rev,
    required String? authHeader,
  }) async {
    final file = await ResourceFiles.existingFileFor(
      docId: fileDocId,
      filename: filename,
    );
    if (file == null) return null;

    final List<int> bytes;
    try {
      bytes = await file.readAsBytes();
    } on Exception catch (e, stack) {
      log('Could not read local resource file', error: e, stackTrace: stack);
      return null;
    }

    // `endpoint` is already `<db>/resources`, so the document id and the
    // attachment name append directly — the same construction
    // `SubmitPhotosUploader` uses, and the same
    // `String.format("%s/resources/%s/%s", url, id, name)` Kotlin builds.
    final attachmentUrl =
        '$endpoint/${Uri.encodeComponent(couchId)}'
        '/${Uri.encodeComponent(filename)}';
    final contentType = lookupMimeType(filename) ?? 'application/octet-stream';

    var attachResult = await _api.uploadAttachment(
      attachmentUrl,
      bytes: bytes,
      authHeader: authHeader,
      // Kotlin passes the rev from the POST response as `If-Match`
      // (`FileUploader.getHeaderMap:85`), which is how CouchDB accepts an
      // attachment onto an existing document.
      ifMatch: rev,
      contentType: contentType,
    );

    // **The 409 arm, and the reason this PUT gets one where the document POST
    // beside it does not.**
    //
    // A 409 on a *conditional* PUT says one thing only: the `If-Match`
    // revision is not the document's current one, so these bytes were not
    // stored. It is not a verdict on the bytes — which is what the Phase 148
    // policy classifies a 4xx as, and why `OutboxRepository.classifyStatus`
    // reads 409 as `rejected` and terminal. That reading is right for an
    // append and right for a document PUT, whose `_rev` lives *in* the
    // payload, so a pull supplying a newer one changes the request and
    // `enqueue`'s memo re-arms it. This row's payload deliberately carries no
    // revision (see [_enqueueAttachmentRetry]) — the handler reads the live
    // one — so the request never changes and there is nothing the policy's
    // own recovery route can re-arm it with. Without an arm here a stale
    // revision strands the attachment permanently, which is why the fix is
    // here rather than in `classifyStatus`: 409 is terminal for the other
    // nineteen uploaders for good reasons, and this is the one caller whose
    // repair is not expressible as a changed request.
    //
    // Same shape as `ConflictRecovery.send`'s *update* branch, taken inline
    // because that helper sends a JSON body and this sends bytes: read the
    // document, take the revision it reports, and re-send **once** under it.
    // Bounded at one extra GET and one extra PUT per drain attempt, and only
    // on a conflict.
    //
    // `liveRev == rev` short-circuits for the reason that branch gives: the
    // server has just refused this exact request, and re-sending it is
    // precisely what the memo exists to stop.
    if (attachResult case NetworkError<Map<String, dynamic>>(code: 409)) {
      final liveRev = await _liveRevision(
        endpoint: endpoint,
        couchId: couchId,
        authHeader: authHeader,
      );
      if (liveRev != null && liveRev != rev) {
        attachResult = await _api.uploadAttachment(
          attachmentUrl,
          bytes: bytes,
          authHeader: authHeader,
          ifMatch: liveRev,
          contentType: contentType,
        );
      }
    }

    if (attachResult case NetworkSuccess<Map<String, dynamic>>(:final data)) {
      // **Delivery recorded, and this is the only place that records it.**
      // [MyLibraryTable.attachmentPending] exists because every other column
      // reads identically for an attachment that landed and one that never
      // will; a 2xx from this PUT is the single event that separates them, so
      // it is cleared here and nowhere the send did not actually succeed.
      //
      // Before the rev adoption rather than after, and unconditionally rather
      // than inside the `newRev` guard: a response that omits `rev` is still a
      // response that accepted the bytes, and a row left flagged would be
      // re-swept and re-PUT for the life of the install.
      await _resources.clearAttachmentPending(couchId);

      // CouchDB bumped the revision to accept these bytes. Kotlin discards
      // that response, which leaves the local row a revision behind and makes
      // the next sync ask the user to re-download their own file; see
      // [MyLibraryDao.adoptAttachmentRev].
      final newRev = data['rev'];
      if (newRev is String && newRev.isNotEmpty) {
        await _resources.adoptAttachmentRev(couchId, newRev);
      }
    }
    return attachResult;
  }

  /// The document's current `_rev` as the **server** reports it, for the 409
  /// arm above.
  ///
  /// Read from the server rather than from the local row on purpose: the local
  /// row is what the failing attempt already used, so re-reading it would
  /// return the same stale value and make the arm inert — the shape Phase 149
  /// calls tracing the caller chain to its end.
  ///
  /// Every failure answers null, which leaves the original 409 standing as the
  /// result. The `try` is the one `ConflictRecovery` documents at length: a
  /// throw out of a recovery would be caught by the drainer and relabelled
  /// *transient*, turning a terminal rejection into a row re-offered on every
  /// sweep — Phase 148's accretion re-entering through the recovery arm.
  /// `PlanetApi` returns a `NetworkException` rather than throwing, so this is
  /// unreachable through it and reachable through a fake.
  Future<String?> _liveRevision({
    required String endpoint,
    required String couchId,
    required String? authHeader,
  }) async {
    final NetworkResult<Map<String, dynamic>> existing;
    try {
      existing = await _api.getJsonObject(
        '$endpoint/${Uri.encodeComponent(couchId)}',
        authHeader: authHeader,
      );
    } catch (_) {
      return null;
    }
    if (existing is! NetworkSuccess<Map<String, dynamic>>) return null;
    final rev = existing.data['_rev'];
    return rev is String && rev.isNotEmpty ? rev : null;
  }

  Future<void> _enqueueAttachmentRetry({
    required OutboxRow row,
    required String couchId,
    required String filename,
    required String fileDocId,
  }) async {
    try {
      await _outbox.enqueue(
        uploadType: attachmentType,
        // The **document** id, because that is what the row is keyed on now
        // and what [attachmentHandler] reads its current revision from.
        itemId: couchId,
        endpoint: row.endpoint,
        // The name, and where the bytes actually are.
        //
        // [fileDocId] is normally [couchId], and storing it anyway is not
        // redundancy — it is the one fact the retry cannot re-derive. When
        // [ResourceFiles.moveResourceDirectory] failed, the files are still
        // under the local uuid, which is *not* this row's `itemId`; a handler
        // that assumed `itemId` would look in an empty directory, read that as
        // "no bytes to send", report success and delete its own memo. The
        // attachment would then never be sent, and by this class's own
        // argument nothing downstream could tell.
        //
        // The revision is deliberately *not* stored: it moves whenever
        // anything touches the document, and a stored one would make every
        // later attempt a guaranteed 409 while also changing the request on
        // each refresh, which is precisely what [OutboxRepository.enqueue]'s
        // memo is unable to match. Both of these values are stable across
        // refreshes, which is what makes them admissible in a stored payload
        // and the revision not. [attachmentHandler] reads the live one.
        payload: {'filename': filename, 'fileDocId': fileDocId},
        userId: row.userId,
      );
    } on Exception catch (e, stack) {
      // The document is filed and the bytes are on the device; a failure to
      // record the retry must not be reported as a failure to upload, which
      // would re-POST the document and duplicate it.
      log(
        'Could not queue resource attachment retry',
        error: e,
        stackTrace: stack,
      );
    }
  }

  /// The [OutboxHandler] for [attachmentType] — the durable half of the
  /// attachment PUT.
  ///
  /// It is enqueued only by [_uploadAttachment] after a refused send, so an
  /// attachment that lands first time never files a row at all.
  ///
  /// Four states end the row without a send, all reported as success so no
  /// dead row accretes for a question that can never be re-asked: the payload
  /// carries no usable filename, the resource is gone locally, it has no
  /// revision to match against, or its bytes are no longer on disk. (The first
  /// is unreachable from [_enqueueAttachmentRetry], which only files a row
  /// once it has read a non-empty name off the row — it is there for a payload
  /// an older or corrupted write left behind. An earlier revision of this
  /// comment said "three" and did not count it.) The Phase 148 policy is what shapes the rest — a
  /// transport failure or a 5xx is `transient` and tries again, another 4xx is
  /// `rejected` and stops. **This is a PUT to a known URL, not an append**, so
  /// unlike the document POST above a retry cannot create a second anything:
  /// the worst case is the same bytes written twice under the same name.
  ///
  /// The revision comes from the local row at drain time rather than from the
  /// payload, so a document whose revision moved on (the resource was edited,
  /// or a sync pulled a newer one) is still matched correctly.
  OutboxHandler get attachmentHandler => (row, payload, authHeader) async {
    const nothingToSend = NetworkSuccess<Map<String, dynamic>>(
      <String, dynamic>{},
    );

    final filename = payload['filename'];
    // The flag is deliberately **not** cleared here. An unusable payload is a
    // fact about this outbox row, not about the resource: the bytes may still
    // be on disk and still owed, and [queuePendingAttachments] files a fresh
    // row with a good payload on the next pass. Clearing would report delivery
    // on the strength of a corrupted memo.
    if (filename is! String || filename.isEmpty) return nothingToSend;

    final resource = await _resources.getLibraryItemById(row.itemId);
    // The resource is gone locally, so nothing can ever be sent for it — and
    // nothing can read the flag either. A missing revision is likewise about
    // the moment, not the row. Neither clears anything.
    final rev = resource?.rev;
    if (rev == null || rev.isEmpty) return nothingToSend;

    // Where the bytes are, as the enqueue observed it — not re-derived. It
    // falls back to the document id only for a row an older build wrote
    // without the key; that is the value that row would have carried.
    final stored = payload['fileDocId'];
    final fileDocId = (stored is String && stored.isNotEmpty)
        ? stored
        : row.itemId;

    final result = await _sendAttachment(
      endpoint: row.endpoint,
      couchId: row.itemId,
      fileDocId: fileDocId,
      filename: filename,
      rev: rev,
      authHeader: authHeader,
    );
    return result ?? nothingToSend;
  };

  /// Both handlers this uploader owns, for the drainer's registration map.
  ///
  /// A map rather than two getters for the same reason `ActivitiesUploader`
  /// exposes one: the registration site then cannot pick up a new type's
  /// handler by editing one line and forget the other.
  Map<String, OutboxHandler> get handlers => {
    type: handler,
    attachmentType: attachmentHandler,
  };

  static String authHeaderFor(ServerConfig config) =>
      UrlUtils.authHeader(config);
}

/// The safety net, wired into both sync paths.
///
/// Kotlin's `uploadResource` has exactly the unconditional-sweep property
/// Phase 134 was about: three callers with no precondition on how the row got
/// there (`AutoSyncWorker.kt:129`, `UserDataWorker.kt:84`,
/// `TeamsRepositoryImpl.kt:928`). The port has **one** enqueue site, in
/// `add_resource_screen._save`, so a resource whose enqueue never ran has
/// nothing to deliver it.
///
/// The write site does call [ResourcesUploader.queuePending] — an *unscoped*
/// sweep rather than an enqueue of the one row it just wrote — so saving any
/// resource rescues every stranded one. That is the only thing standing in for
/// the sweep today, and it is not enough.
///
/// **This is the widest such window the port has had**, not a narrow one.
/// Every resource created on any previous build has a `my_library` row with no
/// `_id` and no outbox row, and `my_library` is preserved, so no schema bump
/// clears them: they upload only if the user happens to open the add-resource
/// screen and save something. A user who creates a resource with no server
/// configured (`serverConfigProvider` null, which is the offline-first
/// premise), or whose process dies between the row write and the enqueue, and
/// who never returns to that screen, never uploads it — invisible in Planet
/// for ever, with the reset-app action able to destroy it meanwhile.
///
/// Both calls landed after this note was first written; they sit beside the
/// existing voices and submissions sweeps, this one in
/// `drainOutbox`, for the reason `sweepPendingVoices` documents at length —
/// `syncSteps` runs only for a due `autoSync` task with auto-sync enabled,
/// which is precisely not the user most likely to have an undelivered write.
/// Ordering against the other sweeps is free: no sync step writes `my_library`
/// rows over a locally authored one ([MyLibraryMapper.fromDoc] keys on the
/// CouchDB `_id`, and a *pending* resource has none), and `deleteNotIn` spares
/// a row with no `_rev`.
///
/// **That second clause protected the row only while it was pending, and this
/// direction is what ended that** — the defect this uploader shipped with and
/// no longer has. `markUploaded` writes `_rev`, which makes the row eligible
/// for `deleteNotIn`, whose keep set is document `_id`s; while the row's
/// primary key was still the local uuid it was in no keep set, so the first
/// resources sync after a successful upload inserted a *second* row keyed on
/// the CouchDB id — empty shelf, `resourceOffline` at its default — and pruned
/// the original, detaching the user from their own resource and orphaning the
/// bytes under `ole/<uuid>/`. Kotlin reaches the identical outcome one column
/// over (`deleteStalePublicNotIn` matches `resourceId`, which
/// `markResourceUploaded` does not update either). [MyLibraryDao.markUploaded]
/// now moves the row's whole identity onto the document's, so the row appears
/// in every later keep set like any other synced row; the ordering claim above
/// stands unchanged, because a *pending* resource still has no `_id` for a
/// walk to key on.
///
/// Kotlin calls `uploadResource` from three places, of which two are the
/// port's headless and foreground sync paths. **Both are covered, but not both
/// by this function**, and an earlier revision of this sentence said they were:
/// `background_entrypoint.dart:145` calls *this*, while the foreground pass
/// has its own copy in `DashboardSyncNotifier.queuePendingResources`
/// (`dashboard_sync_provider.dart:583`, reached from `_runPass`) — one sweep
/// written twice, which is worth collapsing but is not this lane's file. The
/// third Kotlin caller (`TeamsRepositoryImpl:928`, via `saveLocalResource`'s
/// `teamId != null` tail) needs no port counterpart, because the screen
/// enqueues on every save rather than only for a team.
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
// Deliberately not `@visibleForTesting`: the production caller is
// `background_entrypoint.dart`'s `drainOutbox`, in another library, which is
// what the paragraph above means by "exposed". The sweeps it sits beside need
// no annotation only because they are declared in that file already.
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
