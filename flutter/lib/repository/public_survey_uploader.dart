import '../core/network/network_result.dart';
import '../data/api/planet_api.dart';
import 'outbox_drainer.dart';
import 'outbox_repository.dart';
import 'submissions_repository.dart';
import 'surveys_repository.dart';

/// Durable delivery for an anonymous public-survey answer sheet.
///
/// This was the weakest write path in the port, and the Kotlin's too:
/// `PublicSurveyActivity` POSTs to the public API and, if the post fails, the
/// answers are gone — there is no queue, no local record the respondent can
/// return to, and no second attempt. Every other write in the port goes through
/// the outbox; this one now does as well.
///
/// The shape follows `ChatUploader`: a live attempt first, so the common case
/// still ends with "thank you for taking this survey" on screen, and the outbox
/// only when that attempt fails.
///
/// Two properties of this upload type are unlike every other one:
///
/// - **It carries no credentials.** The endpoint is the *public* API, reached by
///   a respondent who has none. The handler deliberately ignores the drain's
///   `authHeader` rather than attaching a Planet Basic credential to an
///   anonymous request.
/// - **It has to drain with no server configured.** A respondent who followed a
///   link has never been through the server handshake, so `OutboxDrainScope`'s
///   usual "no config, nothing to send to" guard would hold this row forever.
///   The scope drains this type specifically in that case.
class PublicSurveyUploader {
  PublicSurveyUploader(
    this._api,
    this._surveys,
    this._submissions,
    this._outbox,
  );

  static const type = 'public_survey';

  final PlanetApi _api;
  final SurveysRepository _surveys;
  final SubmissionsRepository _submissions;
  final OutboxRepository _outbox;

  /// Where the answer sheet is posted. The origin comes from the deep link, so
  /// it is stored on the row rather than derived at drain time — by then the app
  /// may have been configured against an unrelated server.
  static String endpointFor({
    required String baseUrl,
    required String teamId,
    required String surveyId,
  }) => SurveysRepository.publicSubmissionsUrl(baseUrl, teamId, surveyId);

  /// Queues one answer sheet. Returns false when there was nothing to queue —
  /// the submission is gone, or it already reached the server.
  Future<bool> queue({
    required String baseUrl,
    required String teamId,
    required String surveyId,
    required String submissionId,
  }) async {
    if (baseUrl.isEmpty) return false;
    final submission = await _submissions.getById(submissionId);
    // `uploaded` is set by the live attempt; without this check a respondent who
    // tapped submit twice would post two answer sheets.
    if (submission == null || submission.uploaded) return false;

    final body = await _surveys.buildPublicSubmissionBody(
      surveyId: surveyId,
      submissionId: submissionId,
    );
    if (body == null) return false;

    await _outbox.enqueue(
      uploadType: type,
      itemId: submissionId,
      endpoint: endpointFor(
        baseUrl: baseUrl,
        teamId: teamId,
        surveyId: surveyId,
      ),
      payload: body,
    );
    return true;
  }

  /// Sends the stored answer sheet.
  ///
  /// The mirror fallback (`ServerUrlMapper`'s alternative URL) belongs to the
  /// live attempt in `SurveysRepository.submitPublicSurvey`, which tries both
  /// before anything is queued. A queued row keeps the primary endpoint: the
  /// respondent reached this app through a link to *that* host, and silently
  /// posting their answers to a different one is not a retry.
  OutboxHandler get handler => (row, payload, authHeader) async {
    final result = await _api.sendJsonDynamic(
      row.endpoint,
      body: payload,
      method: 'POST',
    );
    if (result is NetworkSuccess<dynamic>) {
      await _submissions.markPublicSubmitted(row.itemId);
      return const NetworkSuccess<Map<String, dynamic>>({});
    }
    return switch (result) {
      NetworkError<dynamic>(:final code, :final message) =>
        NetworkError<Map<String, dynamic>>(code, message),
      NetworkException<dynamic>(:final error) =>
        NetworkException<Map<String, dynamic>>(error),
      // Unreachable: the success case returned above.
      _ => const NetworkError<Map<String, dynamic>>(null, 'Unknown result'),
    };
  };
}
