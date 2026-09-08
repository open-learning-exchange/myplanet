import 'dart:developer';

import 'package:mime/mime.dart';

import '../core/config/server_config.dart';
import '../core/files/voice_images.dart';
import '../core/network/network_result.dart';
import '../core/system/device_identity.dart';
import '../core/utils/url_utils.dart';
import '../data/api/planet_api.dart';
import '../data/local/app_database.dart';
import 'outbox_drainer.dart';
import 'outbox_repository.dart';
import 'voices_repository.dart';

/// Durable upload for voices posts and replies, replacing the news branch of
/// `UploadManager`.
///
/// A post is an append: it exists only on this device until it is delivered, so
/// a push lost to a dead network is lost outright unless the outbox remembers.
class VoicesUploader {
  VoicesUploader(this._api, this._voices, this._outbox, this._identity);

  /// The `uploadType` these operations carry in the outbox.
  static const String type = 'voices';

  final PlanetApi _api;
  final VoicesRepository _voices;
  final OutboxRepository _outbox;
  final DeviceIdentitySource _identity;

  /// Credential-free: this string is persisted in `outbox.endpoint`, and that
  /// table survives schema upgrades. The PIN travels as the `Authorization`
  /// header at send time instead.
  static String endpointFor(ServerConfig config) =>
      '${UrlUtils.credentialFreeDbUrl(config)}/news';

  /// Queues every post that has not reached the server.
  ///
  /// Safe to call repeatedly: [OutboxRepository.enqueue] keys on
  /// `(uploadType, itemId)`, so a post already queued has its payload refreshed
  /// rather than being posted twice, and a post whose send is already on the
  /// wire is skipped outright — see the loop.
  ///
  /// Returns the number of posts queued by *this* call, so a caller can tell
  /// "nothing to send" from "everything was already in flight".
  ///
  /// `VoicesRepositoryImpl.serializeNews` gained `addDocumentOrigin()` in
  /// `27c0470`, a pure addition — `androidId` and `app` are both new on the
  /// wire, with no device name ([DeviceIdentity.originFields], not
  /// `documentFields`). It stamps the **outer** document, not the nested
  /// `news` sub-object: the Kotlin calls it on `` `object` ``, and Phase 74's
  /// reactions bug is what a writer choosing the wrong level costs. The
  /// identity read is guarded on an empty list so a pass with nothing to send
  /// makes no platform-channel call.
  Future<int> queuePending({
    required ServerConfig config,
    String? userId,
  }) async {
    final endpoint = endpointFor(config);
    final pending = await _voices.pendingUploads();
    final identity = pending.isEmpty ? null : await _identity.read();
    var queued = 0;
    for (final row in pending) {
      // A send already on the wire is left alone. [OutboxRepository.enqueue]
      // would put it back to `pending` to preserve a mid-flight payload edit,
      // and `markCompleted` only deletes an `in_progress` row — so the send
      // that succeeds moments later deletes nothing, the row survives with the
      // same body, and the next drain posts a **second** `news` document. That
      // reset is right for derived state, whose handler rebuilds from the
      // database; a voice with no `_id` yet is an append and replaying it
      // duplicates the post.
      //
      // Reachable without the sweep and systematic with it: [queuePending] is
      // unscoped, so any second write — another post, an edit, a reaction —
      // re-enqueues every undelivered row, including one whose POST is in
      // flight. That window is handled at the other end rather than here:
      // `markUploaded` takes the document that actually went out and keeps
      // `isEdited` set when the row no longer matches it, so the next sweep
      // re-queues the edit carrying the `_id`/`_rev` just recorded.
      //
      // An earlier version of this comment said Kotlin "loses it identically".
      // It does not — `getNewsForUpload` has no predicate beyond the guest
      // prefix and `markNewsUploaded` touches no edit marker, so its next
      // sweep re-serializes the edited message with the fresh `_rev`. That
      // sentence was copied from `submissions_uploader.dart`, where it is
      // true; `PHASE_144_NOTES.md` corrected the claim at `markUploaded` and
      // this copy of it outlived the correction by a phase.
      if (await _outbox.isInFlight(type, row.id)) continue;
      queued++;
      await _outbox.enqueue(
        uploadType: type,
        itemId: row.id,
        endpoint: endpoint,
        payload: {
          ...VoicesRepository.serialize(row),
          ...identity!.originFields,
        },
        userId: userId,
      );
    }
    return queued;
  }

  /// The `resources` database, derived from the `news` endpoint this row was
  /// queued against.
  ///
  /// A voice's image is **not** an attachment on the news document. Kotlin
  /// creates a separate `resources` document per image and attaches the bytes
  /// to *that* (`UploadManager.kt:311-331`), then references it from the post
  /// by markdown. Derived rather than stored so an outbox row queued before
  /// this phase — whose `endpoint` is only the news URL — still resolves.
  static String resourcesEndpointFor(String newsEndpoint) {
    final slash = newsEndpoint.lastIndexOf('/');
    if (slash <= 0) return newsEndpoint;
    return '${newsEndpoint.substring(0, slash)}/resources';
  }

  /// The [OutboxHandler] for [type].
  ///
  /// Adopting `id`/`rev` on success is what stops the next [queuePending] from
  /// posting the same message again.
  ///
  /// **Images go first, and the document carries what they produced.** Each
  /// pending image becomes a `resources` document plus an attachment PUT, and
  /// only then is the news document POSTed — because its `message` gains a
  /// `![](resources/<id>/<name>)` line per image and its `images` array names
  /// the resource ids, neither of which exists until the resource documents
  /// do. Ordering it the other way would upload a post that references
  /// attachments that do not exist yet.
  OutboxHandler get handler => (row, payload, authHeader) async {
    final Map<String, dynamic> document;
    try {
      document = await _withImages(row, payload, authHeader);
    } on _ImageUploadFailure catch (failure) {
      // Deliberately **not** Kotlin's behaviour, and the difference matters.
      // Kotlin discards both the resource POST's `.body()` and the attachment
      // PUT's `Response` unchecked (`UploadManager.kt:313-331`), so a failed
      // image still appends its markdown, still writes its `images` entry,
      // still uploads the document, and `markNewsUploaded` then clears
      // `imageUrls` — the post permanently references an attachment that does
      // not exist and the only local pointer to the file is gone, with nothing
      // logged. Failing the operation instead leaves the outbox row `pending`
      // with `imageUrls` intact, so the drain retries the whole post.
      return NetworkError<Map<String, dynamic>>(null, failure.message);
    }

    final result = await _api.postJsonObject(
      row.endpoint,
      document,
      authHeader: authHeader,
    );

    if (result case NetworkSuccess<Map<String, dynamic>>(:final data)) {
      final couchId = data['id'];
      final rev = data['rev'];
      if (couchId is! String || rev is! String) {
        // Reporting success would drop the outbox row while the post stays
        // undelivered, so the next `queuePending` would post a duplicate.
        return const NetworkError<Map<String, dynamic>>(
          null,
          'Upload response carried no id/rev',
        );
      }
      final images = document['images'];
      await _voices.markUploaded(
        row.itemId,
        couchId,
        rev,
        images: images is List ? images : const [],
        // The payload **as queued**, not [document]. The message and images
        // this handler derived from the uploaded resources are not a local
        // edit, and comparing against the derived body would report every
        // post with an image as superseded — leaving `isEdited` set, so the
        // next sweep re-queues it forever. What this comparison is for is a
        // row the *user* changed while the POST was on the wire.
        delivered: payload,
      );
    }
    return result;
  };

  /// Uploads each pending image and returns the news document to send.
  ///
  /// Returns [payload] untouched when the post has no pending images. That is
  /// load-bearing, not an optimisation: Kotlin overwrites `images` with a
  /// freshly built array on every sweep (`UploadManager.kt:345`), and because
  /// `markNewsUploaded` clears `imageUrls`, the *second* sweep of an
  /// already-delivered post builds an **empty** array and strips the image
  /// metadata off the server document — and off the local row, on the way
  /// back. The port cannot reach that state (a delivered, unedited post is not
  /// in `pendingUploads` at all), and leaving the key alone here means it
  /// still cannot if that predicate is ever widened.
  Future<Map<String, dynamic>> _withImages(
    OutboxRow row,
    Map<String, dynamic> payload,
    String? authHeader,
  ) async {
    final pending = await _voices.pendingImagesFor(row.itemId);
    if (pending.isEmpty) return payload;

    final post = await _voices.getById(row.itemId);
    final identity = await _identity.read();
    final resourcesEndpoint = resourcesEndpointFor(row.endpoint);
    final message = StringBuffer(payload['message'] as String? ?? '');
    final images = <Map<String, dynamic>>[];

    for (final image in pending) {
      final uploaded = await _uploadImage(
        row: row,
        image: image,
        resourcesEndpoint: resourcesEndpoint,
        authHeader: authHeader,
        document: _resourceDocument(image, post, identity),
      );
      images.add(uploaded);
      // A single `\n` per image, appended to the original message — the
      // separator and the order are Kotlin's (`UploadManager.kt:340`).
      message
        ..write('\n')
        ..write(uploaded['markdown']);
    }

    return {...payload, 'message': message.toString(), 'images': images};
  }

  /// The two steps Kotlin runs per image: POST a `resources` document, then
  /// PUT the bytes onto it as an attachment.
  ///
  /// Returns the `images` entry describing it — `resourceId`, `filename` and
  /// `markdown`, the three keys `UploadManager.kt:334-337` writes.
  Future<Map<String, dynamic>> _uploadImage({
    required OutboxRow row,
    required PendingVoiceImage image,
    required String resourcesEndpoint,
    required String? authHeader,
    required Map<String, dynamic> document,
  }) async {
    final file = await VoiceImages.existingFileFor(
      newsId: row.itemId,
      filename: image.fileName,
    );
    if (file == null) {
      // Every other attachment uploader in the port treats a missing file as
      // a no-op, because there the document is already on the server and only
      // the bytes can be re-sent. Here the document has **not** been sent yet
      // and its `message` would carry a `![](…)` line pointing at an
      // attachment that will never exist, so refusing is the safer half: the
      // outbox retries, and if the bytes really are gone for good the row is
      // abandoned after `maxAttempts` with `imageUrls` still set, which is
      // visible rather than silent.
      throw _ImageUploadFailure(
        'Pending voice image ${image.fileName} has no bytes on disk',
      );
    }

    final List<int> bytes;
    try {
      bytes = await file.readAsBytes();
    } on Exception catch (e, stack) {
      log('Could not read pending voice image', error: e, stackTrace: stack);
      throw _ImageUploadFailure('Could not read ${image.fileName}');
    }

    final created = await _api.postJsonObject(
      resourcesEndpoint,
      document,
      authHeader: authHeader,
    );
    if (created is! NetworkSuccess<Map<String, dynamic>>) {
      throw _ImageUploadFailure('Resource document rejected: $created');
    }
    final resourceId = created.data['id'];
    final resourceRev = created.data['rev'];
    if (resourceId is! String || resourceId.isEmpty || resourceRev is! String) {
      // Kotlin reads these with `JsonUtils.getString`, whose default is `""`,
      // and then PUTs to `resources//<name>` with `If-Match: ""` — a double
      // slash and no revision. Refusing is the port's divergence.
      throw _ImageUploadFailure('Resource document carried no id/rev');
    }

    final encodedName = Uri.encodeComponent(image.fileName);
    final attached = await _api.uploadAttachment(
      '$resourcesEndpoint/${Uri.encodeComponent(resourceId)}/$encodedName',
      bytes: bytes,
      authHeader: authHeader,
      contentType: contentTypeFor(image.fileName),
      ifMatch: resourceRev,
    );
    if (attached is! NetworkSuccess<Map<String, dynamic>>) {
      throw _ImageUploadFailure('Attachment rejected: $attached');
    }

    return {
      'resourceId': resourceId,
      'filename': image.fileName,
      'markdown': markdownFor(resourceId, image.fileName),
    };
  }

  /// The image reference a post carries: relative, no leading slash, no `/db`
  /// prefix, empty alt text. The literal template at `UploadManager.kt:337`.
  ///
  /// Unencoded, as Kotlin leaves it — this is markdown in a message body, not
  /// a URL the app builds a request from, and Planet renders it against its
  /// own base.
  static String markdownFor(String resourceId, String fileName) =>
      '![](resources/$resourceId/$fileName)';

  /// Port of `UploadManager.createImage` — the `resources` document an image
  /// attachment hangs off.
  ///
  /// `addedBy`/`resideOn`/`sourcePlanet` are read off the **post** rather than
  /// from a session: the drain is headless and may run long after the
  /// composer, and the row already records who wrote it and where. Kotlin
  /// reads the live `user` there, which for its two workers is the same
  /// account; for a drain that can outlive a logout, the row is the honest
  /// source.
  ///
  /// Key order follows `createImage` (`UploadManager.kt:126-142`) so a diff
  /// against the Kotlin reads straight. `deviceName`/`customDeviceName` and
  /// the `androidId`/`app` origin pair arrive together as
  /// [DeviceIdentity.documentFields], which is exactly the set
  /// `addDocumentOrigin()` plus the two `addProperty` calls write.
  Map<String, dynamic> _resourceDocument(
    PendingVoiceImage image,
    NewsRow? post,
    DeviceIdentity identity,
  ) => {
    'title': image.fileName,
    'createdDate': DateTime.now().millisecondsSinceEpoch,
    'filename': image.fileName,
    'private': true,
    if ((post?.userId ?? '').isNotEmpty) 'addedBy': post!.userId,
    if ((post?.parentCode ?? '').isNotEmpty) 'resideOn': post!.parentCode,
    if ((post?.createdOn ?? '').isNotEmpty) 'sourcePlanet': post!.createdOn,
    ...identity.documentFields,
    'privateFor': const <String, dynamic>{},
    'mediaType': 'image',
  };

  /// Name-based MIME detection, matching post-fix Kotlin.
  ///
  /// `a182edd` replaced `imageFile.toURI().toURL().openConnection().contentType`
  /// — which sniffs bytes through a `file://` connection — with
  /// `FileUtils.getMimeType(fileName) ?: "application/octet-stream"`, i.e. the
  /// extension of the *name*. `lookupMimeType` is the port's equivalent and is
  /// already what `personals_uploader` uses; the fallback string is Kotlin's
  /// verbatim.
  static String contentTypeFor(String fileName) =>
      lookupMimeType(fileName) ?? 'application/octet-stream';
}

/// An image step that failed, carrying why. Not exported: the handler turns it
/// into a [NetworkError] so the outbox retries the whole post.
class _ImageUploadFailure implements Exception {
  const _ImageUploadFailure(this.message);

  final String message;
}
