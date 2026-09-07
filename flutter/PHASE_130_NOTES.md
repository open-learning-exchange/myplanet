# Phase 130 — the tray tap, and a trailing space the derivation ate

Work in progress. Two items:

1. **A correct handler nothing can reach.** Phase 127 left
   `NotificationsRepository.markNotificationAsRead` with no caller, because
   `NotificationActionReceiver` and `DashboardActivity.handleNotificationIntent`
   are unported: the port raises system notifications and tapping one does
   nothing. Porting the entry points.
2. **`tool/arb_from_strings_xml.dart` drops a deliberate trailing space** on the
   plain-text by-name path.
