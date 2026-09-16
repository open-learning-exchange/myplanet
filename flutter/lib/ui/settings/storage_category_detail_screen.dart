import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/utils/file_utils.dart';
import '../../l10n/app_localizations.dart';
import '../../providers/app_providers.dart';
import '../../data/local/offline_resource_item.dart';
import 'storage_breakdown_screen.dart';

/// Port of `ui/settings/StorageCategoryDetailFragment.kt`.
///
/// Lists the downloaded files in one storage category and lets the user delete
/// a selection or all of them. Item discovery and deletion are owned by
/// [ResourcesRepository] (port of `getOfflineResourceItems` /
/// `deleteOfflineResources`); this widget only renders the rows and forwards
/// the chosen items back. Deletion clears the library rows' offline flag, so
/// the resources list reflects it before the next sync re-checks the disk.
class StorageCategoryDetailScreen extends ConsumerStatefulWidget {
  const StorageCategoryDetailScreen({super.key, required this.extra});

  final StorageCategoryExtra extra;

  @override
  ConsumerState<StorageCategoryDetailScreen> createState() =>
      _StorageCategoryDetailScreenState();
}

class _StorageCategoryDetailScreenState
    extends ConsumerState<StorageCategoryDetailScreen> {
  bool _isLoading = true;
  List<OfflineResourceItem> _items = [];
  final Set<String> _selectedIds = {};
  bool _loaded = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    // Triggered after localisations (and other inherited dependencies) resolve,
    // so [AppLocalizations.of] is ready. `initState` runs before the delegates
    // finish loading, so reading l10n there throws and leaves the spinner up.
    if (!_loaded) {
      _loaded = true;
      _loadResources();
    }
  }

  Future<void> _loadResources() async {
    final repository = ref.read(resourcesRepositoryProvider);
    final items = await repository.getOfflineResourceItems(
      extensions: widget.extra.extensions.toSet(),
      allKnownExtensions: allKnownExtensions,
      unknownTitle: AppLocalizations.of(context).storageUnknownResource,
    );
    if (mounted) {
      setState(() {
        _items = items;
        _isLoading = false;
      });
    }
  }

  void _toggleSelection(String resourceId) {
    setState(() {
      if (_selectedIds.contains(resourceId)) {
        _selectedIds.remove(resourceId);
      } else {
        _selectedIds.add(resourceId);
      }
    });
  }

  void _toggleSelectAll() {
    setState(() {
      if (_allSelected) {
        _selectedIds.clear();
      } else {
        _selectedIds.addAll(_items.map((i) => i.resourceId));
      }
    });
  }

  bool get _allSelected =>
      _selectedIds.length == _items.length && _items.isNotEmpty;

  Future<void> _deleteSelected() async {
    final selected = _items
        .where((i) => _selectedIds.contains(i.resourceId))
        .toList();
    final l10n = AppLocalizations.of(context);
    final confirmed = await _showDeleteConfirmation(
      l10n.storageDeleteSelectedConfirm(selected.length),
    );
    if (confirmed) {
      await _deleteItems(selected);
    }
  }

  Future<void> _deleteAll() async {
    final l10n = AppLocalizations.of(context);
    final confirmed = await _showDeleteConfirmation(
      l10n.storageDeleteConfirm(_getCategoryTitle()),
    );
    if (confirmed) {
      await _deleteItems(_items);
    }
  }

  Future<bool> _showDeleteConfirmation(String message) async {
    final l10n = AppLocalizations.of(context);
    final result = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(l10n.areYouSure),
        content: Text(message),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: Text(l10n.no),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            child: Text(l10n.yes),
          ),
        ],
      ),
    );
    return result ?? false;
  }

  Future<void> _deleteItems(List<OfflineResourceItem> toDelete) async {
    setState(() => _isLoading = true);

    try {
      await ref
          .read(resourcesRepositoryProvider)
          .deleteOfflineResources(toDelete);
      if (mounted) {
        Navigator.pop(context, true);
      }
    } catch (e) {
      // The Kotlin dismisses unconditionally; this surfaces a failure rather
      // than silently swallowing a file-system error, which is a port-only
      // deviation.
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(AppLocalizations.of(context).failedToDelete(e)),
          ),
        );
        setState(() => _isLoading = false);
      }
    }
  }

  String _getCategoryTitle() {
    final l10n = AppLocalizations.of(context);
    switch (widget.extra.label) {
      case CategoryLabel.videos:
        return l10n.storageVideos;
      case CategoryLabel.audio:
        return l10n.storageAudio;
      case CategoryLabel.pdfs:
        return l10n.storagePdfs;
      case CategoryLabel.images:
        return l10n.storageImages;
      case CategoryLabel.other:
        return l10n.storageOther;
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);

    return Scaffold(
      appBar: AppBar(title: Text(_getCategoryTitle())),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : _items.isEmpty
          ? Center(child: Text(l10n.noStorageUsed))
          : _buildContent(context, l10n),
    );
  }

  Widget _buildContent(BuildContext context, AppLocalizations l10n) {
    return Column(
      children: [
        CheckboxListTile(
          value: _allSelected,
          onChanged: (_) => _toggleSelectAll(),
          title: Text(l10n.selectAll),
        ),
        const Divider(),
        if (_selectedIds.isNotEmpty)
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            child: Row(
              children: [
                Text(l10n.storageSelectedCount(_selectedIds.length)),
                const Spacer(),
                TextButton(
                  onPressed: _deleteSelected,
                  child: Text(l10n.deleteSelected),
                ),
              ],
            ),
          ),
        Expanded(
          child: ListView.builder(
            itemCount: _items.length,
            itemBuilder: (context, index) {
              final item = _items[index];
              final isSelected = _selectedIds.contains(item.resourceId);
              return CheckboxListTile(
                value: isSelected,
                onChanged: (_) => _toggleSelection(item.resourceId),
                title: Text(item.title),
                subtitle: Text(formatFileSize(item.totalSizeBytes)),
              );
            },
          ),
        ),
        Padding(
          padding: const EdgeInsets.all(16),
          child: SizedBox(
            width: double.infinity,
            child: OutlinedButton(
              onPressed: _deleteAll,
              child: Text(l10n.deleteAll),
            ),
          ),
        ),
      ],
    );
  }
}
