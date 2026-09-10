import 'package:flutter/material.dart';

/// A selectable list where items can be checked/unchecked
/// Similar to Android's CheckboxAdapter
class CheckboxList<T> extends StatefulWidget {
  const CheckboxList({
    super.key,
    required this.items,
    required this.onChanged,
    this.selectedItems = const {},
    this.itemBuilder,
    this.height,
  });

  /// List of items to display
  final List<T> items;

  /// Callback when selection changes
  final ValueChanged<Set<T>> onChanged;

  /// Currently selected items (controlled)
  final Set<T> selectedItems;

  /// Optional custom item builder
  final Widget Function(BuildContext context, T item, bool isSelected)?
  itemBuilder;

  /// Fixed height for the list (optional)
  final double? height;

  @override
  State<CheckboxList<T>> createState() => _CheckboxListState<T>();
}

class _CheckboxListState<T> extends State<CheckboxList<T>> {
  late Set<T> _selectedItems;

  @override
  void initState() {
    super.initState();
    _selectedItems = Set.from(widget.selectedItems);
  }

  @override
  void didUpdateWidget(CheckboxList<T> oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.selectedItems != widget.selectedItems) {
      _selectedItems = Set.from(widget.selectedItems);
    }
  }

  void _toggleItem(T item) {
    setState(() {
      if (_selectedItems.contains(item)) {
        _selectedItems.remove(item);
      } else {
        _selectedItems.add(item);
      }
    });
    widget.onChanged(_selectedItems);
  }

  @override
  Widget build(BuildContext context) {
    final listView = ListView.builder(
      shrinkWrap: true,
      itemCount: widget.items.length,
      itemBuilder: (context, index) {
        final item = widget.items[index];
        final isSelected = _selectedItems.contains(item);

        if (widget.itemBuilder != null) {
          return widget.itemBuilder!(context, item, isSelected);
        }

        return ListTile(
          title: Text(item.toString()),
          leading: Checkbox(
            value: isSelected,
            onChanged: (_) => _toggleItem(item),
          ),
          onTap: () => _toggleItem(item),
          dense: true,
        );
      },
    );

    if (widget.height != null) {
      return SizedBox(height: widget.height, child: listView);
    }

    return listView;
  }
}

/// Simple string-based checkbox list
class CheckboxStringList extends StatelessWidget {
  const CheckboxStringList({
    super.key,
    required this.items,
    required this.selectedItems,
    required this.onChanged,
  });

  final List<String> items;
  final Set<int> selectedItems;
  final ValueChanged<Set<int>> onChanged;

  @override
  Widget build(BuildContext context) {
    return ListView.builder(
      shrinkWrap: true,
      itemCount: items.length,
      itemBuilder: (context, index) {
        final isSelected = selectedItems.contains(index);
        return CheckboxListTile(
          title: Text(items[index]),
          value: isSelected,
          onChanged: (value) {
            final newSelected = Set<int>.from(selectedItems);
            if (value == true) {
              newSelected.add(index);
            } else {
              newSelected.remove(index);
            }
            onChanged(newSelected);
          },
          dense: true,
        );
      },
    );
  }
}
