package com.zwheel.core.protocol

import com.zwheel.core.model.BoardType

object BoardTypeDetector {
    // XR open-architecture revisions (accept battery/BMS mods without chipping):
    //   4206, 4208, 4209
    // XR locked revisions (DRM pairing; blocks unchipped battery mods):
    //   4210–4213
    // Anchor verified: HW 4209 (Corey's XR) → BoardType.XR ✓
    //
    // XRC (Onewheel XR Classic) uses modern 75V architecture with a separate hardware block;
    // it does not appear in the 4000–4999 range. Detect it by BLE name before calling detect().
    fun detect(hardwareRevision: Int): BoardType = when (hardwareRevision) {
        in 1..2999 -> BoardType.ONEWHEEL_V1
        in 3000..3999 -> BoardType.PLUS
        4206, 4208, 4209, in 4210..4213 -> BoardType.XR
        in 5000..5999 -> BoardType.PINT
        in 7000..7999 -> BoardType.PINT_X
        else -> BoardType.UNKNOWN
    }

    // XRC broadcasts a distinct BLE name. Call this before detect() when the BLE name is available.
    fun detectFromBleName(bleName: String): BoardType? = when {
        bleName.startsWith("OwXRC", ignoreCase = true) -> BoardType.XRC
        else -> null
    }
}
