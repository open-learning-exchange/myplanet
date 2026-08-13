import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart' hide TextDirection;

import '../../l10n/app_localizations.dart';
import '../../providers/activities_provider.dart';
import '../../providers/sync_state.dart';

/// Port of `ui/dashboard/ActivitiesFragment.kt`.
///
/// Shows a bar chart of the current user's monthly login count. The Kotlin uses
/// MPAndroidChart; here the chart is a [CustomPainter] so no new charting
/// dependency is needed. Pull-to-refresh runs the `login_activities` sync.
class ActivitiesScreen extends ConsumerWidget {
  const ActivitiesScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final counts = ref.watch(monthlyLoginCountsProvider);
    final syncState = ref.watch(activitiesSyncProvider);

    return Scaffold(
      appBar: AppBar(title: Text(l10n.myActivity)),
      body: RefreshIndicator(
        onRefresh: () => ref.read(activitiesSyncProvider.notifier).sync(),
        child: Column(
          children: [
            if (syncState is SyncRunning)
              LinearProgressIndicator(
                value: syncState.progress.total == 0
                    ? null
                    : syncState.progress.completed / syncState.progress.total,
              ),
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
              child: Align(
                alignment: Alignment.centerLeft,
                child: Text(l10n.chartLabel),
              ),
            ),
            Expanded(
              child: counts.when(
                data: (data) => _LoginBarChart(
                  data: data,
                  emptyLabel: l10n.noLoginActivity,
                ),
                loading: () => const Center(child: CircularProgressIndicator()),
                error: (error, _) => Center(child: Text(error.toString())),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _LoginBarChart extends StatelessWidget {
  const _LoginBarChart({required this.data, required this.emptyLabel});

  final List<MonthLoginCount> data;
  final String emptyLabel;

  @override
  Widget build(BuildContext context) {
    if (data.isEmpty) {
      return Center(child: Text(emptyLabel));
    }
    return Padding(
      padding: const EdgeInsets.all(16),
      child: CustomPaint(
        size: Size.infinite,
        painter: _LoginBarChartPainter(data: data),
        child: const SizedBox.expand(),
      ),
    );
  }
}

class _LoginBarChartPainter extends CustomPainter {
  _LoginBarChartPainter({required this.data});

  final List<MonthLoginCount> data;

  @override
  void paint(Canvas canvas, Size size) {
    if (data.isEmpty) return;

    final maxValue = data.fold<int>(0, (a, b) => a > b.count ? a : b.count);
    final chartMax = maxValue == 0 ? 1 : maxValue;

    const labelWidth = 36.0;
    const bottomPadding = 24.0;
    const topPadding = 8.0;
    final chartWidth = size.width - labelWidth;
    final chartHeight = size.height - bottomPadding - topPadding;
    if (chartWidth <= 0 || chartHeight <= 0) return;

    final barCount = data.length;
    final slotWidth = chartWidth / barCount;
    final barWidth = slotWidth * 0.55;

    final gridPaint = Paint()
      ..color = Colors.grey.shade300
      ..strokeWidth = 1;
    final axisPaint = Paint()
      ..color = Colors.grey.shade600
      ..strokeWidth = 1;
    final barPaint = Paint()..color = Colors.blue.shade400;

    canvas.drawLine(
      Offset(labelWidth, topPadding),
      Offset(labelWidth, topPadding + chartHeight),
      axisPaint,
    );
    canvas.drawLine(
      Offset(labelWidth, topPadding + chartHeight),
      Offset(size.width, topPadding + chartHeight),
      axisPaint,
    );

    final maxTick = chartMax >= 4 ? chartMax : 4;
    final tickCount = 4;
    final tickLabelStyle = TextStyle(color: Colors.grey.shade700, fontSize: 10);
    for (var i = 0; i <= tickCount; i++) {
      final y = topPadding + chartHeight - (chartHeight * i / tickCount);
      canvas.drawLine(Offset(labelWidth, y), Offset(size.width, y), gridPaint);
      final tickValue = ((maxTick * i) / tickCount).round();
      final tp = TextPainter(
        text: TextSpan(text: '$tickValue', style: tickLabelStyle),
        textDirection: TextDirection.ltr,
      )..layout();
      tp.paint(canvas, Offset(labelWidth - tp.width - 4, y - tp.height / 2));
    }

    final monthLabelStyle = TextStyle(
      color: Colors.grey.shade700,
      fontSize: 10,
    );
    final countLabelStyle = const TextStyle(
      color: Colors.black87,
      fontSize: 11,
      fontWeight: FontWeight.w600,
    );
    for (var i = 0; i < barCount; i++) {
      final entry = data[i];
      final barHeight = (entry.count / chartMax) * chartHeight;
      final left = labelWidth + slotWidth * i + (slotWidth - barWidth) / 2;
      final top = topPadding + chartHeight - barHeight;
      final rect = Rect.fromLTWH(left, top, barWidth, barHeight);
      if (entry.count > 0) {
        canvas.drawRRect(
          RRect.fromRectAndRadius(rect, const Radius.circular(3)),
          barPaint,
        );
        final tp = TextPainter(
          text: TextSpan(text: '${entry.count}', style: countLabelStyle),
          textDirection: TextDirection.ltr,
        )..layout();
        tp.paint(canvas, Offset(left + barWidth / 2 - tp.width / 2, top - 14));
      }

      final label = DateFormat('MMM').format(entry.month);
      final tp = TextPainter(
        text: TextSpan(text: label, style: monthLabelStyle),
        textDirection: TextDirection.ltr,
      )..layout();
      tp.paint(
        canvas,
        Offset(
          labelWidth + slotWidth * i + slotWidth / 2 - tp.width / 2,
          topPadding + chartHeight + 6,
        ),
      );
    }
  }

  @override
  bool shouldRepaint(covariant _LoginBarChartPainter old) => old.data != data;
}
