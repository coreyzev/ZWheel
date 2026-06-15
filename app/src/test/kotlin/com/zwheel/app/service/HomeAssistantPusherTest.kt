package com.zwheel.app.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeAssistantPusherTest {

    @Test
    fun `haBody embeds percent as state`() {
        val body = haBody(42)
        assertTrue(body.contains(""""state":"42""""), "expected state=42 in: $body")
    }

    @Test
    fun `haBody includes battery device class`() {
        val body = haBody(0)
        assertTrue(body.contains(""""device_class":"battery""""), "missing device_class: $body")
    }

    @Test
    fun `haEndpoint appends correct HA path`() {
        assertEquals(
            "http://homeassistant.local:8123/api/states/sensor.onewheel_battery",
            haEndpoint("http://homeassistant.local:8123"),
        )
    }

    @Test
    fun `haEndpoint strips trailing slash before appending path`() {
        assertEquals(
            "http://homeassistant.local:8123/api/states/sensor.onewheel_battery",
            haEndpoint("http://homeassistant.local:8123/"),
        )
    }

    @Test
    fun `haEndpoint works with https`() {
        val endpoint = haEndpoint("https://ha.example.com")
        assertTrue(endpoint.startsWith("https://"), "should preserve https: $endpoint")
        assertTrue(endpoint.endsWith("/api/states/sensor.onewheel_battery"))
    }
}
