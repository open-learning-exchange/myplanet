import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/config/planet_servers.dart';
import '../../core/config/server_config.dart';
import '../../core/utils/url_utils.dart';
import '../../l10n/app_localizations.dart';
import '../../providers/app_providers.dart';
import '../../providers/settings_provider.dart';
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
  bool _showAllServers = false;
  String? _error;

  /// Port of `ServerAddressAdapter.selectedPosition`, keyed by host rather
  /// than by index because the list is reordered on every `setState` and an
  /// index does not survive that. `-1` becomes `null`.
  ///
  /// Kotlin's companion `lastSelectedPosition` and its `revertSelection` are
  /// **not** ported, and the reason is where the gate had to move to: they
  /// exist only to undo a selection when the user declines the clear-data
  /// dialog, and in this port that dialog is raised from the Connect button
  /// rather than from the row tap. Porting them would have added an undo with
  /// no caller. See `_connect` for why the gate sits there.
  String? _selectedHost;

  /// The host of the persisted configuration, when there is one. Kotlin's
  /// `urlWithoutProtocol` (`ServerDialogExtensions.kt:172`), which drives both
  /// the initial selection and the list ordering.
  String? _configuredHost;

  /// Whether the local database holds synced data at all. Read once, because
  /// after a wipe it is stale by construction:
  /// [deviceHoldsServerDataProvider] reads a preference off an object whose
  /// identity does not change when the preference does.
  bool _holdsServerData = false;

  /// Which request failed and what it said. Debug builds only — a release
  /// build keeps the one clean sentence.
  String? _diagnostic;

  @override
  void initState() {
    super.initState();
    _holdsServerData = ref.read(deviceHoldsServerDataProvider);
    final existing = ref.read(serverConfigProvider);
    if (existing != null) {
      _urlController.text = existing.serverUrl;
      _pinController.text = existing.pin;
      // Port of the `submitList` completion callback
      // (`ServerDialogExtensions.kt:196-205`): the initial selection is
      // resolved only once the list exists, and only for a stored URL. The
      // `!syncFailed` half of that guard has no counterpart — `syncFailed` is
      // a `SyncActivity` field set when a sync attempt failed, and the port's
      // route carries no such state.
      _configuredHost = hostWithoutScheme(existing.serverUrl);
      _selectedHost = _configuredHost;
    }
  }

  /// Where the clear-data gate lives, because this is where the switch is
  /// actually committed: `serverConfigProvider.save(config)` is the line that
  /// makes this device belong to the server in the fields, whether those
  /// fields were filled by a row tap or typed by hand.
  ///
  /// Kotlin gates its own commit twice over. In list mode the row tap *is* the
  /// submit and every tap of a row other than the selected one raises
  /// `clearDataDialog` (`ServerAddressAdapter.kt:88-92`); to type a URL at all
  /// you must switch manual configuration **on**, and doing that over an
  /// existing configuration raises the same dialog before you can type
  /// (`ServerDialogExtensions.kt:268-274`). So there is no way to reach a new
  /// server's `configurations` document in Kotlin without having been offered
  /// the wipe. This port's form is always editable, so the equivalent single
  /// rule is: never adopt a configuration over another community's data.
  ///
  /// The comparison is the **community code**, not the host. Kotlin has this
  /// exact comparison as its other clear-data trigger — `clearDataDialog` from
  /// `SyncConfigurationCoordinator` when the `minapk` check succeeded and the
  /// server answered with a `configurations` id other than the stored one
  /// (`SyncActivity.kt:245`) — and it is the more correct question anyway,
  /// because a Planet's clone URL is a different host serving the same
  /// community and nothing should be wiped for switching to it. It also
  /// survives the thing that destroys the host: "change server" clears the
  /// persisted config, but `users.planetCode` is in the database this is
  /// protecting. See [localPlanetCodesProvider].
  Future<void> _connect() async {
    if (!(_formKey.currentState?.validate() ?? false)) return;

    setState(() {
      _isChecking = true;
      _error = null;
      _diagnostic = null;
    });

    final result = await ref
        .read(configurationsRepositoryProvider)
        .getMinApk(_urlController.text.trim(), _pinController.text.trim());

    if (!mounted) return;

    switch (result) {
      case ConfigurationSuccess(:final config, :final versionDetail):
        // Everything from here can throw — `saveServerConfig` writes to secure
        // storage, which raises `PlatformException` on a keystore fault — and
        // `_isChecking` is deliberately left set on success, because the
        // router's redirect navigates away. Without the guard a throw on any
        // of these lines leaves the Connect button spinning for ever with
        // nothing said: the same failure the repository's own try/catch was
        // added for, one layer up.
        try {
          if (await _wipeRefusedFor(config)) return;
          await ref.read(serverConfigProvider.notifier).save(config);
          if (versionDetail != null) {
            // Port of `SharedPrefManager.setVersionDetail` — the raw
            // `/versions` body, cached so the telemetry upload can echo
            // `planetVersion`.
            await ref.read(planetPrefsProvider).setVersionDetail(versionDetail);
          }
          // The router redirect takes it from here.
        } catch (error) {
          if (!mounted) return;
          setState(() {
            _isChecking = false;
            _error = AppLocalizations.of(context).operationFailed;
            _diagnostic = UrlUtils.redactCredentials('$error');
          });
        }
      case ConfigurationFailure(:final reason, :final diagnostic):
        setState(() {
          _isChecking = false;
          _error = _messageFor(AppLocalizations.of(context), reason);
          _diagnostic = diagnostic;
        });
    }
  }

  /// Whether the switch must stop: this device holds another community's data
  /// and the user declined to clear it. Returns `false` — carry on — both when
  /// no wipe is needed and when one was carried out.
  Future<bool> _wipeRefusedFor(ServerConfig config) async {
    if (!_holdsServerData) return false;
    final localCodes = await ref.read(localPlanetCodesProvider.future);
    if (!mounted) return true;
    // An empty set means the data cannot be attributed to a community at all,
    // which on a device that has synced is unexpected rather than reassuring —
    // so it is treated as "not this one". Declining costs the user nothing but
    // the switch; assuming a match would risk the mixing this exists to stop.
    if (localCodes.contains(config.code) && config.code.isNotEmpty) {
      return false;
    }

    final cleared = await _showClearDataDialog();
    if (!mounted) return true;
    if (!cleared) {
      setState(() => _isChecking = false);
      return true;
    }
    setState(() {
      _holdsServerData = false;
      _configuredHost = null;
    });
    return false;
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
      ConfigurationFailureReason.pinRejected => l10n.serverPinRejected,
    };
  }

  /// Port of the click listener in `ServerAddressAdapter.onBindViewHolder`
  /// (`ServerAddressAdapter.kt:88-97`), which is **two** branches:
  ///
  /// ```kotlin
  /// if (isServerAlreadyConfigured && position != selectedPosition) {
  ///     onClearDataDialog(serverAddress, position)
  /// } else {
  ///     onItemClick(serverAddress); setSelectedPosition(position)
  /// }
  /// ```
  ///
  /// Only the `else` arm is ported **here**, and that is deliberate: in Kotlin
  /// the row tap *is* the commit — `serverCheck` is `true` and never assigned
  /// (`SyncActivity.kt:122`), the URL and PIN fields are disabled and the
  /// submit button is `GONE` in list mode (`ServerDialogExtensions.kt:211-212`,
  /// `:32`), so tapping a row fills both fields, writes the protocol and fires
  /// the sync. This port has an editable form and a separate Connect button,
  /// so a tap commits nothing and warning about one would warn about nothing.
  /// The warn arm therefore moves to the place that does commit: see
  /// [_connect].
  void _onServerTapped(PlanetServer server) {
    _useServer(server);
    setState(() => _selectedHost = server.host);
  }

  /// Port of `SyncActivity.clearDataDialog(message, config, onCancel)`
  /// (`SyncActivity.kt:270-300`). Returns whether the data was actually
  /// cleared, so the caller can tell Cancel from a wipe that failed.
  ///
  /// **What this cannot do.** Kotlin ends with `restartApp()`, which is
  /// `Intent.makeRestartActivityTask` followed by `Runtime.getRuntime().exit(0)`
  /// (`SyncActivity.kt:831-836`) — a real process kill. Flutter has no
  /// equivalent, and the nearest thing here is what the wipe already does:
  /// empty every table, clear the preferences and secure storage, delete the
  /// downloaded-file tree, and clear the persisted server and session so the
  /// router's `redirect` has nowhere to send the user but this screen.
  ///
  /// What a process death would additionally discard, and this does not:
  /// whatever a provider captured by value rather than by watching
  /// `serverConfigProvider`, the open Drift connection (its tables are empty,
  /// the handle is not new), in-flight futures and timers, and any live
  /// background isolate. An earlier version of this comment concluded from
  /// that list that "none of those hold the old server's documents"; a
  /// second audit pass pointed out that the list omitted the one place that
  /// did — `<appDocuments>/ole/**`, which no wipe in this port had ever
  /// deleted. `ClearDataNotifier` deletes it now. What is left is stale
  /// in-memory state, which the next read replaces.
  Future<bool> _showClearDataDialog() async {
    final cleared = await showDialog<bool>(
      context: context,
      // `setCancelable(false)`, plus the activity's own
      // `guardBackPressWhileDialogShowing()` while the wipe runs.
      barrierDismissible: false,
      builder: (_) => const _ClearDataDialog(),
    );
    return cleared ?? false;
  }

  /// Port of `ServerAddressAdapter`'s `onItemClick`: a tapped row fills the
  /// host **and** its PIN, which is the whole reason this list exists.
  ///
  /// Kotlin additionally submits immediately when its `serverCheck` flag is
  /// set. Deliberately not ported: this is a route rather than a dialog, the
  /// fields stay visible after the tap, and firing a network request from a
  /// list tap hides which server is about to be contacted. One tap of Connect
  /// is the cost.
  void _useServer(PlanetServer server) {
    setState(() {
      _urlController.text = server.url;
      _pinController.text = server.pin;
      _error = null;
      _diagnostic = null;
    });
  }

  Widget _serverPicker(AppLocalizations l10n) {
    final all = ref.watch(planetServersProvider);
    // A build with no `--dart-define`s has no list to offer, which is how this
    // screen behaved before the list existed. See `planet_servers.dart`.
    if (all.isEmpty) return const SizedBox.shrink();

    // The **persisted** configuration, not the text field. Kotlin orders this
    // list from `prefData.getPinnedServerUrl()` / `getServerUrl()`, both
    // stored values; deriving it from the field made the list reorder under
    // the user's finger — tapping row 7 hoisted row 7 into the collapsed
    // four, and typing a host by hand rearranged the rows as the characters
    // arrived.
    final shown = planetServersToShow(
      servers: all,
      showAdditional: _showAllServers,
      configuredHost: _configuredHost,
    );

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Align(
          alignment: AlignmentDirectional.centerStart,
          child: Text(
            l10n.syncToServer,
            style: Theme.of(context).textTheme.titleSmall,
          ),
        ),
        const SizedBox(height: 8),
        for (final server in shown)
          ListTile(
            dense: true,
            contentPadding: EdgeInsets.zero,
            title: Text(server.name),
            subtitle: Text(server.url),
            // `ViewHolder.updateSelectionState`, which paints the selected row
            // `R.color.selected_color`. Without a highlight there is nothing
            // for `revertSelection` to revert and no way to see which server
            // the device is on.
            selected: server.host == _selectedHost,
            onTap: _isChecking ? null : () => _onServerTapped(server),
          ),
        if (all.length > shown.length || _showAllServers)
          Align(
            alignment: AlignmentDirectional.centerStart,
            child: TextButton(
              onPressed: () =>
                  setState(() => _showAllServers = !_showAllServers),
              child: Text(_showAllServers ? l10n.showLess : l10n.showMore),
            ),
          ),
        const Divider(height: 24),
      ],
    );
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
                    _serverPicker(l10n),
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
                      // Not localised and not shown in release: this is the
                      // status code or exception the one sentence above used
                      // to swallow, and without it a failure that cannot be
                      // reproduced off-device can only be guessed at.
                      if (kDebugMode && _diagnostic != null) ...[
                        const SizedBox(height: 8),
                        SelectableText(
                          _diagnostic!,
                          style: Theme.of(context).textTheme.bodySmall
                              ?.copyWith(fontFamily: 'monospace'),
                        ),
                      ],
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

/// Port of the `AlertDialog` built in `SyncActivity.clearDataDialog`
/// (`SyncActivity.kt:270-300`).
///
/// The Kotlin's positive button disables **both** buttons, shows a separate
/// progress dialog reading `clearing_data`, guards the back press, and on an
/// exception dismisses the progress, releases the guard and re-enables both
/// buttons — leaving the dialog up so the user can try again. One stateful
/// dialog reproduces all of that; two would only reproduce the file layout.
///
/// Pops `true` once the data is gone, `false` on Cancel, and nothing at all
/// while the wipe is running.
class _ClearDataDialog extends ConsumerStatefulWidget {
  const _ClearDataDialog();

  @override
  ConsumerState<_ClearDataDialog> createState() => _ClearDataDialogState();
}

class _ClearDataDialogState extends ConsumerState<_ClearDataDialog> {
  bool _clearing = false;
  bool _failed = false;

  Future<void> _clear() async {
    setState(() {
      _clearing = true;
      _failed = false;
    });
    try {
      await ref.read(clearDataProvider.notifier).clearForServerSwitch();
      if (!mounted) return;
      Navigator.of(context).pop(true);
    } catch (_) {
      if (!mounted) return;
      // Kotlin's `catch`: progress away, guard released, both buttons live
      // again, dialog still open.
      setState(() {
        _clearing = false;
        _failed = true;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);

    return PopScope(
      // `setCancelable(false)` for the whole life of the dialog, and
      // `guardBackPressWhileDialogShowing()` while the wipe runs.
      canPop: false,
      child: AlertDialog(
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(l10n.youWantToConnectToADifferentServer),
            if (_clearing) ...[
              const SizedBox(height: 16),
              Row(
                children: [
                  const SizedBox.square(
                    dimension: 16,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  ),
                  const SizedBox(width: 12),
                  Text(l10n.clearingData),
                ],
              ),
            ],
            if (_failed) ...[
              const SizedBox(height: 16),
              // Kotlin says nothing here — it logs, re-enables the buttons
              // and leaves the user looking at a dialog that did nothing
              // when they pressed the button. An existing string beats both
              // silence and a twelfth way to spell "it failed".
              Text(
                l10n.operationFailed,
                style: TextStyle(color: Theme.of(context).colorScheme.error),
              ),
            ],
          ],
        ),
        actions: [
          TextButton(
            onPressed: _clearing
                ? null
                : () => Navigator.of(context).pop(false),
            child: Text(l10n.cancel),
          ),
          TextButton(
            onPressed: _clearing ? null : _clear,
            child: Text(l10n.clearData),
          ),
        ],
      ),
    );
  }
}
