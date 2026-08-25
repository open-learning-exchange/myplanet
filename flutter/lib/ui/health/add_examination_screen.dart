import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../l10n/app_localizations.dart';
import '../../providers/health_provider.dart';
import '../../providers/session_provider.dart';

/// Port of `ui/health/HealthExaminationActivity.kt`.
///
/// Form for adding or editing a health examination record with vital signs
/// and examination details.
class AddExaminationScreen extends ConsumerStatefulWidget {
  const AddExaminationScreen({super.key, this.examinationId});

  final String? examinationId;

  @override
  ConsumerState<AddExaminationScreen> createState() =>
      _AddExaminationScreenState();
}

class _AddExaminationScreenState extends ConsumerState<AddExaminationScreen> {
  final _formKey = GlobalKey<FormState>();

  // Vital signs controllers
  final _temperatureController = TextEditingController();
  final _pulseController = TextEditingController();
  final _bpController = TextEditingController();
  final _heightController = TextEditingController();
  final _weightController = TextEditingController();
  final _visionController = TextEditingController();
  final _hearingController = TextEditingController();

  // Examination details controllers
  final _allergiesController = TextEditingController();
  final _diagnosisController = TextEditingController();
  final _medicationsController = TextEditingController();
  final _immunizationController = TextEditingController();
  final _treatmentsController = TextEditingController();
  final _observationController = TextEditingController();
  final _referralsController = TextEditingController();
  final _labTestController = TextEditingController();
  final _xrayController = TextEditingController();

  // Conditions checkboxes
  final Map<String, bool> _conditions = {};

  // Custom diagnoses added by the user — port of Kotlin's `customDiag`.
  final Set<String> _customDiagnoses = {};
  final _otherDiagnosisController = TextEditingController();

  bool _selfExamination = false;
  bool _isSaving = false;

  // Conditions checkboxes — port of Kotlin's R.array.diagnosis_list.
  static const List<String> _conditionOptions = [
    'Acute Otitis Media',
    'Acute Respiratory Infection',
    'Amebiasis',
    'Brucellosis',
    'Cancer',
    'Cardiovascular disorders',
    'Chagas Disease',
    'Chancroid',
    'Chikungunya',
    'Chlamydia',
    'Chronic Kidney Disease',
    'Cirrhosis of the liver',
    'Conjunctivitis',
    'COVID-19',
    'Cryptosporidiosis',
    'Dental Caries',
    'Dengue',
    'Diabetes',
    'Diarrhoea diseases',
    'Ebola',
    'Emphysema',
    'Epilepsy',
    'FGM',
    'Fungal Infection',
    'Giardiasis',
    'Gonorrhea',
    'Heatstroke',
    'Hepatitis A',
    'Hepatitis B',
    'Hepatitis C',
    'Herpes Simplex Virus',
    'HIV/AIDS',
    'Human Papillomavirus',
    'Hypertension',
    'Influenza',
    'Iron-Deficiency Anemia',
    'Ischaemic heart disease',
    'Iodine Deficiency',
    'Leishmaniasis',
    'Leptospirosis',
    'Low Birth Weight',
    'Lymphogranuloma Venereum',
    'Malaria',
    'Malnutrition',
    'Maternal Hemorrhage',
    'Measles',
    'Meningitis',
    'Mycoplasma genitalium',
    'Neonatal sepsis and infections',
    'Obesity',
    'Pneumonia',
    'Preeclampsia/Eclampsia',
    'Preterm Birth Complications',
    'Rabies',
    'Rotavirus',
    'Scabies',
    'Schistosomiasis',
    'Soil-Transmitted Helminths',
    'Stroke',
    'Syphilis',
    'Trauma',
    'Trichomoniasis',
    'Tuberculosis',
    'Typhoid Fever',
    'Vitamin A Deficiency',
    'Zika',
  ];

  @override
  void initState() {
    super.initState();
    for (final condition in _conditionOptions) {
      _conditions[condition] = false;
    }
    _loadExistingData();
  }

  Future<void> _loadExistingData() async {
    final session = ref.read(sessionProvider);
    final user = session.valueOrNull;
    if (user == null) return;

    final params = (userId: user.id, examId: widget.examinationId);

    final state = ref.read(examinationNotifierProvider(params));

    if (state.examination != null) {
      final exam = state.examination!;
      setState(() {
        _temperatureController.text = exam.temperature > 0
            ? exam.temperature.toString()
            : '';
        _pulseController.text = exam.pulse > 0 ? exam.pulse.toString() : '';
        _bpController.text = exam.bp ?? '';
        _heightController.text = exam.height > 0 ? exam.height.toString() : '';
        _weightController.text = exam.weight > 0 ? exam.weight.toString() : '';
        _visionController.text = exam.vision ?? '';
        _hearingController.text = exam.hearing ?? '';
        _selfExamination = exam.selfExamination;
      });
    }

    // Load exam data if available
    if (state.examData != null) {
      final data = state.examData!;
      setState(() {
        _allergiesController.text = data.allergies ?? '';
        _diagnosisController.text = data.diagnosis ?? '';
        _medicationsController.text = data.medications ?? '';
        _immunizationController.text = data.immunizations ?? '';
        _treatmentsController.text = data.treatments ?? '';
        _observationController.text = data.notes ?? '';
        _referralsController.text = data.referrals ?? '';
        _labTestController.text = data.tests ?? '';
        _xrayController.text = data.xrays ?? '';
      });
    }

    // Load conditions — port of Kotlin's preloadCustomDiagnosis: any
    // condition that is checked but not in the standard list becomes a
    // custom diagnosis chip.
    for (final entry in state.conditions.entries) {
      if (_conditions.containsKey(entry.key)) {
        setState(() => _conditions[entry.key] = entry.value);
      } else if (entry.value) {
        setState(() => _customDiagnoses.add(entry.key));
      }
    }
  }

  @override
  void dispose() {
    _temperatureController.dispose();
    _pulseController.dispose();
    _bpController.dispose();
    _heightController.dispose();
    _weightController.dispose();
    _visionController.dispose();
    _hearingController.dispose();
    _allergiesController.dispose();
    _diagnosisController.dispose();
    _medicationsController.dispose();
    _immunizationController.dispose();
    _treatmentsController.dispose();
    _observationController.dispose();
    _referralsController.dispose();
    _labTestController.dispose();
    _xrayController.dispose();
    _otherDiagnosisController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final session = ref.watch(sessionProvider);
    final user = session.valueOrNull;

    final params = (userId: user?.id, examId: widget.examinationId);

    final examState = ref.watch(examinationNotifierProvider(params));

    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, _) {
        if (didPop) return;
        _confirmExit(context);
      },
      child: Scaffold(
        appBar: AppBar(
          title: Text(l10n.addHealthRecord),
          actions: [
            if (_selfExamination)
              Padding(
                padding: const EdgeInsets.all(8.0),
                child: Chip(label: Text(l10n.selfExamination)),
              ),
          ],
        ),
        body: examState.isLoading
            ? const Center(child: CircularProgressIndicator())
            : Form(
                key: _formKey,
                child: ListView(
                  padding: const EdgeInsets.all(16),
                  children: [
                    // Vital Signs Section
                    _SectionHeader(title: l10n.vitalSigns),
                    const SizedBox(height: 12),
                    Row(
                      children: [
                        Expanded(
                          child: _buildTextField(
                            controller: _temperatureController,
                            label: '${l10n.temperature} (°C)',
                            keyboardType: TextInputType.number,
                            validator: (value) {
                              if (value == null || value.isEmpty) return null;
                              final temp = double.tryParse(value);
                              if (temp == null) return l10n.invalidNumber;
                              if (temp < 30 || temp > 40) {
                                return l10n.tempRangeError;
                              }
                              return null;
                            },
                          ),
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          child: _buildTextField(
                            controller: _pulseController,
                            label: '${l10n.pulse} (bpm)',
                            keyboardType: TextInputType.number,
                            validator: (value) {
                              if (value == null || value.isEmpty) return null;
                              final pulse = int.tryParse(value);
                              if (pulse == null) return l10n.invalidNumber;
                              if (pulse < 40 || pulse > 120) {
                                return l10n.pulseRangeError;
                              }
                              return null;
                            },
                          ),
                        ),
                      ],
                    ),
                    _buildTextField(
                      controller: _bpController,
                      label: '${l10n.bloodPressure} (systolic/diastolic)',
                      hintText: '120/80',
                      validator: (value) {
                        if (value == null || value.isEmpty) return null;
                        if (!value.contains('/')) {
                          return l10n.bloodPressureFormatError;
                        }
                        final parts = value.trim().split('/');
                        if (parts.length != 2) {
                          return l10n.bloodPressureFormatError;
                        }
                        final sys = int.tryParse(parts[0]);
                        final dis = int.tryParse(parts[1]);
                        if (sys == null || dis == null) {
                          return l10n.bloodPressureNotNumbers;
                        }
                        if (sys < 60 || dis < 40 || sys > 300 || dis > 200) {
                          return l10n.bloodPressureRangeError;
                        }
                        return null;
                      },
                    ),
                    Row(
                      children: [
                        Expanded(
                          child: _buildTextField(
                            controller: _heightController,
                            label: '${l10n.height} (cm)',
                            keyboardType: TextInputType.number,
                            validator: (value) {
                              if (value == null || value.isEmpty) return null;
                              final height = double.tryParse(value);
                              if (height == null) return l10n.invalidNumber;
                              if (height < 1 || height > 250) {
                                return l10n.heightRangeError;
                              }
                              return null;
                            },
                          ),
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          child: _buildTextField(
                            controller: _weightController,
                            label: '${l10n.weight} (kg)',
                            keyboardType: TextInputType.number,
                            validator: (value) {
                              if (value == null || value.isEmpty) return null;
                              final weight = double.tryParse(value);
                              if (weight == null) return l10n.invalidNumber;
                              if (weight < 1 || weight > 150) {
                                return l10n.weightRangeError;
                              }
                              return null;
                            },
                          ),
                        ),
                      ],
                    ),
                    Row(
                      children: [
                        Expanded(
                          child: _buildTextField(
                            controller: _visionController,
                            label: l10n.vision,
                          ),
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          child: _buildTextField(
                            controller: _hearingController,
                            label: l10n.hearing,
                          ),
                        ),
                      ],
                    ),

                    const SizedBox(height: 24),

                    // Conditions Section
                    _SectionHeader(title: l10n.conditions),
                    const SizedBox(height: 8),
                    Wrap(
                      spacing: 8,
                      runSpacing: 8,
                      children: _conditionOptions.map((condition) {
                        return FilterChip(
                          label: Text(condition),
                          selected: _conditions[condition] ?? false,
                          onSelected: (selected) {
                            setState(() => _conditions[condition] = selected);
                          },
                        );
                      }).toList(),
                    ),

                    const SizedBox(height: 16),

                    // Other Diagnosis — port of Kotlin's ChipCloud.
                    _SectionHeader(title: l10n.otherDiagnosis),
                    const SizedBox(height: 8),
                    Row(
                      children: [
                        Expanded(
                          child: TextField(
                            controller: _otherDiagnosisController,
                            decoration: InputDecoration(
                              labelText: l10n.otherDiagnosis,
                              border: const OutlineInputBorder(),
                              isDense: true,
                            ),
                            onSubmitted: (_) => _addCustomDiagnosis(),
                          ),
                        ),
                        const SizedBox(width: 8),
                        FilledButton.tonal(
                          onPressed: _addCustomDiagnosis,
                          child: Text(l10n.add),
                        ),
                      ],
                    ),
                    if (_customDiagnoses.isNotEmpty) ...[
                      const SizedBox(height: 8),
                      Wrap(
                        spacing: 8,
                        runSpacing: 4,
                        children: _customDiagnoses.map((diagnosis) {
                          return Chip(
                            label: Text(diagnosis),
                            onDeleted: () {
                              setState(
                                () => _customDiagnoses.remove(diagnosis),
                              );
                            },
                          );
                        }).toList(),
                      ),
                    ],

                    const SizedBox(height: 24),

                    // Examination Details Section
                    _SectionHeader(title: l10n.examinationDetails),
                    const SizedBox(height: 12),
                    _buildTextField(
                      controller: _allergiesController,
                      label: l10n.allergies,
                      maxLines: 2,
                    ),
                    _buildTextField(
                      controller: _diagnosisController,
                      label: l10n.diagnosis,
                      maxLines: 2,
                    ),
                    _buildTextField(
                      controller: _medicationsController,
                      label: l10n.medications,
                      maxLines: 2,
                    ),
                    _buildTextField(
                      controller: _immunizationController,
                      label: l10n.immunizations,
                      maxLines: 2,
                    ),
                    _buildTextField(
                      controller: _treatmentsController,
                      label: l10n.treatments,
                      maxLines: 2,
                    ),
                    _buildTextField(
                      controller: _observationController,
                      label: l10n.observations,
                      maxLines: 2,
                    ),
                    _buildTextField(
                      controller: _referralsController,
                      label: l10n.referrals,
                      maxLines: 2,
                    ),
                    _buildTextField(
                      controller: _labTestController,
                      label: l10n.labTests,
                      maxLines: 2,
                    ),
                    _buildTextField(
                      controller: _xrayController,
                      label: l10n.xrays,
                      maxLines: 2,
                    ),

                    const SizedBox(height: 32),

                    FilledButton(
                      onPressed: _isSaving ? null : _saveExamination,
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
      ),
    );
  }

  Widget _buildTextField({
    required TextEditingController controller,
    required String label,
    String? hintText,
    TextInputType? keyboardType,
    int maxLines = 1,
    String? Function(String?)? validator,
  }) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: TextFormField(
        controller: controller,
        decoration: InputDecoration(
          labelText: label,
          hintText: hintText,
          border: const OutlineInputBorder(),
        ),
        keyboardType: keyboardType,
        maxLines: maxLines,
        validator: validator,
      ),
    );
  }

  void _addCustomDiagnosis() {
    final text = _otherDiagnosisController.text.trim();
    if (text.isEmpty) return;
    setState(() {
      _customDiagnoses.add(text);
      _otherDiagnosisController.clear();
    });
  }

  /// Port of `HealthExaminationActivity.finish()` — intercepts the back
  /// gesture and asks the user to confirm leaving, since the in-progress
  /// examination data would be lost.
  void _confirmExit(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    showDialog<void>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        content: Text(l10n.cancelAddingExamination),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(),
            child: Text(l10n.cancel),
          ),
          TextButton(
            onPressed: () {
              Navigator.of(dialogContext).pop();
              Navigator.of(context).pop();
            },
            child: Text(l10n.yesIWantToExit),
          ),
        ],
      ),
    );
  }

  Future<void> _saveExamination() async {
    if (!_formKey.currentState!.validate()) return;

    setState(() => _isSaving = true);

    try {
      final session = ref.read(sessionProvider);
      final user = session.valueOrNull;
      if (user == null) return;

      final params = (userId: user.id, examId: widget.examinationId);

      final temperature = double.tryParse(_temperatureController.text) ?? 0;
      final pulse = int.tryParse(_pulseController.text) ?? 0;
      final height = double.tryParse(_heightController.text) ?? 0;
      final weight = double.tryParse(_weightController.text) ?? 0;

      await ref
          .read(examinationNotifierProvider(params).notifier)
          .save(
            temperature: temperature,
            pulse: pulse,
            bp: _bpController.text.trim().isEmpty
                ? null
                : _bpController.text.trim(),
            height: height,
            weight: weight,
            vision: _visionController.text.trim().isEmpty
                ? null
                : _visionController.text.trim(),
            hearing: _hearingController.text.trim().isEmpty
                ? null
                : _hearingController.text.trim(),
            allergies: _allergiesController.text.trim().isEmpty
                ? null
                : _allergiesController.text.trim(),
            diagnosis: _diagnosisController.text.trim().isEmpty
                ? null
                : _diagnosisController.text.trim(),
            medications: _medicationsController.text.trim().isEmpty
                ? null
                : _medicationsController.text.trim(),
            immunizations: _immunizationController.text.trim().isEmpty
                ? null
                : _immunizationController.text.trim(),
            treatments: _treatmentsController.text.trim().isEmpty
                ? null
                : _treatmentsController.text.trim(),
            notes: _observationController.text.trim().isEmpty
                ? null
                : _observationController.text.trim(),
            referrals: _referralsController.text.trim().isEmpty
                ? null
                : _referralsController.text.trim(),
            tests: _labTestController.text.trim().isEmpty
                ? null
                : _labTestController.text.trim(),
            xrays: _xrayController.text.trim().isEmpty
                ? null
                : _xrayController.text.trim(),
            conditions: Map.fromEntries([
              ..._conditions.entries,
              for (final d in _customDiagnoses) MapEntry(d, true),
            ]),
          );

      if (!mounted) return;

      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(AppLocalizations.of(context).healthRecordAdded)),
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
