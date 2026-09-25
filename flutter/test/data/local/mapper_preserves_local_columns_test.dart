import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

/// The "two writers, one column" rule, enforced mechanically.
///
/// A sync mapper turns one server document into one row, and some of that row's
/// columns are not the server's to set: a shelf membership, a read flag, an
/// emoji reaction, a stored credential. Those columns have a second writer — the
/// user — and the walk has to carry the stored value forward rather than write
/// over it with what the document does not say.
///
/// The port expresses that as a parameter: `existing`, or `existingUserIds`,
/// `existingSubject` and so on. **The convention only works if the production
/// call site actually passes them**, and the defect this test exists for is
/// exactly that it did not: `MyLibraryMapper.fromDoc` declares six of them and
/// writes `userId: Value(existingUserIds)` unconditionally, while its single
/// caller in `resources_repository.dart` passed none — so every argument
/// defaulted to `const []` and every sync emptied My Library. Nothing could see
/// it, because the mapper's own tests pass those arguments and were the only
/// callers that ever did.
///
/// This is the same shape as Phase 56 (a null credential wiping `derived_key`),
/// Phase 74 (a sync erasing the user's own reaction), Phase 98 (a re-pull
/// undoing a read) and Phase 113 (`Value.absent()` on `stepId`) — four phases
/// finding one rule the hard way, which is what makes it worth a guard rather
/// than a fifth comment.
///
/// The rule: if a mapper method declares a *named* parameter called `existing`
/// or beginning with `existing`, every call to that method in `lib/` passes it.
/// Named is the point — an optional positional parameter is visible in the
/// call's arity, while a named one with a default disappears in silence.
void main() {
  test(
    'every mapper call passes the local-authority columns it must preserve',
    () {
      final omissions = <String>[];

      for (final mapper
          in Directory('lib/data/local').listSync().whereType<File>().where(
            (f) => f.path.endsWith('_mapper.dart'),
          )) {
        final source = _stripComments(mapper.readAsStringSync());

        for (final method in _methodsWithExistingParams(source).entries) {
          final expected = method.value;
          for (final call in _callsTo(method.key)) {
            final missing = expected
                .where((param) => !call.arguments.contains('$param:'))
                .toList();
            if (missing.isEmpty) continue;
            omissions.add(
              '  ${call.file}:${call.line} calls ${method.key} '
              'without ${missing.join(', ')}',
            );
          }
        }
      }

      expect(
        omissions,
        isEmpty,
        reason:
            'A mapper takes an `existing…` argument because the column it feeds '
            'has a second writer that the server document knows nothing about. '
            'Omitting it does not leave the stored value alone — it writes the '
            'default over it, silently, on every sync:\n'
            '${omissions.join('\n')}',
      );
    },
  );

  test('the scanner finds the mappers it is meant to police', () {
    // A guard that quietly matches nothing passes forever. Pin the two mappers
    // that carry these parameters today, so deleting a parameter (or breaking
    // the regex) fails here rather than going unnoticed.
    final found = <String, Set<String>>{};
    for (final mapper
        in Directory('lib/data/local').listSync().whereType<File>().where(
          (f) => f.path.endsWith('_mapper.dart'),
        )) {
      final methods = _methodsWithExistingParams(
        _stripComments(mapper.readAsStringSync()),
      );
      for (final entry in methods.entries) {
        found[entry.key] = entry.value;
      }
    }

    expect(found.keys, containsAll(<String>['MyLibraryMapper.fromDoc']));
    expect(
      found['MyLibraryMapper.fromDoc'],
      containsAll(<String>[
        'existingUserIds',
        'existingResourceFor',
        'existingSubject',
        'existingLevel',
        'existingTag',
        'existingLanguages',
      ]),
    );
  });
}

// ---------------------------------------------------------------------------
// Source scanning
// ---------------------------------------------------------------------------

/// `Class.method` -> the `existing…` parameter names it declares.
///
/// A mapper is a class of static factories, so the class name is the file's one
/// top-level `class`, and a method is a `static … name(` whose parameter list
/// runs to the matching `) {`.
Map<String, Set<String>> _methodsWithExistingParams(String source) {
  final className = RegExp(
    r'^class (\w+)',
    multiLine: true,
  ).firstMatch(source)?.group(1);
  if (className == null) return const {};

  final result = <String, Set<String>>{};
  // The parameter list is taken by balancing parentheses rather than by regex:
  // a mapper's parameters are a named block, so the list contains `{`, `}` and
  // nested generic commas that a flat pattern truncates at.
  final signature = RegExp(r'static\s+[\w<>?,\s]+?\s(\w+)\(', multiLine: true);
  for (final match in signature.allMatches(source)) {
    final open = match.end - 1;
    final close = _matchingParen(source, open);
    if (close == -1) continue;
    // Only the *named* block. An optional positional parameter is visible in
    // the call's arity and cannot go missing unnoticed; a named one with a
    // default disappears in silence, which is the failure being policed.
    final parameters = source.substring(open + 1, close);
    final namedFrom = parameters.indexOf('{');
    if (namedFrom == -1) continue;
    final params = RegExp(r'\b(existing\w*)\s*[,:=}]')
        .allMatches(parameters.substring(namedFrom))
        .map((m) => m.group(1)!)
        .toSet();
    if (params.isNotEmpty) result['$className.${match.group(1)}'] = params;
  }
  return result;
}

class _Call {
  const _Call(this.file, this.line, this.arguments);
  final String file;
  final int line;
  final String arguments;
}

/// Every call to `Class.method(` under `lib/`, with its argument text.
///
/// The argument list is taken by balancing parentheses from the opening one, so
/// a nested call or a collection literal in an argument does not truncate it.
List<_Call> _callsTo(String qualifiedName) {
  final calls = <_Call>[];
  final needle = '$qualifiedName(';

  for (final file
      in Directory('lib')
          .listSync(recursive: true)
          .whereType<File>()
          .where((f) => f.path.endsWith('.dart'))
          // A mapper may call its own helpers with the arguments already in hand.
          .where((f) => !f.path.startsWith('lib/data/local/'))) {
    final source = _stripComments(file.readAsStringSync());
    var index = source.indexOf(needle);
    while (index != -1) {
      final open = index + needle.length - 1;
      final close = _matchingParen(source, open);
      if (close != -1) {
        calls.add(
          _Call(
            file.path,
            '\n'.allMatches(source.substring(0, index)).length + 1,
            source.substring(open + 1, close),
          ),
        );
      }
      index = source.indexOf(needle, index + needle.length);
    }
  }
  return calls;
}

int _matchingParen(String source, int open) {
  var depth = 0;
  for (var i = open; i < source.length; i++) {
    if (source[i] == '(') depth++;
    if (source[i] == ')') {
      depth--;
      if (depth == 0) return i;
    }
  }
  return -1;
}

String _stripComments(String source) => source
    .replaceAll(RegExp(r'/\*.*?\*/', dotAll: true), '')
    .split('\n')
    .map((line) {
      final index = line.indexOf('//');
      return index == -1 ? line : line.substring(0, index);
    })
    .join('\n');
