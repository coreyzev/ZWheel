package com.zwheel.core.protocol

import com.zwheel.core.model.BoardType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BoardTypeDetectorTest {
    @Test
    fun `detects Coreys XR hardware revision`() {
        assertEquals(BoardType.XR, BoardTypeDetector.detect(4209))
    }

    @Test
    fun `detects V1 hardware revision`() {
        assertEquals(BoardType.ONEWHEEL_V1, BoardTypeDetector.detect(2999))
    }

    @Test
    fun `detects Plus hardware revision`() {
        assertEquals(BoardType.PLUS, BoardTypeDetector.detect(3000))
    }

    @Test
    fun `detects Pint hardware revision`() {
        assertEquals(BoardType.PINT, BoardTypeDetector.detect(5000))
    }

    @Test
    fun `detects Pint X hardware revision`() {
        assertEquals(BoardType.PINT_X, BoardTypeDetector.detect(7000))
    }

    @Test
    fun `detects unknown out of range hardware revision`() {
        assertEquals(BoardType.UNKNOWN, BoardTypeDetector.detect(0))
    }
}
