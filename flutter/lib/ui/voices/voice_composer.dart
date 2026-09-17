import 'package:flutter/material.dart';

import '../../core/system/voice_image_picker.dart';
import '../../l10n/app_localizations.dart';
import '../../repository/voices_repository.dart';

/// What the composer returns: the trimmed message and any images attached.
class VoiceComposition {
  const VoiceComposition({required this.message, this.images = const []});

  final String message;
  final List<VoiceImageAttachment> images;
}

/// The compose sheet used for both a new post and a reply, standing in for
/// `AddNewsFragment`'s dialog and `ReplyActivity`'s inline composer.
///
/// Returns the composition, or null when dismissed. An empty message resolves
/// to null rather than to `''` so callers cannot accidentally store a blank
/// post — `editPost` ignores those upstream, and `createNews` would happily
/// save one.
///
/// [allowImages] renders the attach affordance Kotlin has as
/// `binding.addNewsImage`. It is off for the edit path: Kotlin's `editPost`
/// does accept `newImages`, but the port's edit flow has never offered the
/// affordance and wiring it is a separate slice — reported rather than
/// smuggled in here.
Future<VoiceComposition?> showVoiceComposer(
  BuildContext context, {
  String? initialText,
  String? title,
  String? hintText,
  bool allowImages = false,
}) {
  return showModalBottomSheet<VoiceComposition>(
    context: context,
    isScrollControlled: true,
    builder: (context) => _VoiceComposerSheet(
      initialText: initialText,
      title: title,
      hintText: hintText,
      allowImages: allowImages,
    ),
  );
}

class _VoiceComposerSheet extends StatefulWidget {
  const _VoiceComposerSheet({
    this.initialText,
    this.title,
    this.hintText,
    this.allowImages = false,
  });

  final String? initialText;
  final String? title;
  final String? hintText;
  final bool allowImages;

  @override
  State<_VoiceComposerSheet> createState() => _VoiceComposerSheetState();
}

class _VoiceComposerSheetState extends State<_VoiceComposerSheet> {
  late final TextEditingController _controller = TextEditingController(
    text: widget.initialText ?? '',
  );

  final List<VoiceImageAttachment> _images = [];
  var _picking = false;

  Future<void> _pickImages() async {
    if (_picking) return;
    setState(() => _picking = true);
    try {
      final picked = await VoiceImagePicker.instance.pick();
      if (!mounted) return;
      setState(
        () => _images.addAll(
          picked.map(
            (image) => VoiceImageAttachment(
              bytes: image.bytes,
              filename: image.filename,
            ),
          ),
        ),
      );
    } catch (_) {
      // A denied permission or a cancelled picker is not an error the
      // composer should surface, matching the Kotlin launcher's null result.
    } finally {
      if (mounted) setState(() => _picking = false);
    }
  }

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
          if (widget.allowImages) ...[
            const SizedBox(height: 8),
            Align(
              alignment: AlignmentDirectional.centerStart,
              child: TextButton.icon(
                onPressed: _picking ? null : _pickImages,
                icon: const Icon(Icons.image_outlined),
                label: Text(l10n.addImage),
              ),
            ),
            if (_images.isNotEmpty)
              Wrap(
                spacing: 8,
                runSpacing: 4,
                children: [
                  for (final image in _images)
                    InputChip(
                      label: Text(image.filename),
                      onDeleted: () => setState(() => _images.remove(image)),
                    ),
                ],
              ),
          ],
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
              Navigator.of(context).pop(
                VoiceComposition(
                  message: text,
                  images: List.unmodifiable(_images),
                ),
              );
            },
            child: Text(l10n.postVoice),
          ),
        ],
      ),
    );
  }
}
