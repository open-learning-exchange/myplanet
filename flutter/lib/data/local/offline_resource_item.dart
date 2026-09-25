/// Port of `model/OfflineResourceItem.kt`.
///
/// A downloaded resource grouped by its on-disk `docId` directory, with the
/// files it comprises and their combined size. Storage management builds these
/// from the `ole` tree and deletes them through the repository.
class OfflineResourceItem {
  const OfflineResourceItem({
    required this.resourceId,
    required this.title,
    required this.filePaths,
    required this.totalSizeBytes,
    this.isChecked = false,
  });

  final String resourceId;
  final String title;
  final List<String> filePaths;
  final int totalSizeBytes;
  final bool isChecked;

  OfflineResourceItem copyWith({bool? isChecked}) => OfflineResourceItem(
    resourceId: resourceId,
    title: title,
    filePaths: filePaths,
    totalSizeBytes: totalSizeBytes,
    isChecked: isChecked ?? this.isChecked,
  );
}
