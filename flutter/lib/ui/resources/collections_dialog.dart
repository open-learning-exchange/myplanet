import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../data/local/app_database.dart';
import '../../l10n/app_localizations.dart';
import '../../providers/tags_providers.dart';

/// Port of `ui/resources/CollectionsFragment.kt`: the tag picker shared by
/// the resources and courses screens.
///
/// Returns the selected tags — a one-element list when "select many" is off
/// (a tap picks immediately and dismisses, `CollectionsFragment.onTagClicked`)
/// or the checked tags when it is on (the OK button,
/// `CollectionsFragment.onOkClicked`). `null` when the dialog is dismissed.
Future<List<Tag>?> showCollectionsDialog(
  BuildContext context, {
  required String dbType,
}) {
  return showDialog<List<Tag>>(
    context: context,
    builder: (context) => CollectionsDialog(dbType: dbType),
  );
}

class CollectionsDialog extends ConsumerStatefulWidget {
  const CollectionsDialog({super.key, required this.dbType});

  final String dbType;

  @override
  ConsumerState<CollectionsDialog> createState() => _CollectionsDialogState();
}

class _CollectionsDialogState extends ConsumerState<CollectionsDialog> {
  final _filterController = TextEditingController();
  Timer? _debounce;
  String _filter = '';

  /// Parent ids currently expanded, port of `TagData.Parent.isExpanded`.
  final _expanded = <String>{};

  /// Multi-select working set, port of `CollectionsFragment.selectedItemsList`.
  final _selected = <String, Tag>{};

  bool _dismissedForEmpty = false;

  @override
  void dispose() {
    _debounce?.cancel();
    _filterController.dispose();
    super.dispose();
  }

  /// Port of `CollectionsFragment.filterTags` with its 300ms debounce.
  void _onFilterChanged(String value) {
    _debounce?.cancel();
    _debounce = Timer(const Duration(milliseconds: 300), () {
      setState(() => _filter = value.trim().toLowerCase());
    });
  }

  void _pickSingle(Tag tag) => Navigator.of(context).pop([tag]);

  void _toggleSelected(Tag tag) {
    setState(() {
      if (_selected.containsKey(tag.id)) {
        _selected.remove(tag.id);
      } else {
        _selected[tag.id] = tag;
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final multiSelect = ref.watch(collectionMultiSelectProvider);
    final treeAsync = ref.watch(tagTreeProvider(widget.dbType));

    return AlertDialog(
      title: Text(l10n.collections),
      content: SizedBox(
        width: double.maxFinite,
        child: treeAsync.when(
          loading: () => const SizedBox(
            height: 120,
            child: Center(child: CircularProgressIndicator()),
          ),
          error: (error, _) => Text('$error'),
          data: (tree) {
            // Port of `CollectionsState.Empty`: nothing to pick, so toast and
            // dismiss rather than showing a dead list.
            if (tree.parents.isEmpty) {
              if (!_dismissedForEmpty) {
                _dismissedForEmpty = true;
                WidgetsBinding.instance.addPostFrameCallback((_) {
                  if (!mounted) return;
                  ScaffoldMessenger.of(
                    context,
                  ).showSnackBar(SnackBar(content: Text(l10n.noDataAvailable)));
                  Navigator.of(context).pop();
                });
              }
              return const SizedBox(height: 120);
            }

            final parents = _filter.isEmpty
                ? tree.parents
                : tree.parents
                      .where((p) => p.name.toLowerCase().contains(_filter))
                      .toList();

            return Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                TextField(
                  controller: _filterController,
                  decoration: InputDecoration(
                    hintText: l10n.filterCollections,
                    prefixIcon: const Icon(Icons.search),
                    isDense: true,
                  ),
                  onChanged: _onFilterChanged,
                ),
                Row(
                  children: [
                    Expanded(child: Text(l10n.selectManyCollections)),
                    Switch(
                      value: multiSelect,
                      onChanged: (value) =>
                          ref
                                  .read(collectionMultiSelectProvider.notifier)
                                  .state =
                              value,
                    ),
                  ],
                ),
                Flexible(
                  child: ListView.builder(
                    shrinkWrap: true,
                    itemCount: parents.length,
                    itemBuilder: (context, index) {
                      final parent = parents[index];
                      final children = tree.children[parent.id] ?? [];
                      return Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          _TagRow(
                            tag: parent,
                            hasChildren: children.isNotEmpty,
                            expanded: _expanded.contains(parent.id),
                            multiSelect: multiSelect,
                            selected: _selected.containsKey(parent.id),
                            onTap: children.isEmpty
                                ? () => _pickSingle(parent)
                                : () => setState(() {
                                    if (!_expanded.remove(parent.id)) {
                                      _expanded.add(parent.id);
                                    }
                                  }),
                            onToggle: () => _toggleSelected(parent),
                          ),
                          if (_expanded.contains(parent.id))
                            for (final child in children)
                              Padding(
                                padding: const EdgeInsetsDirectional.only(
                                  start: 24,
                                ),
                                child: _TagRow(
                                  tag: child,
                                  hasChildren: false,
                                  expanded: false,
                                  multiSelect: multiSelect,
                                  selected: _selected.containsKey(child.id),
                                  onTap: () => _pickSingle(child),
                                  onToggle: () => _toggleSelected(child),
                                ),
                              ),
                        ],
                      );
                    },
                  ),
                ),
              ],
            );
          },
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(),
          child: Text(l10n.cancel),
        ),
        if (multiSelect)
          FilledButton(
            onPressed: () =>
                Navigator.of(context).pop(_selected.values.toList()),
            child: Text(l10n.ok),
          ),
      ],
    );
  }
}

class _TagRow extends StatelessWidget {
  const _TagRow({
    required this.tag,
    required this.hasChildren,
    required this.expanded,
    required this.multiSelect,
    required this.selected,
    required this.onTap,
    required this.onToggle,
  });

  final Tag tag;
  final bool hasChildren;
  final bool expanded;
  final bool multiSelect;
  final bool selected;
  final VoidCallback onTap;
  final VoidCallback onToggle;

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: multiSelect ? onToggle : onTap,
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 8),
        child: Row(
          children: [
            if (hasChildren)
              Icon(
                expanded ? Icons.keyboard_arrow_up : Icons.keyboard_arrow_down,
                size: 20,
              )
            else
              const SizedBox(width: 20),
            const SizedBox(width: 8),
            Expanded(child: Text(tag.name)),
            if (multiSelect)
              Checkbox(value: selected, onChanged: (_) => onToggle()),
          ],
        ),
      ),
    );
  }
}
