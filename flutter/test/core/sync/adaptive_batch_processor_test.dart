import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/core/sync/adaptive_batch_processor.dart';

void main() {
  group('AdaptiveBatchProcessor', () {
    test('clamps the initial size into range', () {
      expect(
        AdaptiveBatchProcessor(
          initialSize: 1,
          minSize: 10,
          maxSize: 100,
        ).currentSize,
        10,
      );
      expect(
        AdaptiveBatchProcessor(
          initialSize: 500,
          minSize: 10,
          maxSize: 100,
        ).currentSize,
        100,
      );
    });

    test('halves the batch on a slow response, down to minSize', () {
      final processor = AdaptiveBatchProcessor(
        initialSize: 100,
        minSize: 10,
        maxSize: 100,
      );
      processor.recordSuccess(9000);
      expect(processor.currentSize, 50);
      processor.recordSuccess(9000);
      expect(processor.currentSize, 25);
      for (var i = 0; i < 10; i++) {
        processor.recordSuccess(9000);
      }
      expect(processor.currentSize, 10);
    });

    test('grows the batch on a fast response, up to maxSize', () {
      final processor = AdaptiveBatchProcessor(
        initialSize: 20,
        minSize: 10,
        maxSize: 100,
      );
      processor.recordSuccess(500);
      expect(processor.currentSize, 26);
      for (var i = 0; i < 20; i++) {
        processor.recordSuccess(500);
      }
      expect(processor.currentSize, 100);
    });

    test('holds steady between the thresholds', () {
      final processor = AdaptiveBatchProcessor(
        initialSize: 50,
        minSize: 10,
        maxSize: 100,
      );
      processor.recordSuccess(5000);
      expect(processor.currentSize, 50);
    });

    test('halves the batch on failure', () {
      final processor = AdaptiveBatchProcessor(
        initialSize: 80,
        minSize: 10,
        maxSize: 100,
      );
      processor.recordFailure();
      expect(processor.currentSize, 40);
    });

    test('defaults maxSize to the initial size', () {
      final processor = AdaptiveBatchProcessor(initialSize: 40);
      processor.recordSuccess(100);
      expect(processor.currentSize, 40);
    });
  });
}
