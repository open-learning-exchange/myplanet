import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../l10n/app_localizations.dart';
import '../../providers/app_providers.dart';
import '../../providers/session_provider.dart';

/// Port of `UserInformationFragment.kt` for team surveys.
///
/// Collects user profile information (name, email, phone, DOB, gender, level)
/// and saves it to the submission. Shown after completing team surveys.
class UserInformationScreen extends ConsumerStatefulWidget {
  const UserInformationScreen({
    required this.submissionId,
    this.teamId,
    this.showAdditionalFields = true,
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

  String? _selectedLanguage;
  String? _selectedLevel;
  String? _selectedGender;
  DateTime? _dateOfBirth;
  bool _showAdditionalFields = false;
  bool _isSubmitting = false;

  static const _languages = [
    'English',
    'Spanish',
    'French',
    'Arabic',
    'Nepali',
    'Somali',
  ];
  static const _levels = [
    'Primary',
    'Secondary',
    'High School',
    'College',
    'University',
  ];

  @override
  void initState() {
    super.initState();
    _showAdditionalFields = widget.showAdditionalFields;
    _loadUserData();
  }

  void _loadUserData() {
    final user = ref.read(sessionProvider).valueOrNull;
    if (user != null) {
      _fnameController.text = user.firstName ?? '';
      _mnameController.text = user.middleName ?? '';
      _lnameController.text = user.lastName ?? '';
      _emailController.text = user.email ?? '';
      _phoneController.text = user.phoneNumber ?? '';
      _selectedLanguage = user.language;
      _selectedLevel = user.level;
      _selectedGender = user.gender;
      if (user.dob?.isNotEmpty == true) {
        try {
          _dateOfBirth = DateTime.parse(user.dob!);
        } catch (_) {}
      }
    }
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
            if (widget.showAdditionalFields) ...[
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
              _buildTextField(
                controller: _fnameController,
                label: l10n.firstName,
                validator: (v) =>
                    v?.isEmpty == true ? l10n.fieldRequired : null,
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
                items: _languages,
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
                items: _levels,
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

  Future<void> _cancel(BuildContext context) async {
    Navigator.of(context).pop();
  }

  Future<void> _submit(BuildContext context) async {
    final l10n = AppLocalizations.of(context);

    if (!_formKey.currentState!.validate()) {
      return;
    }

    setState(() => _isSubmitting = true);

    // Build the user profile JSON
    final profile = {
      if (_showAdditionalFields) ...{
        'firstName': _fnameController.text.trim(),
        'middleName': _mnameController.text.trim(),
        'lastName': _lnameController.text.trim(),
        'email': _emailController.text.trim(),
        'phoneNumber': _phoneController.text.trim(),
        'language': _selectedLanguage ?? '',
        'level': _selectedLevel ?? '',
        if (_dateOfBirth != null)
          'dob':
              '${_dateOfBirth!.year}-${_dateOfBirth!.month.toString().padLeft(2, '0')}-${_dateOfBirth!.day.toString().padLeft(2, '0')}',
      } else ...{
        'birthYear': _yobController.text.trim(),
      },
      'gender': _selectedGender ?? '',
    };

    try {
      // Mirrors `saveSubmission` → `markSubmissionComplete` in the Kotlin.
      // This used to build `profile` and drop it, then thank the user for data
      // that never left the widget.
      await ref
          .read(submissionsRepositoryProvider)
          .markSubmissionComplete(widget.submissionId, profile);
      final config = ref.read(serverConfigProvider);
      final user = ref.read(sessionProvider).valueOrNull;
      if (config != null && user != null) {
        await ref
            .read(submissionsUploaderProvider)
            .queuePending(config: config, userId: user.id);
      }
      if (context.mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(l10n.thankYouForTakingSurvey)));
        context.pop();
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
}
