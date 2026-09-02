import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../l10n/app_localizations.dart';
import '../../core/utils/time_utils.dart';
import '../../providers/app_providers.dart';
import '../../providers/health_provider.dart';
import '../../providers/session_provider.dart';

/// Port of `ui/health/AddHealthActivity.kt`.
///
/// Form for adding or editing user health profile information
/// including emergency contacts and special needs. Loads the existing
/// profile (encrypted `data` blob on the examination row) and the user's
/// personal fields, and persists both via [HealthRepository.saveHealthProfile].
class AddHealthScreen extends ConsumerStatefulWidget {
  const AddHealthScreen({super.key, this.userId});

  /// The patient whose profile is being edited — `AddHealthActivity`'s
  /// `"userId"` intent extra, which `MyHealthFragment` fills with the
  /// *selected* patient. Null falls back to the signed-in user.
  final String? userId;

  @override
  ConsumerState<AddHealthScreen> createState() => _AddHealthScreenState();
}

class _AddHealthScreenState extends ConsumerState<AddHealthScreen> {
  final _formKey = GlobalKey<FormState>();
  final _fnameController = TextEditingController();
  final _mnameController = TextEditingController();
  final _lnameController = TextEditingController();
  final _emailController = TextEditingController();
  final _phoneController = TextEditingController();
  final _dobController = TextEditingController();
  final _birthPlaceController = TextEditingController();
  final _emergencyNameController = TextEditingController();
  final _emergencyContactController = TextEditingController();
  final _specialNeedsController = TextEditingController();
  final _otherNeedsController = TextEditingController();
  int _contactTypeIndex = 0;

  bool _isSaving = false;
  String? _patientId;

  /// Matches `R.array.contact_type` — the Kotlin spinner source.
  final List<String> _contactTypes = ['Phone', 'Email'];

  @override
  void initState() {
    super.initState();
    _loadUserData();
  }

  Future<void> _loadUserData() async {
    // `viewModel.loadHealthData(userId)` is keyed by the *patient*, so a
    // health provider editing the record they selected no longer loads — and
    // no longer saves over — their own profile.
    final session = await ref.read(sessionProvider.future);
    // `patientIdOf`, not `session.id` — see `patientIdOf`: `_id` wins, and
    // that is the id the record is stored under.
    final patientId =
        widget.userId ?? (session == null ? null : patientIdOf(session));
    if (patientId == null || patientId.isEmpty) return;
    if (mounted) setState(() => _patientId = patientId);

    final data = await ref.read(healthDataProvider(patientId).future);
    final user = data?.user;
    if (user == null) return;
    final health = data?.myHealth;

    if (!mounted) return;
    setState(() {
      _fnameController.text = user.firstName ?? '';
      _mnameController.text = user.middleName ?? '';
      _lnameController.text = user.lastName ?? '';
      _emailController.text = user.email ?? '';
      _phoneController.text = user.phoneNumber ?? '';
      _dobController.text = TimeUtils.formatDateToDDMMYYYY(user.dob);
      _birthPlaceController.text = user.birthPlace ?? '';

      final profile = health?.profile;
      _emergencyNameController.text = profile?.emergencyContactName ?? '';
      _emergencyContactController.text = profile?.emergencyContact ?? '';
      final typeIndex = _contactTypes.indexOf(
        profile?.emergencyContactType ?? '',
      );
      _contactTypeIndex = typeIndex >= 0 ? typeIndex : 0;
      _specialNeedsController.text = profile?.specialNeeds ?? '';
      _otherNeedsController.text = profile?.notes ?? '';
    });
  }

  @override
  void dispose() {
    _fnameController.dispose();
    _mnameController.dispose();
    _lnameController.dispose();
    _emailController.dispose();
    _phoneController.dispose();
    _dobController.dispose();
    _birthPlaceController.dispose();
    _emergencyNameController.dispose();
    _emergencyContactController.dispose();
    _specialNeedsController.dispose();
    _otherNeedsController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);

    return Scaffold(
      appBar: AppBar(title: Text(l10n.updateHealth)),
      body: Form(
        key: _formKey,
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            // Personal Information Section
            _SectionHeader(title: l10n.personalInformation),
            const SizedBox(height: 12),
            _buildTextField(
              controller: _fnameController,
              label: l10n.firstName,
              validator: (value) {
                if (value == null || value.trim().isEmpty) {
                  return l10n.requiredField;
                }
                return null;
              },
            ),
            _buildTextField(
              controller: _mnameController,
              label: l10n.middleName,
            ),
            _buildTextField(controller: _lnameController, label: l10n.lastName),
            _buildTextField(
              controller: _emailController,
              label: l10n.email,
              keyboardType: TextInputType.emailAddress,
            ),
            _buildTextField(
              controller: _phoneController,
              label: l10n.phone,
              keyboardType: TextInputType.phone,
            ),
            _buildTextField(
              controller: _dobController,
              label: l10n.birthDate,
              readOnly: true,
              suffixIcon: IconButton(
                icon: const Icon(Icons.calendar_today),
                onPressed: () => _selectDate(context),
              ),
            ),
            _buildTextField(
              controller: _birthPlaceController,
              label: l10n.birthPlace,
            ),

            const SizedBox(height: 24),

            // Emergency Contact Section
            _SectionHeader(title: l10n.emergencyContact),
            const SizedBox(height: 12),
            _buildTextField(
              controller: _emergencyNameController,
              label: l10n.contactName,
            ),
            _buildDropdownField(
              label: l10n.contactType,
              value: _contactTypeIndex,
              items: _contactTypes,
              onChanged: (value) {
                setState(() => _contactTypeIndex = value ?? 0);
              },
            ),
            _buildTextField(
              controller: _emergencyContactController,
              label: l10n.contactNumber,
              keyboardType: TextInputType.phone,
            ),

            const SizedBox(height: 24),

            // Health Information Section
            _SectionHeader(title: l10n.healthInformation),
            const SizedBox(height: 12),
            _buildTextField(
              controller: _specialNeedsController,
              label: l10n.specialNeeds,
              maxLines: 3,
            ),
            _buildTextField(
              controller: _otherNeedsController,
              label: l10n.otherNeeds,
              maxLines: 3,
            ),

            const SizedBox(height: 32),

            FilledButton(
              onPressed: _isSaving ? null : _saveHealthData,
              child: _isSaving
                  ? const SizedBox(
                      height: 20,
                      width: 20,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : Text(l10n.save),
            ),

            const SizedBox(height: 16),
          ],
        ),
      ),
    );
  }

  Widget _buildTextField({
    required TextEditingController controller,
    required String label,
    TextInputType? keyboardType,
    int maxLines = 1,
    bool readOnly = false,
    Widget? suffixIcon,
    String? Function(String?)? validator,
  }) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: TextFormField(
        controller: controller,
        decoration: InputDecoration(
          labelText: label,
          border: const OutlineInputBorder(),
          suffixIcon: suffixIcon,
        ),
        keyboardType: keyboardType,
        maxLines: maxLines,
        readOnly: readOnly,
        validator: validator,
      ),
    );
  }

  Widget _buildDropdownField({
    required String label,
    required int value,
    required List<String> items,
    required void Function(int?) onChanged,
  }) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: DropdownButtonFormField<int>(
        initialValue: value,
        decoration: InputDecoration(
          labelText: label,
          border: const OutlineInputBorder(),
        ),
        items: items.asMap().entries.map((entry) {
          return DropdownMenuItem<int>(
            value: entry.key,
            child: Text(entry.value),
          );
        }).toList(),
        onChanged: onChanged,
      ),
    );
  }

  Future<void> _selectDate(BuildContext context) async {
    final now = DateTime.now();
    final picked = await showDatePicker(
      context: context,
      initialDate: now,
      firstDate: DateTime(1900),
      lastDate: now,
    );
    if (picked != null) {
      setState(() {
        _dobController.text =
            '${picked.day.toString().padLeft(2, '0')}-${picked.month.toString().padLeft(2, '0')}-${picked.year}';
      });
    }
  }

  Future<void> _saveHealthData() async {
    if (!_formKey.currentState!.validate()) return;

    // The patient resolved when the form loaded; the signed-in user is only
    // its fallback, and `createMyHealth`'s `userId?.let { ... }` saves nothing
    // without one.
    final session = await ref.read(sessionProvider.future);
    final patientId =
        _patientId ??
        widget.userId ??
        (session == null ? null : patientIdOf(session));
    if (patientId == null || patientId.isEmpty) return;

    setState(() => _isSaving = true);

    try {
      final repo = ref.read(healthRepositoryProvider);
      await repo.saveHealthProfile(patientId, {
        'firstName': _fnameController.text,
        'middleName': _mnameController.text,
        'lastName': _lnameController.text,
        'email': _emailController.text,
        'dob': _dobController.text,
        'birthPlace': _birthPlaceController.text,
        'phoneNumber': _phoneController.text,
        'emergencyContactName': _emergencyNameController.text,
        'emergencyContact': _emergencyContactController.text,
        'emergencyContactType': _contactTypes[_contactTypeIndex],
        'specialNeeds': _specialNeedsController.text,
        'notes': _otherNeedsController.text,
      });

      // `MyHealthFragment.onResume` re-selects the patient when the editor
      // finishes, which is what puts the edit on screen. Invalidating
      // `healthDataProvider` alone did nothing for it: nothing watches that
      // provider — the screen renders `patientDetailProvider` — so the saved
      // profile stayed invisible until the next sync.
      ref.invalidate(healthDataProvider(patientId));
      await ref.read(patientDetailProvider.notifier).refresh();

      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(AppLocalizations.of(context).healthSavedSuccessfully),
        ),
      );
      context.pop();
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('${AppLocalizations.of(context).error}: $e')),
      );
    } finally {
      if (mounted) {
        setState(() => _isSaving = false);
      }
    }
  }
}

class _SectionHeader extends StatelessWidget {
  const _SectionHeader({required this.title});
  final String title;

  @override
  Widget build(BuildContext context) {
    return Text(
      title,
      style: Theme.of(context).textTheme.titleMedium?.copyWith(
        color: Theme.of(context).colorScheme.primary,
      ),
    );
  }
}
