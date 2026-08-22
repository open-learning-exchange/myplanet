import 'package:diacritic/diacritic.dart';

/// Port of `utils/Utilities.normalizeText`.
///
/// Lower-cases and strips combining marks so search is accent-insensitive —
/// "cafe" finds "Café". The Kotlin runs NFD normalisation then drops
/// `\p{InCombiningDiacriticalMarks}`; `removeDiacritics` is the equivalent for
/// the scripts myPlanet ships in, since `java.text.Normalizer` has no
/// `dart:core` counterpart.
///
/// The result is what lands in the `*TitleNormal` columns, which is what the
/// resource and course searches filter on.
String normalizeText(String value) => removeDiacritics(value).toLowerCase();
