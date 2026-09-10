import 'package:flutter/services.dart';

/// Port of `FileUtils.getStorageStats` — the total and available bytes of the
/// device's primary storage volume, surfaced through Android's
/// `StorageStatsManager`. The UI shows `available/total` so the user knows how
/// much room is left before downloading more resources.
///
/// The pure-Dart SDK has no API for device free space, so the real
/// implementation talks to a method channel backed by the Kotlin
/// `MainActivity`. Tests inject [DiskStats] directly — they never touch the
/// platform channel — which is why this is an interface rather than a static
/// helper.
abstract class DiskStats {
  /// `total/available` in bytes, matching `StorageStatsManager.getTotalBytes`
  /// and `getFreeBytes` against the primary storage volume's UUID.
  Future<({int totalBytes, int availableBytes})> storageStats();

  /// The method-channel-backed implementation used in production. Calls
  /// `disk_stats/getStorageStats`, which the `MainActivity` answers with a
  /// `{"totalBytes": …, "availableBytes": …}` map.
  static DiskStats instance = _MethodChannelDiskStats();
}

class _MethodChannelDiskStats implements DiskStats {
  static const _channel = MethodChannel('disk_stats');

  @override
  Future<({int totalBytes, int availableBytes})> storageStats() async {
    final result = await _channel.invokeMethod<Map>('getStorageStats');
    final map = result ?? const <String, dynamic>{};
    return (
      totalBytes: _readInt(map, 'totalBytes'),
      availableBytes: _readInt(map, 'availableBytes'),
    );
  }

  int _readInt(Map map, String key) {
    final value = map[key];
    if (value is int) return value;
    if (value is num) return value.toInt();
    return 0;
  }
}
