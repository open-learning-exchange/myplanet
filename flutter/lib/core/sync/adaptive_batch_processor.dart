/// Port of `services/sync/AdaptiveBatchProcessor.kt`.
///
/// Adjusts sync batch sizes to network conditions (AIMD): slow or failed
/// batches halve the size so weak links get small pages; fast batches grow it
/// back toward [maxSize]. Not safe for concurrent use — one instance per loop.
class AdaptiveBatchProcessor {
  AdaptiveBatchProcessor({
    required int initialSize,
    this.minSize = minBatchSize,
    int? maxSize,
    this.slowThresholdMs = slowThreshold,
    this.fastThresholdMs = fastThreshold,
  }) : maxSize = (maxSize ?? initialSize) < minSize
           ? minSize
           : (maxSize ?? initialSize),
       _currentSize = initialSize {
    _currentSize = _currentSize.clamp(minSize, this.maxSize);
  }

  static const int minBatchSize = 10;
  static const int slowThreshold = 8000;
  static const int fastThreshold = 2000;

  final int minSize;
  final int maxSize;
  final int slowThresholdMs;
  final int fastThresholdMs;

  int _currentSize;

  int get currentSize => _currentSize;

  void recordSuccess(int durationMs) {
    if (durationMs >= slowThresholdMs) {
      _currentSize = (_currentSize ~/ 2).clamp(minSize, maxSize);
    } else if (durationMs <= fastThresholdMs) {
      _currentSize = (_currentSize + _currentSize ~/ 4 + 1).clamp(
        minSize,
        maxSize,
      );
    }
  }

  void recordFailure() {
    _currentSize = (_currentSize ~/ 2).clamp(minSize, maxSize);
  }
}
