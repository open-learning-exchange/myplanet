import '../core/config/server_config.dart';
import '../core/network/network_result.dart';
import '../core/utils/json_utils.dart';
import '../core/utils/url_utils.dart';
import '../data/api/planet_api.dart';
import '../data/local/app_database.dart';
import '../data/local/user_mapper.dart';
import 'outbox_drainer.dart';
import 'outbox_repository.dart';

/// Port of the upload half of `services/UploadToShelfService.uploadUserData`
/// and `repository/UserRepositoryImpl.checkAndUploadUser` /
/// `updateExistingUser` / `uploadNewUser`.
///
/// This is the path that carries a user document — profile edits and a new
/// profile photo — to the CouchDB `_users` database. Until this slice the port
/// only wrote locally; the row never left the device, so a learner who edited
/// their name on one handset never saw the change on another.
///
/// The Kotlin pushes the whole document per upload, embedding the photo as a
/// base64 `_attachments` blob the way `UserEntity.serialize` does. The outbox
/// holds the serialized payload, so the photo's bytes are snapshotted at queue
/// time rather than re-read from a temp file at send time — the temp file may
/// be gone by the time the drain runs, and the outbox is the durable half.
class UserUploader {
  UserUploader(
    this._api,
    this._userDao,
    this._outbox, {
    Future<void> Function({
      required String localId,
      required ServerConfig config,
      required String username,
      required String password,
    })?
    onCreated,
    ServerConfig? Function()? readConfig,
    Future<String?> Function()? readPassword,
  }) : _onCreated = onCreated,
       _readConfig = readConfig,
       _readPassword = readPassword;

  /// The `uploadType` these operations carry in the outbox.
  static const String type = 'user';

  final PlanetApi _api;
  final UserDao _userDao;
  final OutboxRepository _outbox;

  /// Fires after a new user's `_users` PUT succeeds — the durable counterpart
  /// of `processUserAfterCreation`: publish the health key/IV to `userdb-*`
  /// (`UserRepository.saveKeyIv`) and rewrite health examinations' userId
  /// from the local id to the server-assigned couch id. Best-effort, as the
  /// Kotlin's `try/catch` is; a failure just means the key stays device-local.
  ///
  /// The provider wires this with the real [UserRepository] + health DAO; tests
  /// inject a recorder or leave it null to assert the handler still succeeds.
  final Future<void> Function({
    required String localId,
    required ServerConfig config,
    required String username,
    required String password,
  })?
  _onCreated;

  /// Resolves the current [ServerConfig] — the outbox handler signature has no
  /// config parameter, so this seam lets the handler reach the live config the
  /// provider holds without threading it through the drainer.
  final ServerConfig? Function()? _readConfig;

  /// Reads the signed-in user's password from secure storage, for the
  /// per-user basic auth header `saveKeyIv` needs.
  final Future<String?> Function()? _readPassword;

  /// `_users/org.couchdb.user:<name>` behind the configured server, credential-
  /// free — the PIN travels as the `Authorization` header at send time. This
  /// endpoint is persisted in the outbox (which survives schema upgrades), so
  /// it must not embed the `satellite:PIN` userinfo the way [dbUrl] does.
  static String endpointFor(ServerConfig config, String userName) =>
      '${UrlUtils.credentialFreeDbUrl(config)}/_users/org.couchdb.user:'
      '${Uri.encodeComponent(userName)}';

  /// Queues every locally-edited or not-yet-created account for upload.
  ///
  /// Safe to call repeatedly: [OutboxRepository.enqueue] keys on
  /// `(uploadType, itemId)`, so an account already queued has its payload
  /// refreshed (a later edit replaces the earlier snapshot) rather than being
  /// posted twice. The image bytes are read now, before the temp file the
  /// picker wrote can be cleared, and travel inside the payload.
  Future<int> queuePending({required ServerConfig config}) async {
    final pending = await _userDao.pendingSyncUsers();
    for (final user in pending) {
      final name = user.name;
      if (name == null || name.trim().isEmpty) continue;
      final imageBytes = await UserMapper.readImageBytes(user.userImage);
      await _outbox.enqueue(
        uploadType: type,
        itemId: user.id,
        endpoint: endpointFor(config, name),
        httpMethod: 'PUT',
        payload: UserMapper.toDoc(user, imageBytes: imageBytes),
        userId: user.id,
      );
    }
    return pending.length;
  }

  /// The [OutboxHandler] for [type].
  ///
  /// Reproduces `updateExistingUser`'s read-then-write: a `_users` PUT must
  /// carry the document's current `_rev` or CouchDB rejects it with a 409, so
  /// the handler GETs the live document first and stamps its `_rev` onto the
  /// payload. A new account (no `couchId`) has no rev to fetch and PUTs as a
  /// creation. On success the server-assigned `_id`/`_rev` are recorded and
  /// `isUpdated` cleared, exactly as `markUserUploaded`/`markUserRevUpdated` do.
  OutboxHandler get handler =>
      (row, payload, authHeader) async => _send(row, payload, authHeader);

  Future<NetworkResult<Map<String, dynamic>>> _send(
    OutboxRow row,
    Map<String, dynamic> payload,
    String? authHeader,
  ) async {
    final user = await _userDao.getById(row.itemId);
    if (user == null) {
      return const NetworkError(null, 'User row vanished before upload');
    }

    final hasCouchId = user.couchId != null && user.couchId!.isNotEmpty;

    // A 404 on the GET is normal for a brand-new account; the PUT that follows
    // is a creation and carries no `_rev`. Any other failure is fatal for this
    // attempt — a stale rev would 409, and we refuse to overwrite blind.
    var body = payload;
    if (hasCouchId) {
      final fetched = await _api.getJsonObject(
        row.endpoint,
        authHeader: authHeader,
      );
      switch (fetched) {
        case NetworkSuccess(:final data):
          final latestRev = JsonUtils.getStringOrNull('_rev', data);
          if (latestRev == null || latestRev.isEmpty) {
            return const NetworkError(
              null,
              'User document carries no _rev; cannot update',
            );
          }
          body = Map<String, dynamic>.from(payload)..['_rev'] = latestRev;
        case NetworkError(:final code) when code == 404:
          // The account has a local id but the server has no document — treat
          // this as a creation, as the Kotlin's `checkIfUserExists` path would.
          break;
        case NetworkError():
        case NetworkException():
          return fetched;
      }
    }

    final result = await _api.putJsonObject(
      row.endpoint,
      body,
      authHeader: authHeader,
    );
    if (result case NetworkSuccess<Map<String, dynamic>>(:final data)) {
      final id = data['id'];
      final rev = data['rev'];
      if (id is! String || id.isEmpty) {
        return const NetworkError(null, 'Upload response carried no id');
      }
      // The attachment name Kotlin stores after a successful upload. We mirror
      // `addImageUrl`: a `_users` document's first attachment key is the image
      // name, and the URL is rebuilt at display time. Clearing a stale local
      // file path here stops `readImageBytes` from re-embedding the photo on
      // every later edit that happens to leave `userImage` set.
      await _userDao.markUploaded(
        row.itemId,
        couchId: id,
        rev: rev is String ? rev : null,
      );
      // A first-time creation (no prior couchId) triggers the post-create
      // steps the Kotlin's `processUserAfterCreation` runs: publish the
      // health key/IV and rewrite health exams' userId. Best-effort, as the
      // Kotlin wraps the whole chain in try/catch.
      if (!hasCouchId && _onCreated != null && _readConfig != null) {
        final config = _readConfig();
        final name = user.name;
        if (config != null && name != null && name.isNotEmpty) {
          final password = await (_readPassword ?? _noPassword)();
          try {
            await _onCreated(
              localId: row.itemId,
              config: config,
              username: name,
              password: password ?? '',
            );
          } catch (_) {
            // Swallowed — see the comment above.
          }
        }
      }
    }
    return result;
  }

  /// The `satellite:PIN` Basic credential the outbox drainer attaches.
  static String authHeaderFor(ServerConfig config) =>
      UrlUtils.authHeader(config);

  static Future<String?> _noPassword() async => null;
}
