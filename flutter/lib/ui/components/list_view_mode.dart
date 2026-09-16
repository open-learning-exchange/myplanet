/// Port of `utils/ListViewMode.kt`.
///
/// GRID is the default, matching the Kotlin's `fromPref` which returns GRID
/// for any value that is not `LIST`.
enum ListViewMode {
  grid,
  list;

  static ListViewMode fromPref(String? value) =>
      value == list.name ? list : grid;
}
