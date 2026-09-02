import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../../core/files/achievement_files.dart';
import '../../core/system/file_pick.dart';
import '../../l10n/app_localizations.dart';
import '../../providers/achievements_provider.dart';
import '../../providers/app_providers.dart';
import '../../providers/session_provider.dart';
import '../../repository/achievements_repository.dart';
import '../../repository/resources_repository.dart';
import 'cv_viewer_dialog.dart';

/// Port of `EditAchievementFragment`.
///
/// One form edits both the user row's profile fields (first/last/middle name,
/// birth date and place) and the achievements ledger row (header, goals,
/// purpose, entries, references, resume name). The Kotlin hides save behind
/// `btnUpdate` validation that lists the missing required fields; this keeps
/// the same rule — first name, last name and birth date are mandatory.
class EditAchievementScreen extends ConsumerStatefulWidget {
  const EditAchievementScreen({super.key});

  @override
  ConsumerState<EditAchievementScreen> createState() =>
      _EditAchievementScreenState();
}

class _EditAchievementScreenState extends ConsumerState<EditAchievementScreen> {
  final _formKey = GlobalKey<FormState>();

  final _firstName = TextEditingController();
  final _middleName = TextEditingController();
  final _lastName = TextEditingController();
  final _birthPlace = TextEditingController();
  final _achievementsHeader = TextEditingController();
  final _purpose = TextEditingController();
  final _goals = TextEditingController();

  String? _dobIso;
  bool _sendToNation = false;

  List<Map<String, dynamic>> _achievementEntries = const [];
  List<Map<String, dynamic>> _referenceEntries = const [];

  String _resumeFileName = '';
  String? _pickedCvName;
  List<int>? _pickedCvBytes;
  bool _deleteCv = false;

  bool _initialized = false;
  bool _saving = false;

  @override
  void dispose() {
    _firstName.dispose();
    _middleName.dispose();
    _lastName.dispose();
    _birthPlace.dispose();
    _achievementsHeader.dispose();
    _purpose.dispose();
    _goals.dispose();
    super.dispose();
  }

  void _initialize() {
    if (_initialized) return;
    final user = ref.read(sessionProvider).valueOrNull;
    final row = ref.read(achievementEntryProvider).valueOrNull;
    if (user == null || row == null) return;
    _firstName.text = user.firstName ?? '';
    _middleName.text = user.middleName ?? '';
    _lastName.text = user.lastName ?? '';
    _birthPlace.text = user.birthPlace ?? '';
    _dobIso = (user.dob ?? '').isEmpty ? null : user.dob;
    _achievementsHeader.text = row.achievementsHeader;
    _purpose.text = row.purpose;
    _goals.text = row.goals;
    _sendToNation = row.sendToNation;
    _achievementEntries = AchievementsRepository.achievementsArray(
      row.achievementsJson,
    );
    _referenceEntries = AchievementsRepository.referencesArray(
      row.referencesJson,
    );
    _resumeFileName = row.resumeFileName;
    _initialized = true;
  }

  Future<void> _pickDob() async {
    final now = DateTime.now();
    final picked = await showDatePicker(
      context: context,
      initialDate: now,
      firstDate: DateTime(1900),
      lastDate: now,
    );
    if (picked == null) return;
    setState(() {
      _dobIso =
          '${picked.year.toString().padLeft(4, '0')}-'
          '${picked.month.toString().padLeft(2, '0')}-'
          '${picked.day.toString().padLeft(2, '0')}';
    });
  }

  Future<void> _pickCv(AppLocalizations l10n) async {
    final file = await FilePick.instance.pickSingle();
    if (file == null) return;
    // Kotlin checks the display name's suffix before it reads a byte, so a
    // wrong pick costs nothing.
    if (file.extension?.toLowerCase() != 'pdf' &&
        !file.name.toLowerCase().endsWith('.pdf')) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(l10n.selectPdfOnly)));
      }
      return;
    }
    final bytes = await file.readBytes();
    if (!mounted) return;
    setState(() {
      _pickedCvName = file.name;
      _pickedCvBytes = bytes;
      _deleteCv = false;
    });
  }

  void _toast(String message) {
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(SnackBar(content: Text(message)));
  }

  Future<void> _save(AppLocalizations l10n) async {
    final missing = <String>[
      if (_firstName.text.trim().isEmpty) l10n.firstName,
      if (_lastName.text.trim().isEmpty) l10n.lastName,
      if (_dobIso == null || _dobIso!.isEmpty) l10n.birthDate,
    ];
    if (missing.isNotEmpty) {
      _toast(l10n.fillRequiredFields(missing.join(', ')));
      return;
    }
    final user = ref.read(sessionProvider).valueOrNull;
    if (user == null) return;
    setState(() => _saving = true);
    try {
      var resumeFileName = _deleteCv ? '' : _resumeFileName;
      if (_pickedCvName != null && _pickedCvBytes != null) {
        try {
          await AchievementFiles.write(
            resumeFileName: _pickedCvName!,
            bytes: _pickedCvBytes!,
          );
          resumeFileName = _pickedCvName!;
        } catch (_) {
          // `computeCvFilename` catches and toasts; the row keeps the old
          // name.
        }
      }
      await ref
          .read(achievementActionsProvider)
          .save(
            input: AchievementInput(
              // `btnUpdate` trims the header, goals and purpose too, not just
              // the name fields.
              purpose: _purpose.text.trim(),
              goals: _goals.text.trim(),
              achievementsHeader: _achievementsHeader.text.trim(),
              sendToNation: _sendToNation,
              achievementsJson: jsonEncode(_achievementEntries),
              referencesJson: jsonEncode(_referenceEntries),
              createdOn: user.planetCode ?? '',
              username: user.name ?? '',
              parentCode: user.parentCode ?? '',
              resumeFileName: resumeFileName,
            ),
            firstName: _firstName.text.trim(),
            lastName: _lastName.text.trim(),
            middleName: _middleName.text.trim(),
            birthPlace: _birthPlace.text.trim(),
            birthDate: _dobIso,
          );
      ref.invalidate(achievementEntryProvider);
      if (!mounted) return;
      _toast(l10n.achievementSaved);
      context.pop();
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  Future<void> _addAchievement([Map<String, dynamic>? existing]) async {
    final edited = await showAchievementDialog(context, existing);
    if (edited == null) return;
    setState(() {
      if (existing == null) {
        _achievementEntries = [..._achievementEntries, edited];
      } else {
        _achievementEntries = [
          for (final entry in _achievementEntries)
            identical(entry, existing) ? edited : entry,
        ];
      }
    });
  }

  Future<void> _addReference([Map<String, dynamic>? existing]) async {
    final edited = await showReferenceDialog(context, existing);
    if (edited == null) return;
    setState(() {
      if (existing == null) {
        _referenceEntries = [..._referenceEntries, edited];
      } else {
        _referenceEntries = [
          for (final entry in _referenceEntries)
            identical(entry, existing) ? edited : entry,
        ];
      }
    });
  }

  /// The stored resume only. A fresh pick has no bytes on disk yet — the
  /// Kotlin picker callback hides `llCurrentCv` outright and
  /// `computeCvFilename` copies the file at save time — and writing them here
  /// to have something to show left a file under `<base>/ole/cv/` that no row
  /// names when the edit was then cancelled.
  Future<void> _viewCv() async {
    final name = _resumeFileName;
    if (name.isEmpty) return;
    if (!await AchievementFiles.hasResume(name)) {
      // `btnViewCvEdit`'s else branch: the Kotlin says so rather than doing
      // nothing. Its string names the file (`file_not_found` takes a `%s`);
      // the port's key has no placeholder.
      if (mounted) _toast(AppLocalizations.of(context).fileNotFound);
      return;
    }
    if (!mounted) return;
    await showCvViewerDialog(context, name);
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final entry = ref.watch(achievementEntryProvider);
    if (!_initialized && entry.valueOrNull != null) {
      _initialize();
    }
    // `tvCvFilename` names whatever is pending; `llCurrentCv` — the row
    // carrying View/Delete — is for the stored resume, and the pick callback
    // hides it (`EditAchievementFragment:92`).
    final pendingCvName = _deleteCv ? '' : (_pickedCvName ?? _resumeFileName);
    final storedCv = _deleteCv || _pickedCvName != null ? '' : _resumeFileName;

    return Scaffold(
      appBar: AppBar(title: Text(l10n.editAchievement)),
      body: _saving
          ? Center(child: Text(l10n.saving))
          : Form(
              key: _formKey,
              child: ListView(
                padding: const EdgeInsets.all(16),
                children: [
                  TextFormField(
                    controller: _firstName,
                    decoration: InputDecoration(
                      labelText: l10n.firstName,
                      prefixIcon: const Icon(Icons.person_outline),
                    ),
                  ),
                  const SizedBox(height: 8),
                  TextFormField(
                    controller: _middleName,
                    decoration: InputDecoration(labelText: l10n.middleName),
                  ),
                  const SizedBox(height: 8),
                  TextFormField(
                    controller: _lastName,
                    decoration: InputDecoration(
                      labelText: l10n.lastName,
                      prefixIcon: const Icon(Icons.person_outline),
                    ),
                  ),
                  const SizedBox(height: 8),
                  ListTile(
                    contentPadding: EdgeInsets.zero,
                    title: Text(
                      achievementBirthDate(_dobIso) ?? l10n.birthDate,
                    ),
                    trailing: const Icon(Icons.calendar_month),
                    onTap: _pickDob,
                  ),
                  TextFormField(
                    controller: _birthPlace,
                    decoration: InputDecoration(labelText: l10n.birthPlace),
                  ),
                  const SizedBox(height: 8),
                  TextFormField(
                    controller: _achievementsHeader,
                    decoration: InputDecoration(
                      labelText: l10n.noAchievementAdded,
                      prefixIcon: const Icon(Icons.star_border),
                    ),
                  ),
                  const SizedBox(height: 8),
                  TextFormField(
                    controller: _purpose,
                    decoration: InputDecoration(labelText: l10n.myPurpose),
                  ),
                  const SizedBox(height: 8),
                  TextFormField(
                    controller: _goals,
                    decoration: InputDecoration(labelText: l10n.myGoals),
                  ),
                  SwitchListTile(
                    contentPadding: EdgeInsets.zero,
                    title: Text(l10n.sendToNation),
                    value: _sendToNation,
                    onChanged: (v) => setState(() => _sendToNation = v),
                  ),
                  const Divider(),
                  Text(l10n.addMaterials),
                  const SizedBox(height: 8),
                  OutlinedButton.icon(
                    icon: const Icon(Icons.add),
                    label: Text(l10n.addAnAchievement),
                    onPressed: () => _addAchievement(),
                  ),
                  for (final entry in _achievementEntries)
                    ListTile(
                      dense: true,
                      title: Text('${entry['title'] ?? ''}'),
                      // `showAchievementAndInfo` inflates a chip per attached
                      // resource into the row's flexbox, so the card says what
                      // the entry carries without reopening the dialog.
                      subtitle: () {
                        final attached = AchievementsRepository.resourcesOf(
                          entry,
                        );
                        if (attached.isEmpty) return null;
                        return Wrap(
                          spacing: 4,
                          children: [
                            for (final resource in attached)
                              Chip(
                                label: Text('${resource['title'] ?? ''}'),
                                visualDensity: VisualDensity.compact,
                              ),
                          ],
                        );
                      }(),
                      trailing: Wrap(
                        children: [
                          IconButton(
                            icon: const Icon(Icons.edit),
                            tooltip: l10n.edit,
                            onPressed: () => _addAchievement(entry),
                          ),
                          // `EditAttachementBinding.ivDelete` — without it an
                          // entry added by mistake can never be removed.
                          IconButton(
                            icon: const Icon(Icons.delete_outline),
                            tooltip: l10n.delete,
                            onPressed: () => setState(() {
                              _achievementEntries = [
                                for (final other in _achievementEntries)
                                  if (!identical(other, entry)) other,
                              ];
                            }),
                          ),
                        ],
                      ),
                    ),
                  const Divider(),
                  Text(l10n.labelAddReferences),
                  const SizedBox(height: 8),
                  OutlinedButton.icon(
                    icon: const Icon(Icons.add),
                    label: Text(l10n.addAReference),
                    onPressed: () => _addReference(),
                  ),
                  for (final entry in _referenceEntries)
                    ListTile(
                      dense: true,
                      title: Text('${entry['name'] ?? ''}'),
                      trailing: Wrap(
                        children: [
                          IconButton(
                            icon: const Icon(Icons.edit),
                            tooltip: l10n.edit,
                            onPressed: () => _addReference(entry),
                          ),
                          // `EditOtherInfoBinding.ivDelete`.
                          IconButton(
                            icon: const Icon(Icons.delete_outline),
                            tooltip: l10n.delete,
                            onPressed: () => setState(() {
                              _referenceEntries = [
                                for (final other in _referenceEntries)
                                  if (!identical(other, entry)) other,
                              ];
                            }),
                          ),
                        ],
                      ),
                    ),
                  const Divider(),
                  Text(l10n.uploadCvLabel),
                  const SizedBox(height: 8),
                  if (storedCv.isNotEmpty)
                    ListTile(
                      contentPadding: EdgeInsets.zero,
                      title: Text(l10n.currentCv(storedCv)),
                      trailing: Wrap(
                        children: [
                          TextButton(
                            onPressed: _viewCv,
                            child: Text(l10n.viewCv),
                          ),
                          TextButton(
                            onPressed: () => setState(() {
                              _pickedCvName = null;
                              _pickedCvBytes = null;
                              _deleteCv = true;
                            }),
                            child: Text(l10n.deleteCv),
                          ),
                        ],
                      ),
                    ),
                  Text(
                    pendingCvName.isEmpty ? l10n.noFileChosen : pendingCvName,
                  ),
                  OutlinedButton.icon(
                    icon: const Icon(Icons.upload_file),
                    label: Text(l10n.chooseFile),
                    onPressed: () => _pickCv(l10n),
                  ),
                  const SizedBox(height: 16),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                    children: [
                      OutlinedButton(
                        onPressed: () => context.pop(),
                        child: Text(l10n.cancel),
                      ),
                      FilledButton(
                        onPressed: () => _save(l10n),
                        child: Text(l10n.update),
                      ),
                    ],
                  ),
                ],
              ),
            ),
    );
  }
}

/// The `showAddAchievementAlert` dialog — title (required), description,
/// date and link fields, and the multi-select resource list built from the
/// whole resources table.
Future<Map<String, dynamic>?> showAchievementDialog(
  BuildContext context, [
  Map<String, dynamic>? existing,
]) async {
  final l10n = AppLocalizations.of(context);
  final title = TextEditingController(text: '${existing?['title'] ?? ''}');
  final description = TextEditingController(
    text: '${existing?['description'] ?? ''}',
  );
  final link = TextEditingController(text: '${existing?['link'] ?? ''}');
  var date = existing?['date'] as String? ?? '';
  // Kotlin's `resourceArray` holds `list[ii].serializeResource()` — the whole
  // resource document, not just its title, which is what the achievement
  // document carries to the server.
  final resources = [
    ...AchievementsRepository.resourcesOf(existing ?? const {}),
  ];
  return showDialog<Map<String, dynamic>>(
    context: context,
    builder: (dialogContext) => StatefulBuilder(
      builder: (context, setDialogState) {
        Future<void> pickDate() async {
          final now = DateTime.now();
          final picked = await showDatePicker(
            context: context,
            initialDate: now,
            firstDate: DateTime(1900),
            // `dpd.datePicker.maxDate = now.timeInMillis` — an achievement is
            // something that already happened.
            lastDate: now,
          );
          if (picked == null) return;
          setDialogState(() {
            date =
                '${picked.year.toString().padLeft(4, '0')}-'
                '${picked.month.toString().padLeft(2, '0')}-'
                '${picked.day.toString().padLeft(2, '0')}';
          });
        }

        Future<void> pickResources() async {
          final container = ProviderScope.containerOf(context);
          final list = await container
              .read(resourcesRepositoryProvider)
              .getAllLibraries();
          if (!context.mounted) return;
          final selected = await showDialog<List<String>>(
            context: context,
            builder: (context) {
              final checked = {
                for (final resource in resources) '${resource['title'] ?? ''}',
              };
              return StatefulBuilder(
                builder: (context, setInner) => AlertDialog(
                  title: Text(l10n.selectResources),
                  content: SizedBox(
                    width: double.maxFinite,
                    child: ListView(
                      shrinkWrap: true,
                      children: [
                        for (final lib in list)
                          CheckboxListTile(
                            title: Text(lib.title ?? ''),
                            value: checked.contains(lib.title ?? ''),
                            onChanged: (v) => setInner(() {
                              if (v ?? false) {
                                checked.add(lib.title ?? '');
                              } else {
                                checked.remove(lib.title ?? '');
                              }
                            }),
                          ),
                      ],
                    ),
                  ),
                  actions: [
                    TextButton(
                      onPressed: () => Navigator.pop(context),
                      child: Text(l10n.cancel),
                    ),
                    TextButton(
                      onPressed: () => Navigator.pop(context, checked.toList()),
                      child: Text(l10n.submit),
                    ),
                  ],
                ),
              );
            },
          );
          if (selected != null) {
            // Rebuilt from the library rows, like `showResourceListDialog`'s
            // positive button — anything no longer in the list is dropped.
            setDialogState(() {
              resources
                ..clear()
                ..addAll([
                  for (final lib in list)
                    if (selected.contains(lib.title ?? ''))
                      ResourcesRepository.serializeResource(lib),
                ]);
            });
          }
        }

        return AlertDialog(
          title: Text(l10n.addAchievement),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                TextField(
                  controller: title,
                  decoration: InputDecoration(labelText: l10n.title),
                ),
                TextField(
                  controller: description,
                  decoration: InputDecoration(labelText: l10n.description),
                ),
                ListTile(
                  contentPadding: EdgeInsets.zero,
                  title: Text(date.isEmpty ? l10n.date : date),
                  trailing: const Icon(Icons.calendar_month),
                  onTap: pickDate,
                ),
                TextField(
                  controller: link,
                  decoration: InputDecoration(labelText: l10n.link),
                ),
                OutlinedButton(
                  onPressed: pickResources,
                  child: Text(l10n.selectResources),
                ),
                Wrap(
                  spacing: 4,
                  children: [
                    for (final resource in resources)
                      InputChip(
                        label: Text('${resource['title'] ?? ''}'),
                        onDeleted: () =>
                            setDialogState(() => resources.remove(resource)),
                      ),
                  ],
                ),
              ],
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(dialogContext),
              child: Text(l10n.cancel),
            ),
            TextButton(
              onPressed: () {
                if (title.text.trim().isEmpty) {
                  ScaffoldMessenger.of(
                    context,
                  ).showSnackBar(SnackBar(content: Text(l10n.titleIsRequired)));
                  return;
                }
                Navigator.pop(dialogContext, <String, dynamic>{
                  'title': title.text.trim(),
                  'description': description.text.trim(),
                  'date': date,
                  'link': link.text.trim(),
                  'resources': [...resources],
                });
              },
              child: Text(l10n.submit),
            ),
          ],
        );
      },
    ),
  );
}

/// The `showReferenceDialog` — name (required), relationship, phone, email.
Future<Map<String, dynamic>?> showReferenceDialog(
  BuildContext context, [
  Map<String, dynamic>? existing,
]) async {
  final l10n = AppLocalizations.of(context);
  final name = TextEditingController(text: '${existing?['name'] ?? ''}');
  final relationship = TextEditingController(
    text: '${existing?['relationship'] ?? ''}',
  );
  final phone = TextEditingController(text: '${existing?['phone'] ?? ''}');
  final email = TextEditingController(text: '${existing?['email'] ?? ''}');
  return showDialog<Map<String, dynamic>>(
    context: context,
    builder: (dialogContext) => AlertDialog(
      title: Text(l10n.addReference),
      content: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
              controller: name,
              decoration: InputDecoration(labelText: l10n.name),
            ),
            TextField(
              controller: relationship,
              decoration: InputDecoration(labelText: l10n.relationship),
            ),
            TextField(
              controller: phone,
              decoration: InputDecoration(labelText: l10n.phone),
            ),
            TextField(
              controller: email,
              decoration: InputDecoration(labelText: l10n.email),
            ),
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(dialogContext),
          child: Text(l10n.cancel),
        ),
        TextButton(
          onPressed: () {
            if (name.text.trim().isEmpty) {
              ScaffoldMessenger.of(
                dialogContext,
              ).showSnackBar(SnackBar(content: Text(l10n.nameIsRequired)));
              return;
            }
            Navigator.pop(dialogContext, <String, dynamic>{
              'name': name.text.trim(),
              'phone': phone.text,
              'relationship': relationship.text,
              'email': email.text,
            });
          },
          child: Text(l10n.submit),
        ),
      ],
    ),
  );
}

/// The birth-date row's label.
///
/// Port of `getFormattedDate(user?.dob, "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")`,
/// which runs the stored value through `TimeUtils`'s default formatter
/// (`EEEE, MMM dd, yyyy`). The row used to print the column verbatim, so a
/// synced profile showed `1990-05-02T00:00:00.000Z` in the field.
///
/// Returns null when nothing is stored — the caller shows the `birth_date`
/// placeholder there, as `populateAchievementData` does — and the Kotlin's
/// `"N/A"` for a value that will not parse.
String? achievementBirthDate(String? stored) {
  if (stored == null || stored.trim().isEmpty) return null;
  final parsed = DateTime.tryParse(stored.trim());
  if (parsed == null) return 'N/A';
  // No zone conversion: `DateFormat` reads the instant's own field values, and
  // a `...Z` value parses to UTC, so the day the server stored is the day
  // shown.
  return DateFormat('EEEE, MMM dd, yyyy').format(parsed);
}
