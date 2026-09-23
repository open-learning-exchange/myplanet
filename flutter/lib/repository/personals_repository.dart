import 'dart:math';

import 'package:drift/drift.dart';
import 'package:path/path.dart' as p;

import '../data/local/app_database.dart';

class DuplicatePersonalTitle implements Exception {
  const DuplicatePersonalTitle();
}

/// Offline CRUD portion of `repository/PersonalsRepositoryImpl.kt`.
class PersonalsRepository {
  PersonalsRepository(
    this._dao, {
    DateTime Function()? now,
    String Function()? createId,
  }) : _now = now ?? DateTime.now,
       _createId = createId ?? _randomId;

  final PersonalDao _dao;
  final DateTime Function() _now;
  final String Function() _createId;

  Stream<List<PersonalRow>> watch(String userId) => _dao.watchForUser(userId);

  Future<void> create({
    required String userId,
    required String? userName,
    required String title,
    String? description,
    String? path,
  }) async {
    final trimmedTitle = title.trim();
    final normalized = trimmedTitle.toLowerCase();
    if (await _dao.titleExists(userId, normalized)) {
      throw const DuplicatePersonalTitle();
    }
    final id = _createId();
    await _dao.upsert(
      PersonalEntriesCompanion.insert(
        id: id,
        couchId: Value(id),
        title: trimmedTitle,
        titleNormalized: normalized,
        description: Value(_nullable(description)),
        date: _now().millisecondsSinceEpoch,
        userId: userId,
        userName: Value(_nullable(userName)),
        path: Value(_nullable(path)),
      ),
    );
  }

  Future<void> update({
    required String id,
    required String title,
    String? description,
    String? path,
  }) async {
    final current = await _dao.getById(id);
    if (current == null) return;
    final trimmedTitle = title.trim();
    final normalized = trimmedTitle.toLowerCase();
    if (await _dao.titleExists(current.userId, normalized, excludingId: id)) {
      throw const DuplicatePersonalTitle();
    }
    // `toCompanion(false)` writes every column. With `nullToAbsent: true` a
    // cleared description would be dropped from the statement instead of
    // nulling the column, so editing a note could never remove its
    // description.
    //
    // **This used to cite Room's `@Update` in `PersonalDao` as the thing it
    // matched. There is no `@Update` in `PersonalDao`, and there never has
    // been.** Kotlin's edit is `updateFields`, a hand-written
    // `UPDATE … SET title = COALESCE(:title, title), description =
    // COALESCE(:description, description)`, which writes two columns rather
    // than every one.
    //
    // **The replacement sentence then went on to get Kotlin wrong a second
    // time**, claiming the `COALESCE` means Kotlin "cannot clear a description
    // at all". `COALESCE` falls back on SQL `NULL`, not on `''`, and
    // `PersonalsFragment.kt:114` reads
    // `etDescription.text.toString().trim { it <= ' ' }` — a non-null String
    // that is `""` when the user clears the field. So Kotlin does clear it, to
    // the empty string; the `COALESCE` is there for the interface's
    // `PersonalUpdate(title = null, description = null)` defaults, which the
    // one caller never uses. The port stores `null` instead of `''` via
    // `_nullable`, which no user can tell apart. Only the justification was
    // false, twice over — which is the cost of writing a replacement claim
    // without opening its citation.
    //
    // `isUploaded` is reset **only when the attachment changes**, and the
    // narrowing is the point. Kotlin never resets it, so an edited note is
    // never re-sent there at all — but Kotlin also cannot edit the file, which
    // the port can (`personals_screen.dart:317-318`, `:321`). Leaving a new file
    // undelivered would strand it, so a changed path re-opens the note for
    // upload.
    //
    // An unchanged path must not, and before Phase 160 it did: every edit
    // re-queued the note, and since `serialize` carries no `_id` the handler
    // POSTed a **second CouchDB document**, orphaning the first. The
    // skip-the-POST guard in [PersonalsUploader.handler] closes that, but on
    // its own it would leave a title edit re-PUTting the whole attachment over
    // a link this app exists to cope without — for nothing, since neither app
    // can publish the edit itself. Publishing one needs a PUT carrying
    // `_id`/`_rev`, which neither app has; until something does, the honest
    // state for a title-only edit is *still delivered*.
    final attachmentChanged = current.path != _nullable(path);
    await _dao.upsert(
      current
          .copyWith(
            title: trimmedTitle,
            titleNormalized: normalized,
            description: Value(_nullable(description)),
            path: Value(_nullable(path)),
            isUploaded: current.isUploaded && !attachmentChanged,
          )
          .toCompanion(false),
    );
  }

  Future<int> delete(String id) => _dao.deleteById(id);
  Future<PersonalRow?> getById(String id) => _dao.getById(id);
  Future<List<PersonalRow>> pendingUploads(String userId) =>
      _dao.pendingUploads(userId);

  /// Port of the private `PersonalsRepositoryImpl.serialize` (`:93-111`).
  ///
  /// **Not `Personal.serialize`, which an earlier revision of this line named
  /// and which does not exist** — `model/Personal.kt` is 22 lines of fields
  /// with no methods at all.
  ///
  /// Device telemetry is deliberately added by [PersonalsUploader], where the
  /// platform seam is available.
  ///
  /// **This is only deterministic if [uploadedAt] is passed.** The
  /// `DateTime.now()` default made the same row serialize differently on every
  /// call, which defeats `OutboxRepository.enqueue`'s memo — see the call site
  /// in [PersonalsUploader.queuePending] for why that mattered enough to fix.
  static Map<String, dynamic> serialize(
    PersonalRow row, {
    DateTime? uploadedAt,
  }) {
    final filename = row.path != null && row.path!.isNotEmpty
        ? p.basename(row.path!)
        : null;
    return <String, dynamic>{
      'title': row.title,
      'uploadDate': (uploadedAt ?? DateTime.now()).millisecondsSinceEpoch,
      'createdDate': row.date,
      if (filename != null && filename.isNotEmpty) 'filename': filename,
      'author': row.userName,
      'addedBy': row.userName,
      'description': row.description,
      'resourceType': 'Activities',
      'private': true,
      'privateFor': {'users': row.userId},
    };
  }

  /// Port of `PersonalDao.updateRemoteDocRef`
  /// (`UPDATE my_personal SET _id = :newId, _rev = :rev WHERE id = :id`),
  /// called from `PersonalsRepositoryImpl.uploadPersonalDocument:87`.
  ///
  /// [markUploaded]'s statement with `isUploaded = 1` removed, and that one
  /// missing assignment is the whole of Kotlin's fix — and of this one.
  ///
  /// **A personal note with an attachment is delivered in two requests, and
  /// only the second one finishes it.** Recording the ids the POST assigned is
  /// not evidence the file arrived, so it must not be written with the flag
  /// that says the note is done. Splitting the two gives `my_personal` a state
  /// it could not previously express:
  ///
  /// | `_rev` | `isUploaded` | what it means |
  /// |---|---|---|
  /// | null | false | the document has not been POSTed |
  /// | set | **false** | **the document landed, the attachment did not** |
  /// | set | true | both landed |
  ///
  /// The middle row is the one that did not exist. Before it, a note whose
  /// bytes never reached CouchDB and one whose bytes did were the same row,
  /// byte for byte — so nothing could detect the loss and nothing could
  /// re-send. Because [pendingUploads] reads exactly `isUploaded = 0`, the new
  /// state is *already* in the set every sweep re-queues; no column and no
  /// sweep of its own were needed. Compare `my_library`, which needed both
  /// ([MyLibraryTable.attachmentPending] at schema 50): its `is_uploaded`
  /// equivalent is `_rev == downloaded_rev`, which a pull can write on its
  /// own, so it carries no flag the upload path solely owns. `my_personal`
  /// does, and that is why this fix costs no schema version.
  Future<void> recordRemoteDocRef(String id, String couchId, String rev) async {
    final current = await _dao.getById(id);
    if (current == null) return;
    await _dao.upsert(
      current
          .copyWith(couchId: Value(couchId), rev: Value(rev))
          .toCompanion(false),
    );
  }

  /// Port of `updatePersonalAfterSync` → `PersonalDao.updateUploadedStatus`.
  ///
  /// The note is finished: the document is filed **and** any attachment it
  /// carries has been accepted. Kotlin reaches its single call site only past
  /// both attachment failure returns (`PersonalsRepositoryImpl:145`, `:150`),
  /// and [PersonalsUploader.handler] mirrors that — see
  /// [recordRemoteDocRef] for what the split buys.
  ///
  /// [rev] is Kotlin's `finalRev`: the revision the *attachment* response
  /// reported, falling back to the POST's when there is no attachment or the
  /// response did not carry one.
  ///
  /// [deliveredPath] is the `path` the delivered bytes came from, read before
  /// the upload began. **It is required rather than optional because omitting
  /// the check is the bug it exists to prevent**, and it has to be checked
  /// here rather than at the call site: this method's own `getById` is the
  /// read the decision must be made against, so a caller comparing first
  /// would leave the window open between its read and this one.
  ///
  /// An edit landing while the PUT is on the wire sets a different `path` and
  /// clears `isUploaded` precisely so the new file is sent
  /// ([PersonalsRepository.update]). Writing `isUploaded: true` unconditionally
  /// then goes straight over that reset, and because
  /// `OutboxRepository.markCompleted` only deletes an `in_progress` row — the
  /// re-enqueue has already put this one back to `pending` — the next drain
  /// sees a note flagged delivered and retires the row. The file the user
  /// attached is never uploaded and nothing says so. So when the path has
  /// moved, the ids are recorded and the flag is not: that is exactly
  /// [recordRemoteDocRef]'s state, and it leaves the note in
  /// [pendingUploads] where the new file's own drain will find it.
  Future<void> markUploaded(
    String id,
    String couchId,
    String rev, {
    required String? deliveredPath,
  }) async {
    final current = await _dao.getById(id);
    if (current == null) return;
    await _dao.upsert(
      current
          .copyWith(
            isUploaded: current.path == deliveredPath,
            couchId: Value(couchId),
            rev: Value(rev),
          )
          .toCompanion(false),
    );
  }
}

String? _nullable(String? value) {
  final trimmed = value?.trim();
  return trimmed == null || trimmed.isEmpty ? null : trimmed;
}

String _randomId() {
  final timestamp = DateTime.now().microsecondsSinceEpoch;
  final random = Random.secure().nextInt(1 << 32);
  return '$timestamp-$random';
}
