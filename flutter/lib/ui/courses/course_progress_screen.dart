import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../l10n/app_localizations.dart';
import '../../providers/courses_providers.dart';
import '../../repository/progress_repository.dart';

/// Port of `ui/courses/CourseProgressActivity.kt` +
/// `ui/courses/ProgressGridAdapter.kt`.
///
/// One course's progress: a ring showing the share of steps reached, the
/// course title, "Progress x of y", and a four-column grid with one cell per
/// step coloured by how much of that step's exam the learner has answered.
///
/// **Placement.** The Kotlin puts this on its own Activity, reached by tapping
/// a row of the My Progress list (`CoursesProgressAdapter.onBindViewHolder` →
/// `startActivity(CourseProgressActivity, "courseId")`), and nothing else
/// opens it. The port mirrors that with its own route under the My Progress
/// screen; the tap used to land on the course *detail* screen instead, which
/// is a different destination with none of this on it.
///
/// The Kotlin action bar carries no label of its own (the manifest entry sets
/// no `android:label`, so it shows the app name plus an up arrow). "My
/// Progress" — the name of the list this screen is entered from — is the
/// closest honest equivalent for a Flutter `AppBar`, which cannot be
/// title-less without looking broken.
class CourseProgressScreen extends ConsumerWidget {
  const CourseProgressScreen({required this.courseId, super.key});

  final String courseId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final data = ref.watch(courseProgressGridProvider(courseId));

    return Scaffold(
      appBar: AppBar(title: Text(l10n.myProgress)),
      body: data.when(
        // `CourseProgressViewModel` starts at `null` and
        // `collectWhenStarted` skips the null, so the Kotlin screen shows its
        // empty chrome until the load lands. A spinner is the port's
        // equivalent and is what every other screen here does.
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => Center(child: Text(l10n.unavailable)),
        data: (data) => _Body(data: data),
      ),
    );
  }
}

class _Body extends StatelessWidget {
  const _Body({required this.data});

  final CourseProgressData data;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);

    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        children: [
          _ProgressRing(percent: data.ringPercent),
          Padding(
            padding: const EdgeInsets.all(8),
            child: Text(
              data.title ?? '',
              textAlign: TextAlign.center,
              style: theme.textTheme.titleMedium,
            ),
          ),
          Padding(
            padding: const EdgeInsets.all(8),
            child: Text(
              l10n.courseProgressCount(data.current, data.max),
              textAlign: TextAlign.center,
              style: theme.textTheme.bodyLarge,
            ),
          ),
          Padding(
            padding: const EdgeInsets.all(8),
            child: Text(l10n.stepsHeading, style: theme.textTheme.titleMedium),
          ),
          _StepGrid(steps: data.steps),
        ],
      ),
    );
  }
}

/// The `CircularProgressView` of `activity_course_progress.xml`: a 164dp ring
/// filled with the main colour, its arc in white, and the progress **value**
/// as its centre text — `app:progressTextType="progress"` with
/// `app:totalValue="100"`, so the label is the bare number the Activity
/// computed, without a percent sign.
class _ProgressRing extends StatelessWidget {
  const _ProgressRing({required this.percent});

  final int percent;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return SizedBox(
      width: 164,
      height: 164,
      child: DecoratedBox(
        decoration: BoxDecoration(
          shape: BoxShape.circle,
          color: theme.colorScheme.primary,
        ),
        child: Stack(
          alignment: Alignment.center,
          children: [
            Padding(
              padding: const EdgeInsets.all(6),
              child: SizedBox.expand(
                child: CircularProgressIndicator(
                  value: percent / 100,
                  strokeWidth: 12,
                  strokeCap: StrokeCap.round,
                  backgroundColor: Colors.white24,
                  valueColor: const AlwaysStoppedAnimation(Colors.white),
                ),
              ),
            ),
            Text(
              '$percent',
              style: theme.textTheme.headlineMedium?.copyWith(
                color: Colors.white,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// `GridLayoutManager(this, 4)` over `row_my_progress_grid.xml`: 60dp square
/// cards with a 4dp margin, in a block that is `wrap_content` wide and
/// centred.
///
/// Laid out as explicit rows of four rather than a `GridView` so that every
/// cell is mounted — a lazily-built grid inside a scroll view mounts only what
/// is in the viewport, which silently turns a "this cell is not shown"
/// assertion into a pass.
class _StepGrid extends StatelessWidget {
  const _StepGrid({required this.steps});

  static const int columns = 4;

  final List<CourseStepProgress> steps;

  @override
  Widget build(BuildContext context) {
    final rows = <Widget>[];
    for (var start = 0; start < steps.length; start += columns) {
      final end = (start + columns) > steps.length
          ? steps.length
          : start + columns;
      rows.add(
        Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            for (final step in steps.sublist(start, end)) _StepCell(step: step),
          ],
        ),
      );
    }
    // The RecyclerView is `wrap_content` and `layout_gravity="center"`, so the
    // whole block is centred — but `GridLayoutManager` puts item n in span
    // `n % 4`, so a final row of one cell sits in the **first column**, not
    // the middle. Rows centred inside the enclosing Column instead put a
    // 9th step under the gap between columns 1 and 2.
    return Padding(
      padding: const EdgeInsets.all(8),
      child: SizedBox(
        width: columns * _StepCell.extent,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: rows,
        ),
      ),
    );
  }
}

/// One cell of the grid — the whole of `ProgressGridAdapter.onBindViewHolder`.
///
/// Three states and no more: no percentage at all (the step has no exam, or
/// none the learner has attempted) paints the main colour and writes nothing;
/// otherwise green when every question is answered and yellow when not.
///
/// The greens and yellows are the fixed Material palette values the Kotlin
/// names (`md_green_500`, `md_yellow_500`) rather than theme roles, because
/// the Kotlin resolves them from the palette and they carry the meaning here;
/// `md_black_1000` text and `mainColor` (an alias of `colorPrimary`) map onto
/// black and the scheme's primary.
///
/// One deliberate divergence: these cells are rounded, and in the shipping app
/// they are square. `row_my_progress_grid.xml` asks for
/// `app:cardCornerRadius="8dp"`, but the adapter paints the cell with
/// `itemView.setBackgroundColor`, which AndroidX `CardView` does not override
/// — so the `RoundRectDrawable` that supplies the rounded outline is replaced
/// by a `ColorDrawable` and the radius and shadow are dead. The port draws
/// the radius the layout asks for rather than an artefact of how the colour
/// is applied.
///
/// The text is set only in the has-percentage branch, as the Kotlin does — but
/// the Kotlin's `else` branch never *clears* `tvProgress`, which is latent
/// there only because the grid is measured unbounded and so never recycles a
/// holder. A rebuilt widget carries no previous text, so the port cannot
/// resurrect it.
class _StepCell extends StatelessWidget {
  const _StepCell({required this.step});

  static const Color completedColor = Color(0xFF4CAF50);
  static const Color inProgressColor = Color(0xFFFFEB3B);

  /// 60dp of card plus the 4dp margin on each side, matching
  /// `row_my_progress_grid.xml`.
  static const double extent = 68;

  final CourseStepProgress step;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final label = step.percentageLabel;
    final Color background;
    if (label == null) {
      background = theme.colorScheme.primary;
    } else if (step.completed == true) {
      background = completedColor;
    } else {
      background = inProgressColor;
    }

    return Container(
      // `ProgressGridAdapter`'s `areItemsTheSame` keys on `stepId`; using it
      // here is what keeps the field read rather than merely carried.
      key: ValueKey(step.stepId),
      width: 60,
      height: 60,
      margin: const EdgeInsets.all(4),
      decoration: BoxDecoration(
        color: background,
        borderRadius: BorderRadius.circular(8),
        boxShadow: const [
          BoxShadow(color: Colors.black26, blurRadius: 2, offset: Offset(0, 1)),
        ],
      ),
      child: Center(
        child: label == null
            ? null
            : Text(
                l10n.percentageValue(label),
                textAlign: TextAlign.center,
                style: theme.textTheme.titleMedium?.copyWith(
                  color: Colors.black,
                ),
              ),
      ),
    );
  }
}
