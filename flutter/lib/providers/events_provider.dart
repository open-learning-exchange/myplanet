import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../data/local/app_database.dart';
import 'app_providers.dart';
import 'session_provider.dart';

final eventsProvider = StreamProvider<List<MeetupRow>>(
  (ref) => ref.watch(eventsRepositoryProvider).watchAll(),
);

final meetupProvider = FutureProvider.family<MeetupRow?, String>(
  (ref, id) => ref.watch(eventsRepositoryProvider).getById(id),
);

class EventsActions {
  EventsActions(this.ref);
  final Ref ref;

  Future<String?> save({
    String? id,
    required String title,
    required String description,
    required int startDate,
    required int endDate,
    required String startTime,
    required String endTime,
    required String location,
    required String link,
    required String recurring,
  }) async {
    final repository = ref.read(eventsRepositoryProvider);
    String? savedId = id;
    if (id == null) {
      final user = ref.read(sessionProvider).valueOrNull;
      savedId = await repository.create(
        title: title,
        description: description,
        startDate: startDate,
        endDate: endDate,
        startTime: startTime,
        endTime: endTime,
        location: location,
        link: link,
        recurring: recurring,
        creator: user?.name ?? user?.id ?? '',
        sourcePlanet: user?.planetCode,
      );
    } else {
      final success = await repository.update(
        id,
        title: title,
        description: description,
        startDate: startDate,
        endDate: endDate,
        startTime: startTime,
        endTime: endTime,
        location: location,
        link: link,
        recurring: recurring,
      );
      if (!success) return null;
    }
    await queuePending();
    if (savedId != null) ref.invalidate(meetupProvider(savedId));
    return savedId;
  }

  Future<void> toggleAttendance(MeetupRow row) async {
    final user = ref.read(sessionProvider).valueOrNull;
    await ref
        .read(eventsRepositoryProvider)
        .toggleAttendance(row.meetupId ?? row.id, user?.id);
    ref.invalidate(meetupProvider(row.id));
  }

  Future<int> queuePending() async {
    final config = ref.read(serverConfigProvider);
    if (config == null) return 0;
    return ref
        .read(eventsUploaderProvider)
        .queuePending(
          config: config,
          userId: ref.read(sessionProvider).valueOrNull?.id,
        );
  }
}

final eventsActionsProvider = Provider<EventsActions>(EventsActions.new);
