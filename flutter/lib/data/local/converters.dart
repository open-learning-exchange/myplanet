import 'dart:convert';

import 'package:drift/drift.dart';

/// Port of `data/room/Converters.kt`.
///
/// The Room schema stores the formerly-`RealmList<String>` fields (`subject`,
/// `level`, `tag`, `languages`, `resourceFor`, `userId`, `rolesList`) as a JSON
/// array in a TEXT column, and shelf membership is queried with `LIKE` against
/// that column. Keeping the identical on-disk encoding here means the same
/// queries port over unchanged.
class StringListConverter extends TypeConverter<List<String>, String>
    with JsonTypeConverter<List<String>, String> {
  const StringListConverter();

  @override
  List<String> fromSql(String fromDb) {
    if (fromDb.isEmpty) return const [];
    final decoded = jsonDecode(fromDb);
    if (decoded is! List) return const [];
    return decoded.map((e) => e.toString()).toList(growable: false);
  }

  @override
  String toSql(List<String> value) => jsonEncode(value);
}

/// One selectable answer on an exam or survey question.
///
/// CouchDB stores these as `{"id": "...", "text": "..."}` objects, and both
/// halves are load-bearing: `text` is what the learner reads, `id` is what the
/// answer records and what grading compares against. Flattening them to plain
/// strings — as `JsonUtils.getStringList` does — calls `toString()` on the map
/// and yields `{id: a, text: Paris}` for the label while destroying the id, so
/// nothing can ever match a correct answer.
///
/// Some documents carry bare strings instead of objects; those are kept with
/// the text doubling as the id, which is how `ExamTakingFragment.addRadioButton`
/// treats them.
class ExamChoice {
  const ExamChoice({required this.id, required this.text});

  final String id;
  final String text;

  static ExamChoice? fromJson(Object? raw) {
    if (raw is String) {
      return raw.isEmpty ? null : ExamChoice(id: raw, text: raw);
    }
    if (raw is Map) {
      // `text` first with `res` only as a fallback, which is
      // `ExamAnswerUtils.choiceDisplayValue` and what
      // `SubmissionsRepository._questionFromJson` already does for the
      // display column. Exam documents really do label choices with `res` —
      // `ExamQuestion.insertCorrectChoice`'s single-id branch reads it — and
      // reading `text` alone left those with a blank label *and* recorded a
      // blank value on the answer. Kotlin's `addCompoundButton` does read
      // `text` alone and so draws them blank; the port keeps one display rule
      // instead, the same call Phase 104 made for the cached choice labels.
      var text = raw['text']?.toString() ?? '';
      if (text.isEmpty) text = raw['res']?.toString() ?? '';
      final id = raw['id']?.toString() ?? text;
      if (id.isEmpty && text.isEmpty) return null;
      return ExamChoice(id: id.isEmpty ? text : id, text: text);
    }
    return null;
  }

  Map<String, dynamic> toJson() => {'id': id, 'text': text};

  /// This choice as one stored answer entry.
  ///
  /// `saveExamAnswer` writes `"""{"id":"\$id","text":"\$text"}"""` into
  /// `valueChoices`, and `Answer.valueChoicesArray` parses it straight back
  /// with `gson.fromJson(choice, JsonObject::class.java)`.
  String encode() => jsonEncode(toJson());

  /// Reads one stored answer entry back.
  ///
  /// The single reader for a `valueChoices` entry, wherever it came from: the
  /// choice object both write paths store, or a bare id an earlier build of
  /// the exam path wrote. A bare entry resolves to `{id: raw, text: raw}`,
  /// which is exactly what Kotlin's own unresolvable-id fallback produces —
  /// `getChoiceTextById` returns `map[id] ?: id`, so `saveExamAnswer` writes
  /// the id as the text too.
  static ExamChoice? decode(String raw) {
    try {
      return ExamChoice.fromJson(jsonDecode(raw));
    } on FormatException {
      return ExamChoice.fromJson(raw);
    }
  }

  /// Port of `ExamAnswerUtils.getChoiceTextById`: the display text of the
  /// choice [id] names, or [id] itself when the question no longer offers it
  /// (`map[id] ?: id`). A choice with no display value is left out of the
  /// lookup, so it too falls back to its id.
  static String textById(Iterable<ExamChoice> choices, String id) {
    for (final choice in choices) {
      if (choice.id == id && choice.text.isNotEmpty) return choice.text;
    }
    return id;
  }

  /// Parses a document's `choices` array. Anything that is neither an object
  /// nor a non-empty string is dropped, matching `ExamTakingFragment`, which
  /// renders a compound button per object and a radio per bare string and has
  /// no branch for anything else.
  static List<ExamChoice> listFromJson(Object? raw) {
    if (raw is! List) return const [];
    return raw
        .map(ExamChoice.fromJson)
        .whereType<ExamChoice>()
        .toList(growable: false);
  }

  /// The label to show for one stored answer choice.
  ///
  /// Port of `SubmissionsRepositoryExporter.formatAnswer`, which does
  /// `JSONObject(choice).optString("text", choice)` per entry: an answer
  /// records the whole `{"id":…,"text":…}` object as a JSON string
  /// (`Answer.valueChoicesArray` parses it straight back), so displaying the
  /// entry verbatim would put raw JSON in front of the user. An entry that is
  /// not a choice object — the exam path stores bare choice ids — is shown as
  /// it stands.
  static String labelFor(String raw) {
    try {
      final decoded = jsonDecode(raw);
      if (decoded is Map) {
        final text = decoded['text'];
        if (text is String && text.isNotEmpty) return text;
      }
    } on FormatException {
      // Not JSON — the entry is already the label (or an id).
    }
    return raw;
  }

  /// [labelFor] applied to a whole answer, joined the way both Kotlin display
  /// paths join it.
  static String labelsFor(Iterable<String> raw) => raw.map(labelFor).join(', ');

  @override
  bool operator ==(Object other) =>
      other is ExamChoice && other.id == id && other.text == text;

  @override
  int get hashCode => Object.hash(id, text);
}

/// Stores [ExamChoice] lists in the shape CouchDB uses, so a round-trip through
/// SQLite preserves the id/text pairing rather than collapsing it.
class ExamChoiceListConverter extends TypeConverter<List<ExamChoice>, String>
    with JsonTypeConverter<List<ExamChoice>, String> {
  const ExamChoiceListConverter();

  @override
  List<ExamChoice> fromSql(String fromDb) {
    if (fromDb.isEmpty) return const [];
    final decoded = jsonDecode(fromDb);
    if (decoded is! List) return const [];
    return decoded
        .map(ExamChoice.fromJson)
        .whereType<ExamChoice>()
        .toList(growable: false);
  }

  @override
  String toSql(List<ExamChoice> value) =>
      jsonEncode(value.map((choice) => choice.toJson()).toList());
}
