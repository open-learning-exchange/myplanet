import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/utils/file_utils.dart';
import '../../l10n/app_localizations.dart';
import '../../providers/app_providers.dart';
import '../../providers/resources_providers.dart';
import '../router.dart';

/// Port of `ui/settings/StorageBreakdownFragment.kt`.
///
/// Shows a breakdown of downloaded files by category (videos, audio, PDFs, images, other).
class StorageBreakdownScreen extends ConsumerStatefulWidget {
  const StorageBreakdownScreen({super.key});

  @override
  ConsumerState<StorageBreakdownScreen> createState() =>
      _StorageBreakdownScreenState();
}

class _StorageBreakdownScreenState
    extends ConsumerState<StorageBreakdownScreen> {
  bool _isLoading = true;
  bool _isFreeing = false;
  bool _loaded = false;
  List<_CategoryData> _categories = [];
  int _totalBytes = 0;
  ({int totalBytes, int availableBytes})? _diskStats;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    // Triggered after localisations (and other inherited dependencies) resolve,
    // so [AppLocalizations.of] is ready. `initState` runs before the delegates
    // finish loading, so reading l10n there throws and leaves the spinner up —
    // the same trap `storage_category_detail_screen` documents.
    if (!_loaded) {
      _loaded = true;
      _loadStorage();
    }
  }

  Future<void> _loadStorage() async {
    final categories = await _scanStorage();
    final stats = await ref.read(diskStatsProvider).storageStats();
    if (mounted) {
      setState(() {
        _categories = categories;
        _totalBytes = categories.fold(0, (sum, c) => sum + c.sizeBytes);
        _diskStats = stats;
        _isLoading = false;
      });
    }
  }

  /// Sizes each category through `ResourcesRepository.getOfflineResourceItems`
  /// rather than walking `ole/` inline. Routing the filesystem read through the
  /// repository seam keeps the breakdown testable — a real `ole/` walk hangs
  /// under the test binding's fake clock the way it does in
  /// `storage_category_detail_screen_test.dart`, which mocks the same call.
  Future<List<_CategoryData>> _scanStorage() async {
    final l10n = AppLocalizations.of(context);
    final repo = ref.read(resourcesRepositoryProvider);
    final categories = <_CategoryData>[
      _CategoryData(label: CategoryLabel.videos, extensions: videoExtensions),
      _CategoryData(label: CategoryLabel.audio, extensions: audioExtensions),
      _CategoryData(label: CategoryLabel.pdfs, extensions: pdfExtensions),
      _CategoryData(label: CategoryLabel.images, extensions: imageExtensions),
      _CategoryData(label: CategoryLabel.other, extensions: const {}),
    ];
    for (final category in categories) {
      final items = await repo.getOfflineResourceItems(
        extensions: category.extensions,
        allKnownExtensions: allKnownExtensions,
        unknownTitle: l10n.storageUnknownResource,
      );
      for (final item in items) {
        category.sizeBytes += item.totalSizeBytes;
        category.fileCount += item.filePaths.length;
      }
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
          : _buildContent(context, l10n),
    );
  }

  Widget _buildContent(BuildContext context, AppLocalizations l10n) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
          child: Text(
            '${l10n.storageTotalDownloaded}: ${formatFileSize(_totalBytes)}',
            style: Theme.of(context).textTheme.titleMedium,
          ),
        ),
        if (_diskStats != null)
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
            child: Text(
              '${l10n.availableSpace}: '
              '${formatFileSize(_diskStats!.availableBytes)}/'
              '${formatFileSize(_diskStats!.totalBytes)}',
              style: Theme.of(context).textTheme.bodyMedium,
            ),
          ),
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
          child: SizedBox(
            width: double.infinity,
            child: FilledButton.tonalIcon(
              onPressed: _isFreeing ? null : () => _confirmFreeUpSpace(l10n),
              icon: _isFreeing
                  ? const SizedBox(
                      width: 18,
                      height: 18,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Icon(Icons.cleaning_services_outlined),
              label: Text(l10n.freeUpSpace),
            ),
          ),
        ),
        Expanded(
          child: _categories.isEmpty
              ? Center(child: Text(l10n.noStorageUsed))
              : ListView.separated(
                  padding: const EdgeInsets.symmetric(horizontal: 16),
                  itemCount: _categories.length,
                  separatorBuilder: (context, index) =>
                      const SizedBox(height: 8),
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

  Future<void> _confirmFreeUpSpace(AppLocalizations l10n) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(l10n.freeUpSpace),
        content: Text(l10n.freeUpSpaceConfirm),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: Text(l10n.no),
          ),
          FilledButton(
            onPressed: () => Navigator.of(context).pop(true),
            child: Text(l10n.yes),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;
    await _freeUpSpace(l10n);
  }

  Future<void> _freeUpSpace(AppLocalizations l10n) async {
    setState(() => _isFreeing = true);
    try {
      final result = await ref.read(resourcesRepositoryProvider).freeUpSpace();
      ref.read(freeSpaceResultProvider.notifier).state = result;
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              l10n.storageFreedSummary(
                formatFileSize(result.freedBytes),
                result.deletedFiles,
              ),
            ),
          ),
        );
        await _loadStorage();
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(l10n.freeUpSpaceFailed(e.toString()))),
        );
      }
    } finally {
      if (mounted) setState(() => _isFreeing = false);
    }
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
