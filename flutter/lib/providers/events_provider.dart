import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../data/local/app_database.dart';
import '../core/sync/sync_result.dart';
import 'app_providers.dart';
import 'session_provider.dart';
import 'sync_state.dart';

enum EventsSort {
  dateAscending,
  dateDescending,
  titleAscending,
  titleDescending,
}

final eventsSearchProvider = StateProvider<String>((ref) => '');
final eventsSortProvider = StateProvider<EventsSort>(
  (ref) => EventsSort.dateDescending,
);

final eventsProvider = StreamProvider<List<MeetupRow>>((ref) async* {
  final query = ref.watch(eventsSearchProvider).trim().toLowerCase();
  final sort = ref.watch(eventsSortProvider);
  await for (final cached in ref.watch(eventsRepositoryProvider).watchAll()) {
    final rows = cached
        .where(
          (row) =>
              query.isEmpty ||
              (row.title ?? '').toLowerCase().contains(query) ||
              (row.description ?? '').toLowerCase().contains(query) ||
              (row.meetupLocation ?? '').toLowerCase().contains(query),
        )
        .toList();
    rows.sort(switch (sort) {
      EventsSort.dateAscending => (a, b) => a.startDate.compareTo(b.startDate),
      EventsSort.dateDescending => (a, b) => b.startDate.compareTo(a.startDate),
      EventsSort.titleAscending =>
        (a, b) => (a.title ?? '').toLowerCase().compareTo(
          (b.title ?? '').toLowerCase(),
        ),
      EventsSort.titleDescending =>
        (a, b) => (b.title ?? '').toLowerCase().compareTo(
          (a.title ?? '').toLowerCase(),
        ),
    });
    yield rows;
  }
});

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
    final config = ref.read(serverConfigProvider);
    if (config != null && user?.couchId?.isNotEmpty == true) {
      await ref
          .read(shelfRepositoryProvider)
          .upload(config: config, userId: user!.id, shelfDocId: user.couchId!);
    }
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

class EventsSyncNotifier extends SyncNotifier {
  @override
  Future<SyncResult> runSync(config, void Function(SyncProgress) onProgress) =>
      ref
          .read(eventsRepositoryProvider)
          .sync(config: config, onProgress: onProgress);
}

final eventsSyncProvider = NotifierProvider<EventsSyncNotifier, SyncUiState>(
  EventsSyncNotifier.new,
);
