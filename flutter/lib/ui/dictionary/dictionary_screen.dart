import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../data/local/app_database.dart';
import '../../l10n/app_localizations.dart';
import '../../providers/dictionary_provider.dart';

/// Port of `ui/dictionary/DictionaryActivity.kt`.
class DictionaryScreen extends ConsumerStatefulWidget {
  const DictionaryScreen({super.key});

  @override
  ConsumerState<DictionaryScreen> createState() => _DictionaryScreenState();
}

class _DictionaryScreenState extends ConsumerState<DictionaryScreen> {
  final _searchController = TextEditingController();

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final asyncState = ref.watch(dictionaryProvider);

    return Scaffold(
      appBar: AppBar(title: Text(l10n.dictionary)),
      body: asyncState.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (_, _) => Center(child: Text(l10n.dictionaryUnavailable)),
        data: (state) => ListView(
          padding: const EdgeInsets.all(16),
          children: [
            TextField(
              controller: _searchController,
              decoration: InputDecoration(
                labelText: l10n.searchDictionary,
                prefixIcon: const Icon(Icons.search),
                border: const OutlineInputBorder(),
              ),
              textInputAction: TextInputAction.search,
              onSubmitted: (_) => _search(),
            ),
            const SizedBox(height: 12),
            FilledButton.icon(
              onPressed: state.loading ? null : _search,
              icon: const Icon(Icons.search),
              label: Text(l10n.search),
            ),
            const SizedBox(height: 12),
            Text(l10n.dictionaryWordCount(state.count)),
            if (state.count == 0 || state.importFailed) ...[
              const SizedBox(height: 12),
              OutlinedButton.icon(
                onPressed: state.loading
                    ? null
                    : () => ref.read(dictionaryProvider.notifier).download(),
                icon: state.loading
                    ? const SizedBox.square(
                        dimension: 18,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Icon(Icons.download_outlined),
                label: Text(
                  state.importFailed
                      ? l10n.retryDownload
                      : l10n.downloadDictionary,
                ),
              ),
            ],
            if (state.importFailed) ...[
              const SizedBox(height: 8),
              Text(
                l10n.dictionaryDownloadFailed,
                style: TextStyle(color: Theme.of(context).colorScheme.error),
              ),
            ],
            if (state.result case final result?) ...[
              const SizedBox(height: 24),
              _DictionaryResult(result: result),
            ],
          ],
        ),
      ),
    );
  }

  Future<void> _search() async {
    FocusScope.of(context).unfocus();
    final found = await ref
        .read(dictionaryProvider.notifier)
        .search(_searchController.text);
    if (!found && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(AppLocalizations.of(context).wordNotFound)),
      );
    }
  }
}

class _DictionaryResult extends StatelessWidget {
  const _DictionaryResult({required this.result});

  final DictionaryRow result;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(result.word, style: Theme.of(context).textTheme.headlineSmall),
            if (result.definition.isNotEmpty) ...[
              const SizedBox(height: 12),
              Text(result.definition),
            ],
            if (result.meaning.isNotEmpty)
              _DefinitionLine(label: l10n.meaning, value: result.meaning),
            if (result.synonym.isNotEmpty)
              _DefinitionLine(label: l10n.synonyms, value: result.synonym),
            if (result.antonym.isNotEmpty)
              _DefinitionLine(label: l10n.antonyms, value: result.antonym),
          ],
        ),
      ),
    );
  }
}

class _DefinitionLine extends StatelessWidget {
  const _DefinitionLine({required this.label, required this.value});
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(top: 12),
      child: Text.rich(
        TextSpan(
          children: [
            TextSpan(
              text: '$label: ',
              style: const TextStyle(fontWeight: FontWeight.bold),
            ),
            TextSpan(text: value),
          ],
        ),
      ),
    );
  }
}
