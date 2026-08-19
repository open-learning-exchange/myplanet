import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../l10n/app_localizations.dart';
import '../../providers/app_providers.dart';
import '../../repository/configurations_repository.dart';

/// Port of the server-address half of `ui/sync/SyncActivity.kt` +
/// `ServerDialogExtensions.kt`.
///
/// The Kotlin presents this as an AlertDialog built imperatively over a
/// `LayoutInflater`-inflated view; here it is a route, which is what makes the
/// "configured yet?" redirect in the router possible.
class ServerConfigScreen extends ConsumerStatefulWidget {
  const ServerConfigScreen({super.key});

  @override
  ConsumerState<ServerConfigScreen> createState() => _ServerConfigScreenState();
}

class _ServerConfigScreenState extends ConsumerState<ServerConfigScreen> {
  final _formKey = GlobalKey<FormState>();
  final _urlController = TextEditingController();
  final _pinController = TextEditingController();

  bool _isChecking = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    final existing = ref.read(serverConfigProvider);
    if (existing != null) {
      _urlController.text = existing.serverUrl;
      _pinController.text = existing.pin;
    }
  }

  @override
  void dispose() {
    _urlController.dispose();
    _pinController.dispose();
    super.dispose();
  }

  Future<void> _connect() async {
    if (!(_formKey.currentState?.validate() ?? false)) return;

    setState(() {
      _isChecking = true;
      _error = null;
    });

    final result = await ref
        .read(configurationsRepositoryProvider)
        .getMinApk(_urlController.text.trim(), _pinController.text.trim());

    if (!mounted) return;

    switch (result) {
      case ConfigurationSuccess(:final config, :final versionDetail):
        await ref.read(serverConfigProvider.notifier).save(config);
        if (versionDetail != null) {
          // Port of `SharedPrefManager.setVersionDetail` — the raw `/versions`
          // body, cached so the telemetry upload can echo `planetVersion`.
          await ref.read(planetPrefsProvider).setVersionDetail(versionDetail);
        }
      // The router redirect takes it from here.
      case ConfigurationFailure(:final reason):
        setState(() {
          _isChecking = false;
          _error = _messageFor(AppLocalizations.of(context), reason);
        });
    }
  }

  static String _messageFor(
    AppLocalizations l10n,
    ConfigurationFailureReason reason,
  ) {
    return switch (reason) {
      ConfigurationFailureReason.localServerUnreachable =>
        l10n.deviceCouldNotReachLocalServer,
      ConfigurationFailureReason.nationServerUnreachable =>
        l10n.deviceCouldNotReachNationServer,
    };
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);

    return Scaffold(
      appBar: AppBar(title: Text(l10n.serverConfigurationTitle)),
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
                    TextFormField(
                      controller: _urlController,
                      autocorrect: false,
                      keyboardType: TextInputType.url,
                      textInputAction: TextInputAction.next,
                      decoration: InputDecoration(
                        labelText: l10n.serverUrlLabel,
                        hintText: l10n.serverUrlHint,
                        border: const OutlineInputBorder(),
                      ),
                      validator: (value) {
                        final text = value?.trim() ?? '';
                        if (text.isEmpty) return l10n.serverUrlNotConfigured;
                        final uri = Uri.tryParse(text);
                        if (uri == null ||
                            !uri.hasScheme ||
                            uri.host.isEmpty ||
                            !(uri.scheme == 'http' || uri.scheme == 'https')) {
                          return l10n.serverUrlNotConfigured;
                        }
                        return null;
                      },
                    ),
                    const SizedBox(height: 16),
                    TextFormField(
                      controller: _pinController,
                      obscureText: true,
                      keyboardType: TextInputType.number,
                      textInputAction: TextInputAction.done,
                      decoration: InputDecoration(
                        labelText: l10n.serverPinLabel,
                        border: const OutlineInputBorder(),
                      ),
                      onFieldSubmitted: (_) => _connect(),
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
                      onPressed: _isChecking ? null : _connect,
                      child: _isChecking
                          ? const SizedBox.square(
                              dimension: 20,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            )
                          : Text(l10n.connect),
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
