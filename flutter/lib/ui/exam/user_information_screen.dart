import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/utils/constants.dart';
import '../../core/utils/time_utils.dart';
import '../../l10n/app_localizations.dart';
import '../../providers/app_providers.dart';
import '../../providers/session_provider.dart';

/// Port of `UserInformationFragment.kt` for team surveys.
///
/// Collects user profile information (name, email, phone, DOB, gender, level)
/// and saves it to the submission. Shown after completing team surveys.
///
/// Nothing here is prefilled from the signed-in profile, which is deliberate:
/// `initViews` sets no text on any field and the gender radios ship unchecked,
/// so `createUserProfile` reports only what the respondent typed. The port did
/// carry a prefill, but it was dead — it read `sessionProvider` with
/// `valueOrNull` from `initState`, where that provider is still loading — and
/// waking it up is worse than leaving it: `sessionProvider` restores the last
/// account that logged in on the device, so a public-survey link opened by a
/// stranger would have filed the device owner's name, email, phone, birth date,
/// language, level and gender as the respondent's own answers.
///
/// [showAdditionalFields] is the negation of the Kotlin's `shouldHideElements`,
/// and the two modes are worth stating because they are easy to invert:
///
///  * `false` (Kotlin `shouldHideElements = true`, which is what
///    `BaseExamFragment` passes for every survey that is not from the nation)
///    opens on the **year of birth** with the four extra blocks hidden, and
///    offers the `btnAdditionalFields` toggle so the respondent can open them.
///  * `true` opens on the full profile form, and the toggle is **gone** —
///    `initViews`'s else branch hides it outright, because that mode has no
///    second state to toggle into.
class UserInformationScreen extends ConsumerStatefulWidget {
  const UserInformationScreen({
    required this.submissionId,
    this.teamId,
    this.showAdditionalFields = false,
    super.key,
  });

  final String submissionId;
  final String? teamId;
  final bool showAdditionalFields;

  @override
  ConsumerState<UserInformationScreen> createState() =>
      _UserInformationScreenState();
}

class _UserInformationScreenState extends ConsumerState<UserInformationScreen> {
  final _formKey = GlobalKey<FormState>();
  final _fnameController = TextEditingController();
  final _mnameController = TextEditingController();
  final _lnameController = TextEditingController();
  final _emailController = TextEditingController();
  final _phoneController = TextEditingController();
  final _yobController = TextEditingController();

  /// The Kotlin spinners have no empty entry, so `selectedItem` is item 0
  /// until the respondent picks another — which is why `createUserProfile`
  /// always carries a language and a level in full-fields mode.
  String? _selectedLanguage = memberLanguages.first;
  String? _selectedLevel = memberLevels.first;
  String? _selectedGender;
  DateTime? _dateOfBirth;
  bool _showAdditionalFields = false;
  bool _isSubmitting = false;

  @override
  void initState() {
    super.initState();
    _showAdditionalFields = widget.showAdditionalFields;
  }

  @override
  void dispose() {
    _fnameController.dispose();
    _mnameController.dispose();
    _lnameController.dispose();
    _emailController.dispose();
    _phoneController.dispose();
    _yobController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);

    return Scaffold(
      appBar: AppBar(title: Text(l10n.yourInformation)),
      body: Form(
        key: _formKey,
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            if (!widget.showAdditionalFields) ...[
              Padding(
                padding: const EdgeInsets.only(bottom: 8),
                child: OutlinedButton(
                  onPressed: () {
                    setState(() {
                      _showAdditionalFields = !_showAdditionalFields;
                      if (!_showAdditionalFields) {
                        _fnameController.clear();
                        _mnameController.clear();
                        _lnameController.clear();
                        _emailController.clear();
                        _phoneController.clear();
                        _dateOfBirth = null;
                      } else {
                        _yobController.clear();
                      }
                    });
                  },
                  child: Text(
                    _showAdditionalFields
                        ? l10n.hideAdditionalFields
                        : l10n.showAdditionalFields,
                  ),
                ),
              ),
            ],

            // Year of birth (simplified mode)
            if (_showAdditionalFields) ...[
              // No validator: `createUserProfile` requires nothing but the
              // year of birth, so a respondent who gives no name still
              // completes the survey.
              _buildTextField(
                controller: _fnameController,
                label: l10n.firstName,
              ),
              _buildTextField(
                controller: _mnameController,
                label: l10n.middleName,
              ),
              _buildTextField(
                controller: _lnameController,
                label: l10n.lastName,
              ),
              _buildTextField(
                controller: _emailController,
                label: l10n.email,
                keyboardType: TextInputType.emailAddress,
              ),
              _buildDropdown(
                value: _selectedLanguage,
                label: l10n.language,
                items: memberLanguages,
                onChanged: (v) => setState(() => _selectedLanguage = v),
              ),
              _buildTextField(
                controller: _phoneController,
                label: l10n.phoneNumber,
                keyboardType: TextInputType.phone,
              ),
              _buildDateField(context, l10n),
            ] else ...[
              _buildTextField(
                controller: _yobController,
                label: l10n.yearOfBirth,
                keyboardType: TextInputType.number,
                inputFormatters: [
                  FilteringTextInputFormatter.digitsOnly,
                  LengthLimitingTextInputFormatter(4),
                ],
                validator: _validateYearOfBirth,
              ),
            ],

            // Gender
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 8),
              child: Text(
                l10n.gender,
                style: Theme.of(context).textTheme.titleSmall,
              ),
            ),
            RadioGroup<String?>(
              groupValue: _selectedGender,
              onChanged: (v) => setState(() => _selectedGender = v),
              child: Row(
                children: [
                  Expanded(
                    child: RadioListTile<String?>(
                      title: Text(l10n.male),
                      value: 'male',
                      contentPadding: EdgeInsets.zero,
                    ),
                  ),
                  Expanded(
                    child: RadioListTile<String?>(
                      title: Text(l10n.female),
                      value: 'female',
                      contentPadding: EdgeInsets.zero,
                    ),
                  ),
                ],
              ),
            ),

            // Level (for additional fields mode)
            if (_showAdditionalFields) ...[
              _buildDropdown(
                value: _selectedLevel,
                label: l10n.level,
                items: memberLevels,
                onChanged: (v) => setState(() => _selectedLevel = v),
              ),
            ],

            const SizedBox(height: 24),

            // Action buttons
            Row(
              children: [
                Expanded(
                  child: OutlinedButton(
                    onPressed: _isSubmitting ? null : () => _cancel(context),
                    child: Text(l10n.cancel),
                  ),
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: FilledButton(
                    onPressed: _isSubmitting ? null : () => _submit(context),
                    child: _isSubmitting
                        ? const SizedBox.square(
                            dimension: 18,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          )
                        : Text(l10n.save),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildTextField({
    required TextEditingController controller,
    required String label,
    TextInputType? keyboardType,
    List<TextInputFormatter>? inputFormatters,
    String? Function(String?)? validator,
  }) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: TextFormField(
        controller: controller,
        decoration: InputDecoration(
          labelText: label,
          border: const OutlineInputBorder(),
        ),
        keyboardType: keyboardType,
        inputFormatters: inputFormatters,
        validator: validator,
      ),
    );
  }

  Widget _buildDropdown({
    required String? value,
    required String label,
    required List<String> items,
    required void Function(String?) onChanged,
  }) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: DropdownButtonFormField<String>(
        // `initialValue` is read once per form-field state, so the prefill
        // arriving after the first build needs a fresh key to show up.
        key: ValueKey('$label:$value'),
        initialValue: value,
        decoration: InputDecoration(
          labelText: label,
          border: const OutlineInputBorder(),
        ),
        items: items
            .map((item) => DropdownMenuItem(value: item, child: Text(item)))
            .toList(),
        onChanged: onChanged,
      ),
    );
  }

  Widget _buildDateField(BuildContext context, AppLocalizations l10n) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: InkWell(
        onTap: () => _selectDate(context),
        child: InputDecorator(
          decoration: InputDecoration(
            labelText: l10n.birthDate,
            border: const OutlineInputBorder(),
            suffixIcon: const Icon(Icons.calendar_today),
          ),
          child: Text(
            _dateOfBirth != null
                ? '${_dateOfBirth!.year}-${_dateOfBirth!.month.toString().padLeft(2, '0')}-${_dateOfBirth!.day.toString().padLeft(2, '0')}'
                : l10n.selectDate,
          ),
        ),
      ),
    );
  }

  String? _validateYearOfBirth(String? value) {
    if (value == null || value.isEmpty) {
      return AppLocalizations.of(context).yearOfBirthRequired;
    }
    final year = int.tryParse(value);
    if (year == null) {
      return AppLocalizations.of(context).invalidYear;
    }
    final currentYear = DateTime.now().year;
    if (year < 1900 || year > currentYear) {
      return AppLocalizations.of(context).yearBetween(1900, currentYear);
    }
    return null;
  }

  Future<void> _selectDate(BuildContext context) async {
    final now = DateTime.now();
    final picked = await showDatePicker(
      context: context,
      initialDate: _dateOfBirth ?? now,
      firstDate: DateTime(1900),
      lastDate: now,
    );
    if (picked != null) {
      setState(() {
        _dateOfBirth = picked;
      });
    }
  }

  /// Pops `false`: `PublicSurveyActivity.uploadCompletedSubmission` looks for
  /// a submission the dialog marked `complete`, so a cancel leaves Kotlin with
  /// nothing to POST.
  Future<void> _cancel(BuildContext context) async {
    Navigator.of(context).pop(false);
  }

  Future<void> _submit(BuildContext context) async {
    final l10n = AppLocalizations.of(context);

    if (!_formKey.currentState!.validate()) {
      return;
    }

    setState(() => _isSubmitting = true);

    // `createUserProfile` reads a field only while its block is on screen.
    final profile = userSurveyProfileJson(
      fname: _showAdditionalFields ? _fnameController.text.trim() : '',
      mName: _showAdditionalFields ? _mnameController.text.trim() : '',
      lname: _showAdditionalFields ? _lnameController.text.trim() : '',
      email: _showAdditionalFields ? _emailController.text.trim() : '',
      language: _showAdditionalFields ? (_selectedLanguage ?? '') : '',
      phone: _showAdditionalFields ? _phoneController.text.trim() : '',
      dob: _showAdditionalFields && _dateOfBirth != null
          ? _isoDay(_dateOfBirth!)
          : '',
      yob: _showAdditionalFields ? '' : _yobController.text.trim(),
      level: _showAdditionalFields ? (_selectedLevel ?? '') : '',
      gender: _selectedGender ?? '',
    );

    try {
      // Mirrors `saveSubmission` → `markSubmissionComplete` in the Kotlin.
      // This used to build `profile` and drop it, then thank the user for data
      // that never left the widget.
      await ref
          .read(submissionsRepositoryProvider)
          .markSubmissionComplete(widget.submissionId, profile);
      await _queueUpload();
      if (context.mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(l10n.thankYouForTakingSurvey)));
        Navigator.of(context).pop(true);
      }
    } catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(l10n.errorSavingProfile)));
      }
    } finally {
      if (mounted) {
        setState(() => _isSubmitting = false);
      }
    }
  }

  /// The Kotlin's `onDismiss` hands a team survey to
  /// `submissionsUploader.checkAvailableServer`; here the outbox carries it.
  ///
  /// Swallowed on purpose, and awaited on the session's **future** rather than
  /// its `valueOrNull`: a screen that only reads `sessionProvider` sees it
  /// still loading, and a failure to queue is not a failure to save — the
  /// submission is already complete, and the next sync picks it up.
  Future<void> _queueUpload() async {
    try {
      final config = ref.read(serverConfigProvider);
      if (config == null) return;
      final user = await ref.read(sessionProvider.future);
      if (user == null) return;
      await ref
          .read(submissionsUploaderProvider)
          .queuePending(config: config, userId: user.id);
    } catch (_) {
      // See above.
    }
  }

  static String _isoDay(DateTime date) =>
      '${date.year.toString().padLeft(4, '0')}-'
      '${date.month.toString().padLeft(2, '0')}-'
      '${date.day.toString().padLeft(2, '0')}';
}

/// Port of `model/UserSurveyProfile.kt`'s `toJson()` — the document the Kotlin
/// stores on the submission, and what `SubmissionsRepository.serialize` hands
/// to CouchDB verbatim.
///
/// Three rules are easy to lose, and were: an empty field is **omitted**
/// rather than sent as `""`; a birth date travels as `birthDate` in ISO-8601
/// (not as the bare `dob` the picker produces) while a year of birth travels
/// as an `age` in years (not as `birthYear`); and `betaEnabled` is always
/// present.
Map<String, dynamic> userSurveyProfileJson({
  String fname = '',
  String mName = '',
  String lname = '',
  String email = '',
  String phone = '',
  String dob = '',
  String yob = '',
  String level = '',
  String gender = '',
  String language = '',
  DateTime? now,
}) {
  final user = <String, dynamic>{};
  if (fname.isNotEmpty) user['firstName'] = fname;
  if (mName.isNotEmpty) user['middleName'] = mName;
  if (lname.isNotEmpty) user['lastName'] = lname;
  if (email.isNotEmpty) user['email'] = email;
  if (language.isNotEmpty) user['language'] = language;
  if (phone.isNotEmpty) user['phoneNumber'] = phone;
  if (dob.isNotEmpty) user['birthDate'] = TimeUtils.convertToISO8601(dob);
  final birthYear = int.tryParse(yob);
  if (birthYear != null) {
    user['age'] = '${(now ?? DateTime.now()).year - birthYear}';
  }
  if (level.isNotEmpty) user['level'] = level;
  if (gender.isNotEmpty) user['gender'] = gender;
  user['betaEnabled'] = false;
  return user;
}
