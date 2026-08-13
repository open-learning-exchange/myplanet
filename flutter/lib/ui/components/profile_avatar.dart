import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../data/local/app_database.dart';
import '../../providers/dashboard_providers.dart';

/// Port of the profile-photo avatar shown by `BellDashboardFragment`,
/// `UserProfileFragment`, and the member/voices/health adapters in Kotlin.
///
/// A `_users` document stores the photo as a CouchDB attachment behind Basic
/// auth, so it cannot be loaded with `Image.network`. [ProfileAvatar] watches
/// [profileImageProvider] (which fetches the bytes through the authenticated
/// `PlanetApi.getBytes` path) and renders `Image.memory` on success, falling
/// back to the user's initials exactly as Kotlin falls back to
/// `R.drawable.profile`.
///
/// Pass [userImage] from `users.userImage` (the attachment name set by
/// `UserMapper`); when it is blank the avatar renders synchronously with no
/// network attempt, which is the path for guests and accounts with no photo.
class ProfileAvatar extends ConsumerWidget {
  const ProfileAvatar({required this.user, this.radius = 24, super.key});

  /// The user whose photo is shown. `id` is the `_users` document id;
  /// `userImage` is the attachment name; the name fields drive the fallback
  /// initials.
  final UserRow user;
  final double radius;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final imageName = user.userImage?.trim() ?? '';
    if (imageName.isEmpty) {
      return _InitialsAvatar(user: user, radius: radius);
    }
    final image = ref.watch(
      profileImageProvider(
        ProfileImageRequest(userId: user.id, imageName: imageName),
      ),
    );
    return CircleAvatar(
      radius: radius,
      backgroundColor: Theme.of(context).colorScheme.primaryContainer,
      child: image.when(
        data: (bytes) => bytes == null || bytes.isEmpty
            ? _InitialsAvatar(user: user, radius: radius)
            : ClipOval(child: Image.memory(bytes, fit: BoxFit.cover)),
        loading: () => _InitialsAvatar(user: user, radius: radius),
        error: (_, _) => _InitialsAvatar(user: user, radius: radius),
      ),
    );
  }
}

/// The initials placeholder shown while the photo loads, when it is absent,
/// or when the fetch fails. Matches Kotlin's `R.drawable.profile` fallback.
class _InitialsAvatar extends StatelessWidget {
  const _InitialsAvatar({required this.user, required this.radius});

  final UserRow user;
  final double radius;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return CircleAvatar(
      radius: radius,
      backgroundColor: theme.colorScheme.primaryContainer,
      child: Text(
        _initials(user),
        style: theme.textTheme.headlineSmall?.copyWith(
          color: theme.colorScheme.onPrimaryContainer,
          fontSize: radius,
        ),
      ),
    );
  }
}

/// The user's display name, falling back to the username and then to a
/// generic label — shared with the profile screen's own copy.
String displayName(UserRow user) {
  final parts = [user.firstName, user.middleName, user.lastName]
      .whereType<String>()
      .map((part) => part.trim())
      .where((part) => part.isNotEmpty);
  final fullName = parts.join(' ');
  if (fullName.isNotEmpty) return fullName;
  final username = user.name?.trim();
  return username == null || username.isEmpty ? 'myPlanet learner' : username;
}

/// Up to two initials (first + last name), falling back to the first letter
/// of the username, then `MP` — shared with the profile screen's own copy.
String _initials(UserRow user) {
  final parts = [user.firstName, user.lastName]
      .whereType<String>()
      .map((part) => part.trim())
      .where((part) => part.isNotEmpty)
      .toList();
  if (parts.isNotEmpty) {
    return parts.map((part) => part.characters.first.toUpperCase()).join();
  }
  final username = user.name?.trim();
  return username == null || username.isEmpty
      ? 'MP'
      : username.characters.first.toUpperCase();
}
