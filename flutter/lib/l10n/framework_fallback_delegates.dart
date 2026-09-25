import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';

/// English framework strings for locales `flutter_localizations` does not
/// translate.
///
/// The Kotlin app supports Somali, and Flutter does not: all three global
/// delegates answer `isSupported(Locale('so'))` with false, where Arabic,
/// French, Nepali and Spanish are all covered. That is a real difference between
/// the two platforms rather than a gap in this port — Android resources need no
/// framework support, Flutter widgets do.
///
/// Without these, shipping `app_so.arb` is *worse* than not shipping it: the
/// locale resolves (it is in `supportedLocales`), no framework delegate claims
/// it, and the first widget to call `MaterialLocalizations.of` throws. Somali
/// would go from a menu entry that quietly showed English to one that crashed.
///
/// So the app's own strings come from the generated `AppLocalizations` — which
/// does support Somali, 195 of them — and only the framework's own labels
/// ("Cancel", month names, the date picker) fall back to English. That is the
/// graceful half of the degradation, and it is invisible on most screens.
///
/// These must be listed **after** the global delegates. `Localizations` uses the
/// first delegate that claims a given type for the resolved locale, so a
/// fallback placed first would override real translations for every locale.
const List<LocalizationsDelegate<dynamic>> frameworkFallbackDelegates =
    <LocalizationsDelegate<dynamic>>[
      _FallbackMaterialLocalizationsDelegate(),
      _FallbackWidgetsLocalizationsDelegate(),
      _FallbackCupertinoLocalizationsDelegate(),
    ];

class _FallbackMaterialLocalizationsDelegate
    extends LocalizationsDelegate<MaterialLocalizations> {
  const _FallbackMaterialLocalizationsDelegate();

  @override
  bool isSupported(Locale locale) => true;

  @override
  Future<MaterialLocalizations> load(Locale locale) =>
      GlobalMaterialLocalizations.delegate.load(const Locale('en'));

  @override
  bool shouldReload(_FallbackMaterialLocalizationsDelegate old) => false;
}

class _FallbackWidgetsLocalizationsDelegate
    extends LocalizationsDelegate<WidgetsLocalizations> {
  const _FallbackWidgetsLocalizationsDelegate();

  @override
  bool isSupported(Locale locale) => true;

  @override
  Future<WidgetsLocalizations> load(Locale locale) =>
      GlobalWidgetsLocalizations.delegate.load(const Locale('en'));

  @override
  bool shouldReload(_FallbackWidgetsLocalizationsDelegate old) => false;
}

class _FallbackCupertinoLocalizationsDelegate
    extends LocalizationsDelegate<CupertinoLocalizations> {
  const _FallbackCupertinoLocalizationsDelegate();

  @override
  bool isSupported(Locale locale) => true;

  @override
  Future<CupertinoLocalizations> load(Locale locale) =>
      GlobalCupertinoLocalizations.delegate.load(const Locale('en'));

  @override
  bool shouldReload(_FallbackCupertinoLocalizationsDelegate old) => false;
}
