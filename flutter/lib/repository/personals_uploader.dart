import 'dart:developer';
import 'dart:io';

import 'package:mime/mime.dart';
import 'package:path/path.dart' as p;

import '../core/config/server_config.dart';
import '../core/network/network_result.dart';
import '../core/system/device_identity.dart';
import '../core/utils/url_utils.dart';
import '../data/api/planet_api.dart';
import '../data/local/app_database.dart';
import 'outbox_drainer.dart';
import 'outbox_repository.dart';
import 'personals_repository.dart';

/// Port of `PersonalsRepositoryImpl.uploadPersonalDocument`.
///
/// This is the first *append* write-back in the port, and the reason the
/// outbox had to exist. The shelf could stay queue-free because its payload is
/// derived state — recomputing it from the database always yields current
/// truth, so a dropped push costs nothing. A personal note is the opposite: it
/// exists only locally until it is POSTed, and a push lost to a dead network is
/// lost outright unless something durable remembers to send it again.
///
/// When a note carries a local [PersonalRow.path] the flow POSTs the document
/// and then PUTs the file as a CouchDB attachment.
///
/// **That second step is not best-effort, and a previous revision of this
/// comment said it was.** The claim — *"best-effort in the Kotlin source: the
/// document is already uploaded before the attachment is attempted, and an
/// attachment failure does not roll the document back"* — described Kotlin as
/// it stood when this file was written, and Kotlin has since moved. It now
/// splits the two-request delivery across two DAO statements
/// (`PersonalDao.updateRemoteDocRef` and `updateUploadedStatus`, the same
/// statement with `isUploaded = 1` removed) and returns early on an attachment
/// failure (`PersonalsRepositoryImpl:145` on a throw, `:150` on a non-2xx), so
/// `updatePersonalAfterSync` — the sole caller of `updateUploadedStatus` — is
/// unreachable unless the file landed.
///
/// Neither half of the old claim is defended here even as a description of the
/// port. Nothing rolls the document back, which is true and unavoidable: a
/// CouchDB POST cannot be un-made cheaply, and the document is worth keeping
/// anyway. What changed is that the port no longer *reports* the note as
/// uploaded on the strength of the POST alone. See [PersonalsRepository
/// .recordRemoteDocRef] for the three-state table that buys, and [handler]
/// for the guard that makes retrying safe.
class PersonalsUploader {
  PersonalsUploader(this._api, this._personals, this._outbox, this._identity);

  /// The `uploadType` these operations carry in the outbox.
  static const String type = 'personals';

  final PlanetApi _api;
  final PersonalsRepository _personals;
  final OutboxRepository _outbox;
  final DeviceIdentitySource _identity;

  /// Credential-free: this string is persisted in `outbox.endpoint`.
  /// The PIN travels as the `Authorization` header at send time instead.
  static String endpointFor(ServerConfig config) =>
      '${UrlUtils.credentialFreeDbUrl(config)}/resources';

  /// Queues every not-yet-uploaded note for [userId].
  ///
  /// Safe to call repeatedly: [OutboxRepository.enqueue] keys on
  /// `(uploadType, itemId)`, so a note already queued has its payload
  /// refreshed rather than being posted twice.
  Future<int> queuePending({
    required ServerConfig config,
    required String userId,
  }) async {
    final endpoint = endpointFor(config);
    final pending = await _personals.pendingUploads(userId);
    final identity = pending.isEmpty ? null : await _identity.read();
    for (final row in pending) {
      await _outbox.enqueue(
        uploadType: type,
        itemId: row.id,
        endpoint: endpoint,
        payload: {
          // `uploadedAt` is passed, and passed *deterministically*, because
          // `serialize`'s default is `DateTime.now()` — and this payload is
          // re-serialized on every sweep, not once at send time as Kotlin's is
          // (`PersonalsRepositoryImpl.serialize:96`, `System.currentTimeMillis()`
          // — there is no `serialize` on `model/Personal.kt`, which is 22 lines
          // of fields; an earlier revision of this line cited one). A drifting
          // field makes the payload
          // differ from the one already recorded against this note, so
          // `OutboxRepository.enqueue`'s memo can never match and a note the
          // server refused is POSTed again on every sweep. That is the
          // duplicate this handler's own "carried no id/rev" branch exists to
          // prevent, so of all twenty uploaders this was the worst one to
          // leave volatile.
          //
          // The note's creation date is the honest value here. Kotlin's
          // `Date().time` is the moment of the POST, which this app cannot
          // know at enqueue time — the drain may be days later — so a captured
          // `now` would be neither creation nor delivery.
          ...PersonalsRepository.serialize(
            row,
            uploadedAt: DateTime.fromMillisecondsSinceEpoch(row.date),
          ),
          ...identity!.documentFields,
        },
        userId: userId,
      );
    }
    return pending.length;
  }

  /// The [OutboxHandler] for [type].
  ///
  /// A plain replay would POST correctly but drop the ids CouchDB assigns, so
  /// the note would stay `isUploaded == false` and be posted again on the next
  /// drain — one duplicate per drain, forever. Adopting `id`/`rev` is what
  /// closes the loop. Port of `PersonalsRepositoryImpl.uploadPersonal`, whose
  /// three load-bearing properties this mirrors in order:
  ///
  /// 1. **The POST records `_id`/`_rev` without marking the note uploaded**
  ///    ([PersonalsRepository.recordRemoteDocRef], Kotlin's
  ///    `updateRemoteDocRef` at `:87`). Until this, `markUploaded` ran here,
  ///    before the attachment, and a note whose bytes never reached CouchDB
  ///    was permanently `isUploaded == true` — indistinguishable from one
  ///    whose bytes did land, and re-armable by nothing.
  /// 2. **A failed attachment is the handler's answer**, so `markUploaded`
  ///    does not run and the note stays in [PersonalsRepository.pendingUploads]
  ///    (Kotlin returns early at `:145` on a throw and `:150` on a non-2xx,
  ///    both ahead of the `updatePersonalAfterSync` call at `:155` — and that
  ///    method, `:73-75`, is the sole caller of `updateUploadedStatus` in
  ///    `app/src/main`, at `:74`).
  /// 3. **A retry skips the POST** when the note already carries both ids
  ///    (Kotlin's `if (!existingId.isNullOrBlank() && !existingRev
  ///    .isNullOrBlank())` at `:119-127`), so re-sending a note whose document
  ///    already landed cannot file a second one.
  ///
  /// Property 3 is what makes property 2 safe, and the order matters: adopting
  /// the pending state without the skip-the-POST guard would turn every drain
  /// of a note with a failed attachment into a fresh duplicate document —
  /// undetectable afterwards, because a personal note is an append with a
  /// server-minted id.
  ///
  /// `_rev` is the load-bearing half of that guard. `_id` is never blank:
  /// `create` seeds `couchId` with the *local* id, exactly as
  /// `savePersonalResource` does (`_id = id`), so it is non-null from the
  /// first insert and says nothing about whether the server has seen the note.
  /// `_rev` is written only from a response. Both are tested, as Kotlin tests
  /// both, because an `_id` still holding the local id would address the
  /// attachment PUT at a document that does not exist.
  ///
  /// ### What this does to the outbox row, and why it stays inside Phase 148
  ///
  /// The policy is that an item owns one row for ever, and that a terminal row
  /// is a memo nothing re-asks unattended. Returning the attachment's own
  /// refusal keeps that intact rather than working around it, because the
  /// drainer already classifies what comes back:
  ///
  /// * **5xx, a transport failure, 401/403/404/408/429** —
  ///   [OutboxRefusal.transient]. Retried under the backoff, and re-armable by
  ///   a later sweep. Each retry skips the POST and re-attempts only the PUT,
  ///   which is Kotlin's behaviour exactly.
  /// * **400, 409, 413, 415, 422** — [OutboxRefusal.rejected]. Terminal: the
  ///   server considered these bytes and refused them, and re-sending them
  ///   cannot help. The row is kept `abandoned` carrying the reason, and the
  ///   note stays pending, which is the honest pair of facts.
  /// * **Bytes that cannot be sent at all** — no file where the note says one
  ///   is, or an unreadable one — answered as a [NetworkException], which the
  ///   drainer records with no status and therefore as transient. Retried, and
  ///   re-armed by a later sweep.
  ///
  /// That last verdict is the one deliberate change of *kind* rather than of
  /// timing, and **the first cut of this fix had it terminal, which was
  /// wrong.** A missing file used to be a silent `return` that still reported
  /// the note as uploaded, so recording it is not optional — `my_library`
  /// settled the same question and for the reason that applies here, *"the
  /// bytes are not where the row says they are" is not evidence the attachment
  /// was delivered* ([MyLibraryTable.attachmentPending]). But *terminal*
  /// overstates what is known. Kotlin never stats the file: the read throws
  /// inside `uploadRepository.uploadAttachment`, is caught at `:143`, and the
  /// note stays pending and retryable. A missing file is about the environment
  /// rather than about the bytes — an unmounted card is the ordinary case on
  /// these handsets — so it belongs with the 5xx and not with the 400.
  /// Terminal would also have been unrecoverable in practice: nothing in
  /// `lib/` calls [OutboxRepository.rearm] for personals and the screen offers
  /// no retry action, so such a note would be stranded on the device for
  /// good.
  ///
  /// **What no longer happens unattended is a retry past the ladder**, and
  /// that is the policy's intent rather than a gap in it. A `transient`
  /// refusal is re-armed by the next [queuePending]; a `rejected` one is not,
  /// and wants [OutboxRepository.rearm] — a person asking — as its own dartdoc
  /// reserves. Kotlin, by contrast, re-reads the live table and retries a
  /// refused attachment for ever. See the PR body for the one sweep the port
  /// is missing on top of this, which lives in files this lane does not own.
  OutboxHandler get handler => (row, payload, authHeader) async {
    // Read once. `recordRemoteDocRef` touches neither `path` nor `isUploaded`,
    // so this row stays true for the attachment step below.
    final note = await _personals.getById(row.itemId);

    // **The note is gone, so nothing here is owed to anyone.** Kotlin cannot
    // reach this: `getPendingPersonalUploads` reads the live table, so a
    // deleted note is simply not in the list it iterates. The port's queue is
    // durable and outlives the row it describes, so the handler has to make
    // the same check the query makes there.
    //
    // Without it the delete is *worse* than a no-op, because the
    // skip-the-POST guard below reads its ids off this row: a note deleted
    // between two drain attempts loses `_id`/`_rev`, the guard fails, and the
    // retry POSTs a **second** document — the exact duplicate property 3
    // exists to prevent, arriving through the retry loop property 2 added.
    // `notSent` rather than a success because nothing was sent and the row
    // should say so; terminal because a deleted note does not come back.
    //
    // The proper fix is upstream of here — `PersonalActions.delete` should
    // call `OutboxRepository.cancel`, as `voices_provider.dart:309` and
    // `team_tasks_provider.dart:55` both do — but that file is another lane's
    // this round, and this guard is worth having regardless: the outbox is
    // durable, so the handler cannot assume its subject still exists.
    if (note == null) {
      return const NetworkError<Map<String, dynamic>>(
        OutboxRepository.notSent,
        'The personal note this operation describes no longer exists',
      );
    }

    // Kotlin's `if (personal.isUploaded) return "Resource already uploaded"`
    // (`:113-116`). Reachable here when two drains race a row that a third
    // party has already settled; re-PUTting the attachment would be harmless
    // but pointless, and re-POSTing would not be.
    if (note.isUploaded) {
      return NetworkSuccess<Map<String, dynamic>>({
        'id': note.couchId,
        'rev': note.rev,
      });
    }

    final String couchId;
    final String rev;

    final knownId = note.couchId;
    final knownRev = note.rev;
    if (knownId != null &&
        knownId.isNotEmpty &&
        knownRev != null &&
        knownRev.isNotEmpty) {
      // Property 3. This device has already published this document: the only
      // thing outstanding is the attachment.
      couchId = knownId;
      rev = knownRev;
    } else {
      final result = await _api.postJsonObject(
        row.endpoint,
        payload,
        authHeader: authHeader,
      );
      if (result is! NetworkSuccess<Map<String, dynamic>>) return result;

      final postedId = result.data['id'];
      final postedRev = result.data['rev'];
      if (postedId is! String ||
          postedRev is! String ||
          postedId.isEmpty ||
          postedRev.isEmpty) {
        // Reporting success here would delete the outbox row while the note
        // stays `isUploaded == false`, so the next `queuePending` would POST
        // it again — a fresh duplicate document on every drain.
        //
        // Empty strings are rejected alongside absent ones because the
        // skip-the-POST guard reads these two columns back: a blank `_rev`
        // written here would fail that guard on the retry and file the
        // duplicate this branch exists to prevent.
        return const NetworkError<Map<String, dynamic>>(
          null,
          'Upload response carried no id/rev',
        );
      }
      couchId = postedId;
      rev = postedRev;
      // Property 1: the ids, not the flag.
      await _personals.recordRemoteDocRef(row.itemId, couchId, rev);
    }

    final attachment = await _uploadAttachment(
      note,
      row.endpoint,
      couchId,
      rev,
      authHeader,
    );
    // Property 2: the note stays pending, and the drainer decides whether this
    // is worth another attempt.
    if (attachment != null &&
        attachment is! NetworkSuccess<Map<String, dynamic>>) {
      return attachment;
    }

    // Kotlin's `finalRev`: `getString("rev", response.body()).ifBlank { rev }`
    // (`:152`). A CouchDB attachment PUT bumps the revision, so keeping the
    // POST's would leave the note holding a stale `_rev` — and the next write
    // against this document would earn a 409 for no reason.
    final attached = attachment is NetworkSuccess<Map<String, dynamic>>
        ? attachment.data['rev']
        : null;
    final finalRev = attached is String && attached.isNotEmpty ? attached : rev;

    // `deliveredPath` is the path read at the top of this handler — the file
    // these bytes came from. An edit landing while the PUT was on the wire
    // sets a *different* path and clears `isUploaded` to have the new file
    // sent; flagging the note here regardless would write straight over that
    // and the newly attached file would never be uploaded, with the note
    // reading as delivered. See [PersonalsRepository.markUploaded].
    await _personals.markUploaded(
      row.itemId,
      couchId,
      finalRev,
      deliveredPath: note.path,
    );
    return NetworkSuccess<Map<String, dynamic>>({
      'id': couchId,
      'rev': finalRev,
    });
  };

  /// Sends the note's file, or says why it could not.
  ///
  /// `null` means *there was nothing to send* — the note carries no local
  /// path, which is most notes. That is the one case the caller may treat as
  /// success, and it is why this returns a nullable result rather than a bare
  /// [NetworkResult]: "no attachment" and "the attachment failed" were the two
  /// states the old `Future<void>` collapsed, and collapsing them is what lost
  /// the file.
  ///
  /// Everything else comes back as a result the drainer classifies. Two of the
  /// three local refusals are [NetworkException]s, which it records with no
  /// status and therefore retries; only a path naming no file is terminal.
  /// [handler] argues both.
  Future<NetworkResult<Map<String, dynamic>>?> _uploadAttachment(
    PersonalRow? note,
    String endpoint,
    String couchId,
    String rev,
    String? authHeader,
  ) async {
    final localPath = note?.path;
    if (localPath == null || localPath.isEmpty) return null;

    final file = File(localPath);
    if (!await file.exists()) {
      // Transient, not terminal — see [handler]. The drainer's exception arm
      // records no status, and `classifyStatus(null)` is transient, so the
      // ladder retries this and a later sweep re-arms it.
      return NetworkException<Map<String, dynamic>>(
        FileSystemException('Personal attachment file is missing', localPath),
      );
    }

    final List<int> bytes;
    try {
      bytes = await file.readAsBytes();
    } on FileSystemException catch (e, stack) {
      log('Could not read personal attachment', error: e, stackTrace: stack);
      return NetworkException<Map<String, dynamic>>(e);
    }

    final filename = p.basename(localPath);
    if (filename.isEmpty) {
      // This one is terminal, and about the row rather than the moment: a
      // stored path that names no file will not start naming one. Unreachable
      // by construction — `PersonalsRepository._nullable` stores a blank path
      // as null, which returned above — so it is a guard rather than a branch
      // with a scenario behind it.
      return NetworkError<Map<String, dynamic>>(
        OutboxRepository.notSent,
        'Personal attachment path names no file: $localPath',
      );
    }

    final base = endpoint.endsWith('/resources')
        ? endpoint.substring(0, endpoint.length - 10)
        : endpoint;
    final attachmentUrl =
        '$base/resources'
        '/${Uri.encodeComponent(couchId)}/${Uri.encodeComponent(filename)}';
    final contentType = lookupMimeType(localPath) ?? 'application/octet-stream';

    final attachResult = await _api.uploadAttachment(
      attachmentUrl,
      bytes: bytes,
      authHeader: authHeader,
      contentType: contentType,
      ifMatch: rev,
    );
    if (attachResult is NetworkSuccess<Map<String, dynamic>>) {
      return attachResult;
    }

    // **A 409 here is the one refusal that is not about the bytes**, and
    // leaving it to the drainer strands the note for good. `classifyStatus`
    // reads 409 as `rejected`, so the row is abandoned on its first attempt
    // and `enqueue`'s memo refuses it for ever after; nothing in `lib/` calls
    // [OutboxRepository.rearm] for personals and the screen has no retry
    // action, so there is no route back.
    //
    // And it is reachable by the ordinary bad link this app is built for: a
    // PUT that *lands* whose response is lost comes back as a transport
    // failure, so the ladder retries — with the `_rev` from before the PUT,
    // which the PUT itself bumped. `If-Match` is stale, CouchDB says 409, and
    // the file is already on the server. Property 2 is what made this state
    // reachable at all, so property 2 owes it an answer.
    //
    // The answer is the one `ConflictRecovery`'s update arm makes, for the
    // same reason and with the same bound: re-read the document, take its
    // current revision, and send once more. Bounded to a single extra round
    // trip and no loop — a second 409 is returned as the refusal it is. This
    // cannot duplicate anything: an attachment PUT names its document *and*
    // its filename, so re-sending writes the same bytes to the same place.
    //
    // `ConflictRecovery` itself is deliberately not used. It is not armed for
    // personals and must not be, because the operation it would wrap is the
    // append POST, where a re-send files a second document.
    if (attachResult is! NetworkError<Map<String, dynamic>> ||
        attachResult.code != 409) {
      log('Personal attachment upload failed: $attachResult');
      return attachResult;
    }

    final documentUrl = '$base/resources/${Uri.encodeComponent(couchId)}';
    final existing = await _api.getJsonObject(
      documentUrl,
      authHeader: authHeader,
    );
    if (existing is! NetworkSuccess<Map<String, dynamic>>) return attachResult;

    final currentRev = existing.data['_rev'];
    // Nothing new to ask: the revision the server reports is the one already
    // refused, so the conflict was not about staleness after all.
    if (currentRev is! String || currentRev.isEmpty || currentRev == rev) {
      log('Personal attachment upload failed: $attachResult');
      return attachResult;
    }

    final retried = await _api.uploadAttachment(
      attachmentUrl,
      bytes: bytes,
      authHeader: authHeader,
      contentType: contentType,
      ifMatch: currentRev,
    );
    if (retried is! NetworkSuccess<Map<String, dynamic>>) {
      log('Personal attachment upload failed after a rev refresh: $retried');
    }
    return retried;
  }

  static String authHeaderFor(ServerConfig config) =>
      UrlUtils.authHeader(config);
}
