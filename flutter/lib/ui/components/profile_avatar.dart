import 'dart:io';

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
    // A locally-picked photo is stored as a filesystem path (see
    // `SessionNotifier.setUserImage`), not a CouchDB attachment name. Showing
    // it straight from disk avoids a pointless network round-trip and renders
    // immediately — the upload is async and the attachment name only replaces
    // the path once the PUT succeeds.
    if (_isLocalPath(imageName)) {
      return _LocalFileAvatar(path: imageName, user: user, radius: radius);
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

/// Whether [value] is a filesystem path rather than a CouchDB attachment name.
///
/// `image_picker` always returns an absolute path (`/` on every platform) or
/// a `file://` uri, and the upload slice only stores what the picker returned.
/// A bare attachment name from a prior sync is neither, so it falls through to
/// the network fetch.
bool _isLocalPath(String value) =>
    value.startsWith('/') || value.startsWith('file://');

class _LocalFileAvatar extends StatelessWidget {
  const _LocalFileAvatar({
    required this.path,
    required this.user,
    required this.radius,
  });

  final String path;
  final UserRow user;
  final double radius;

  @override
  Widget build(BuildContext context) {
    final file = File(path);
    return FutureBuilder<bool>(
      future: file.exists(),
      builder: (context, snapshot) {
        final exists = snapshot.data ?? false;
        if (!exists) return _InitialsAvatar(user: user, radius: radius);
        return CircleAvatar(
          radius: radius,
          backgroundColor: Theme.of(context).colorScheme.primaryContainer,
          child: ClipOval(child: Image.file(file, fit: BoxFit.cover)),
        );
      },
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

/// The first character of a bare display-name string, upper-cased, with `?`
/// for a name that is absent, empty, or whitespace-only.
///
/// `''.characters.first` throws `StateError`, so a `name ?? '?'` guard is not
/// enough — a synced row can carry an empty string as readily as a null. Use
/// this wherever the only thing to hand is a name rather than a [UserRow];
/// prefer [ProfileAvatar] when a [UserRow] is available.
String initialFor(String? name) {
  final trimmed = (name ?? '').trim();
  return trimmed.isEmpty ? '?' : trimmed.characters.first.toUpperCase();
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
