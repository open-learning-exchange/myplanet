import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../l10n/app_localizations.dart';
import '../../providers/app_providers.dart';
import '../../providers/session_provider.dart';
import '../../repository/user_repository.dart';
import '../router.dart';

/// Port of `ui/sync/LoginActivity.kt` (the credential form only — the guest and
/// become-member flows come with the `ui/user` package).
class LoginScreen extends ConsumerStatefulWidget {
  const LoginScreen({super.key});

  @override
  ConsumerState<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends ConsumerState<LoginScreen> {
  final _formKey = GlobalKey<FormState>();
  final _nameController = TextEditingController();
  final _passwordController = TextEditingController();

  bool _isSubmitting = false;
  String? _error;

  @override
  void dispose() {
    _nameController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  /// [offline] skips the network entirely and checks the cached credentials,
  /// mirroring `UserRepositoryImpl.authenticateUser`.
  Future<void> _submit({required bool offline}) async {
    if (!(_formKey.currentState?.validate() ?? false)) return;

    setState(() {
      _isSubmitting = true;
      _error = null;
    });

    final repository = ref.read(userRepositoryProvider);
    final username = _nameController.text.trim();
    final password = _passwordController.text;
    final config = ref.read(serverConfigProvider);

    final result = offline || config == null
        ? await repository.loginOffline(username: username, password: password)
        : await repository.loginOnline(
            config: config,
            username: username,
            password: password,
          );

    if (!mounted) return;

    switch (result) {
      case LoginSuccess(:final user):
        await ref
            .read(sessionProvider.notifier)
            .signIn(user, password: password);
      // The router redirect navigates on to the resources list.
      case LoginFailure(:final reason):
        setState(() {
          _isSubmitting = false;
          _error = _messageFor(AppLocalizations.of(context), reason);
        });
    }
  }

  static String _messageFor(AppLocalizations l10n, LoginFailureReason reason) {
    return switch (reason) {
      LoginFailureReason.emptyCredentials => l10n.emptyCredentials,
      LoginFailureReason.invalidCredentials => l10n.invalidCredentials,
      LoginFailureReason.userNotFound => l10n.userNotFound,
      LoginFailureReason.missingAuthData => l10n.missingAuthData,
      LoginFailureReason.serverError => l10n.serverError,
      LoginFailureReason.network => l10n.networkError,
      LoginFailureReason.notAManager => l10n.notAManager,
    };
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final config = ref.watch(serverConfigProvider);

    return Scaffold(
      appBar: AppBar(
        title: Text(l10n.login),
        actions: [
          TextButton(
            onPressed: () => ref.read(serverConfigProvider.notifier).clear(),
            child: Text(l10n.changeServer),
          ),
        ],
      ),
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 480),
              child: Form(
                key: _formKey,
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    if (config != null && config.code.isNotEmpty) ...[
                      Text(
                        l10n.connectedToCommunity(config.code),
                        style: Theme.of(context).textTheme.bodyMedium,
                        textAlign: TextAlign.center,
                      ),
                      const SizedBox(height: 24),
                    ],
                    TextFormField(
                      controller: _nameController,
                      autocorrect: false,
                      textInputAction: TextInputAction.next,
                      decoration: InputDecoration(
                        labelText: l10n.name,
                        border: const OutlineInputBorder(),
                      ),
                      validator: (value) => (value?.trim().isEmpty ?? true)
                          ? l10n.emptyCredentials
                          : null,
                    ),
                    const SizedBox(height: 16),
                    TextFormField(
                      controller: _passwordController,
                      obscureText: true,
                      textInputAction: TextInputAction.done,
                      decoration: InputDecoration(
                        labelText: l10n.password,
                        border: const OutlineInputBorder(),
                      ),
                      validator: (value) => (value?.isEmpty ?? true)
                          ? l10n.emptyCredentials
                          : null,
                      onFieldSubmitted: (_) => _submit(offline: false),
                    ),
                    const SizedBox(height: 24),
                    if (_error != null) ...[
                      Text(
                        _error!,
                        style: TextStyle(
                          color: Theme.of(context).colorScheme.error,
                        ),
                      ),
                      const SizedBox(height: 16),
                    ],
                    FilledButton(
                      onPressed: _isSubmitting
                          ? null
                          : () => _submit(offline: false),
                      child: _isSubmitting
                          ? const SizedBox.square(
                              dimension: 20,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            )
                          : Text(l10n.login),
                    ),
                    const SizedBox(height: 8),
                    TextButton(
                      onPressed: _isSubmitting
                          ? null
                          : () => _submit(offline: true),
                      child: Text(l10n.signInOffline),
                    ),
                    // Kotlin puts this on `LoginActivity`. The screen was
                    // built and routed with nothing pointing at it.
                    TextButton(
                      onPressed: _isSubmitting
                          ? null
                          : () => context.push(Routes.becomeMember),
                      child: Text(l10n.becomeMember),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
