package com.zwheel.core.protocol

data class HandshakeResult(
    val unlocked: Boolean,
    val strategyName: String,
    val message: String? = null,
)

sealed interface KeepAliveAction {
    data class Write(
        val characteristicId: GattCharacteristicId,
        val value: ByteArray,
    ) : KeepAliveAction
}
