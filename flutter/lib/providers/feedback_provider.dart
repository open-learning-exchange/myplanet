import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_riverpod/legacy.dart';

import '../core/config/server_config.dart';
import '../core/sync/sync_result.dart';
import '../data/local/app_database.dart';
import '../data/local/feedback_mapper.dart';
import '../data/local/user_mapper.dart';
import 'app_providers.dart';
import 'session_provider.dart';
import 'sync_state.dart';

/// Provider for the current user's feedback list.
final feedbackListProvider = StreamProvider<List<FeedbackRow>>((ref) {
  final repo = ref.watch(feedbackRepositoryProvider);
  final session = ref.watch(sessionProvider).value;

  if (session != null && UserMapper.isManager(session) == true) {
    return repo.getFeedback(isManager: true);
  }
  return repo.getFeedback(userName: session?.name);
});

/// Provider for a single feedback by id.
final feedbackByIdProvider = FutureProvider.family<FeedbackRow?, String>((
  ref,
  id,
) async {
  final repo = ref.watch(feedbackRepositoryProvider);
  return repo.getFeedbackById(id);
});

/// Feedback list filter state.
class FeedbackFilter {
  const FeedbackFilter({
    this.status = 'all',
    this.priority = 'all',
    this.type = 'all',
    this.searchQuery = '',
  });

  final String status;
  final String priority;
  final String type;
  final String searchQuery;

  FeedbackFilter copyWith({
    String? status,
    String? priority,
    String? type,
    String? searchQuery,
  }) {
    return FeedbackFilter(
      status: status ?? this.status,
      priority: priority ?? this.priority,
      type: type ?? this.type,
      searchQuery: searchQuery ?? this.searchQuery,
    );
  }
}

final feedbackFilterProvider = StateProvider<FeedbackFilter>((ref) {
  return const FeedbackFilter();
});

/// Provider for filtered feedback list.
final filteredFeedbackProvider = Provider<AsyncValue<List<FeedbackRow>>>((ref) {
  final feedbackList = ref.watch(feedbackListProvider);
  final filter = ref.watch(feedbackFilterProvider);

  return feedbackList.whenData((items) {
    return items.where((feedback) {
      // Status filter
      if (filter.status != 'all') {
        if (feedback.status?.toLowerCase() != filter.status.toLowerCase()) {
          return false;
        }
      }

      // Priority filter
      if (filter.priority != 'all') {
        if (feedback.priority?.toLowerCase() != filter.priority.toLowerCase()) {
          return false;
        }
      }

      // Type filter
      if (filter.type != 'all') {
        if (feedback.type?.toLowerCase() != filter.type.toLowerCase()) {
          return false;
        }
      }

      // Search filter
      if (filter.searchQuery.isNotEmpty) {
        final query = filter.searchQuery.toLowerCase();
        final title = feedback.title?.toLowerCase() ?? '';
        final message = FeedbackMapper.getFirstMessage(
          feedback.messages,
        ).toLowerCase();
        if (!title.contains(query) && !message.contains(query)) {
          return false;
        }
      }

      return true;
    }).toList();
  });
});

/// Hands every un-uploaded feedback row to the durable outbox.
///
/// Enqueuing rather than posting directly is what makes filing feedback work
/// offline: the row and the queue entry are both on disk, and the drain runs
/// when the app next resumes with a reachable server.
///
/// This is deliberately reachable outside the create form. A reply or a close
/// sets `isUploaded = false` on an existing row, and the uploader queues by
/// pending state rather than by what changed — so those paths need the same
/// call. Without it they leave a row marked un-uploaded that nothing collects.
class FeedbackQueue {
  const FeedbackQueue(this._ref);

  final Ref _ref;

  Future<int> queuePending() async {
    final config = _ref.read(serverConfigProvider);
    if (config == null) return 0;
    return _ref
        .read(feedbackUploaderProvider)
        .queuePending(config: config, userId: await _signedInUserId());
  }

  /// The filer's id, or null when nobody is signed in.
  ///
  /// `outbox.userId` is nullable and nothing in `OutboxDrainer` reads it, so
  /// null is a legitimate value here rather than a failure — which is exactly
  /// why this was `read(sessionProvider).value` and looked harmless. It is
  /// not: on a screen that never watches the provider the two nulls are
  /// indistinguishable, and "nobody is signed in" would have been reported for
  /// a session that had simply not finished loading. Awaiting the future
  /// answers the question that was actually asked.
  ///
  /// The `catch` keeps this the *un*important half it is meant to be: a
  /// rejecting session must not fail a queue pass, because the payload it is
  /// enqueuing is already on disk and does not depend on the answer.
  Future<String?> _signedInUserId() async {
    try {
      return (await _ref.read(sessionProvider.future))?.id;
    } catch (_) {
      return null;
    }
  }
}

final feedbackQueueProvider = Provider<FeedbackQueue>(FeedbackQueue.new);

/// Pull of the `feedback` database, giving `FeedbackRepository.sync` its first
/// caller. Without it the list only ever showed threads filed on this device —
/// a reply written on the web or by a manager elsewhere never appeared.
class FeedbackSyncNotifier extends SyncNotifier {
  @override
  Future<SyncResult> runSync(
    ServerConfig config,
    void Function(SyncProgress) onProgress,
  ) async {
    final result = await ref
        .read(feedbackRepositoryProvider)
        .sync(config: config, onProgress: onProgress);

    // The pull merges an admin's replies into a thread this device has not
    // finished uploading (`FeedbackMapper._mergePendingReplies`), but the
    // outbox holds a *snapshot* of the payload taken when the reply was
    // written — and nothing else re-queues feedback, so that snapshot would
    // drain the pre-merge array over the server's document and undo the merge
    // there. Re-queuing refreshes it: `OutboxRepository.enqueue` replaces the
    // payload of the row this item already owns, and puts an in-flight row
    // back to `pending` so the edit still goes out.
    if (result is SyncComplete) {
      await ref.read(feedbackQueueProvider).queuePending();
    }
    return result;
  }
}

final feedbackSyncProvider =
    NotifierProvider<FeedbackSyncNotifier, SyncUiState>(
      FeedbackSyncNotifier.new,
    );

/// State for feedback creation.
class FeedbackCreateState {
  const FeedbackCreateState({
    this.priority = 'No',
    this.type = '',
    this.message = '',
    this.isSubmitting = false,
    this.error,
  });

  final String priority; // 'Yes' or 'No'
  final String type; // 'Question', 'Bug', 'Suggestion'
  final String message;
  final bool isSubmitting;
  final String? error;

  FeedbackCreateState copyWith({
    String? priority,
    String? type,
    String? message,
    bool? isSubmitting,
    String? error,
  }) {
    return FeedbackCreateState(
      priority: priority ?? this.priority,
      type: type ?? this.type,
      message: message ?? this.message,
      isSubmitting: isSubmitting ?? this.isSubmitting,
      error: error,
    );
  }
}

/// Notifier for feedback creation form.
class FeedbackCreateNotifier extends Notifier<FeedbackCreateState> {
  @override
  FeedbackCreateState build() => const FeedbackCreateState();

  void setPriority(String priority) {
    state = state.copyWith(priority: priority);
  }

  void setType(String type) {
    state = state.copyWith(type: type);
  }

  void setMessage(String message) {
    state = state.copyWith(message: message);
  }

  /// Files the feedback, **with or without a signed-in user**.
  ///
  /// The session is a value written onto the document, not a precondition, and
  /// Kotlin is unambiguous about it: `FeedbackComposerViewModel.kt:38` is
  /// `val user = userRepository.getUserModel()?.name ?: ""` — an elvis
  /// default, with no early return, no toast and no error event anywhere on
  /// the path. `FeedbackRepositoryImpl.createFeedback:57-58,66` writes that
  /// string straight into `owner`, `source` and `messages[0].user`, the row is
  /// saved unconditionally (`:115-117`), and the upload authenticates with the
  /// *server* credential (`UrlUtils.header`), never the user's. So a document
  /// filed with nobody signed in carries `owner: ""` and reaches the server.
  ///
  /// This used to refuse — `'Not logged in'`, with the screen's Submit button
  /// disabled behind the same test — which made the login screen's feedback
  /// button unportable: the one affordance for a user who cannot get past the
  /// front door led to a form that could not be sent.
  ///
  /// **One deliberate divergence, and it is the honest direction.** Kotlin
  /// resolves the name through `UserRepositoryImpl.getUserModel`, which reads
  /// the `userId` preference — and nothing clears that preference on logout
  /// (`UserSessionManager.logoutAsync` only writes an activity row, and
  /// `DashboardElementActivity.logout` clears the `SecurePrefs` credentials
  /// and the logged-in flag without touching `USER_ID`; the only `clear()` is
  /// `SharedPrefManager.clearPreferences`, whose two callers are
  /// `SyncActivity.clearDataDialog` and `SettingsViewModel` via
  /// `ConfigurationsRepositoryImpl`). So after a logout the Android app files
  /// the *previous* user's name against feedback typed by whoever is holding
  /// the phone now. `SessionNotifier.signOut` clears the session here, so this
  /// writes `''` in that case. Matching Kotlin would mean attributing one
  /// person's words to another, which is a bug to leave behind rather than a
  /// behaviour to reproduce.
  ///
  /// **It has a cost, and it is the same one Kotlin pays on a fresh install.**
  /// The list is `getByOwnerFlow(user.name)` for a non-manager, so a thread
  /// filed with `owner: ''` never appears to its author — not even after they
  /// sign in. Only a manager's all-threads view shows it. Kotlin's post-logout
  /// attribution does make the thread visible to *someone*, which is the one
  /// respect in which its behaviour is better than this.
  Future<bool> submit({String? item, String? feedbackState}) async {
    if (state.message.isEmpty) {
      state = state.copyWith(error: 'Please enter feedback');
      return false;
    }

    if (state.type.isEmpty) {
      state = state.copyWith(error: 'Please select a type');
      return false;
    }

    state = state.copyWith(isSubmitting: true, error: null);

    try {
      // Inside the `try`, and that placement is the port's standing rule
      // rather than style: a future can reject where `.value` could only be
      // null, and Kotlin's own `catch` turns exactly this failure into
      // `SubmitEvent.Error` (`FeedbackComposerViewModel.kt:41-43`).
      final session = await ref.read(sessionProvider.future);
      await ref
          .read(feedbackRepositoryProvider)
          .createFeedback(
            user: session?.name ?? '',
            priority: state.priority,
            type: state.type,
            message: state.message,
            item: item,
            state: feedbackState,
          );
      await queuePending();
      state = const FeedbackCreateState();
      return true;
    } catch (e) {
      state = state.copyWith(isSubmitting: false, error: e.toString());
      return false;
    }
  }

  void reset() {
    state = const FeedbackCreateState();
  }

  /// Hands every un-uploaded feedback to the durable outbox.
  Future<int> queuePending() => ref.read(feedbackQueueProvider).queuePending();
}

final feedbackCreateProvider =
    NotifierProvider<FeedbackCreateNotifier, FeedbackCreateState>(
      FeedbackCreateNotifier.new,
    );
