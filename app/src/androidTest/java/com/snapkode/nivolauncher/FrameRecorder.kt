package com.snapkode.nivolauncher

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.FrameMetrics
import android.view.Window

/** Records real frame timings of a window while a gesture runs, and logs a one-line summary under the tag PERF. */
class FrameRecorder(private val activity: Activity, private val label: String) {
    private val total = ArrayList<Long>()
    private val layout = ArrayList<Long>()
    private val draw = ArrayList<Long>()
    private val input = ArrayList<Long>()
    private val listener = Window.OnFrameMetricsAvailableListener { _, m, _ ->
        synchronized(total) {
            total += m.getMetric(FrameMetrics.TOTAL_DURATION)
            layout += m.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION)
            draw += m.getMetric(FrameMetrics.DRAW_DURATION)
            input += m.getMetric(FrameMetrics.INPUT_HANDLING_DURATION)
        }
    }

    fun start() = activity.window.addOnFrameMetricsAvailableListener(listener, Handler(Looper.getMainLooper()))

    fun stop() {
        activity.window.removeOnFrameMetricsAvailableListener(listener)
        synchronized(total) {
            if (total.isEmpty()) { Log.d("PERF", "$label: no frames"); return }
            fun pct(l: List<Long>, p: Double) = l.sorted()[((l.size - 1) * p).toInt()] / 1e6
            val over16 = total.count { it > 16_700_000 }
            val over32 = total.count { it > 33_400_000 }
            Log.d(
                "PERF",
                "%s: frames=%d total p50=%.1f p90=%.1f p99=%.1f max=%.1fms | >16.7ms=%d >33ms=%d | layout p90=%.1f draw p90=%.1f input p90=%.1f".format(
                    label, total.size, pct(total, .5), pct(total, .9), pct(total, .99), total.max() / 1e6, over16, over32,
                    pct(layout, .9), pct(draw, .9), pct(input, .9),
                ),
            )
        }
    }
}
