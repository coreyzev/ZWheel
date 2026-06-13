package com.zwheel.core.calc

/** Pure SI→display unit conversions. Internal state is always SI (m/s, meters, °C). */
object UnitConversions {
    fun metersPerSecondToMph(metersPerSecond: Double): Double = metersPerSecond * 2.236936

    fun metersPerSecondToKph(metersPerSecond: Double): Double = metersPerSecond * 3.6

    fun metersToMiles(meters: Double): Double = meters / 1609.344

    fun kilometersToMiles(kilometers: Double): Double = kilometers / 1.609344

    fun celsiusToFahrenheit(celsius: Double): Double = celsius * 9.0 / 5.0 + 32.0
}
