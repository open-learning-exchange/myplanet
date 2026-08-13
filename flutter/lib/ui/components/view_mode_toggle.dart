import 'package:flutter/material.dart';

import '../../l10n/app_localizations.dart';
import 'list_view_mode.dart';

/// A two-button segmented control that switches between grid and list layouts.
///
/// Port of the `toggle_grid` / `toggle_list` `ImageButton` pair in
/// `ResourcesFragment.setupViewModeToggle` and `CoursesFragment.updateToggleUi`.
/// The active button gets a filled background; the inactive one is transparent.
class ViewModeToggle extends StatelessWidget {
  const ViewModeToggle({
    super.key,
    required this.mode,
    required this.onChanged,
  });

  final ListViewMode mode;
  final ValueChanged<ListViewMode> onChanged;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final isGrid = mode == ListViewMode.grid;

    return Container(
      decoration: BoxDecoration(
        border: Border.all(color: theme.dividerColor),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          _button(
            context: context,
            icon: Icons.grid_view_outlined,
            label: l10n.gridView,
            selected: isGrid,
            onTap: isGrid ? null : () => onChanged(ListViewMode.grid),
            theme: theme,
          ),
          _button(
            context: context,
            icon: Icons.view_list_outlined,
            label: l10n.listView,
            selected: !isGrid,
            onTap: !isGrid ? null : () => onChanged(ListViewMode.list),
            theme: theme,
          ),
        ],
      ),
    );
  }

  Widget _button({
    required BuildContext context,
    required IconData icon,
    required String label,
    required bool selected,
    required VoidCallback? onTap,
    required ThemeData theme,
  }) {
    return Tooltip(
      message: label,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(8),
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 6),
          decoration: BoxDecoration(
            color: selected ? theme.colorScheme.primary : Colors.transparent,
            borderRadius: BorderRadius.circular(8),
          ),
          child: Icon(
            icon,
            size: 20,
            color: selected
                ? theme.colorScheme.onPrimary
                : theme.colorScheme.onSurfaceVariant,
          ),
        ),
      ),
    );
  }
}
