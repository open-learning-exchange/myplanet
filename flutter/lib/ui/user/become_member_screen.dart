import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/utils/constants.dart';
import '../../data/local/app_database.dart';
import '../../l10n/app_localizations.dart';
import '../../providers/app_providers.dart';
import '../../repository/user_repository.dart';

/// Port of `ui/user/BecomeMemberActivity.kt`.
///
/// A registration form for creating a new member account. The form collects:
/// - username, password (with confirmation)
/// - first name, middle name, last name
/// - email, phone number
/// - language and level preferences
/// - birth date and gender
///
/// On submission, the account is created locally and queued for sync.
class BecomeMemberScreen extends ConsumerStatefulWidget {
  const BecomeMemberScreen({super.key});

  @override
  ConsumerState<BecomeMemberScreen> createState() => _BecomeMemberScreenState();
}

class _BecomeMemberScreenState extends ConsumerState<BecomeMemberScreen> {
  final _formKey = GlobalKey<FormState>();

  late final TextEditingController _usernameController;
  late final TextEditingController _passwordController;
  late final TextEditingController _rePasswordController;
  late final TextEditingController _firstNameController;
  late final TextEditingController _middleNameController;
  late final TextEditingController _lastNameController;
  late final TextEditingController _emailController;
  late final TextEditingController _phoneController;

  bool _isSubmitting = false;
  String _selectedGender = '';
  DateTime? _birthDate;
  String _selectedLanguage = memberLanguages.first;
  String _selectedLevel = memberLevels.first;

  /// Debounced live username validation (port of
  /// `BecomeMemberActivity`'s `usernameValidationJob`). A 300 ms `Timer`
  /// supersedes the previous one on every keystroke, so a fast-typing user
  /// does not hammer the DAO or see flicker from stale lookups. The stale-result
  /// guard (`_usernameController.text == input`) drops a result that arrived
  /// after the user kept typing.
  Timer? _usernameValidationTimer;
  String? _usernameError;
  bool _isValidatingUsername = false;

  @override
  void initState() {
    super.initState();
    _usernameController = TextEditingController();
    _passwordController = TextEditingController();
    _rePasswordController = TextEditingController();
    _firstNameController = TextEditingController();
    _middleNameController = TextEditingController();
    _lastNameController = TextEditingController();
    _emailController = TextEditingController();
    _phoneController = TextEditingController();
  }

  @override
  void dispose() {
    _usernameValidationTimer?.cancel();
    _usernameController.dispose();
    _passwordController.dispose();
    _rePasswordController.dispose();
    _firstNameController.dispose();
    _middleNameController.dispose();
    _lastNameController.dispose();
    _emailController.dispose();
    _phoneController.dispose();
    super.dispose();
  }

  Future<void> _selectBirthDate() async {
    final now = DateTime.now();
    final picked = await showDatePicker(
      context: context,
      initialDate: now,
      firstDate: DateTime(1900),
      lastDate: now,
      helpText: AppLocalizations.of(context).selectDate,
    );
    if (picked != null) {
      setState(() {
        _birthDate = picked;
      });
    }
  }

  String _formatDate(DateTime date) {
    return '${date.year.toString().padLeft(4, '0')}-'
        '${date.month.toString().padLeft(2, '0')}-'
        '${date.day.toString().padLeft(2, '0')}';
  }

  bool _validatePasswords() {
    final password = _passwordController.text;
    final rePassword = _rePasswordController.text;
    return password.isEmpty || password == rePassword;
  }

  void _scheduleUsernameValidation() {
    final input = _usernameController.text;
    _usernameValidationTimer?.cancel();
    // An empty field has nothing to check yet — the form validator covers it on
    // submit. Clearing any stale error here keeps the field from showing a
    // red outline after the user deleted everything.
    if (input.isEmpty) {
      if (_usernameError != null || _isValidatingUsername) {
        setState(() {
          _usernameError = null;
          _isValidatingUsername = false;
        });
      }
      return;
    }
    setState(() => _isValidatingUsername = true);
    _usernameValidationTimer = Timer(const Duration(milliseconds: 300), () {
      _validateUsername(input);
    });
  }

  Future<void> _validateUsername(String input) async {
    final l10n = AppLocalizations.of(context);
    final error = await ref
        .read(userRepositoryProvider)
        .validateUsername(
          input,
          UsernameValidationMessages(
            cannotBeEmpty: l10n.usernameCannotBeEmpty,
            invalid: l10n.invalidUsername,
            mustStartWithLetterOrNumber:
                l10n.usernameMustStartWithLetterOrNumber,
            onlyLettersNumbers: l10n.usernameOnlyLettersNumbers,
            taken: l10n.usernameTaken,
          ),
        );
    // Stale-result guard: drop the result if the user kept typing past `input`.
    if (!mounted || _usernameController.text != input) return;
    // The Kotlin path lowercases a valid input in place; do the same so the
    // stored username is always lowercased even before submit.
    if (error == null && input != input.toLowerCase()) {
      final lower = input.toLowerCase();
      _usernameController.value = TextEditingValue(
        text: lower,
        selection: TextSelection.collapsed(offset: lower.length),
      );
    }
    setState(() {
      _usernameError = error;
      _isValidatingUsername = false;
    });
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;

    if (_selectedGender.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(AppLocalizations.of(context).pleaseSelectGender),
        ),
      );
      return;
    }

    setState(() => _isSubmitting = true);

    try {
      final username = _usernameController.text.trim().toLowerCase();
      final password = _passwordController.text;

      // Check if username exists
      final existingUser = await ref.read(userDaoProvider).getByName(username);
      if (existingUser != null) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(AppLocalizations.of(context).usernameAlreadyExists),
            ),
          );
        }
        setState(() => _isSubmitting = false);
        return;
      }

      // Create member account locally
      final localId = await _createMember(username, password);

      // Attempt to upload to server. This populates the server-assigned
      // _id/_rev and the PBKDF2 security data, so the member can log in
      // on other devices. On failure we fall back to the offline-only account.
      final serverConfig = ref.read(serverConfigProvider);
      bool uploadedOnline = false;
      if (serverConfig != null) {
        uploadedOnline = await ref
            .read(userRepositoryProvider)
            .uploadNewUser(
              localId: localId,
              config: serverConfig,
              username: username,
              password: password,
            );
      }

      if (mounted) {
        final l10n = AppLocalizations.of(context);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              uploadedOnline
                  ? l10n.memberCreatedOnline
                  : l10n.memberCreatedOffline,
            ),
          ),
        );
        // Navigate to login with new credentials
        context.go(
          '/login',
          extra: {
            'username': username,
            'password': password,
            'isNewMember': true,
          },
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(AppLocalizations.of(context).errorOccurred(e)),
          ),
        );
      }
    } finally {
      if (mounted) {
        setState(() => _isSubmitting = false);
      }
    }
  }

  Future<String> _createMember(String username, String password) async {
    final db = ref.read(appDatabaseProvider);
    final prefs = ref.read(planetPrefsProvider);
    final serverConfig = ref.read(serverConfigProvider);

    final id = '${DateTime.now().millisecondsSinceEpoch}';
    final joinDate = DateTime.now().millisecondsSinceEpoch;

    final user = UserRow(
      id: id,
      name: username,
      password: password,
      rolesList: const [],
      userAdmin: false,
      joinDate: joinDate,
      firstName: _firstNameController.text.trim().isEmpty
          ? null
          : _firstNameController.text.trim(),
      lastName: _lastNameController.text.trim().isEmpty
          ? null
          : _lastNameController.text.trim(),
      middleName: _middleNameController.text.trim().isEmpty
          ? null
          : _middleNameController.text.trim(),
      email: _emailController.text.trim().isEmpty
          ? null
          : _emailController.text.trim(),
      phoneNumber: _phoneController.text.trim().isEmpty
          ? null
          : _phoneController.text.trim(),
      level: _selectedLevel,
      language: _selectedLanguage,
      gender: _selectedGender,
      dob: _birthDate != null ? _formatDate(_birthDate!) : null,
      planetCode: serverConfig?.code,
      parentCode: serverConfig?.parentCode,
      isArchived: false,
      isUpdated: false,
    );

    await db.into(db.users).insertOnConflictUpdate(user);

    // The server upload is handled in _submit after this returns.
    // If it succeeds, the user can log in on other devices.
    // If it fails, the local account still works here.
    await prefs.setLoggedInUserId(id);
    await prefs.savePassword(password);

    return id;
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);

    return Scaffold(
      appBar: AppBar(title: Text(l10n.becomeMember)),
      body: _isSubmitting
          ? const Center(child: CircularProgressIndicator())
          : SingleChildScrollView(
              padding: const EdgeInsets.all(16),
              child: Form(
                key: _formKey,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    // Username
                    TextFormField(
                      controller: _usernameController,
                      decoration: InputDecoration(
                        labelText: l10n.enterUsername,
                        prefixIcon: const Icon(Icons.person_outline),
                        // Live, debounced result of `UserRepository.validateUsername`
                        // (the field also has a submit-time validator below, so an
                        // empty field stays covered until submit).
                        errorText: _usernameError,
                        suffixIcon: _isValidatingUsername
                            ? const SizedBox(
                                width: 18,
                                height: 18,
                                child: Padding(
                                  padding: EdgeInsets.all(12),
                                  child: CircularProgressIndicator(
                                    strokeWidth: 2,
                                  ),
                                ),
                              )
                            : null,
                      ),
                      textInputAction: TextInputAction.next,
                      autofocus: true,
                      onChanged: (_) => _scheduleUsernameValidation(),
                      validator: (value) {
                        if (value == null || value.trim().isEmpty) {
                          return l10n.enterUsername;
                        }
                        // The debounced check owns the deep validation; the
                        // submit-time validator only guarantees non-empty so a
                        // fast tap does not slip through before the debounce
                        // fires. If a debounced error is already showing, hold
                        // the line and block submit.
                        if (_usernameError != null) return _usernameError;
                        return null;
                      },
                    ),
                    const SizedBox(height: 16),

                    // Password
                    TextFormField(
                      controller: _passwordController,
                      decoration: InputDecoration(
                        labelText: l10n.enterPassword,
                        prefixIcon: const Icon(Icons.lock_outline),
                      ),
                      obscureText: true,
                      textInputAction: TextInputAction.next,
                      validator: (value) {
                        if (value == null || value.isEmpty) {
                          return l10n.pleaseEnterPassword;
                        }
                        return null;
                      },
                    ),
                    const SizedBox(height: 16),

                    // Confirm Password
                    TextFormField(
                      controller: _rePasswordController,
                      decoration: InputDecoration(
                        labelText: l10n.retypePassword,
                        prefixIcon: const Icon(Icons.lock_outline),
                        errorText: _validatePasswords()
                            ? null
                            : l10n.passwordsDoNotMatch,
                      ),
                      obscureText: true,
                      textInputAction: TextInputAction.next,
                      validator: (value) {
                        if (value != _passwordController.text) {
                          return l10n.passwordsDoNotMatch;
                        }
                        return null;
                      },
                    ),
                    const SizedBox(height: 24),

                    // Name fields
                    Row(
                      children: [
                        Expanded(
                          child: TextFormField(
                            controller: _firstNameController,
                            decoration: InputDecoration(
                              labelText: l10n.firstName,
                            ),
                            textInputAction: TextInputAction.next,
                          ),
                        ),
                        const SizedBox(width: 8),
                        Expanded(
                          child: TextFormField(
                            controller: _middleNameController,
                            decoration: InputDecoration(
                              labelText: l10n.middleName,
                            ),
                            textInputAction: TextInputAction.next,
                          ),
                        ),
                        const SizedBox(width: 8),
                        Expanded(
                          child: TextFormField(
                            controller: _lastNameController,
                            decoration: InputDecoration(
                              labelText: l10n.lastName,
                            ),
                            textInputAction: TextInputAction.next,
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 16),

                    // Email
                    TextFormField(
                      controller: _emailController,
                      decoration: InputDecoration(
                        labelText: l10n.email,
                        prefixIcon: const Icon(Icons.email_outlined),
                      ),
                      keyboardType: TextInputType.emailAddress,
                      textInputAction: TextInputAction.next,
                      validator: (value) {
                        if (value != null &&
                            value.isNotEmpty &&
                            !RegExp(
                              r'^[^@\s]+@[^@\s]+\.[^@\s]+$',
                            ).hasMatch(value)) {
                          return l10n.invalidEmail;
                        }
                        return null;
                      },
                    ),
                    const SizedBox(height: 16),

                    // Language and Level
                    Row(
                      children: [
                        Expanded(
                          child: DropdownButtonFormField<String>(
                            initialValue: _selectedLanguage,
                            decoration: InputDecoration(
                              labelText: l10n.language,
                            ),
                            items: memberLanguages
                                .map(
                                  (lang) => DropdownMenuItem(
                                    value: lang,
                                    child: Text(lang),
                                  ),
                                )
                                .toList(),
                            onChanged: (value) {
                              if (value != null) {
                                setState(() => _selectedLanguage = value);
                              }
                            },
                          ),
                        ),
                        const SizedBox(width: 16),
                        Expanded(
                          child: DropdownButtonFormField<String>(
                            initialValue: _selectedLevel,
                            decoration: InputDecoration(labelText: l10n.level),
                            items: memberLevels
                                .map(
                                  (level) => DropdownMenuItem(
                                    value: level,
                                    child: Text(level),
                                  ),
                                )
                                .toList(),
                            onChanged: (value) {
                              if (value != null) {
                                setState(() => _selectedLevel = value);
                              }
                            },
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 16),

                    // Phone and Birth Date
                    Row(
                      children: [
                        Expanded(
                          child: TextFormField(
                            controller: _phoneController,
                            decoration: InputDecoration(
                              labelText: l10n.phone,
                              prefixIcon: const Icon(Icons.phone_outlined),
                            ),
                            keyboardType: TextInputType.phone,
                            textInputAction: TextInputAction.next,
                          ),
                        ),
                        const SizedBox(width: 16),
                        Expanded(
                          child: InkWell(
                            onTap: _selectBirthDate,
                            child: InputDecorator(
                              decoration: InputDecoration(
                                labelText: l10n.birthDate,
                                suffixIcon: const Icon(Icons.calendar_today),
                              ),
                              child: Text(
                                _birthDate != null
                                    ? _formatDate(_birthDate!)
                                    : l10n.selectDate,
                              ),
                            ),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 24),

                    // Gender
                    Text(
                      l10n.gender,
                      style: Theme.of(context).textTheme.titleSmall,
                    ),
                    const SizedBox(height: 8),
                    SegmentedButton<String>(
                      segments: [
                        ButtonSegment(value: 'male', label: Text(l10n.male)),
                        ButtonSegment(
                          value: 'female',
                          label: Text(l10n.female),
                        ),
                      ],
                      selected: {_selectedGender},
                      onSelectionChanged: (selection) {
                        setState(() => _selectedGender = selection.first);
                      },
                    ),
                    const SizedBox(height: 32),

                    // Buttons
                    Row(
                      mainAxisAlignment: MainAxisAlignment.end,
                      children: [
                        TextButton(
                          onPressed: () => context.pop(),
                          child: Text(l10n.cancel),
                        ),
                        const SizedBox(width: 16),
                        FilledButton(
                          onPressed: _submit,
                          child: Text(l10n.becomeMember),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
    );
  }
}
