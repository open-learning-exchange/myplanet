import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../l10n/app_localizations.dart';
import '../../providers/session_provider.dart';

/// Port of `ui/health/AddHealthActivity.kt`.
///
/// Form for adding or editing user health profile information
/// including emergency contacts and special needs.
class AddHealthScreen extends ConsumerStatefulWidget {
  const AddHealthScreen({super.key});

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
  final _birthplaceController = TextEditingController();
  final _emergencyNameController = TextEditingController();
  final _emergencyContactController = TextEditingController();
  final _specialNeedsController = TextEditingController();
  final _otherNeedsController = TextEditingController();
  int _contactTypeIndex = 0;

  bool _isLoading = false;
  bool _isSaved = false;

  final List<String> _contactTypes = [
    'Parent',
    'Spouse',
    'Sibling',
    'Child',
    'Friend',
    'Other',
  ];

  @override
  void initState() {
    super.initState();
    _loadUserData();
  }

  Future<void> _loadUserData() async {
    final session = ref.read(sessionProvider);
    final user = session.valueOrNull;
    if (user == null) return;

    setState(() {
      _fnameController.text = user.firstName ?? '';
      _mnameController.text = user.middleName ?? '';
      _lnameController.text = user.lastName ?? '';
      _emailController.text = user.email ?? '';
      _phoneController.text = user.phoneNumber ?? '';
      _dobController.text = user.dob ?? '';
      _birthplaceController.text = user.birthPlace ?? '';
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
    _birthplaceController.dispose();
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
      appBar: AppBar(
        title: Text(l10n.updateHealth),
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : Form(
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
                  _buildTextField(
                    controller: _lnameController,
                    label: l10n.lastName,
                  ),
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
                    controller: _birthplaceController,
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
                    onPressed: _isSaved ? null : _saveHealthData,
                    child: Text(l10n.save),
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
        value: value,
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
    final l10n = AppLocalizations.of(context);
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

    setState(() => _isLoading = true);

    try {
      // In a full implementation, this would save to the database
      // For now, just simulate a save
      await Future.delayed(const Duration(seconds: 1));

      if (!mounted) return;
      setState(() => _isSaved = true);

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
        setState(() => _isLoading = false);
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
