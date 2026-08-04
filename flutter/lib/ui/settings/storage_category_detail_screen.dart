import 'dart:io';

import 'package:flutter/material.dart';

import '../../core/utils/file_utils.dart';
import '../../l10n/app_localizations.dart';
import 'storage_breakdown_screen.dart';

/// Port of `ui/settings/StorageCategoryDetailFragment.kt`.
///
/// Shows files in a storage category and allows deletion.
class StorageCategoryDetailScreen extends StatefulWidget {
  const StorageCategoryDetailScreen({super.key, required this.extra});

  final StorageCategoryExtra extra;

  @override
  State<StorageCategoryDetailScreen> createState() =>
      _StorageCategoryDetailScreenState();
}

class _StorageCategoryDetailScreenState
    extends State<StorageCategoryDetailScreen> {
  bool _isLoading = true;
  List<_ResourceItem> _items = [];
  final Set<String> _selectedIds = {};
  bool _allSelected = false;

  @override
  void initState() {
    super.initState();
    _loadResources();
  }

  Future<void> _loadResources() async {
    final resources = await _buildResourceItems();
    if (mounted) {
      setState(() {
        _items = resources;
        _isLoading = false;
      });
    }
  }

  Future<List<_ResourceItem>> _buildResourceItems() async {
    final documentsDir = Directory.current.path;
    final oleDir = Directory('$documentsDir/ole');

    if (!oleDir.existsSync()) {
      return [];
    }

    // Get resource titles from the database
    final resourceTitles = await _getResourceTitlesMap();
    final allKnownExt = {
      'mp4', 'mkv', 'avi', 'webm', 'mov', '3gp', 'flv', // videos
      'mp3', 'wav', 'ogg', 'm4a', 'flac', 'aac', 'opus', // audio
      'pdf', // pdfs
      'jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp', // images
    };

    final grouped = <String, List<File>>{};

    try {
      await for (final entity in oleDir.list(recursive: true)) {
        if (entity is File) {
          final ext = entity.path.split('.').last.toLowerCase();
          final matchesCategory = widget.extra.extensions.isEmpty
              ? !allKnownExt.contains(ext)
              : widget.extra.extensions.contains(ext);

          if (matchesCategory) {
            final parentName = entity.parent.path.split('/').last;
            grouped.putIfAbsent(parentName, () => []).add(entity);
          }
        }
      }
    } catch (e) {
      // Ignore errors during file scanning
    }

    return grouped.entries.map((entry) {
      final resourceId = entry.key;
      final files = entry.value;
      final totalSize = files.fold<int>(0, (sum, f) => sum + f.lengthSync());
      final title = resourceTitles[resourceId] ?? '';
      return _ResourceItem(
        resourceId: resourceId,
        title: title.isNotEmpty ? title : 'Unknown resource',
        files: files,
        totalSizeBytes: totalSize,
      );
    }).toList()
      ..sortBy((item) => item.title);
  }

  Future<Map<String, String>> _getResourceTitlesMap() async {
    // This would need to be implemented to query the database
    // For now, return empty map
    return {};
  }

  void _toggleSelection(String resourceId) {
    setState(() {
      if (_selectedIds.contains(resourceId)) {
        _selectedIds.remove(resourceId);
      } else {
        _selectedIds.add(resourceId);
      }
      _updateAllSelected();
    });
  }

  void _toggleSelectAll() {
    setState(() {
      if (_allSelected) {
        _selectedIds.clear();
      } else {
        _selectedIds.addAll(_items.map((i) => i.resourceId));
      }
      _allSelected = !_allSelected;
    });
  }

  void _updateAllSelected() {
    _allSelected = _selectedIds.length == _items.length && _items.isNotEmpty;
  }

  Future<void> _deleteSelected() async {
    final selected =
        _items.where((i) => _selectedIds.contains(i.resourceId)).toList();
    final confirmed = await _showDeleteConfirmation(
      selected.length,
      'Delete ${selected.length} selected items?',
    );
    if (confirmed) {
      await _deleteItems(selected);
    }
  }

  Future<void> _deleteAll() async {
    final confirmed = await _showDeleteConfirmation(
      _items.length,
      'Delete all files in this category?',
    );
    if (confirmed) {
      await _deleteItems(_items);
    }
  }

  Future<bool> _showDeleteConfirmation(int count, String message) async {
    final result = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Are you sure?'),
        content: Text(message),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('No'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Yes'),
          ),
        ],
      ),
    );
    return result ?? false;
  }

  Future<void> _deleteItems(List<_ResourceItem> toDelete) async {
    setState(() => _isLoading = true);

    try {
      final documentsDir = Directory.current.path;
      final oleDir = Directory('$documentsDir/ole');

      for (final item in toDelete) {
        for (final file in item.files) {
          await file.delete();
        }
        // Remove empty parent directory
        final parentDir = Directory('${oleDir.path}/${item.resourceId}');
        if (parentDir.existsSync()) {
          final contents = parentDir.listSync();
          if (contents.isEmpty) {
            await parentDir.delete();
          }
        }
      }

      // Mark resources as not offline in the database
      final deletedIds = toDelete.map((i) => i.resourceId).toSet();
      await _markResourcesAsNotOffline(deletedIds);

      if (mounted) {
        Navigator.pop(context);
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('Failed to delete: $e')));
        setState(() => _isLoading = false);
      }
    }
  }

  Future<void> _markResourcesAsNotOffline(Set<String> ids) async {
    // This would need to be implemented to update the database
  }

  String _getCategoryTitle() {
    switch (widget.extra.label) {
      case CategoryLabel.videos:
        return AppLocalizations.of(context).storageVideos;
      case CategoryLabel.audio:
        return AppLocalizations.of(context).storageAudio;
      case CategoryLabel.pdfs:
        return AppLocalizations.of(context).storagePdfs;
      case CategoryLabel.images:
        return AppLocalizations.of(context).storageImages;
      case CategoryLabel.other:
        return AppLocalizations.of(context).storageOther;
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
        // Select all row
        CheckboxListTile(
          value: _allSelected,
          onChanged: (_) => _toggleSelectAll(),
          title: Text(l10n.selectAll),
        ),
        const Divider(),
        // Selected count
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
        // File list
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
        // Delete all button
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

class _ResourceItem {
  final String resourceId;
  final String title;
  final List<File> files;
  final int totalSizeBytes;

  _ResourceItem({
    required this.resourceId,
    required this.title,
    required this.files,
    required this.totalSizeBytes,
  });
}

extension _ListSort<T> on List<T> {
  List<T> sortBy(Comparable Function(T) keyExtractor) {
    sort((a, b) => keyExtractor(a).compareTo(keyExtractor(b)));
    return this;
  }
}
