# Phase 131 — close the resource-update notification row

Lane A. Work in progress.

`NotificationsRepository.updateResourceNotification` is ported, has six tests,
and has no production caller. This phase gives it one: the count
(`countLibrariesNeedingUpdate`), the dashboard hook, and whatever that makes
reachable in `notification_format.dart`.
