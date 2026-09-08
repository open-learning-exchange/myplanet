import '../core/config/server_config.dart';
import '../core/network/network_result.dart';
import '../core/system/device_identity.dart';
import '../core/utils/url_utils.dart';
import '../data/api/planet_api.dart';
import '../data/local/app_database.dart';
import 'outbox_drainer.dart';
import 'outbox_repository.dart';
import 'submissions_repository.dart';

/// Durable port of `UploadConfigs.AdoptedSurveys` (`UploadConfigs.kt:186-195`),
/// which `UploadManager.uploadAdoptedSurveys` POSTs to the `exams` endpoint.
///
/// **This is the missing half of the port's adoption path, and its absence was
/// destructive rather than merely incomplete.** `SurveysRepository.adoptSurvey`
/// mints a team's private copy of a shared survey locally; Kotlin then uploads
/// it, so it becomes a document every satellite can replicate, every later
/// `exams` walk names it, and — Kotlin having no delete-except-ids on that
/// table at all — nothing prunes it either way. The port had neither the upload
/// nor a prune exemption, so `SurveyDao.deleteNotIn` destroyed the clone and
/// its question rows on the next surveys sync and left its members' answer
/// sheets pointing at nothing. A team's adopted survey had a lifetime of
/// minutes.
///
/// Ported at the shape of [EventsUploader]: an unscoped sweep that enqueues,
/// and a handler that POSTs and records the CouchDB identity.
class AdoptedSurveysUploader {
  AdoptedSurveysUploader(
    this._api,
    this._surveyDao,
    this._outbox,
    this._identity,
  );

  static const type = 'adopted_surveys';

  final PlanetApi _api;
  final SurveyDao _surveyDao;
  final OutboxRepository _outbox;
  final DeviceIdentitySource _identity;

  /// Credential-free: this string is persisted in `outbox.endpoint`, a table
  /// that deliberately survives schema upgrades. The PIN travels as the
  /// `Authorization` header at send time instead.
  static String endpointFor(ServerConfig config) =>
      '${UrlUtils.credentialFreeDbUrl(config)}/exams';

  /// The document `StepExam.serializeExam` (`StepExam.kt:71-95`) builds, which
  /// is the serializer this upload config uses.
  ///
  /// [SubmissionsRepository.surveyParentDocument] is that serializer's port and
  /// carries every other field, but it deliberately omits `type` — for a
  /// submission's embedded `parent` object the value is not recoverable, since
  /// the port splits Kotlin's one `exams` table on it and then discards it.
  ///
  /// **Here it is recoverable, and omitting it would have been the whole fix
  /// undone.** `SurveyMapper.fromDoc` accepts a document only when
  /// `type == 'surveys'` and `ExamMapper.fromDoc` accepts anything that is
  /// *not*, so a re-pull of a typeless upload would have filed the team's
  /// survey into the `exams` table as a graded course test, left the surveys
  /// walk's keep set without its id, and had `deleteNotIn` delete the row this
  /// class exists to save — the clone destroyed by a longer route. A clone's
  /// type is known for certain: Kotlin's `createMappedSurvey` copies it from
  /// the source (`type = exam.type`, `SurveysRepositoryImpl.kt:180`) and every
  /// list `adoptSurvey` is reachable from queries `type = "surveys"`
  /// (`ExamDao.kt:29-31`), so the source, and therefore the clone, is always
  /// plural.
  static Map<String, dynamic> documentFor(
    SurveyRow survey,
    List<SurveyQuestionRow> questions,
  ) => {
    ...SubmissionsRepository.surveyParentDocument(survey, questions),
    'type': 'surveys',
  };

  /// Queues every adopted clone that has not reached the server.
  ///
  /// Unscoped, like the Kotlin sweep it ports: `getPendingAdoptedSurveys()`
  /// carries no `userId` predicate and `uploadAdoptedSurveys()` takes no user,
  /// so a clone adopted by whoever was signed in last still goes up. [userId]
  /// only tags the outbox row.
  ///
  /// The identity read is guarded on an empty list so a pass with nothing to
  /// send makes no platform-channel call — `PlatformDeviceIdentitySource.read`
  /// rethrows on an engine with no channel and no primed cache.
  Future<int> queuePending({
    required ServerConfig config,
    String? userId,
  }) async {
    final rows = await _surveyDao.pendingAdoptedSurveys();
    final identity = rows.isEmpty ? null : await _identity.read();
    var queued = 0;
    for (final row in rows) {
      // A send already on the wire is left alone, for the reason
      // `SubmissionsUploader.queuePending` gives: `enqueue` would reset an
      // `in_progress` row to `pending` to preserve a mid-flight payload edit,
      // and the send that succeeds moments later then deletes nothing. This
      // POST carries `_id`, so a replay is a 409 rather than a duplicate
      // document — but a 409 is classified permanent and abandons a row whose
      // document actually exists, leaving the local rev unrecorded and the
      // clone permanently exempt from the prune.
      if (await _outbox.isInFlight(type, row.id)) continue;
      queued++;
      await _outbox.enqueue(
        uploadType: type,
        itemId: row.id,
        endpoint: endpointFor(config),
        payload: {
          ...documentFor(row, await _surveyDao.questionsFor(row.id)),
          // `serializeExam`'s closing `addDocumentOrigin()` (`StepExam.kt:94`)
          // — `androidId` and `app`, with no device names, so
          // [DeviceIdentity.originFields] rather than `documentFields`.
          ...identity!.originFields,
        },
        userId: userId,
      );
    }
    return queued;
  }

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
        // Reporting success would drop the outbox row while the clone keeps
        // `rev == null`, so it would stay in `pendingAdoptedSurveys()` and be
        // POSTed again on the next sweep.
        return const NetworkError<Map<String, dynamic>>(
          null,
          'Upload response carried no id/rev',
        );
      }
      if (couchId != row.itemId) {
        // The payload names its own `_id`, so CouchDB is expected to honour it
        // and the two are the same string. If a server ever answers with a
        // different id, the document is unreachable from every local key that
        // embeds this row's id — a member's answer sheet keys on
        // `"<surveyId>@<courseId>"` — and recording a rev against the local id
        // would claim a round trip that did not happen.
        return NetworkError<Map<String, dynamic>>(
          null,
          'Adopted survey stored under $couchId, not ${row.itemId}',
        );
      }
      await _surveyDao.markUploaded(row.itemId, rev);
    }
    return result;
  };
}
