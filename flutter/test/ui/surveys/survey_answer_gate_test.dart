import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/data/local/converters.dart';
import 'package:myplanet/ui/surveys/survey_answer_gate.dart';

/// The rule both survey forms submit under, pinned once.
///
/// `ExamTakingFragment.isQuestionAnswered` (`:289`) has **no `required` test**
/// — it branches on the question's `type` and on the `hasOtherOption` case,
/// and `model/ExamQuestion.kt` has no such field for it to read. So every
/// question must be answered, and the port's two screens have to agree on
/// that. They did not: `take_survey_screen` gated on `question.required &&`,
/// a column that is false for every question of a survey Planet does not mark
/// up, so its guard could not fire and a blank sheet went to the server.
///
/// **The fixtures here are chosen to distinguish, not to pass.** Each case
/// notes what a wrong implementation would do with it; see Phase 156's two
/// decoys, both of which ordered identically with and without the behaviour
/// they claimed to pin.
void main() {
  SurveyQuestionRow question({
    required List<ExamChoice> choices,
    bool required = false,
  }) => SurveyQuestionRow(
    id: 'survey-1:q1',
    surveyId: 'survey-1',
    questionId: 'q1',
    body: 'Which service?',
    type: choices.isEmpty ? 'input' : 'select',
    choices: choices,
    required: required,
    position: 0,
  );

  const water = ExamChoice(id: 'water', text: 'Water');

  group('surveyQuestionAnswered', () {
    test('an untouched text question is unanswered even when the document '
        'does not mark it required', () {
      // The defect, at its smallest. `required` is false because
      // `survey_mapper.dart:51` reads a key Planet does not write, and the old
      // gate's `question.required &&` made this case answered.
      expect(
        surveyQuestionAnswered(
          question(choices: const []),
          text: '',
          selected: const {},
        ),
        isFalse,
      );
    });

    test('whitespace does not answer a text question', () {
      // Pins the `.trim()`. Without it a single space submits the sheet.
      expect(
        surveyQuestionAnswered(
          question(choices: const []),
          text: '   ',
          selected: const {},
        ),
        isFalse,
      );
    });

    test('typed text answers a text question', () {
      expect(
        surveyQuestionAnswered(
          question(choices: const []),
          text: 'Water',
          selected: const {},
        ),
        isTrue,
      );
    });

    test('a choice question needs a choice, not text', () {
      // **This is the case that separates the two shapes.** The old gate was
      // `text.isEmpty && choices.isEmpty`, which calls this answered; the
      // branch-on-the-question form calls it unanswered. Today no card renders
      // a text field for a question that offers choices, so the two agree on
      // screen — which is exactly why the difference has to be pinned here
      // rather than left for a later card to expose.
      expect(
        surveyQuestionAnswered(
          question(choices: const [water]),
          text: 'Water',
          selected: const {},
        ),
        isFalse,
      );
    });

    test('a picked choice answers a choice question with no text', () {
      expect(
        surveyQuestionAnswered(
          question(choices: const [water]),
          text: '',
          selected: {water},
        ),
        isTrue,
      );
    });

    test('marking a question required changes nothing', () {
      // Both directions. Kotlin has no such flag, so a document that does
      // carry one must not make an answered question refuse either.
      expect(
        surveyQuestionAnswered(
          question(choices: const [], required: true),
          text: '',
          selected: const {},
        ),
        isFalse,
      );
      expect(
        surveyQuestionAnswered(
          question(choices: const [], required: true),
          text: 'Water',
          selected: const {},
        ),
        isTrue,
      );
    });
  });

  group('surveyHasUnansweredQuestion', () {
    final text = question(choices: const []);
    final choice = question(choices: const [water]);

    test('one unanswered question among answered ones is enough', () {
      // A fixture of one question cannot tell `any` from `first`, and a
      // fixture whose unanswered question comes first cannot tell `any` from
      // `all`. The unanswered one is last.
      expect(
        surveyHasUnansweredQuestion(
          [choice, text],
          // Both questions are unanswered on their own axis; only `choice`
          // is answered, and it comes first.
          textFor: (_) => '',
          selectedFor: (q) => q == choice ? {water} : const {},
        ),
        isTrue,
      );
    });

    test('every question answered passes', () {
      expect(
        surveyHasUnansweredQuestion(
          [choice, text],
          textFor: (q) => q == text ? 'Water' : '',
          selectedFor: (q) => q == choice ? {water} : const {},
        ),
        isFalse,
      );
    });

    test('an empty list is vacuously answered', () {
      // Recorded rather than asserted as desirable: it is only safe because
      // neither screen reaches Submit with no questions — both render
      // `surveyHasNoQuestions` instead, as `ExamTakingFragment` does. A screen
      // that lost that branch would post an empty sheet through this.
      expect(
        surveyHasUnansweredQuestion(
          const [],
          textFor: (_) => '',
          selectedFor: (_) => const {},
        ),
        isFalse,
      );
    });
  });

  group('both survey screens go through this file', () {
    // Phase 158's lesson one layer up: the tests above prove the rule is
    // right, and the screen tests prove each screen behaves. **Nothing proves
    // a screen calls this file.** Re-inlining a private copy whose only
    // difference is the branch shape is unobservable through any card the port
    // renders today — the doc comment on `surveyQuestionAnswered` says so — so
    // no behavioural test can be made to fail on it. A source-text guard can,
    // and this is the `pending_submissions_sweep_test.dart` shape.
    //
    // These two screens are the port's whole survey-answering surface;
    // `send_survey_screen` and `surveys_screen` are list screens that submit
    // nothing. If a third answering screen lands, it belongs in this list.
    const screens = [
      'lib/ui/surveys/take_survey_screen.dart',
      'lib/ui/surveys/public_survey_screen.dart',
    ];

    for (final path in screens) {
      test('$path imports and calls the shared gate', () {
        final source = File(path).readAsStringSync();
        expect(
          source,
          contains("import 'survey_answer_gate.dart';"),
          reason:
              '$path must take its answered-everything rule from the shared '
              'file. Two copies of this rule is how they drifted the first '
              'time: one gated on `question.required &&`, which Kotlin has no '
              'counterpart for, and uploaded blank answer sheets for months.',
        );
        expect(
          source,
          contains('surveyHasUnansweredQuestion('),
          reason:
              '$path must gate its submit on surveyHasUnansweredQuestion. A '
              'private re-implementation is behaviourally identical today, so '
              'nothing else in the suite can fail on it.',
        );
        expect(
          source,
          contains('isSelectMultiple('),
          reason:
              '$path must fold the question type through isSelectMultiple. '
              'Each screen fixed the case-sensitivity bug separately, in '
              'different rounds, which is why it lives in one place now.',
        );
      });
    }

    test('the shared gate is the only definition of these rules', () {
      // The other half: an import plus a *shadowing* local definition would
      // satisfy the checks above and still be two copies.
      for (final path in screens) {
        final source = File(path).readAsStringSync();
        expect(
          source,
          isNot(contains('bool surveyQuestionAnswered(')),
          reason: '$path defines its own copy of the gate.',
        );
        expect(
          source,
          isNot(contains('bool _isSelectMultiple(')),
          reason:
              '$path has re-grown the private `_isSelectMultiple` that was '
              'lifted into survey_answer_gate.dart.',
        );
      }
    });
  });

  group('isSelectMultiple', () {
    test(
      'matches case-insensitively, as `equals(…, ignoreCase = true)` does',
      () {
        expect(isSelectMultiple('selectMultiple'), isTrue);
        expect(isSelectMultiple('selectmultiple'), isTrue);
        expect(isSelectMultiple('SELECTMULTIPLE'), isTrue);
        expect(isSelectMultiple('select'), isFalse);
        expect(isSelectMultiple(null), isFalse);
      },
    );
  });
}
