import 'dart:io';

import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../core/files/resource_files.dart';
import '../../core/utils/file_utils.dart';
import '../../l10n/app_localizations.dart';
import '../router.dart';

/// Port of `ui/settings/StorageBreakdownFragment.kt`.
///
/// Shows a breakdown of downloaded files by category (videos, audio, PDFs, images, other).
class StorageBreakdownScreen extends StatefulWidget {
  const StorageBreakdownScreen({super.key});

  @override
  State<StorageBreakdownScreen> createState() => _StorageBreakdownScreenState();
}

class _StorageBreakdownScreenState extends State<StorageBreakdownScreen> {
  bool _isLoading = true;
  List<_CategoryData> _categories = [];
  int _totalBytes = 0;

  @override
  void initState() {
    super.initState();
    _loadStorage();
  }

  Future<void> _loadStorage() async {
    final categories = await _scanStorage();
    if (mounted) {
      setState(() {
        _categories = categories;
        _totalBytes = categories.fold(0, (sum, c) => sum + c.sizeBytes);
        _isLoading = false;
      });
    }
  }

  Future<List<_CategoryData>> _scanStorage() async {
    final oleDir = await ResourceFiles.oleDirectory();
    if (!await oleDir.exists()) {
      return [];
    }

    final categories = <_CategoryData>[
      _CategoryData(label: CategoryLabel.videos, extensions: videoExtensions),
      _CategoryData(label: CategoryLabel.audio, extensions: audioExtensions),
      _CategoryData(label: CategoryLabel.pdfs, extensions: pdfExtensions),
      _CategoryData(label: CategoryLabel.images, extensions: imageExtensions),
      _CategoryData(label: CategoryLabel.other, extensions: const {}),
    ];

    try {
      await for (final entity in oleDir.list(recursive: true)) {
        if (entity is! File) continue;
        final ext = entity.path.split('.').last.toLowerCase();
        final size = entity.lengthSync();

        CategoryLabel label;
        if (videoExtensions.contains(ext)) {
          label = CategoryLabel.videos;
        } else if (audioExtensions.contains(ext)) {
          label = CategoryLabel.audio;
        } else if (pdfExtensions.contains(ext)) {
          label = CategoryLabel.pdfs;
        } else if (imageExtensions.contains(ext)) {
          label = CategoryLabel.images;
        } else {
          label = CategoryLabel.other;
        }

        final category = categories.firstWhere((c) => c.label == label);
        category.sizeBytes += size;
        category.fileCount++;
      }
    } catch (e) {
      // Ignore errors during file scanning
    }

    return categories.where((c) => c.fileCount > 0).toList();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);

    return Scaffold(
      appBar: AppBar(title: Text(l10n.storageManagement)),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : _categories.isEmpty
          ? Center(child: Text(l10n.noStorageUsed))
          : _buildContent(context, l10n),
    );
  }

  Widget _buildContent(BuildContext context, AppLocalizations l10n) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.all(16),
          child: Text(
            '${l10n.storageTotalDownloaded}: ${formatFileSize(_totalBytes)}',
            style: Theme.of(context).textTheme.titleMedium,
          ),
        ),
        Expanded(
          child: ListView.separated(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            itemCount: _categories.length,
            separatorBuilder: (context, index) => const SizedBox(height: 8),
            itemBuilder: (context, index) {
              final category = _categories[index];
              return _CategoryTile(
                category: category,
                l10n: l10n,
                onTap: () => _openCategoryDetail(context, category),
              );
            },
          ),
        ),
      ],
    );
  }

  void _openCategoryDetail(BuildContext context, _CategoryData category) {
    context
        .push(
          Routes.storageCategory,
          extra: StorageCategoryExtra(
            label: category.label,
            extensions: category.extensions.toList(),
          ),
        )
        .then((_) => _loadStorage());
  }
}

class _CategoryData {
  final CategoryLabel label;
  final Set<String> extensions;
  int sizeBytes = 0;
  int fileCount = 0;

  _CategoryData({required this.label, required this.extensions});
}

enum CategoryLabel { videos, audio, pdfs, images, other }

class _CategoryTile extends StatelessWidget {
  const _CategoryTile({
    required this.category,
    required this.l10n,
    required this.onTap,
  });

  final _CategoryData category;
  final AppLocalizations l10n;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: ListTile(
        leading: Icon(_getIcon()),
        title: Text(_getLabel()),
        subtitle: Text(
          '${formatFileSize(category.sizeBytes)} · ${category.fileCount == 1 ? l10n.fileCountOne : l10n.fileCountMany(category.fileCount)}',
        ),
        trailing: const Icon(Icons.chevron_right),
        onTap: onTap,
      ),
    );
  }

  IconData _getIcon() {
    switch (category.label) {
      case CategoryLabel.videos:
        return Icons.video_library_outlined;
      case CategoryLabel.audio:
        return Icons.audiotrack_outlined;
      case CategoryLabel.pdfs:
        return Icons.picture_as_pdf_outlined;
      case CategoryLabel.images:
        return Icons.image_outlined;
      case CategoryLabel.other:
        return Icons.folder_outlined;
    }
  }

  String _getLabel() {
    switch (category.label) {
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
}

class StorageCategoryExtra {
  final CategoryLabel label;
  final List<String> extensions;

  StorageCategoryExtra({required this.label, required this.extensions});
}

/// Extension sets mirroring `StorageBreakdownFragment.categories`. Shared so the
/// breakdown and the category detail never disagree on what belongs where.
const Set<String> videoExtensions = {
  'mp4',
  'mkv',
  'avi',
  'webm',
  'mov',
  '3gp',
  'flv',
};
const Set<String> audioExtensions = {
  'mp3',
  'wav',
  'ogg',
  'm4a',
  'flac',
  'aac',
  'opus',
};
const Set<String> pdfExtensions = {'pdf'};
const Set<String> imageExtensions = {
  'jpg',
  'jpeg',
  'png',
  'gif',
  'webp',
  'bmp',
};

/// Every known extension, matching `StorageBreakdownFragment`'s
/// `categories.dropLast(1).flatMap { it.extensions }.toSet()`. Used by the
/// category detail to route unrecognised extensions into "other".
const Set<String> allKnownExtensions = {
  ...videoExtensions,
  ...audioExtensions,
  ...pdfExtensions,
  ...imageExtensions,
};
