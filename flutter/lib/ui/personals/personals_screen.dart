import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import 'package:path/path.dart' as p;

import '../../data/local/app_database.dart';
import '../../l10n/app_localizations.dart';
import '../../providers/personals_provider.dart';
import '../../repository/personals_repository.dart';
import '../viewer/path_resource_viewer_screen.dart';

/// Offline CRUD port of `ui/personals/PersonalsFragment.kt`.
class PersonalsScreen extends ConsumerWidget {
  const PersonalsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final personals = ref.watch(personalsProvider);
    return Scaffold(
      appBar: AppBar(title: Text(l10n.myPersonals)),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _edit(context, ref),
        icon: const Icon(Icons.add),
        label: Text(l10n.addPersonal),
      ),
      body: personals.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (_, _) => Center(child: Text(l10n.personalsUnavailable)),
        data: (rows) => rows.isEmpty
            ? _EmptyPersonals(onAdd: () => _edit(context, ref))
            : ListView.separated(
                padding: const EdgeInsets.fromLTRB(12, 12, 12, 96),
                itemCount: rows.length,
                separatorBuilder: (_, _) => const SizedBox(height: 8),
                itemBuilder: (context, index) => _PersonalCard(
                  personal: rows[index],
                  onOpen: () => _openAttachment(context, rows[index]),
                  onEdit: () => _edit(context, ref, personal: rows[index]),
                  onDelete: () => _delete(context, ref, rows[index]),
                ),
              ),
      ),
    );
  }

  Future<void> _openAttachment(
    BuildContext context,
    PersonalRow personal,
  ) async {
    final path = personal.path;
    if (path == null || path.isEmpty) return;
    if (!context.mounted) return;
    await Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) =>
            PathResourceViewerScreen(path: path, title: personal.title),
      ),
    );
  }

  Future<void> _edit(
    BuildContext context,
    WidgetRef ref, {
    PersonalRow? personal,
  }) async {
    final value = await showDialog<_PersonalFormValue>(
      context: context,
      builder: (context) => _PersonalDialog(personal: personal),
    );
    if (value == null || !context.mounted) return;
    try {
      if (personal == null) {
        await ref
            .read(personalActionsProvider)
            .create(
              title: value.title,
              description: value.description,
              path: value.path,
            );
      } else {
        await ref
            .read(personalActionsProvider)
            .update(
              id: personal.id,
              title: value.title,
              description: value.description,
              path: value.path,
            );
      }
    } on DuplicatePersonalTitle {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(AppLocalizations.of(context).duplicateTitle)),
        );
      }
    }
  }

  Future<void> _delete(
    BuildContext context,
    WidgetRef ref,
    PersonalRow personal,
  ) async {
    final l10n = AppLocalizations.of(context);
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(l10n.deletePersonal),
        content: Text(l10n.deletePersonalConfirmation(personal.title)),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: Text(l10n.cancel),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: Text(l10n.delete),
          ),
        ],
      ),
    );
    if (confirmed == true) {
      await ref.read(personalActionsProvider).delete(personal.id);
    }
  }
}

class _EmptyPersonals extends StatelessWidget {
  const _EmptyPersonals({required this.onAdd});
  final VoidCallback onAdd;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.folder_special_outlined, size: 56),
            const SizedBox(height: 12),
            Text(l10n.noPersonals, textAlign: TextAlign.center),
            const SizedBox(height: 16),
            FilledButton.icon(
              onPressed: onAdd,
              icon: const Icon(Icons.add),
              label: Text(l10n.addPersonal),
            ),
          ],
        ),
      ),
    );
  }
}

class _PersonalCard extends StatelessWidget {
  const _PersonalCard({
    required this.personal,
    required this.onOpen,
    required this.onEdit,
    required this.onDelete,
  });
  final PersonalRow personal;
  final VoidCallback onOpen;
  final VoidCallback onEdit;
  final VoidCallback onDelete;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return Card(
      child: ListTile(
        leading: Icon(
          personal.path == null
              ? Icons.description_outlined
              : Icons.attach_file,
        ),
        title: Text(personal.title),
        subtitle: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (personal.description != null) Text(personal.description!),
            Text(
              DateFormat.yMMMd().add_jm().format(
                DateTime.fromMillisecondsSinceEpoch(personal.date),
              ),
            ),
            if (!personal.isUploaded)
              Text(
                l10n.savedOffline,
                style: TextStyle(color: Theme.of(context).colorScheme.primary),
              ),
          ],
        ),
        onTap: personal.path != null && personal.path!.isNotEmpty
            ? onOpen
            : null,
        trailing: PopupMenuButton<_PersonalAction>(
          onSelected: (action) {
            switch (action) {
              case _PersonalAction.edit:
                onEdit();
                return;
              case _PersonalAction.delete:
                onDelete();
                return;
            }
          },
          itemBuilder: (context) => [
            PopupMenuItem(
              value: _PersonalAction.edit,
              child: ListTile(
                leading: const Icon(Icons.edit_outlined),
                title: Text(l10n.edit),
              ),
            ),
            PopupMenuItem(
              value: _PersonalAction.delete,
              child: ListTile(
                leading: const Icon(Icons.delete_outline),
                title: Text(l10n.delete),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

enum _PersonalAction { edit, delete }

class _PersonalDialog extends StatefulWidget {
  const _PersonalDialog({this.personal});
  final PersonalRow? personal;

  @override
  State<_PersonalDialog> createState() => _PersonalDialogState();
}

class _PersonalDialogState extends State<_PersonalDialog> {
  final _formKey = GlobalKey<FormState>();
  late final TextEditingController _title;
  late final TextEditingController _description;
  String? _path;

  @override
  void initState() {
    super.initState();
    _title = TextEditingController(text: widget.personal?.title);
    _description = TextEditingController(text: widget.personal?.description);
    _path = widget.personal?.path;
  }

  @override
  void dispose() {
    _title.dispose();
    _description.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return AlertDialog(
      title: Text(
        widget.personal == null ? l10n.addPersonal : l10n.editPersonal,
      ),
      content: Form(
        key: _formKey,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextFormField(
              controller: _title,
              autofocus: true,
              decoration: InputDecoration(labelText: l10n.title),
              validator: (value) => value == null || value.trim().isEmpty
                  ? l10n.titleRequired
                  : null,
            ),
            const SizedBox(height: 12),
            TextFormField(
              controller: _description,
              decoration: InputDecoration(labelText: l10n.description),
              minLines: 2,
              maxLines: 5,
            ),
            const SizedBox(height: 12),
            _AttachmentField(
              path: _path,
              onPick: _pickFile,
              onClear: _clearFile,
            ),
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: Text(l10n.cancel),
        ),
        FilledButton(onPressed: _submit, child: Text(l10n.save)),
      ],
    );
  }

  Future<void> _pickFile() async {
    final result = await FilePicker.pickFiles(
      type: FileType.any,
      allowMultiple: false,
    );
    if (result == null || result.files.isEmpty) return;
    final picked = result.files.single.path;
    if (picked != null) setState(() => _path = picked);
  }

  void _clearFile() => setState(() => _path = null);

  void _submit() {
    if (!(_formKey.currentState?.validate() ?? false)) return;
    Navigator.pop(
      context,
      _PersonalFormValue(
        title: _title.text.trim(),
        description: _description.text.trim(),
        path: _path,
      ),
    );
  }
}

class _AttachmentField extends StatelessWidget {
  const _AttachmentField({
    required this.path,
    required this.onPick,
    required this.onClear,
  });

  final String? path;
  final VoidCallback onPick;
  final VoidCallback onClear;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return ListTile(
      contentPadding: EdgeInsets.zero,
      leading: Icon(
        path == null ? Icons.attach_file_outlined : Icons.attachment_outlined,
      ),
      title: Text(
        path == null ? l10n.attachFile : l10n.attachedFile(p.basename(path!)),
      ),
      subtitle: path == null ? Text(l10n.noFileSelected) : null,
      trailing: path != null
          ? IconButton(
              icon: const Icon(Icons.close),
              tooltip: l10n.removeFile,
              onPressed: onClear,
            )
          : null,
      onTap: onPick,
    );
  }
}

class _PersonalFormValue {
  const _PersonalFormValue({
    required this.title,
    required this.description,
    this.path,
  });
  final String title;
  final String description;
  final String? path;
}
