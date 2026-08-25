import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../data/local/app_database.dart';
import '../../l10n/app_localizations.dart';
import '../../providers/app_providers.dart';
import '../../providers/network_status_provider.dart';
import '../../providers/ratings_provider.dart';
import '../../providers/session_provider.dart';
import '../ratings/rating_dialog.dart';
import '../router.dart';

/// Port of `ui/resources/ResourceDetailFragment.kt`.
///
/// Shows resource metadata (author, publisher, language, etc.), rating,
/// and actions (add to library, view resource).
class ResourceDetailScreen extends ConsumerStatefulWidget {
  const ResourceDetailScreen({super.key, required this.resourceId});

  final String resourceId;

  @override
  ConsumerState<ResourceDetailScreen> createState() =>
      _ResourceDetailScreenState();
}

class _ResourceDetailScreenState extends ConsumerState<ResourceDetailScreen> {
  MyLibraryRow? _resource;
  bool _loading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _loadResource();
  }

  Future<void> _loadResource() async {
    try {
      final resource = await ref
          .read(myLibraryDaoProvider)
          .getById(widget.resourceId);
      if (!mounted) return;
      setState(() {
        _resource = resource;
        _loading = false;
      });
    } catch (e) {
      if (mounted) {
        setState(() {
          _error = e.toString();
          _loading = false;
        });
      }
    }
  }

  /// `MyLibrary.isResourceOffline()` — the pure data check the Kotlin detail
  /// screen's `setupDownloadButtonState` reads to pick the icon/label. The
  /// actual file-exists check (`FileUtils.checkFileExist`) belongs to
  /// `openResource` / the viewer, not the button state, so the detail screen
  /// never touches the filesystem and stays testable under the fake clock.
  bool _isResourceOffline(MyLibraryRow resource) => resource.resourceOffline;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);

    if (_loading) {
      return Scaffold(
        appBar: AppBar(title: Text(l10n.resources)),
        body: const Center(child: CircularProgressIndicator()),
      );
    }

    if (_error != null) {
      return Scaffold(
        appBar: AppBar(title: Text(l10n.resources)),
        body: Center(child: Text(l10n.syncFailed(_error!))),
      );
    }

    if (_resource == null) {
      return Scaffold(
        appBar: AppBar(title: Text(l10n.resources)),
        body: Center(child: Text(l10n.noDataAvailable)),
      );
    }

    return Scaffold(
      appBar: AppBar(
        title: Text(_resource!.title ?? l10n.untitledResourceTitle),
        actions: [
          IconButton(
            tooltip: l10n.editResource,
            icon: const Icon(Icons.edit),
            onPressed: () => context.push(
              '${Routes.addResource}?edit=${Uri.encodeComponent(_resource!.id)}',
            ),
          ),
        ],
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Title
            Text(
              _resource!.title ?? '',
              style: Theme.of(context).textTheme.headlineSmall,
            ),
            const SizedBox(height: 16),

            // Rating section
            _buildRatingSection(context),
            const SizedBox(height: 24),

            // Metadata section
            _buildMetadataSection(context),
            const SizedBox(height: 24),

            // Description
            if (_resource!.description != null &&
                _resource!.description!.isNotEmpty) ...[
              Text(
                l10n.description,
                style: Theme.of(context).textTheme.titleMedium,
              ),
              const SizedBox(height: 8),
              Text(_resource!.description!),
              const SizedBox(height: 24),
            ],

            // License
            if (_resource!.linkToLicense != null &&
                _resource!.linkToLicense!.isNotEmpty) ...[
              _buildInfoRow(
                context,
                l10n.license,
                _resource!.linkToLicense!,
                Icons.article_outlined,
              ),
              const SizedBox(height: 16),
            ],

            // Added by
            if (_resource!.addedBy != null &&
                _resource!.addedBy!.isNotEmpty) ...[
              _buildInfoRow(
                context,
                l10n.addedBy,
                _resource!.addedBy!,
                Icons.person_outline,
              ),
              const SizedBox(height: 16),
            ],

            // View/Action buttons
            const SizedBox(height: 16),
            _buildActionButtons(context),
          ],
        ),
      ),
    );
  }

  Widget _buildRatingSection(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final resource = _resource!;
    final summary = ref.watch(
      ratingSummaryProvider((type: 'resource', itemId: resource.id)),
    );

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Icon(Icons.star_outline, color: Colors.amber),
                const SizedBox(width: 8),
                Text(
                  l10n.rating,
                  style: Theme.of(context).textTheme.titleMedium,
                ),
              ],
            ),
            const SizedBox(height: 8),
            summary.when(
              loading: () => const LinearProgressIndicator(),
              error: (e, s) => Text(
                l10n.ratingsUnavailable,
                style: Theme.of(context).textTheme.bodySmall,
              ),
              data: (data) {
                if (data.total == 0) {
                  return Text(
                    l10n.noRatings,
                    style: Theme.of(context).textTheme.bodySmall,
                  );
                }
                return Row(
                  children: [
                    Text(
                      '${data.average.toStringAsFixed(1)} / 5',
                      style: Theme.of(context).textTheme.titleLarge,
                    ),
                    const SizedBox(width: 8),
                    Text(
                      l10n.basedOnRatings(data.total),
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                    const Spacer(),
                    TextButton.icon(
                      onPressed: () => _showRatingDialog(context),
                      icon: const Icon(Icons.rate_review_outlined),
                      label: Text(l10n.rateTitle(resource.title ?? '')),
                    ),
                  ],
                );
              },
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildMetadataSection(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final resource = _resource!;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          l10n.resourceInformation,
          style: Theme.of(context).textTheme.titleMedium,
        ),
        const SizedBox(height: 8),
        Card(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              children: [
                if (resource.author != null && resource.author!.isNotEmpty)
                  _buildMetadataRow(l10n.author, resource.author!),
                if (resource.publisher != null &&
                    resource.publisher!.isNotEmpty)
                  _buildMetadataRow(l10n.publisher, resource.publisher!),
                if (resource.language != null && resource.language!.isNotEmpty)
                  _buildMetadataRow(l10n.language, resource.language!),
                if (resource.mediaType != null &&
                    resource.mediaType!.isNotEmpty)
                  _buildMetadataRow(l10n.mediaType, resource.mediaType!),
                if (resource.resourceType != null &&
                    resource.resourceType!.isNotEmpty)
                  _buildMetadataRow(l10n.resourceType, resource.resourceType!),
                if (resource.subject.isNotEmpty)
                  _buildMetadataRow(l10n.subject, resource.subject.join(', ')),
                if (resource.level.isNotEmpty)
                  _buildMetadataRow(l10n.level, resource.level.join(', ')),
                if (resource.languages.isNotEmpty)
                  _buildMetadataRow(
                    l10n.languages,
                    resource.languages.join(', '),
                  ),
                if (resource.resourceFor.isNotEmpty)
                  _buildMetadataRow(
                    l10n.resourceFor,
                    resource.resourceFor.join(', '),
                  ),
                if (resource.year != null && resource.year!.isNotEmpty)
                  _buildMetadataRow(l10n.year, resource.year!),
                if (resource.timesRated > 0)
                  _buildMetadataRow(
                    l10n.timesRated,
                    resource.timesRated.toString(),
                  ),
              ],
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildMetadataRow(String label, String value) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 120,
            child: Text(
              label,
              style: const TextStyle(fontWeight: FontWeight.bold),
            ),
          ),
          Expanded(child: Text(value)),
        ],
      ),
    );
  }

  Widget _buildInfoRow(
    BuildContext context,
    String label,
    String value,
    IconData icon,
  ) {
    return Row(
      children: [
        Icon(icon, size: 20),
        const SizedBox(width: 8),
        Text('$label: ', style: const TextStyle(fontWeight: FontWeight.bold)),
        Expanded(child: Text(value)),
      ],
    );
  }

  Widget _buildActionButtons(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final session = ref.watch(sessionProvider).valueOrNull;
    final resource = _resource!;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        // Download/View button — the port of
        // `ResourceDetailFragment.setupDownloadButton` /
        // `updateDownloadButtonState`. The label and icon tell the user whether
        // the attachment is already on disk (View/Play) or needs fetching
        // (Download), and the button is hidden entirely when the row names no
        // attachment and is not an HTML bundle.
        if (_shouldShowDownloadButton(resource)) ...[
          FilledButton.icon(
            onPressed: () => _openOrDownload(context, resource),
            icon: Icon(_downloadButtonIcon(resource)),
            label: Text(_downloadButtonLabel(l10n, resource)),
          ),
          const SizedBox(height: 12),
        ],

        // Add/Remove from library button
        if (session != null) ...[
          OutlinedButton.icon(
            onPressed: () => _toggleLibraryMembership(context),
            icon: Icon(
              _isOnShelf(session.id)
                  ? Icons.bookmark_remove
                  : Icons.bookmark_add,
            ),
            label: Text(
              _isOnShelf(session.id)
                  ? l10n.removeFromMyLibrary
                  : l10n.addToMyLibrary,
            ),
          ),
        ],
      ],
    );
  }

  /// `setupDownloadButton`'s visibility rule: the button shows for HTML
  /// resources (which are always openable) and for any resource that names a
  /// local address. A row with neither has nothing to fetch or open.
  bool _shouldShowDownloadButton(MyLibraryRow resource) {
    final isHtml = resource.mediaType?.toLowerCase() == 'html';
    final hasLocalAddress = (resource.resourceLocalAddress ?? '').isNotEmpty;
    return isHtml || hasLocalAddress;
  }

  /// `updateDownloadButtonState` — the icon depends on media type and whether
  /// the file is on disk. Video also reads as "play" when the server is
  /// reachable, since the viewer can stream it.
  IconData _downloadButtonIcon(MyLibraryRow resource) {
    final mediaType = (resource.mediaType ?? '').toLowerCase();
    final fileExtension = _fileExtension(resource);
    final isVideo = mediaType.startsWith('video') || fileExtension == 'mp4';
    final isAudio =
        mediaType.startsWith('audio') ||
        fileExtension == 'mp3' ||
        fileExtension == 'aac' ||
        fileExtension == 'wav';

    if (_isResourceOffline(resource)) {
      return isVideo ? Icons.play_arrow : Icons.visibility;
    }
    if (isVideo) {
      // Streaming is available when the server answers.
      final status = ref.read(networkStatusProvider);
      return status == NetworkStatus.connected
          ? Icons.play_arrow
          : Icons.download;
    }
    if (isAudio) {
      return Icons.download;
    }
    return Icons.download;
  }

  String _downloadButtonLabel(AppLocalizations l10n, MyLibraryRow resource) {
    final mediaType = (resource.mediaType ?? '').toLowerCase();
    final fileExtension = _fileExtension(resource);
    final isVideo = mediaType.startsWith('video') || fileExtension == 'mp4';

    if (_isResourceOffline(resource)) {
      return l10n.view;
    }
    if (isVideo) {
      final status = ref.read(networkStatusProvider);
      return status == NetworkStatus.connected ? l10n.view : l10n.download;
    }
    return l10n.download;
  }

  String _fileExtension(MyLibraryRow resource) =>
      (resource.resourceLocalAddress ?? '').split('.').last.toLowerCase();

  void _openOrDownload(BuildContext context, MyLibraryRow resource) {
    final l10n = AppLocalizations.of(context);
    final isHtml = resource.mediaType?.toLowerCase() == 'html';
    if (!isHtml && (resource.resourceLocalAddress ?? '').isEmpty) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(l10n.linkNotAvailable)));
      return;
    }
    context.push('/resources/viewer/${resource.id}');
  }

  bool _isOnShelf(String userId) {
    final resource = _resource;
    if (resource == null) return false;
    return resource.userId.contains(userId);
  }

  Future<void> _toggleLibraryMembership(BuildContext context) async {
    final l10n = AppLocalizations.of(context);
    final session = ref.read(sessionProvider).valueOrNull;
    if (session == null || _resource == null) return;

    final isCurrentlyOnShelf = _isOnShelf(session.id);
    final previousUserCount = _resource!.userId.length;

    // Capture the messenger and theme color before the async gap
    final messenger = ScaffoldMessenger.of(context);
    final errorColor = Theme.of(context).colorScheme.error;

    try {
      // The repository pairs the membership write with the removed_log
      // record/clear — without the record, the next shelf push would merge
      // the resource right back in from the server's copy.
      await ref
          .read(resourcesRepositoryProvider)
          .setShelfMembership(
            _resource!.id,
            session.id,
            joined: !isCurrentlyOnShelf,
          );

      // Reload
      await _loadResource();

      if (!mounted) return;
      // Only toast when the membership actually changed, matching the
      // Kotlin `setUserLibrary` size comparison (`ef80dda52`): a no-op
      // toggle (e.g. another device already synced the same state) stays
      // silent rather than announcing a change that didn't happen.
      final changed = _resource?.userId.length != previousUserCount;
      if (changed) {
        messenger.showSnackBar(
          SnackBar(
            content: Text(
              isCurrentlyOnShelf
                  ? l10n.removedFromMyLibrary
                  : l10n.addedToMyLibrary,
            ),
          ),
        );
      }
    } catch (e) {
      if (!mounted) return;
      messenger.showSnackBar(
        SnackBar(
          content: Text(l10n.operationFailed),
          backgroundColor: errorColor,
        ),
      );
    }
  }

  void _showRatingDialog(BuildContext context) {
    final resource = _resource;
    if (resource == null) return;

    showDialog(
      context: context,
      builder: (context) => RatingDialog(
        target: (type: 'resource', itemId: resource.id),
        title: resource.title ?? '',
      ),
    );
  }
}
