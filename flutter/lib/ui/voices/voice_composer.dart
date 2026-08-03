import 'package:flutter/material.dart';

import '../../l10n/app_localizations.dart';

/// The compose sheet used for both a new post and a reply, standing in for
/// `AddNewsFragment`'s dialog and `ReplyActivity`'s inline composer.
///
/// Returns the trimmed message, or null when dismissed. An empty message
/// resolves to null rather than to `''` so callers cannot accidentally store a
/// blank post — `editPost` ignores those upstream, and `createNews` would
/// happily save one.
Future<String?> showVoiceComposer(
  BuildContext context, {
  String? initialText,
  String? title,
  String? hintText,
}) {
  return showModalBottomSheet<String>(
    context: context,
    isScrollControlled: true,
    builder: (context) => _VoiceComposerSheet(
      initialText: initialText,
      title: title,
      hintText: hintText,
    ),
  );
}

class _VoiceComposerSheet extends StatefulWidget {
  const _VoiceComposerSheet({this.initialText, this.title, this.hintText});

  final String? initialText;
  final String? title;
  final String? hintText;

  @override
  State<_VoiceComposerSheet> createState() => _VoiceComposerSheetState();
}

class _VoiceComposerSheetState extends State<_VoiceComposerSheet> {
  late final TextEditingController _controller = TextEditingController(
    text: widget.initialText ?? '',
  );

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return Padding(
      padding: EdgeInsets.fromLTRB(
        16,
        16,
        16,
        MediaQuery.of(context).viewInsets.bottom + 16,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(
            widget.title ?? l10n.shareYourVoice,
            style: Theme.of(context).textTheme.titleMedium,
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _controller,
            autofocus: true,
            minLines: 3,
            maxLines: 8,
            decoration: InputDecoration(
              border: const OutlineInputBorder(),
              hintText: widget.hintText ?? l10n.shareYourVoice,
            ),
          ),
          const SizedBox(height: 12),
          FilledButton(
            onPressed: () {
              final text = _controller.text.trim();
              if (text.isEmpty) {
                ScaffoldMessenger.of(context).showSnackBar(
                  SnackBar(content: Text(l10n.emptyMessageNotAllowed)),
                );
                return;
              }
              Navigator.of(context).pop(text);
            },
            child: Text(l10n.postVoice),
          ),
        ],
      ),
    );
  }
}
