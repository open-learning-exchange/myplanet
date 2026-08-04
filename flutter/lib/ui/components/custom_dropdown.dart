import 'package:flutter/material.dart';

/// A dropdown button that triggers selection even when the same item is selected
/// Similar to Android's CustomSpinner
class CustomDropdownButton<T> extends StatefulWidget {
  const CustomDropdownButton({
    super.key,
    required this.value,
    required this.items,
    required this.onChanged,
    this.hint,
    this.isExpanded = true,
    this.icon,
    this.style,
  });

  /// Currently selected value
  final T value;

  /// List of dropdown items
  final List<DropdownMenuItem<T>> items;

  /// Callback when selection changes
  final ValueChanged<T> onChanged;

  /// Hint text shown when no value is selected
  final String? hint;

  /// Whether to expand the dropdown to fill available space
  final bool isExpanded;

  /// Icon to show in the dropdown button
  final Widget? icon;

  /// Text style for the dropdown
  final TextStyle? style;

  @override
  State<CustomDropdownButton<T>> createState() =>
      _CustomDropdownButtonState<T>();
}

class _CustomDropdownButtonState<T> extends State<CustomDropdownButton<T>> {
  @override
  Widget build(BuildContext context) {
    return DropdownButton<T>(
      value: widget.value,
      items: widget.items,
      onChanged: (newValue) {
        if (newValue != null) {
          widget.onChanged(newValue);
        }
      },
      hint: widget.hint != null ? Text(widget.hint!) : null,
      isExpanded: widget.isExpanded,
      icon: widget.icon,
      style: widget.style,
      underline: const SizedBox(), // Remove default underline
    );
  }
}

/// A dropdown that always fires onChanged, even when same item is selected
/// This is useful when you need to trigger actions on tap of the current selection
class AlwaysNotifyDropdown<T> extends StatefulWidget {
  const AlwaysNotifyDropdown({
    super.key,
    required this.value,
    required this.items,
    required this.onChanged,
    this.hint,
    this.isExpanded = true,
    this.icon,
  });

  final T value;
  final List<DropdownMenuItem<T>> items;
  final ValueChanged<T> onChanged;
  final String? hint;
  final bool isExpanded;
  final Widget? icon;

  @override
  State<AlwaysNotifyDropdown<T>> createState() =>
      _AlwaysNotifyDropdownState<T>();
}

class _AlwaysNotifyDropdownState<T> extends State<AlwaysNotifyDropdown<T>> {
  Key _dropdownKey = UniqueKey();

  void _onItemSelected(T? value) {
    if (value != null) {
      widget.onChanged(value);
      // Regenerate key to force rebuild and ensure next tap works
      setState(() {
        _dropdownKey = UniqueKey();
      });
    }
  }

  @override
  void didUpdateWidget(AlwaysNotifyDropdown<T> oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.value != widget.value) {
      // Reset key when value changes externally
      _dropdownKey = UniqueKey();
    }
  }

  @override
  Widget build(BuildContext context) {
    return DropdownButton<T>(
      key: _dropdownKey,
      value: widget.value,
      items: widget.items,
      onChanged: _onItemSelected,
      hint: widget.hint != null ? Text(widget.hint!) : null,
      isExpanded: widget.isExpanded,
      icon: widget.icon,
    );
  }
}
