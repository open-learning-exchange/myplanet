import 'package:flutter/material.dart';

import '../../l10n/app_localizations.dart';

/// Port of `utils/CourseSubject.kt` — the five-colour palette a course tile's
/// cover area is tinted with when it has no uploaded cover image. The
/// classification is substring-based, matching `CourseSubjectClassifier`.
enum CourseSubject { mathematics, literacy, health, socialStudies, technology }

/// Port of `CourseSubjectClassifier.classify`. A `subjectLevel` containing
/// "math" is Mathematics; "health" is Health; "social" or "civic" is Social
/// Studies; "computer", "technology", or "ict" is Technology; everything
/// else (including null) is Literacy.
CourseSubject classifyCourseSubject(String? subjectLevel) {
  final subject = subjectLevel?.toLowerCase() ?? '';
  if (subject.contains('math')) return CourseSubject.mathematics;
  if (subject.contains('health')) return CourseSubject.health;
  if (subject.contains('social') || subject.contains('civic')) {
    return CourseSubject.socialStudies;
  }
  if (subject.contains('computer') ||
      subject.contains('technology') ||
      subject.contains('ict')) {
    return CourseSubject.technology;
  }
  return CourseSubject.literacy;
}

/// The subject-tint colours from `values/colors.xml` (`subject_*` entries).
Color courseSubjectColor(CourseSubject subject) {
  return switch (subject) {
    CourseSubject.mathematics => const Color(0xFFF57C00),
    CourseSubject.literacy => const Color(0xFFD32F2F),
    CourseSubject.health => const Color(0xFF388E3C),
    CourseSubject.socialStudies => const Color(0xFF7B1FA2),
    CourseSubject.technology => const Color(0xFF1976D2),
  };
}

/// Maps each subject to the Material icon closest to the Kotlin drawable
/// (`ic_subject_math`, `ic_type_book`, `ic_subject_health`,
/// `ic_subject_social`, `ic_subject_technology`).
IconData courseSubjectIcon(CourseSubject subject) {
  return switch (subject) {
    CourseSubject.mathematics => Icons.calculate_outlined,
    CourseSubject.literacy => Icons.menu_book_outlined,
    CourseSubject.health => Icons.favorite_outline,
    CourseSubject.socialStudies => Icons.public_outlined,
    CourseSubject.technology => Icons.computer_outlined,
  };
}

/// The human-readable label shown beneath the cover area.
String courseSubjectLabel(CourseSubject subject, AppLocalizations l10n) {
  return switch (subject) {
    CourseSubject.mathematics => l10n.subjectLabelMathematics,
    CourseSubject.literacy => l10n.subjectLabelLiteracy,
    CourseSubject.health => l10n.subjectLabelHealth,
    CourseSubject.socialStudies => l10n.subjectLabelSocialStudies,
    CourseSubject.technology => l10n.subjectLabelTechnology,
  };
}
