import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../data/local/app_database.dart';
import '../repository/tags_repository.dart';
import 'app_providers.dart';

/// The collection tree for one `dbType` (`resources` or `courses`): the named
/// parent tags plus each parent's children. Reloaded when the tag cache
/// changes underneath it — the collections dialog reads this once on open,
/// like the Kotlin's `CollectionsViewModel.loadTags`.
final tagTreeProvider = FutureProvider.family<TagTree, String?>((ref, dbType) {
  return ref.watch(tagsRepositoryProvider).getTagsWithChildren(dbType);
});

/// Port of `MainApplication.isCollectionSwitchOn`: when true the collections
/// dialog shows checkboxes and an OK button returning a multi-selection;
/// when false tapping a tag selects it alone and dismisses. A static in the
/// Kotlin, a StateProvider here so widgets rebuild when it flips.
final collectionMultiSelectProvider = StateProvider<bool>((ref) => false);

/// The tags selected on the resources screen, applied as a filter and shown
/// as chips. Port of `ResourcesFragment.searchTags`.
final resourceSelectedTagsProvider = StateProvider<List<Tag>>((ref) => []);

/// The tags selected on the courses screen. Port of
/// `CourseFilterController.searchTags`.
final courseSelectedTagsProvider = StateProvider<List<Tag>>((ref) => []);

/// Resource id → its named tags, for every resource id currently shown.
/// Port of the `tags` the Kotlin attaches to each `ResourceListModel` when it
/// builds the list.
///
/// The key is the ids joined with newlines rather than the list itself:
/// `FutureProvider.family` keys on identity, and a fresh list instance on
/// every rebuild would refetch the tags each time.
final resourceTagsProvider =
    FutureProvider.family<Map<String, List<Tag>>, String>((ref, idsKey) {
      final ids = idsKey.isEmpty ? const <String>[] : idsKey.split('\n');
      return ref.watch(tagsRepositoryProvider).getTagsForResources(ids);
    });

/// Course id → its named tags. Same family-key convention as
/// [resourceTagsProvider].
final courseTagsProvider =
    FutureProvider.family<Map<String, List<Tag>>, String>((ref, idsKey) {
      final ids = idsKey.isEmpty ? const <String>[] : idsKey.split('\n');
      return ref.watch(tagsRepositoryProvider).getTagsForCourses(ids);
    });
