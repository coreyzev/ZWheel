package com.zwheel.core.protocol

import com.zwheel.core.model.BoardType

object BoardTypeDetector {
    // Ranges are approximate — confirm against OWCE OWBoard.cs when other board types are available.
    // Anchor verified: HW 4209 (Corey's XR) → BoardType.XR ✓
    fun detect(hardwareRevision: Int): BoardType = when (hardwareRevision) {
        in 1..2999 -> BoardType.ONEWHEEL_V1
        in 3000..3999 -> BoardType.PLUS
        in 4000..4999 -> BoardType.XR
        in 5000..5999 -> BoardType.PINT
        in 7000..7999 -> BoardType.PINT_X
        else -> BoardType.UNKNOWN
    }
}
