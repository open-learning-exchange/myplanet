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
}
