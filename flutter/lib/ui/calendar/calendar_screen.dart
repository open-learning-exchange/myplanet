import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../l10n/app_localizations.dart';
import '../dashboard/dashboard_shell.dart';
import '../router.dart';

/// Port of `ui/calendar/CalendarFragment.kt` and `fragment_calendar.xml`.
///
/// The Android screen initializes its `CalendarView` to today and otherwise
/// leaves selection entirely local to the widget. [CalendarDatePicker] gives
/// the Flutter screen the same behavior without introducing repository state.
class CalendarScreen extends StatefulWidget {
  const CalendarScreen({super.key, this.initialDate});

  /// Overridable for deterministic widget tests; production defaults to now.
  final DateTime? initialDate;

  @override
  State<CalendarScreen> createState() => _CalendarScreenState();
}

class _CalendarScreenState extends State<CalendarScreen> {
  late DateTime _selectedDate;

  @override
  void initState() {
    super.initState();
    _selectedDate = DateUtils.dateOnly(widget.initialDate ?? DateTime.now());
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);

    return Scaffold(
      appBar: AppBar(
        title: Text(l10n.calendar),
        actions: [
          IconButton(
            tooltip: l10n.meetups,
            onPressed: () => context.push(Routes.events),
            icon: const Icon(Icons.groups_outlined),
          ),
          const LogoutAction(),
        ],
      ),
      body: SafeArea(
        child: Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 600),
            child: CalendarDatePicker(
              initialDate: _selectedDate,
              firstDate: DateTime(1900),
              lastDate: DateTime(2100, 12, 31),
              onDateChanged: (date) => setState(() => _selectedDate = date),
            ),
          ),
        ),
      ),
    );
  }
}
