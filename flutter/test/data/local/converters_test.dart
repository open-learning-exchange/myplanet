import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/converters.dart';

void main() {
  group('ExamChoice.listFromJson', () {
    test('keeps an id/text object as a pair', () {
      expect(
        ExamChoice.listFromJson([
          {'id': 'water', 'text': 'Water'},
        ]),
        [const ExamChoice(id: 'water', text: 'Water')],
      );
    });

    test('keeps a bare string, with the text doubling as the id', () {
      // `ExamTakingFragment.selectQuestion` renders one of these through
      // `addRadioButton`, where the label is also the recorded answer.
      expect(ExamChoice.listFromJson(['Yes']), [
        const ExamChoice(id: 'Yes', text: 'Yes'),
      ]);
    });

    test('drops entries that are neither, and a non-list', () {
      expect(ExamChoice.listFromJson([null, '', 42, <String, dynamic>{}]), [
        // 42 has no id/text and is not a string, `{}` yields neither.
      ]);
      expect(ExamChoice.listFromJson('choices'), isEmpty);
      expect(ExamChoice.listFromJson(null), isEmpty);
    });

    test('reads the label out of `res` when there is no `text`', () {
      // `ExamAnswerUtils.choiceDisplayValue` is `text` first with `res` only
      // as a fallback, and `insertCorrectChoice`'s single-id branch reads
      // `res` — so exam documents do carry choices labelled that way.
      // Reading `text` alone left them with a blank label, which is also what
      // the answer then recorded as its value.
      expect(
        ExamChoice.listFromJson([
          {'id': 'power', 'res': 'Power'},
        ]),
        [const ExamChoice(id: 'power', text: 'Power')],
      );
    });

    test('falls back to the text when a choice carries no id', () {
      expect(
        ExamChoice.listFromJson([
          {'text': 'Water'},
        ]),
        [const ExamChoice(id: 'Water', text: 'Water')],
      );
    });
  });

  group('ExamChoice.labelFor', () {
    // Port of `SubmissionsRepositoryExporter.formatAnswer`, which does
    // `JSONObject(choice).optString("text", choice)` for each stored entry.
    test('decodes the label out of a stored choice object', () {
      expect(ExamChoice.labelFor('{"id":"water","text":"Water"}'), 'Water');
    });

    test('leaves a bare entry — an exam stores choice ids — alone', () {
      expect(ExamChoice.labelFor('choice-a'), 'choice-a');
    });

    test('leaves a choice object with no text alone', () {
      expect(ExamChoice.labelFor('{"id":"water"}'), '{"id":"water"}');
    });

    test('joins a whole answer the way both display paths do', () {
      expect(
        ExamChoice.labelsFor([
          '{"id":"water","text":"Water"}',
          '{"id":"power","text":"Power"}',
        ]),
        'Water, Power',
      );
    });
  });

  group('ExamChoiceListConverter', () {
    const converter = ExamChoiceListConverter();

    test('round-trips the CouchDB shape', () {
      const choices = [
        ExamChoice(id: 'water', text: 'Water'),
        ExamChoice(id: 'power', text: 'Power'),
      ];
      expect(converter.fromSql(converter.toSql(choices)), choices);
    });

    test('reads a row an earlier build wrote as a flattened string', () {
      // `SurveyQuestions.choices` used to be a `StringListConverter`, so rows
      // already on disk hold the `toString()`d map. They decode as a choice
      // whose text is that literal — not a crash — until the next surveys
      // sync rewrites the row.
      expect(converter.fromSql('["{id: water, text: Water}"]'), [
        const ExamChoice(
          id: '{id: water, text: Water}',
          text: '{id: water, text: Water}',
        ),
      ]);
    });

    test('reads an empty column as no choices', () {
      expect(converter.fromSql(''), isEmpty);
      expect(converter.fromSql('[]'), isEmpty);
    });
  });
}
