import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/converters.dart';
import 'package:myplanet/data/local/exam_mapper.dart';

void main() {
  Map<String, dynamic> examDoc({
    List<Map<String, dynamic>> questions = const [],
  }) => {
    '_id': 'exam-1',
    '_rev': '1-a',
    'type': 'exam',
    'name': 'Unit 1 test',
    'courseId': 'course-1',
    'stepId': 'step-1',
    'passingPercentage': '80',
    'questions': questions,
  };

  test('survey documents are left for the survey mapper', () {
    expect(ExamMapper.fromDoc({'_id': 's-1', 'type': 'surveys'}), isNull);
    expect(ExamMapper.fromDoc({'_id': '_design/x', 'type': 'exam'}), isNull);
  });

  test('choices keep their id and label instead of being stringified', () {
    final mapped = ExamMapper.fromDoc(
      examDoc(
        questions: [
          {
            'id': 'q1',
            'title': 'Capital of France?',
            'type': 'select',
            'choices': [
              {'id': 'c1', 'text': 'Paris'},
              {'id': 'c2', 'text': 'Lyon'},
            ],
            'correctChoice': 'c1',
          },
        ],
      ),
    );

    final question = mapped!.questions.single;
    // `getStringList` would have produced `{id: c1, text: Paris}` as the label
    // and thrown the id away, leaving the question unanswerable and ungradeable.
    expect(question.choices.value, [
      const ExamChoice(id: 'c1', text: 'Paris'),
      const ExamChoice(id: 'c2', text: 'Lyon'),
    ]);
    expect(question.correctChoices.value, ['c1']);
  });

  test('the question label comes from `title`, as the Kotlin reads it', () {
    final mapped = ExamMapper.fromDoc(
      examDoc(
        questions: [
          {'id': 'q1', 'title': 'Capital of France?', 'type': 'input'},
        ],
      ),
    );
    expect(mapped!.questions.single.header.value, 'Capital of France?');
  });

  test('a multi-answer correctChoice list is kept whole', () {
    final mapped = ExamMapper.fromDoc(
      examDoc(
        questions: [
          {
            'id': 'q1',
            'type': 'selectMultiple',
            'choices': [
              {'id': 'c1', 'text': 'Paris'},
              {'id': 'c2', 'text': 'Lyon'},
              {'id': 'c3', 'text': 'Nice'},
            ],
            'correctChoice': ['c1', 'c3'],
          },
        ],
      ),
    );
    expect(mapped!.questions.single.correctChoices.value, ['c1', 'c3']);
  });

  test('a correctChoice naming the label still resolves to the id', () {
    final mapped = ExamMapper.fromDoc(
      examDoc(
        questions: [
          {
            'id': 'q1',
            'type': 'select',
            'choices': [
              {'id': 'c1', 'text': 'Paris'},
            ],
            'correctChoice': 'Paris',
          },
        ],
      ),
    );
    expect(mapped!.questions.single.correctChoices.value, ['c1']);
  });

  test('bare string choices are kept, with the text doubling as the id', () {
    final mapped = ExamMapper.fromDoc(
      examDoc(
        questions: [
          {
            'id': 'q1',
            'type': 'select',
            'choices': ['Paris', 'Lyon'],
            'correctChoice': 'Paris',
          },
        ],
      ),
    );
    expect(mapped!.questions.single.choices.value, [
      const ExamChoice(id: 'Paris', text: 'Paris'),
      const ExamChoice(id: 'Lyon', text: 'Lyon'),
    ]);
    expect(mapped.questions.single.correctChoices.value, ['paris']);
  });

  test('a question without an id gets the Kotlin synthetic id', () {
    final mapped = ExamMapper.fromDoc(
      examDoc(
        questions: [
          {'type': 'input'},
        ],
      ),
    );
    expect(mapped!.questions.single.id.value, 'exam-1-0');
  });

  test('exam metadata survives the mapping', () {
    final mapped = ExamMapper.fromDoc(
      examDoc(
        questions: [
          {'id': 'q1', 'type': 'input'},
        ],
      ),
    );
    expect(mapped!.exam.id.value, 'exam-1');
    expect(mapped.exam.rev.value, '1-a');
    expect(mapped.exam.stepId.value, 'step-1');
    expect(mapped.exam.courseId.value, 'course-1');
    expect(mapped.exam.passingPercentage.value, '80');
    expect(mapped.exam.noOfQuestions.value, 1);
  });

  test('the choice converter round-trips through SQLite', () {
    const converter = ExamChoiceListConverter();
    const choices = [
      ExamChoice(id: 'c1', text: 'Paris'),
      ExamChoice(id: 'c2', text: 'Lyon'),
    ];
    expect(converter.fromSql(converter.toSql(choices)), choices);
    expect(converter.fromSql(''), isEmpty);
  });

  group('the exams-database walk', () {
    // Phase 113. The rule is Kotlin's: `bulkInsertExamsFromSync` parses every
    // document of that database, and the split between a survey and a test is
    // made later by `type`. Requiring `type == 'exam'` dropped every real
    // course test, which carries `type: "courses"`
    // (`CoursesRepositoryImpl.kt:196`, `:530`).
    test('accepts a course test, which is typed "courses" and not "exam"', () {
      final mapped = ExamMapper.fromDoc({
        '_id': 'exam-1',
        'type': 'courses',
        'name': 'Unit 1 test',
      });
      expect(mapped, isNotNull);
      expect(mapped!.exam.id.value, 'exam-1');
    });

    test('accepts a document with no type at all', () {
      expect(ExamMapper.fromDoc({'_id': 'exam-1'}), isNotNull);
    });

    test('leaves stepId and courseId absent when the document omits them', () {
      // Not `Value(null)`: this walk is called with a blank step and course id
      // (`SurveysRepositoryImpl.kt:387`), so writing null would erase the join
      // the `courses` walk owns on the next re-pull.
      final mapped = ExamMapper.fromDoc({'_id': 'exam-1', 'type': 'courses'})!;
      expect(mapped.exam.stepId.present, isFalse);
      expect(mapped.exam.courseId.present, isFalse);
    });

    test('still records stepId and courseId when the document has them', () {
      final mapped = ExamMapper.fromDoc(examDoc())!;
      expect(mapped.exam.stepId.value, 'step-1');
      expect(mapped.exam.courseId.value, 'course-1');
    });
  });

  group('ExamMapper.fromCourseDoc', () {
    String stepIdFor(String courseId, int index) => '$courseId:$index';

    Map<String, dynamic> courseDoc({
      Map<String, dynamic>? exam,
      int steps = 1,
    }) => {
      '_id': 'course-1',
      'courseTitle': 'Water',
      'steps': [
        for (var i = 0; i < steps; i++)
          {'stepTitle': 'Step $i', if (i == 0 && exam != null) 'exam': exam},
      ],
    };

    test('attaches the step exam to the step id the course mapper mints', () {
      final mapped = ExamMapper.fromCourseDoc(
        courseDoc(
          exam: {
            '_id': 'exam-1',
            'type': 'courses',
            'name': 'Step test',
            'questions': [
              {'id': 'q1', 'title': 'Which is wet?', 'type': 'input'},
            ],
          },
        ),
        stepIdFor: stepIdFor,
      );

      expect(mapped, hasLength(1));
      expect(mapped.single.exam.id.value, 'exam-1');
      expect(mapped.single.exam.stepId.value, 'course-1:0');
      expect(mapped.single.exam.courseId.value, 'course-1');
      expect(mapped.single.exam.noOfQuestions.value, 1);
      expect(mapped.single.questions.single.examId.value, 'exam-1');
    });

    test('falls back to a derived id when the embedded exam has none', () {
      final mapped = ExamMapper.fromCourseDoc(
        courseDoc(exam: {'name': 'Step test'}),
        stepIdFor: stepIdFor,
      );
      expect(mapped.single.exam.id.value, 'course-1-course-1:0-exam');
    });

    test("a question's body falls back to its title", () {
      // `collectRoomExam` does this; `insertExamQuestions` does not.
      final mapped = ExamMapper.fromCourseDoc(
        courseDoc(
          exam: {
            '_id': 'exam-1',
            'questions': [
              {'id': 'q1', 'title': 'Which is wet?', 'type': 'input'},
            ],
          },
        ),
        stepIdFor: stepIdFor,
      );
      expect(mapped.single.questions.single.body.value, 'Which is wet?');
    });

    test('leaves a step exam that declares itself a survey alone', () {
      expect(
        ExamMapper.fromCourseDoc(
          courseDoc(exam: {'_id': 's-1', 'type': 'surveys'}),
          stepIdFor: stepIdFor,
        ),
        isEmpty,
      );
    });

    test('a step with no exam produces nothing', () {
      expect(
        ExamMapper.fromCourseDoc(courseDoc(steps: 3), stepIdFor: stepIdFor),
        isEmpty,
      );
    });
  });
}
