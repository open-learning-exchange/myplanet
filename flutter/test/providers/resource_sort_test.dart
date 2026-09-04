import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/providers/resources_providers.dart';

import '../support/widget_harness.dart';

/// Pins the sort-toggle semantics ported from `ResourcesViewModel`, including
/// the upstream `14a9f14` fix: the first title toggle must sort A→Z, not Z→A.
void main() {
  test('first title toggle sorts A to Z, second sorts Z to A', () {
    final items = [
      buildLibraryRow(id: 'b', title: 'Banana'),
      buildLibraryRow(id: 'a', title: 'Apple'),
      buildLibraryRow(id: 'c', title: 'Cherry'),
    ];

    var sort = const ResourceSortState().toggleTitle();
    expect(applyResourceSort(items, sort).map((r) => r.title), [
      'Apple',
      'Banana',
      'Cherry',
    ]);

    sort = sort.toggleTitle();
    expect(applyResourceSort(items, sort).map((r) => r.title), [
      'Cherry',
      'Banana',
      'Apple',
    ]);
  });

  test('first date toggle sorts newest first, second oldest first', () {
    final items = [
      buildLibraryRow(id: 'old', title: 'Old', createdDate: 100),
      buildLibraryRow(id: 'new', title: 'New', createdDate: 300),
      buildLibraryRow(id: 'mid', title: 'Mid', createdDate: 200),
    ];

    var sort = const ResourceSortState().toggleDate();
    expect(applyResourceSort(items, sort).map((r) => r.createdDate), [
      300,
      200,
      100,
    ]);

    sort = sort.toggleDate();
    expect(applyResourceSort(items, sort).map((r) => r.createdDate), [
      100,
      200,
      300,
    ]);
  });

  test('each mode keeps its own direction when switching between modes', () {
    // Kotlin holds one direction flag per mode (`isAscending` for date,
    // `isTitleAscending` for title); toggling one mode must not flip the
    // other's.
    var sort = const ResourceSortState().toggleTitle(); // title A→Z
    sort = sort.toggleDate(); // date newest-first
    sort = sort.toggleTitle(); // back to title, now Z→A

    final items = [
      buildLibraryRow(id: 'a', title: 'Apple'),
      buildLibraryRow(id: 'b', title: 'Banana'),
    ];
    expect(applyResourceSort(items, sort).map((r) => r.title), [
      'Banana',
      'Apple',
    ]);
  });

  test('title sort is case-insensitive and null titles sort first', () {
    final items = [
      buildLibraryRow(id: 'b', title: 'banana'),
      buildLibraryRow(id: 'n'),
      buildLibraryRow(id: 'a', title: 'Apple'),
    ];

    final sort = const ResourceSortState().toggleTitle();
    expect(applyResourceSort(items, sort).map((r) => r.id), ['n', 'a', 'b']);
  });

  test('equal keys keep their stream order', () {
    // Kotlin's sortedBy is stable; Dart's List.sort is not, so the comparator
    // tie-breaks on the original index. A regression here shuffles rows that
    // share a title or a createdDate every time the sort reapplies.
    final items = [
      buildLibraryRow(id: 'first', title: 'Same'),
      buildLibraryRow(id: 'second', title: 'Same'),
      buildLibraryRow(id: 'third', title: 'Same'),
    ];

    final sort = const ResourceSortState().toggleTitle();
    expect(applyResourceSort(items, sort).map((r) => r.id), [
      'first',
      'second',
      'third',
    ]);
  });

  test('none mode leaves the stream order untouched', () {
    final items = [
      buildLibraryRow(id: 'b', title: 'Banana'),
      buildLibraryRow(id: 'a', title: 'Apple'),
    ];
    expect(
      applyResourceSort(items, const ResourceSortState()).map((r) => r.id),
      ['b', 'a'],
    );
  });
}
