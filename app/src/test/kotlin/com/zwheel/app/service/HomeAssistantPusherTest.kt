package com.zwheel.app.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeAssistantPusherTest {

    @Test
    fun `haBody embeds percent as state`() {
        val body = haBody(42, "My XR Battery")
        assertTrue(body.contains(""""state":"42""""), "expected state=42 in: $body")
    }

    @Test
    fun `haBody includes battery device class`() {
        val body = haBody(0, "My XR Battery")
        assertTrue(body.contains(""""device_class":"battery""""), "missing device_class: $body")
    }

    @Test
    fun `haBody includes friendly name`() {
        val body = haBody(72, "Pint X Battery")
        assertTrue(body.contains(""""friendly_name":"Pint X Battery""""), "missing friendly_name: $body")
    }

    @Test
    fun `haEntitySlug lowercases and sanitizes serial`() {
        assertEquals("zwheel_abc123_battery", haEntitySlug("ABC123"))
    }

    @Test
    fun `haEntitySlug replaces non-alphanumeric with underscore`() {
        assertEquals("zwheel_a1_b2_battery", haEntitySlug("A1-B2"))
    }

    @Test
    fun `haEntitySlug collapses multiple separators`() {
        assertEquals("zwheel_a1_b2_battery", haEntitySlug("A1--B2"))
    }

    @Test
    fun `haEndpoint appends correct HA path for given slug`() {
        assertEquals(
            "http://homeassistant.local:8123/api/states/sensor.zwheel_12345_battery",
            haEndpoint("http://homeassistant.local:8123", "zwheel_12345_battery"),
        )
    }

    @Test
    fun `haEndpoint strips trailing slash before appending path`() {
        assertEquals(
            "http://homeassistant.local:8123/api/states/sensor.zwheel_12345_battery",
            haEndpoint("http://homeassistant.local:8123/", "zwheel_12345_battery"),
        )
    }

    @Test
    fun `haEndpoint works with https`() {
        val endpoint = haEndpoint("https://ha.example.com", "zwheel_12345_battery")
        assertTrue(endpoint.startsWith("https://"), "should preserve https: $endpoint")
        assertTrue(endpoint.endsWith("/api/states/sensor.zwheel_12345_battery"))
    }
}
