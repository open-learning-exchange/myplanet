import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../data/local/app_database.dart';
import '../data/local/feedback_mapper.dart';
import '../data/local/user_mapper.dart';
import 'app_providers.dart';
import 'session_provider.dart';

/// Provider for the current user's feedback list.
final feedbackListProvider = StreamProvider<List<FeedbackRow>>((ref) {
  final repo = ref.watch(feedbackRepositoryProvider);
  final session = ref.watch(sessionProvider).valueOrNull;

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

  Future<bool> submit({String? item, String? feedbackState}) async {
    final session = ref.read(sessionProvider).valueOrNull;
    if (session == null) {
      state = state.copyWith(error: 'Not logged in');
      return false;
    }

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
      await ref
          .read(feedbackRepositoryProvider)
          .createFeedback(
            user: session.name ?? '',
            priority: state.priority,
            type: state.type,
            message: state.message,
            item: item,
            state: feedbackState,
          );
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
}

final feedbackCreateProvider =
    NotifierProvider<FeedbackCreateNotifier, FeedbackCreateState>(
      FeedbackCreateNotifier.new,
    );
