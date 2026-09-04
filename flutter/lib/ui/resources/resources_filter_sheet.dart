import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../data/local/app_database.dart';
import '../../l10n/app_localizations.dart';
import '../../providers/resources_providers.dart';

/// Filter criteria for resources.
class ResourceFilter {
  final Set<String> languages;
  final Set<String> subjects;
  final Set<String> mediaTypes;
  final Set<String> levels;

  const ResourceFilter({
    this.languages = const {},
    this.subjects = const {},
    this.mediaTypes = const {},
    this.levels = const {},
  });

  bool get isEmpty =>
      languages.isEmpty &&
      subjects.isEmpty &&
      mediaTypes.isEmpty &&
      levels.isEmpty;

  ResourceFilter copyWith({
    Set<String>? languages,
    Set<String>? subjects,
    Set<String>? mediaTypes,
    Set<String>? levels,
  }) {
    return ResourceFilter(
      languages: languages ?? this.languages,
      subjects: subjects ?? this.subjects,
      mediaTypes: mediaTypes ?? this.mediaTypes,
      levels: levels ?? this.levels,
    );
  }
}

/// Provider for the current resource filter state.
final resourceFilterProvider = StateProvider<ResourceFilter>((ref) {
  return const ResourceFilter();
});

/// Provider that computes available filter options from the resource list.
final resourceFilterOptionsProvider =
    Provider.family<ResourceFilterOptions, List<MyLibraryRow>>((
      ref,
      resources,
    ) {
      final languages = <String>{};
      final subjects = <String>{};
      final mediaTypes = <String>{};
      final levels = <String>{};

      for (final r in resources) {
        if (r.language != null && r.language!.isNotEmpty) {
          languages.add(r.language!);
        }
        subjects.addAll(r.subject);
        if (r.mediaType != null && r.mediaType!.isNotEmpty) {
          mediaTypes.add(r.mediaType!);
        }
        levels.addAll(r.level);
      }

      return ResourceFilterOptions(
        languages: languages.toList()..sort(),
        subjects: subjects.toList()..sort(),
        mediaTypes: mediaTypes.toList()..sort(),
        levels: levels.toList()..sort(),
      );
    });

/// Available filter options.
class ResourceFilterOptions {
  final List<String> languages;
  final List<String> subjects;
  final List<String> mediaTypes;
  final List<String> levels;

  const ResourceFilterOptions({
    this.languages = const [],
    this.subjects = const [],
    this.mediaTypes = const [],
    this.levels = const [],
  });
}

/// Bottom sheet for filtering resources.
class ResourcesFilterSheet extends ConsumerStatefulWidget {
  const ResourcesFilterSheet({super.key});

  @override
  ConsumerState<ResourcesFilterSheet> createState() =>
      _ResourcesFilterSheetState();
}

class _ResourcesFilterSheetState extends ConsumerState<ResourcesFilterSheet> {
  late ResourceFilter _currentFilter;

  @override
  void initState() {
    super.initState();
    _currentFilter = ref.read(resourceFilterProvider);
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final resources = ref.watch(resourcesStreamProvider).valueOrNull ?? [];
    final options = ref.watch(resourceFilterOptionsProvider(resources));

    return DraggableScrollableSheet(
      initialChildSize: 0.7,
      minChildSize: 0.5,
      maxChildSize: 0.9,
      expand: false,
      builder: (context, scrollController) {
        return Container(
          decoration: BoxDecoration(
            color: Theme.of(context).colorScheme.surface,
            borderRadius: const BorderRadius.vertical(top: Radius.circular(16)),
          ),
          child: Column(
            children: [
              // Handle bar
              Container(
                margin: const EdgeInsets.symmetric(vertical: 12),
                width: 40,
                height: 4,
                decoration: BoxDecoration(
                  color: Theme.of(context).colorScheme.outline,
                  borderRadius: BorderRadius.circular(2),
                ),
              ),

              // Header
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16),
                child: Row(
                  children: [
                    Text(
                      l10n.filterResources,
                      style: Theme.of(context).textTheme.titleLarge,
                    ),
                    const Spacer(),
                    TextButton(
                      onPressed: () {
                        setState(() {
                          _currentFilter = const ResourceFilter();
                        });
                      },
                      child: Text(l10n.clearFilters),
                    ),
                    IconButton(
                      icon: const Icon(Icons.close),
                      onPressed: () => Navigator.pop(context),
                    ),
                  ],
                ),
              ),
              const Divider(),

              // Filter sections
              Expanded(
                child: ListView(
                  controller: scrollController,
                  padding: const EdgeInsets.all(16),
                  children: [
                    _buildSection(
                      context,
                      title: l10n.language,
                      options: options.languages,
                      selected: _currentFilter.languages,
                      onChanged: (value) {
                        setState(() {
                          _currentFilter = _currentFilter.copyWith(
                            languages: value,
                          );
                        });
                      },
                    ),
                    const SizedBox(height: 16),
                    _buildSection(
                      context,
                      title: l10n.subject,
                      options: options.subjects,
                      selected: _currentFilter.subjects,
                      onChanged: (value) {
                        setState(() {
                          _currentFilter = _currentFilter.copyWith(
                            subjects: value,
                          );
                        });
                      },
                    ),
                    const SizedBox(height: 16),
                    _buildSection(
                      context,
                      title: l10n.mediaType,
                      options: options.mediaTypes,
                      selected: _currentFilter.mediaTypes,
                      onChanged: (value) {
                        setState(() {
                          _currentFilter = _currentFilter.copyWith(
                            mediaTypes: value,
                          );
                        });
                      },
                    ),
                    const SizedBox(height: 16),
                    _buildSection(
                      context,
                      title: l10n.level,
                      options: options.levels,
                      selected: _currentFilter.levels,
                      onChanged: (value) {
                        setState(() {
                          _currentFilter = _currentFilter.copyWith(
                            levels: value,
                          );
                        });
                      },
                    ),
                  ],
                ),
              ),

              // Apply button
              Padding(
                padding: const EdgeInsets.all(16),
                child: FilledButton(
                  onPressed: () {
                    ref.read(resourceFilterProvider.notifier).state =
                        _currentFilter;
                    Navigator.pop(context);
                  },
                  child: Text(l10n.apply),
                ),
              ),
            ],
          ),
        );
      },
    );
  }

  Widget _buildSection(
    BuildContext context, {
    required String title,
    required List<String> options,
    required Set<String> selected,
    required ValueChanged<Set<String>> onChanged,
  }) {
    if (options.isEmpty) {
      return const SizedBox.shrink();
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(title, style: Theme.of(context).textTheme.titleMedium),
        const SizedBox(height: 8),
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: options.map((option) {
            final isSelected = selected.contains(option);
            return FilterChip(
              label: Text(option),
              selected: isSelected,
              onSelected: (value) {
                final newSelected = Set<String>.from(selected);
                if (value) {
                  newSelected.add(option);
                } else {
                  newSelected.remove(option);
                }
                onChanged(newSelected);
              },
            );
          }).toList(),
        ),
      ],
    );
  }
}

/// Extension to filter a list of resources by the current filter.
extension ResourceFilterExtension on List<MyLibraryRow> {
  List<MyLibraryRow> applyFilter(ResourceFilter filter) {
    if (filter.isEmpty) return this;

    return where((resource) {
      // Check language
      if (filter.languages.isNotEmpty) {
        if (resource.language == null ||
            !filter.languages.contains(resource.language)) {
          return false;
        }
      }

      // Check subjects
      if (filter.subjects.isNotEmpty) {
        if (!resource.subject.any((s) => filter.subjects.contains(s))) {
          return false;
        }
      }

      // Check media type
      if (filter.mediaTypes.isNotEmpty) {
        if (resource.mediaType == null ||
            !filter.mediaTypes.contains(resource.mediaType)) {
          return false;
        }
      }

      // Check levels
      if (filter.levels.isNotEmpty) {
        if (!resource.level.any((l) => filter.levels.contains(l))) {
          return false;
        }
      }

      return true;
    }).toList();
  }
}
