import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../data/local/app_database.dart';
import '../../l10n/app_localizations.dart';
import '../../providers/app_providers.dart';
import '../../providers/session_provider.dart';
import '../../repository/submissions_repository.dart';
import '../router.dart';

/// Port of `ExamTakingFragment.kt` for course exams with grading.
/// 
/// Supports multiple question types:
/// - `select`: Single choice (radio buttons)
/// - `selectMultiple`: Multiple choice (checkboxes)
/// - `ratingScale`: Rating scale (1-9 buttons)
/// - `input`: Text input
/// - `textarea`: Multi-line text input
class TakeExamScreen extends ConsumerStatefulWidget {
  const TakeExamScreen({
    required this.examId,
    this.stepId,
    this.courseId,
    super.key,
  });

  final String examId;
  final String? stepId;
  final String? courseId;

  @override
  ConsumerState<TakeExamScreen> createState() => _TakeExamScreenState();
}

class _TakeExamScreenState extends ConsumerState<TakeExamScreen> {
  int _currentIndex = 0;
  String _singleAnswer = '';
  final Map<String, String> _multipleAnswers = {};
  String _textAnswer = '';
  final Map<String, TextEditingController> _controllers = {};
  int? _selectedRating;
  bool _isSubmitting = false;
  List<ExamQuestionRow> _questions = [];

  @override
  void dispose() {
    for (final controller in _controllers.values) {
      controller.dispose();
    }
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final exam = ref.watch(examProvider(widget.examId));
    final questions = ref.watch(examQuestionsProvider(widget.examId));

    return Scaffold(
      appBar: AppBar(
        title: Text(exam.valueOrNull?.name ?? l10n.takeExam),
        actions: [
          IconButton(
            icon: const Icon(Icons.close),
            onPressed: () => _confirmExit(context),
          ),
        ],
      ),
      body: questions.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (_, __) => Center(child: Text(l10n.examLoadFailed)),
        data: (rows) {
          if (rows.isEmpty) {
            return Center(child: Text(l10n.examHasNoQuestions));
          }
          _questions = rows;
          if (_currentIndex >= rows.length) {
            _currentIndex = rows.length - 1;
          }
          return _buildQuestionView(context, rows[_currentIndex], rows.length);
        },
      ),
    );
  }

  Widget _buildQuestionView(
    BuildContext context,
    ExamQuestionRow question,
    int totalQuestions,
  ) {
    final l10n = AppLocalizations.of(context);
    final progress = ((_currentIndex + 1) / totalQuestions);

    return Column(
      children: [
        // Progress bar
        LinearProgressIndicator(value: progress),

        // Question counter
        Padding(
          padding: const EdgeInsets.all(16),
          child: Text(
            '${l10n.question} ${_currentIndex + 1} / $totalQuestions',
            style: Theme.of(context).textTheme.titleMedium,
          ),
        ),

        // Question content
        Expanded(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                if (question.header?.isNotEmpty == true)
                  Padding(
                    padding: const EdgeInsets.only(bottom: 8),
                    child: Text(
                      question.header!,
                      style: Theme.of(context).textTheme.titleLarge,
                    ),
                  ),
                if (question.body?.isNotEmpty == true)
                  Padding(
                    padding: const EdgeInsets.only(bottom: 24),
                    child: Text(
                      question.body!,
                      style: Theme.of(context).textTheme.bodyLarge,
                    ),
                  ),

                // Answer input based on question type
                _buildAnswerInput(context, question),
              ],
            ),
          ),
        ),

        // Navigation buttons
        _buildNavigationButtons(context, question, totalQuestions),
      ],
    );
  }

  Widget _buildAnswerInput(BuildContext context, ExamQuestionRow question) {
    final type = question.type ?? 'input';

    switch (type) {
      case 'select':
        return _buildRadioGroup(context, question);
      case 'selectMultiple':
        return _buildCheckboxGroup(context, question);
      case 'ratingScale':
        return _buildRatingScale(context, question);
      case 'input':
        final controller = _controllers.putIfAbsent(
          question.id,
          () => TextEditingController(text: _textAnswer),
        );
        return TextField(
          controller: controller,
          decoration: InputDecoration(
            labelText: AppLocalizations.of(context).yourAnswer,
            border: const OutlineInputBorder(),
          ),
          onChanged: (value) => _textAnswer = value,
        );
      case 'textarea':
        final controller = _controllers.putIfAbsent(
          question.id,
          () => TextEditingController(text: _textAnswer),
        );
        return TextField(
          controller: controller,
          maxLines: 5,
          decoration: InputDecoration(
            labelText: AppLocalizations.of(context).yourAnswer,
            border: const OutlineInputBorder(),
          ),
          onChanged: (value) => _textAnswer = value,
        );
      default:
        final controller = _controllers.putIfAbsent(
          question.id,
          () => TextEditingController(text: _textAnswer),
        );
        return TextField(
          controller: controller,
          decoration: InputDecoration(
            labelText: AppLocalizations.of(context).yourAnswer,
            border: const OutlineInputBorder(),
          ),
          onChanged: (value) => _textAnswer = value,
        );
    }
  }

  Widget _buildRadioGroup(BuildContext context, ExamQuestionRow question) {
    return Column(
      children: [
        for (final choice in question.choices)
          RadioListTile<String>(
            title: Text(choice),
            value: choice,
            groupValue: _singleAnswer.isEmpty ? null : _singleAnswer,
            onChanged: (value) {
              setState(() {
                _singleAnswer = value ?? '';
              });
            },
          ),
      ],
    );
  }

  Widget _buildCheckboxGroup(BuildContext context, ExamQuestionRow question) {
    return Column(
      children: [
        for (final choice in question.choices)
          CheckboxListTile(
            title: Text(choice),
            value: _multipleAnswers.containsKey(choice),
            onChanged: (checked) {
              setState(() {
                if (checked == true) {
                  _multipleAnswers[choice] = choice;
                } else {
                  _multipleAnswers.remove(choice);
                }
              });
            },
          ),
      ],
    );
  }

  Widget _buildRatingScale(BuildContext context, ExamQuestionRow question) {
    final max = question.scaleMax > 0 ? question.scaleMax : 9;
    return Wrap(
      spacing: 8,
      children: List.generate(max, (index) {
        final value = index + 1;
        final isSelected = _selectedRating == value;
        return ChoiceChip(
          label: Text('$value'),
          selected: isSelected,
          onSelected: (selected) {
            setState(() {
              _selectedRating = selected ? value : null;
            });
          },
        );
      }),
    );
  }

  Widget _buildNavigationButtons(
    BuildContext context,
    ExamQuestionRow question,
    int totalQuestions,
  ) {
    final l10n = AppLocalizations.of(context);
    final isLast = _currentIndex >= totalQuestions - 1;

    return Padding(
      padding: const EdgeInsets.all(16),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          if (_currentIndex > 0)
            OutlinedButton.icon(
              onPressed: _saveAndGoPrevious,
              icon: const Icon(Icons.arrow_back),
              label: Text(l10n.previous),
            )
          else
            const SizedBox.shrink(),
          if (isLast)
            FilledButton.icon(
              onPressed: _isSubmitting ? null : () => _submitExam(context),
              icon: _isSubmitting
                  ? const SizedBox.square(dimension: 18, child: CircularProgressIndicator(strokeWidth: 2))
                  : const Icon(Icons.check),
              label: Text(l10n.submitExam),
            )
          else
            FilledButton.icon(
              onPressed: _saveAndGoNext,
              icon: const Icon(Icons.arrow_forward),
              label: Text(l10n.next),
            ),
        ],
      ),
    );
  }

  void _saveAndGoPrevious() {
    _saveCurrentAnswer();
    setState(() {
      _currentIndex--;
      _loadAnswerForCurrentQuestion();
    });
  }

  void _saveAndGoNext() {
    _saveCurrentAnswer();
    setState(() {
      _currentIndex++;
      _loadAnswerForCurrentQuestion();
    });
  }

  void _saveCurrentAnswer() {
    if (_currentIndex >= _questions.length) return;
    final question = _questions[_currentIndex];
    final type = question.type ?? 'input';

    switch (type) {
      case 'select':
        // Already saved in _singleAnswer
        break;
      case 'selectMultiple':
        // Already saved in _multipleAnswers
        break;
      case 'ratingScale':
        // Already saved in _selectedRating
        break;
      case 'input':
      case 'textarea':
        final controller = _controllers[question.id];
        _textAnswer = controller?.text ?? '';
        break;
    }
  }

  void _loadAnswerForCurrentQuestion() {
    if (_currentIndex >= _questions.length) return;
    final question = _questions[_currentIndex];
    final type = question.type ?? 'input';

    // Reset for new question
    _singleAnswer = '';
    _multipleAnswers.clear();
    _selectedRating = null;

    switch (type) {
      case 'select':
        // Will be set by RadioListTile
        break;
      case 'selectMultiple':
        // Will be set by CheckboxListTile
        break;
      case 'ratingScale':
        // Will be set by ChoiceChip
        break;
      case 'input':
      case 'textarea':
        final controller = _controllers[question.id];
        _textAnswer = controller?.text ?? '';
        break;
    }
  }

  Future<void> _submitExam(BuildContext context) async {
    final l10n = AppLocalizations.of(context);
    setState(() => _isSubmitting = true);

    // Save current answer
    _saveCurrentAnswer();

    // Calculate score
    int correctCount = 0;
    for (final question in _questions) {
      if (_isAnswerCorrect(question)) {
        correctCount++;
      }
    }

    final totalQuestions = _questions.length;
    final score = totalQuestions > 0 ? (correctCount * 100) ~/ totalQuestions : 0;

    // Show result dialog
    if (!mounted) return;
    
    await showDialog<void>(
      context: context,
      barrierDismissible: false,
      builder: (context) => AlertDialog(
        title: Text(l10n.examComplete),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              score >= 70 ? Icons.celebration : Icons.emoji_events,
              size: 64,
              color: score >= 70 ? Colors.green : Colors.orange,
            ),
            const SizedBox(height: 16),
            Text(
              '$score%',
              style: Theme.of(context).textTheme.headlineLarge,
            ),
            Text(
              '${l10n.correctAnswers}: $correctCount / $totalQuestions',
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () {
              Navigator.of(context).pop();
              context.pop();
            },
            child: Text(l10n.finish),
          ),
        ],
      ),
    );

    if (mounted) {
      setState(() => _isSubmitting = false);
    }
  }

  bool _isAnswerCorrect(ExamQuestionRow question) {
    final type = question.type ?? 'input';
    final correctChoices = question.correctChoices
        .map((s) => s.toLowerCase())
        .toSet();

    switch (type) {
      case 'select':
        return correctChoices.contains(_singleAnswer.toLowerCase());
      case 'selectMultiple':
        final userAnswers = _multipleAnswers.values
            .map((s) => s.toLowerCase())
            .toSet();
        return userAnswers.containsAll(correctChoices) &&
            correctChoices.containsAll(userAnswers);
      case 'ratingScale':
        return _selectedRating != null &&
            correctChoices.contains(_selectedRating.toString().toLowerCase());
      case 'input':
      case 'textarea':
        final text = _controllers[question.id]?.text.trim().toLowerCase() ?? '';
        return correctChoices.contains(text);
      default:
        return false;
    }
  }

  Future<void> _confirmExit(BuildContext context) async {
    final l10n = AppLocalizations.of(context);
    final shouldExit = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(l10n.exitExam),
        content: Text(l10n.exitExamMessage),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: Text(l10n.buttonCancel),
          ),
          TextButton(
            onPressed: () => Navigator.of(context).pop(true),
            child: Text(l10n.exit),
          ),
        ],
      ),
    );

    if (shouldExit == true && context.mounted) {
      context.pop();
    }
  }
}

/// Provider for a single exam by ID.
final examProvider = FutureProvider.family<ExamRow?, String>((ref, examId) async {
  final db = ref.watch(appDatabaseProvider);
  return db.examDao.getById(examId);
});

/// Provider for exam questions by exam ID.
final examQuestionsProvider = FutureProvider.family<List<ExamQuestionRow>, String>((ref, examId) async {
  final db = ref.watch(appDatabaseProvider);
  return db.examDao.questionsFor(examId);
});
