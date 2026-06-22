package com.zwheel.app.ui.history

import androidx.compose.ui.graphics.toArgb
import com.zwheel.app.ui.ZWheelDarkColors
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline

fun speedToArgb(speedMps: Double?): Int {
    val c = ZWheelDarkColors
    if (speedMps == null) return c.textDim.toArgb()
    val t = (speedMps / 10.0).coerceIn(0.0, 1.0).toFloat()
    return when {
        t <= 0.5f -> lerpColor(c.cyan.toArgb(), c.rampCaution.toArgb(), t * 2f)
        else -> lerpColor(c.rampCaution.toArgb(), c.rampDanger.toArgb(), (t - 0.5f) * 2f)
    }
}

private fun lerpColor(from: Int, to: Int, fraction: Float): Int {
    val f = fraction.coerceIn(0f, 1f)
    fun ch(fromC: Int, toC: Int) = (fromC + ((toC - fromC) * f)).toInt().coerceIn(0, 255)
    val a = ch((from ushr 24) and 0xFF, (to ushr 24) and 0xFF)
    val r = ch((from ushr 16) and 0xFF, (to ushr 16) and 0xFF)
    val g = ch((from ushr 8) and 0xFF, (to ushr 8) and 0xFF)
    val b = ch(from and 0xFF, to and 0xFF)
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}

fun MapView.applySpeedColoredRoute(
    points: List<Triple<Double, Double, Double?>>,
    strokeWidthPx: Float = 10f,
) {
    overlays.clear()
    for (i in 0 until points.size - 1) {
        val (lat1, lon1, spd) = points[i]
        val (lat2, lon2, _) = points[i + 1]
        val segment = Polyline().apply {
            setPoints(listOf(GeoPoint(lat1, lon1), GeoPoint(lat2, lon2)))
            outlinePaint.color = speedToArgb(spd)
            outlinePaint.strokeWidth = strokeWidthPx
        }
        overlays.add(segment)
    }
    points.firstOrNull()?.let { (lat, lon, _) ->
        val marker = Polyline().apply {
            setPoints(buildCirclePoints(lat, lon, radiusDeg = 0.00005))
            outlinePaint.color = ZWheelDarkColors.lime.toArgb()
            outlinePaint.strokeWidth = strokeWidthPx
        }
        overlays.add(marker)
    }
    points.lastOrNull()?.let { (lat, lon, _) ->
        val marker = Polyline().apply {
            setPoints(buildCirclePoints(lat, lon, radiusDeg = 0.00004))
            outlinePaint.color = ZWheelDarkColors.rampDanger.toArgb()
            outlinePaint.strokeWidth = strokeWidthPx * 2.5f
        }
        overlays.add(marker)
    }
    invalidate()
}

private fun buildCirclePoints(lat: Double, lon: Double, radiusDeg: Double): List<GeoPoint> {
    val n = 16
    return List(n + 1) { i ->
        val angle = 2 * Math.PI * i / n
        GeoPoint(lat + radiusDeg * Math.sin(angle), lon + radiusDeg * Math.cos(angle))
    }
}
