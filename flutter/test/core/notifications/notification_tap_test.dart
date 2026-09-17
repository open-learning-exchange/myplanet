import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/core/notifications/notification_config.dart';
import 'package:myplanet/core/notifications/notification_tap.dart';
import 'package:myplanet/providers/notification_tap_provider.dart';

/// The tray side of Phase 130. Everything here is what a tap carries and what
/// buttons the notification offers — the half that has no Riverpod graph in it.
void main() {
  NotificationConfig taskConfig() => NotificationConfig.task(
    taskId: 'task-42',
    taskTitle: 'Read chapter 3',
    deadlineLabel: 'Wed 19, August 2026',
    urgent: true,
  );

  group('payload', () {
    test('the config a task reminder is built from round-trips', () {
      final config = taskConfig();
      final decoded = NotificationTapPayload.decode(
        NotificationTapPayload.forConfig(config).encode(),
      );

      // Kotlin's three intent extras, and the values `createTaskNotification`
      // puts in them: `type`, `config.id` (the **task** id) and `relatedId`.
      expect(decoded, isNotNull);
      expect(decoded!.type, 'task');
      expect(decoded.notificationId, 'task-42');
      expect(decoded.relatedId, 'task-42');
    });

    test('the encoding uses the Kotlin extra names', () {
      // Not cosmetic: these strings are the readable link between the two
      // apps' handlers, and a reviewer checking this against
      // `NotificationUtils.kt:60-62` should find the same three words.
      expect(
        NotificationTapPayload.forConfig(taskConfig()).encode(),
        '{"notification_type":"task","notification_id":"task-42",'
        '"related_id":"task-42"}',
      );
    });

    test('a null relatedId is omitted rather than written as null', () {
      const payload = NotificationTapPayload(
        type: 'storage',
        notificationId: 'user-1:storage',
      );
      expect(payload.encode(), isNot(contains('related_id')));
      expect(NotificationTapPayload.decode(payload.encode()), payload);
    });

    test('anything this app did not write decodes to nothing', () {
      // An upgrade can leave an older build's notification in the tray, and
      // Kotlin posts tappable notifications with no extras at all
      // (`ServerReachabilityWorker.kt:132-138`) whose tap falls through both
      // branches of `handleNotificationIntent`. Neither is worth an exception
      // on the launch path.
      expect(NotificationTapPayload.decode(null), isNull);
      expect(NotificationTapPayload.decode(''), isNull);
      expect(NotificationTapPayload.decode('not json'), isNull);
      expect(NotificationTapPayload.decode('[1,2,3]'), isNull);
      expect(
        NotificationTapPayload.decode('{"notification_type":"task"}'),
        isNull,
      );
      expect(NotificationTapPayload.decode('{"notification_id":"x"}'), isNull);
      // An empty id is no id: it would mark nothing and resolve nothing, and
      // treating it as present costs a wasted navigation.
      expect(
        NotificationTapPayload.decode(
          '{"notification_type":"task","notification_id":""}',
        ),
        isNull,
      );
    });
  });

  group('actions', () {
    test('a task reminder offers Mark as Read and View Task', () {
      // `NotificationUtils.addNotificationActions` adds *Mark as Read* to
      // every actionable config and then one type-specific action; the `task`
      // arm is `builder.addAction(R.drawable.team, "View Task", …)`.
      expect(notificationActionsFor(taskConfig()), const [
        NotificationAction(
          id: NotificationTapActions.markAsRead,
          title: 'Mark as Read',
        ),
        NotificationAction(id: NotificationTapActions.open, title: 'View Task'),
      ]);
    });

    test('createTaskNotification is actionable, as the Kotlin sets it', () {
      // The field the port dropped. Without it the two buttons never appear,
      // and the only reachable gesture is a body tap.
      expect(taskConfig().actionable, isTrue);
    });

    test('a non-actionable config offers none', () {
      // `buildNotification`'s `if (config.actionable)` gate, which is what
      // makes the default `false` mean something.
      const plain = NotificationConfig(
        id: 'n-1',
        type: 'resource',
        title: 't',
        message: 'm',
        priority: NotificationPriority.defaultPriority,
      );
      expect(notificationActionsFor(plain), isEmpty);
    });
  });

  group('what the plugin hands back', () {
    NotificationResponse response({
      String? actionId,
      String? payload,
      NotificationResponseType type =
          NotificationResponseType.selectedNotification,
    }) => NotificationResponse(
      notificationResponseType: type,
      actionId: actionId,
      payload: payload,
    );

    final payload = NotificationTapPayload.forConfig(taskConfig()).encode();

    test('a body tap has no action id', () {
      final tap = notificationTapFrom(response(payload: payload));
      expect(tap, isNotNull);
      expect(tap!.isBodyTap, isTrue);
      expect(tap.actionId, isNull);
    });

    test('an empty action id is a body tap, not an unknown action', () {
      // Android delivers `""` rather than null on some paths, and reading that
      // as an unknown action would drop the tap entirely.
      final tap = notificationTapFrom(response(actionId: '', payload: payload));
      expect(tap?.isBodyTap, isTrue);
    });

    test('each known action arrives as itself', () {
      for (final id in const [
        NotificationTapActions.markAsRead,
        NotificationTapActions.open,
      ]) {
        expect(
          notificationTapFrom(
            response(actionId: id, payload: payload),
          )?.actionId,
          id,
        );
      }
    });

    test('a dismissal is not a tap', () {
      // The plugin reports a swipe-away through the same callback; Android's
      // `PendingIntent` machinery never delivers one at all. Treating it as a
      // tap would mark a notification read the user deliberately ignored — and
      // on the stamping path, reorder their whole list to say so.
      expect(
        notificationTapFrom(
          response(
            payload: payload,
            type: NotificationResponseType.notificationDismissed,
          ),
        ),
        isNull,
      );
    });

    test('an action this build does not know is dropped', () {
      expect(
        notificationTapFrom(
          response(actionId: 'storage_settings', payload: payload),
        ),
        isNull,
      );
    });

    test('a response with no payload is dropped', () {
      expect(notificationTapFrom(response()), isNull);
      expect(notificationTapFrom(null), isNull);
    });
  });
}
