import 'dart:io';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../data/local/app_database.dart';
import '../../l10n/app_localizations.dart';
import '../../providers/app_providers.dart';
import '../../providers/session_provider.dart';
import '../../repository/local_resource_request.dart';
import '../../repository/resources_repository.dart';

/// Port of `ui/resources/AddResourceActivity.kt` (the metadata form) and the
/// file-pick half of `AddResourceFragment` (the bottom sheet that picks a
/// source: a draft file from the filesystem). The audio/video/image capture
/// paths need platform channels the port has not built, so only the file-pick
/// path is ported — the most common one.
///
/// In create mode the screen picks a file first, then shows the metadata form.
/// In edit mode (reached from the resource detail screen) it prefills the
/// fields from the existing row and saves the update on submit.
class AddResourceScreen extends ConsumerStatefulWidget {
  const AddResourceScreen({this.teamId, this.editResourceId, super.key});

  /// When set, the resource is a private team resource scoped to this team.
  final String? teamId;

  /// When set, the screen opens in edit mode for this resource id.
  final String? editResourceId;

  @override
  ConsumerState<AddResourceScreen> createState() => _AddResourceScreenState();
}

class _AddResourceScreenState extends ConsumerState<AddResourceScreen> {
  final _titleController = TextEditingController();
  final _authorController = TextEditingController();
  final _yearController = TextEditingController();
  final _descriptionController = TextEditingController();
  final _publisherController = TextEditingController();
  final _licenseController = TextEditingController();

  String? _pickedFilePath;
  List<String> _subjects = [];
  List<String> _levels = [];
  final List<String> _resourceFor = [];
  String? _language;
  String? _openWith;
  String? _mediaType;
  String? _resourceType;
  bool _isPrivate = false;
  bool _saving = false;
  String? _titleError;

  bool get _isEditMode => widget.editResourceId != null;

  @override
  void initState() {
    super.initState();
    _yearController.text = DateTime.now().year.toString();
    _isPrivate = widget.teamId != null;
    if (_isEditMode) {
      _loadExistingResource();
    }
  }

  @override
  void dispose() {
    _titleController.dispose();
    _authorController.dispose();
    _yearController.dispose();
    _descriptionController.dispose();
    _publisherController.dispose();
    _licenseController.dispose();
    super.dispose();
  }

  Future<void> _loadExistingResource() async {
    final row = await ref
        .read(resourcesRepositoryProvider)
        .getById(widget.editResourceId!);
    if (row != null && mounted) {
      setState(() {
        _titleController.text = row.title ?? '';
        _authorController.text = row.author ?? '';
        // `prefillFields` sets the field to the stored year verbatim
        // (AddResourceActivity.kt:126). Falling back to this year here would
        // restamp a row whose year the server never carried.
        _yearController.text = row.year ?? '';
        _descriptionController.text = row.description ?? '';
        _publisherController.text = row.publisher ?? '';
        _licenseController.text = row.linkToLicense ?? '';
        _subjects = row.subject.toList();
        _levels = row.level.toList();
        _language = row.language;
        _openWith = row.openWith;
        _mediaType = row.mediaType;
        _resourceType = row.resourceType;
        _pickedFilePath = row.resourceLocalAddress;
      });
    }
  }

  Future<void> _pickFile() async {
    final result = await FilePicker.pickFiles(type: FileType.any);
    if (result != null && result.paths.isNotEmpty && mounted) {
      setState(() => _pickedFilePath = result.paths.first?.toString());
    }
  }

  void _toggleMultiSelect(List<String> list, String value) {
    setState(() {
      if (list.contains(value)) {
        list.remove(value);
      } else {
        list.add(value);
      }
    });
  }

  Future<void> _save() async {
    final title = _titleController.text.trim();
    if (!_validate(title)) return;

    setState(() {
      _saving = true;
      _titleError = null;
    });

    final repo = ref.read(resourcesRepositoryProvider);
    // `ref.read(sessionProvider).valueOrNull` is null until something else
    // resolves the provider, and this screen never watches it — in the app the
    // router's `ref.listen` happens to keep it resolved, so the loss was
    // invisible. Awaiting the future is what `AddResourceActivity` does
    // (`userSessionManager.getUserModel()` in onCreate). A rejecting session
    // costs the attribution, never the resource.
    UserRow? user;
    try {
      user = await ref.read(sessionProvider.future);
    } catch (_) {
      user = null;
    }
    if (!mounted) return;

    LocalResourceError? error;
    if (_isEditMode) {
      error = await repo.updateLocalResource(
        resourceId: widget.editResourceId!,
        title: title,
        author: _authorController.text.trim(),
        year: _yearController.text.trim(),
        description: _descriptionController.text.trim(),
        publisher: _publisherController.text.trim(),
        linkToLicense: _licenseController.text.trim(),
        subjects: _subjects,
        levels: _levels,
      );
    } else {
      final request = LocalResourceRequest(
        title: title,
        addedBy: user?.name,
        author: _authorController.text.trim(),
        year: _yearController.text.trim(),
        description: _descriptionController.text.trim(),
        publisher: _publisherController.text.trim(),
        linkToLicense: _licenseController.text.trim(),
        openWith: _openWith,
        language: _language,
        mediaType: _mediaType,
        resourceType: _resourceType,
        subjects: _subjects,
        levels: _levels,
        resourceFor: _resourceFor,
        resourceUrl: _pickedFilePath,
        userId: user?.id,
        isPrivateTeamResource: _isPrivate && widget.teamId != null,
        teamId: widget.teamId,
      );
      error = await repo.saveLocalResource(request);
    }

    if (!mounted) return;
    setState(() => _saving = false);

    if (error != null) {
      final l10n = AppLocalizations.of(context);
      // `AddResourceActivity` annotates the title field for a title problem
      // (`tlTitle.error`, :204-207) and toasts a failed edit (:164-167).
      switch (error) {
        case LocalResourceError.titleMissing:
          setState(() => _titleError = l10n.titleIsRequired);
        case LocalResourceError.titleAlreadyExists:
          setState(() => _titleError = l10n.resourceTitleAlreadyExists);
        case LocalResourceError.notFound:
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text(l10n.failedToUpdateResource)),
          );
      }
    } else {
      final l10n = AppLocalizations.of(context);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            _isEditMode
                ? l10n.resourceUpdated
                : (_isPrivate
                      ? l10n.resourceAddedToTeam
                      : l10n.addedToMyLibrary),
          ),
        ),
      );
      context.pop();
    }
  }

  bool _validate(String title) {
    final l10n = AppLocalizations.of(context);
    setState(() => _titleError = null);
    if (title.isEmpty) {
      setState(() => _titleError = l10n.titleIsRequired);
      return false;
    }
    if (_descriptionController.text.trim().isEmpty) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(l10n.descriptionIsRequired)));
      return false;
    }
    if (_levels.isEmpty) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(l10n.levelIsRequired)));
      return false;
    }
    if (_subjects.isEmpty) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(l10n.subjectIsRequired)));
      return false;
    }
    return true;
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return Scaffold(
      appBar: AppBar(
        title: Text(_isEditMode ? l10n.editResource : l10n.addResource),
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (!_isEditMode) ...[
              _FilePickerCard(path: _pickedFilePath, onPick: _pickFile),
              const SizedBox(height: 16),
            ],
            TextField(
              controller: _titleController,
              decoration: InputDecoration(
                labelText: l10n.title,
                errorText: _titleError,
                border: const OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _authorController,
              decoration: InputDecoration(
                labelText: l10n.author,
                border: const OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _yearController,
              keyboardType: TextInputType.number,
              decoration: InputDecoration(
                labelText: l10n.year,
                border: const OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _descriptionController,
              maxLines: 3,
              decoration: InputDecoration(
                labelText: l10n.description,
                border: const OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _publisherController,
              decoration: InputDecoration(
                labelText: l10n.publisher,
                border: const OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _licenseController,
              decoration: InputDecoration(
                labelText: l10n.linkToLicense,
                border: const OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 16),
            _MultiSelectChips(
              label: l10n.levels,
              options: _levelOptions,
              selected: _levels,
              onToggle: (v) => _toggleMultiSelect(_levels, v),
            ),
            const SizedBox(height: 12),
            _MultiSelectChips(
              label: l10n.subject,
              options: _subjectOptions,
              selected: _subjects,
              onToggle: (v) => _toggleMultiSelect(_subjects, v),
            ),
            const SizedBox(height: 12),
            _MultiSelectChips(
              label: l10n.resourceFor,
              options: _resourceForOptions,
              selected: _resourceFor,
              onToggle: (v) => _toggleMultiSelect(_resourceFor, v),
            ),
            const SizedBox(height: 12),
            _DropdownField(
              label: l10n.language,
              value: _language,
              options: _languageOptions,
              onChanged: (v) => setState(() => _language = v),
            ),
            const SizedBox(height: 12),
            _DropdownField(
              label: l10n.selectOpenWith,
              value: _openWith,
              options: _openWithOptions,
              onChanged: (v) => setState(() => _openWith = v),
            ),
            const SizedBox(height: 12),
            _DropdownField(
              label: l10n.selectMedia,
              value: _mediaType,
              options: _mediaOptions,
              onChanged: (v) => setState(() => _mediaType = v),
            ),
            const SizedBox(height: 12),
            _DropdownField(
              label: l10n.selectResourceType,
              value: _resourceType,
              options: _resourceTypeOptions,
              onChanged: (v) => setState(() => _resourceType = v),
            ),
            if (widget.teamId != null) ...[
              const SizedBox(height: 12),
              SwitchListTile(
                title: Text(l10n.privateResource),
                value: _isPrivate,
                onChanged: (v) => setState(() => _isPrivate = v),
              ),
            ],
            const SizedBox(height: 24),
            Row(
              children: [
                Expanded(
                  child: OutlinedButton(
                    onPressed: () => context.pop(),
                    child: Text(l10n.cancel),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: FilledButton(
                    onPressed: _saving ? null : _save,
                    child: _saving
                        ? const SizedBox(
                            width: 20,
                            height: 20,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          )
                        : Text(_isEditMode ? l10n.saveChanges : l10n.submit),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

/// The file-pick card — a tappable surface that shows the picked filename or
/// a prompt to pick one. Port of `AddResourceFragment`'s `llDraft` →
/// `openFolderLauncher.launch("*/*")` path (the only capture path that does
/// not need a platform channel).
class _FilePickerCard extends StatelessWidget {
  const _FilePickerCard({required this.path, required this.onPick});
  final String? path;
  final VoidCallback onPick;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return Card(
      child: InkWell(
        onTap: onPick,
        borderRadius: BorderRadius.circular(12),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Row(
            children: [
              Icon(
                path != null ? Icons.attach_file : Icons.upload_file,
                size: 32,
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      path != null ? l10n.fileSelected : l10n.selectFile,
                      style: Theme.of(context).textTheme.titleSmall,
                    ),
                    if (path != null)
                      Text(
                        File(path!).uri.pathSegments.last,
                        style: Theme.of(context).textTheme.bodySmall,
                        overflow: TextOverflow.ellipsis,
                      ),
                  ],
                ),
              ),
              if (path != null)
                const Icon(Icons.check_circle, color: Colors.green),
            ],
          ),
        ),
      ),
    );
  }
}

class _MultiSelectChips extends StatelessWidget {
  const _MultiSelectChips({
    required this.label,
    required this.options,
    required this.selected,
    required this.onToggle,
  });
  final String label;
  final List<String> options;
  final List<String> selected;
  final ValueChanged<String> onToggle;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label, style: Theme.of(context).textTheme.labelLarge),
        const SizedBox(height: 8),
        Wrap(
          spacing: 8,
          runSpacing: 4,
          children: [
            for (final option in options)
              FilterChip(
                label: Text(option),
                selected: selected.contains(option),
                onSelected: (_) => onToggle(option),
              ),
          ],
        ),
      ],
    );
  }
}

class _DropdownField extends StatelessWidget {
  const _DropdownField({
    required this.label,
    required this.value,
    required this.options,
    required this.onChanged,
  });
  final String label;
  final String? value;
  final List<String> options;
  final ValueChanged<String?> onChanged;

  @override
  Widget build(BuildContext context) {
    // A synced row carries whatever the server wrote — `mediaType: "HTML"`,
    // an ISO language code — and `DropdownButton` asserts that a non-null
    // value matches exactly one item. The Kotlin spinner simply stays on its
    // hint for a value it does not know (`setupHintSpinner`), so drop the
    // unknown one rather than taking the screen down with it.
    final known = options.contains(value) ? value : null;
    return DropdownButtonFormField<String>(
      decoration: InputDecoration(
        labelText: label,
        border: const OutlineInputBorder(),
      ),
      initialValue: known,
      items: [
        for (final option in options)
          DropdownMenuItem(value: option, child: Text(option)),
      ],
      onChanged: onChanged,
    );
  }
}

// The string arrays from `strings.xml` (Phase 47 derived the translations;
// these are the option lists the Kotlin spinners populate from).
const _levelOptions = [
  'Early Education',
  'Lower Primary',
  'Upper Primary',
  'Lower Secondary',
  'Upper Secondary',
  'Undergraduate',
  'Graduate',
  'Professional',
];

const _subjectOptions = [
  'Agriculture',
  'Arts',
  'Business and Finance',
  'Environment',
  'Food and Nutrition',
  'Geography',
  'Health and Medicine',
  'History',
  'Human Development',
  'Languages',
  'Law',
  'Learning',
];

const _resourceForOptions = ['Default', 'Leader', 'Learner'];

const _languageOptions = [
  'English',
  'नेपाली',
  'Français',
  'Español',
  'عربى',
  'Somali',
];

const _openWithOptions = [
  'HTML',
  'PDF.js',
  'BeLL-Reader',
  'Mp3',
  'Flow Video Player',
  'BeLL Video Book Player',
  'Native Video',
];

const _mediaOptions = ['Text', 'Graphic/Pictures', 'Audio/Music/Book', 'Video'];

const _resourceTypeOptions = [
  'Textbook',
  'Lesson Plan',
  'Activities',
  'Exercises',
  'Discussion Questions',
];
