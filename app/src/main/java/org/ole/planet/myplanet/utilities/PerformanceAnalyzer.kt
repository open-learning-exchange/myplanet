package org.ole.planet.myplanet.utilities

import android.content.Context
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Enhanced Performance Analyzer that provides actionable insights
 * Add this as an extension to your existing CpuMonitoring
 */
class PerformanceAnalyzer(private val context: Context) {

    companion object {
        // Performance thresholds
        private const val HIGH_CPU_THRESHOLD = 20.0
        private const val CRITICAL_CPU_THRESHOLD = 30.0
        private const val HIGH_MEMORY_THRESHOLD = 150.0
        private const val CRITICAL_MEMORY_THRESHOLD = 200.0
        private const val HIGH_THREAD_THRESHOLD = 50
        private const val CRITICAL_THREAD_THRESHOLD = 80
        private const val MEMORY_GROWTH_THRESHOLD = 10.0 // MB per minute
    }

    data class PerformanceInsight(
        val severity: Severity,
        val category: Category,
        val title: String,
        val description: String,
        val recommendation: String,
        val impact: Impact
    )

    enum class Severity { LOW, MEDIUM, HIGH, CRITICAL }
    enum class Category { CPU, MEMORY, THREADS, GENERAL }
    enum class Impact { PERFORMANCE, BATTERY, STABILITY, USER_EXPERIENCE }

    data class PerformanceAlert(
        val timestamp: Long,
        val alertType: String,
        val severity: Severity,
        val message: String,
        val metrics: Map<String, Any>
    )

    fun analyzePerformanceReport(report: CpuMonitoring.PerformanceReport): List<PerformanceInsight> {
        val insights = mutableListOf<PerformanceInsight>()

        // CPU Analysis
        insights.addAll(analyzeCpuUsage(report))

        // Memory Analysis
        insights.addAll(analyzeMemoryUsage(report))

        // Thread Analysis
        insights.addAll(analyzeThreadUsage(report))

        // Trend Analysis
        insights.addAll(analyzeTrends(report))

        // System Impact Analysis
        insights.addAll(analyzeSystemImpact(report))

        return insights.sortedByDescending { it.severity }
    }

    private fun analyzeCpuUsage(report: CpuMonitoring.PerformanceReport): List<PerformanceInsight> {
        val insights = mutableListOf<PerformanceInsight>()

        when {
            report.maxCpuUsage > CRITICAL_CPU_THRESHOLD -> {
                insights.add(PerformanceInsight(
                    severity = Severity.CRITICAL,
                    category = Category.CPU,
                    title = "Critical CPU Usage Detected",
                    description = "Peak CPU usage of ${String.format("%.1f", report.maxCpuUsage)}% is dangerously high",
                    recommendation = "Immediate action required: Profile CPU-intensive operations, optimize algorithms, reduce concurrent tasks",
                    impact = Impact.PERFORMANCE
                ))
            }
            report.maxCpuUsage > HIGH_CPU_THRESHOLD -> {
                insights.add(PerformanceInsight(
                    severity = Severity.HIGH,
                    category = Category.CPU,
                    title = "High CPU Usage Warning",
                    description = "Peak CPU usage of ${String.format("%.1f", report.maxCpuUsage)}% may impact performance",
                    recommendation = "Consider optimizing heavy computations, use background threads for intensive tasks",
                    impact = Impact.BATTERY
                ))
            }
        }

        if (report.avgCpuUsage > HIGH_CPU_THRESHOLD) {
            insights.add(PerformanceInsight(
                severity = Severity.MEDIUM,
                category = Category.CPU,
                title = "Sustained High CPU Usage",
                description = "Average CPU usage of ${String.format("%.1f", report.avgCpuUsage)}% indicates continuous processing",
                recommendation = "Review background tasks, implement CPU usage throttling, optimize frequent operations",
                impact = Impact.BATTERY
            ))
        }

        return insights
    }

    private fun analyzeMemoryUsage(report: CpuMonitoring.PerformanceReport): List<PerformanceInsight> {
        val insights = mutableListOf<PerformanceInsight>()

        when {
            report.maxMemoryUsage > CRITICAL_MEMORY_THRESHOLD -> {
                insights.add(PerformanceInsight(
                    severity = Severity.CRITICAL,
                    category = Category.MEMORY,
                    title = "Critical Memory Usage",
                    description = "Peak memory usage of ${String.format("%.1f", report.maxMemoryUsage)}MB is extremely high",
                    recommendation = "Urgent: Check for memory leaks, implement memory management, reduce cached data",
                    impact = Impact.STABILITY
                ))
            }
            report.maxMemoryUsage > HIGH_MEMORY_THRESHOLD -> {
                insights.add(PerformanceInsight(
                    severity = Severity.HIGH,
                    category = Category.MEMORY,
                    title = "High Memory Usage",
                    description = "Peak memory usage of ${String.format("%.1f", report.maxMemoryUsage)}MB may cause issues",
                    recommendation = "Optimize memory usage, implement proper cleanup, use memory-efficient data structures",
                    impact = Impact.PERFORMANCE
                ))
            }
        }

        // Check for memory growth
        if (report.memorySnapshots.size >= 2) {
            val firstSnapshot = report.memorySnapshots.first()
            val lastSnapshot = report.memorySnapshots.last()
            val timeDiff = (lastSnapshot.timestamp - firstSnapshot.timestamp) / (1000.0 * 60.0) // minutes
            val memoryGrowth = lastSnapshot.appMemoryUsageMb - firstSnapshot.appMemoryUsageMb

            if (timeDiff > 0 && memoryGrowth / timeDiff > MEMORY_GROWTH_THRESHOLD) {
                insights.add(PerformanceInsight(
                    severity = Severity.HIGH,
                    category = Category.MEMORY,
                    title = "Rapid Memory Growth Detected",
                    description = "Memory growing at ${String.format("%.1f", memoryGrowth/timeDiff)}MB/min",
                    recommendation = "Investigate memory leaks, check object retention, review caching strategies",
                    impact = Impact.STABILITY
                ))
            }
        }

        return insights
    }

    private fun analyzeThreadUsage(report: CpuMonitoring.PerformanceReport): List<PerformanceInsight> {
        val insights = mutableListOf<PerformanceInsight>()

        when {
            report.maxThreadCount > CRITICAL_THREAD_THRESHOLD -> {
                insights.add(PerformanceInsight(
                    severity = Severity.CRITICAL,
                    category = Category.THREADS,
                    title = "Excessive Thread Usage",
                    description = "Peak thread count of ${report.maxThreadCount} is dangerously high",
                    recommendation = "Critical: Implement thread pooling, reduce concurrent operations, optimize coroutine usage",
                    impact = Impact.STABILITY
                ))
            }
            report.maxThreadCount > HIGH_THREAD_THRESHOLD -> {
                insights.add(PerformanceInsight(
                    severity = Severity.HIGH,
                    category = Category.THREADS,
                    title = "High Thread Count",
                    description = "Peak thread count of ${report.maxThreadCount} may impact performance",
                    recommendation = "Consider thread pool optimization, use coroutines instead of raw threads",
                    impact = Impact.PERFORMANCE
                ))
            }
        }

        // Analyze thread states from latest snapshot
        val latestThreadInfo = report.threadSnapshots.lastOrNull()
        latestThreadInfo?.let { threadInfo ->
            val blockedRatio = threadInfo.blockedThreads.toDouble() / threadInfo.threadCount
            if (blockedRatio > 0.1) { // More than 10% blocked
                insights.add(PerformanceInsight(
                    severity = Severity.HIGH,
                    category = Category.THREADS,
                    title = "High Thread Blocking",
                    description = "${threadInfo.blockedThreads} threads (${String.format("%.1f", blockedRatio * 100)}%) are blocked",
                    recommendation = "Review synchronization code, reduce lock contention, optimize blocking operations",
                    impact = Impact.PERFORMANCE
                ))
            }
        }

        return insights
    }

    private fun analyzeTrends(report: CpuMonitoring.PerformanceReport): List<PerformanceInsight> {
        val insights = mutableListOf<PerformanceInsight>()

        // Analyze CPU trend
        if (report.recentSnapshots.size >= 3) {
            val recentCpuValues = report.recentSnapshots.takeLast(3).map { it.appCpuUsage }
            val isIncreasing = recentCpuValues.zipWithNext().all { (a, b) -> b > a }

            if (isIncreasing && recentCpuValues.last() > HIGH_CPU_THRESHOLD) {
                insights.add(PerformanceInsight(
                    severity = Severity.MEDIUM,
                    category = Category.CPU,
                    title = "Rising CPU Usage Trend",
                    description = "CPU usage is continuously increasing: ${recentCpuValues.joinToString(" → ") { "%.1f%%".format(it) }}",
                    recommendation = "Monitor for runaway processes, check for CPU leaks in loops or recursive calls",
                    impact = Impact.PERFORMANCE
                ))
            }
        }

        return insights
    }

    private fun analyzeSystemImpact(report: CpuMonitoring.PerformanceReport): List<PerformanceInsight> {
        val insights = mutableListOf<PerformanceInsight>()

        // Check if app is using significant system resources
        val systemInfo = report.systemInfo
        val memoryUsagePercent = (report.maxMemoryUsage / (systemInfo.totalRam / (1024.0 * 1024.0))) * 100

        if (memoryUsagePercent > 15) { // Using more than 15% of system RAM
            insights.add(PerformanceInsight(
                severity = Severity.HIGH,
                category = Category.MEMORY,
                title = "High System Memory Impact",
                description = "App is using ${String.format("%.1f", memoryUsagePercent)}% of total system memory",
                recommendation = "Reduce memory footprint to improve overall device performance",
                impact = Impact.USER_EXPERIENCE
            ))
        }

        // Check CPU usage relative to available cores
        val cpuPerCore = report.maxCpuUsage / systemInfo.availableProcessors
        if (cpuPerCore > 25) { // More than 25% per core
            insights.add(PerformanceInsight(
                severity = Severity.MEDIUM,
                category = Category.CPU,
                title = "High Per-Core CPU Usage",
                description = "Average ${String.format("%.1f", cpuPerCore)}% CPU usage per core",
                recommendation = "Distribute CPU load across cores, optimize single-threaded bottlenecks",
                impact = Impact.PERFORMANCE
            ))
        }

        return insights
    }

    fun generateActionableReport(report: CpuMonitoring.PerformanceReport): String {
        val insights = analyzePerformanceReport(report)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val criticalIssues = insights.filter { it.severity == Severity.CRITICAL }
        val highIssues = insights.filter { it.severity == Severity.HIGH }
        val mediumIssues = insights.filter { it.severity == Severity.MEDIUM }
        val lowIssues = insights.filter { it.severity == Severity.LOW }

        return buildString {
            appendLine("=== ACTIONABLE PERFORMANCE ANALYSIS ===")
            appendLine("Generated: ${dateFormat.format(Date(report.reportTimestamp))}")
            appendLine("Analysis Duration: ${report.monitoringDurationMs / 1000}s")
            appendLine()

            if (insights.isEmpty()) {
                appendLine("✅ GOOD NEWS: No significant performance issues detected!")
                appendLine("Your app is performing within acceptable parameters.")
                appendLine()
            } else {
                appendLine("🔍 PERFORMANCE INSIGHTS DETECTED: ${insights.size}")
                appendLine()

                // Group by severity


                if (criticalIssues.isNotEmpty()) {
                    appendLine("🚨 CRITICAL ISSUES (${criticalIssues.size}):")
                    criticalIssues.forEach { insight ->
                        appendLine("• ${insight.title}")
                        appendLine("  ${insight.description}")
                        appendLine("  💡 Action: ${insight.recommendation}")
                        appendLine("  Impact: ${insight.impact}")
                        appendLine()
                    }
                }

                if (highIssues.isNotEmpty()) {
                    appendLine("⚠️ HIGH PRIORITY ISSUES (${highIssues.size}):")
                    highIssues.forEach { insight ->
                        appendLine("• ${insight.title}")
                        appendLine("  ${insight.description}")
                        appendLine("  💡 Recommendation: ${insight.recommendation}")
                        appendLine()
                    }
                }

                if (mediumIssues.isNotEmpty()) {
                    appendLine("📋 MEDIUM PRIORITY OPTIMIZATIONS (${mediumIssues.size}):")
                    mediumIssues.forEach { insight ->
                        appendLine("• ${insight.title}")
                        appendLine("  💡 Consider: ${insight.recommendation}")
                        appendLine()
                    }
                }
            }

            appendLine("=== PERFORMANCE SUMMARY ===")
            appendLine("CPU: ${getPerformanceRating(report.avgCpuUsage, HIGH_CPU_THRESHOLD, CRITICAL_CPU_THRESHOLD)} (Avg: ${String.format("%.1f", report.avgCpuUsage)}%)")
            appendLine("Memory: ${getPerformanceRating(report.avgMemoryUsage, HIGH_MEMORY_THRESHOLD, CRITICAL_MEMORY_THRESHOLD)} (Avg: ${String.format("%.1f", report.avgMemoryUsage)}MB)")
            appendLine("Threads: ${getPerformanceRating(report.avgThreadCount.toDouble(), HIGH_THREAD_THRESHOLD.toDouble(), CRITICAL_THREAD_THRESHOLD.toDouble())} (Avg: ${report.avgThreadCount})")
            appendLine()

            appendLine("=== NEXT STEPS ===")
            if (criticalIssues.isNotEmpty()) {
                appendLine("1. 🚨 Address critical issues immediately")
                appendLine("2. 📊 Re-run performance monitoring after fixes")
            }
            if (highIssues.isNotEmpty()) {
                appendLine("${if (criticalIssues.isNotEmpty()) "3" else "1"}. ⚠️ Plan fixes for high-priority issues")
            }
            appendLine("${if (insights.isNotEmpty()) "${insights.size + 1}" else "1"}. 📈 Continue monitoring performance trends")
            appendLine("${if (insights.isNotEmpty()) "${insights.size + 2}" else "2"}. 🔄 Set up automated performance alerts")
        }
    }

    private fun getPerformanceRating(value: Double, highThreshold: Double, criticalThreshold: Double): String {
        return when {
            value > criticalThreshold -> "🔴 CRITICAL"
            value > highThreshold -> "🟡 HIGH"
            value > highThreshold * 0.7 -> "🟢 GOOD"
            else -> "✅ EXCELLENT"
        }
    }

    fun getOptimizationSuggestions(report: CpuMonitoring.PerformanceReport): List<String> {
        val suggestions = mutableListOf<String>()

        // Based on your specific report data
        if (report.maxThreadCount > 70) {
            suggestions.add("Reduce DefaultDispatcher worker threads - consider using Dispatchers.IO for I/O operations")
            suggestions.add("Implement thread pool size limits in your coroutine dispatchers")
            suggestions.add("Review WorkManager configuration to prevent excessive background work")
        }

        if (report.maxCpuUsage > 20) {
            suggestions.add("Profile RenderThread activity - optimize UI updates and drawing operations")
            suggestions.add("Consider using RecyclerView optimizations if displaying lists")
            suggestions.add("Cache expensive calculations and reuse results")
        }

        if (report.maxMemoryUsage > 140) {
            suggestions.add("Implement proper image loading with memory management (Glide/Picasso)")
            suggestions.add("Use memory-efficient data structures (ArrayMap vs HashMap for small collections)")
            suggestions.add("Review Realm database queries for efficiency")
        }

        return suggestions
    }
}