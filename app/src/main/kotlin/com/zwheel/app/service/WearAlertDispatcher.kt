package com.zwheel.app.service

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.Wearable
import com.zwheel.core.alerts.AlertTone
import com.zwheel.core.alerts.AlertType
import com.zwheel.core.model.ALERT_MESSAGE_PATH

internal class WearAlertDispatcher(private val context: Context) {
    private val messageClient = Wearable.getMessageClient(context)
    private val nodeClient = Wearable.getNodeClient(context)

    /** Send alert to all connected watch nodes. Returns immediately; delivery is async. */
    fun fire(type: AlertType) {
        nodeClient.connectedNodes.addOnSuccessListener { nodes ->
            nodes.forEach { node ->
                messageClient.sendMessage(node.id, ALERT_MESSAGE_PATH, type.name.toByteArray())
            }
        }.addOnFailureListener { e ->
            Log.w("WearAlertDispatcher", "Could not query nodes: ${e.message}")
        }
    }

    /**
     * Send to watch if any node is reachable, otherwise play on phone.
     */
    fun fireAutoWithFallback(type: AlertType, fallback: PhoneAudioPlayer, tone: AlertTone) {
        nodeClient.connectedNodes
            .addOnSuccessListener { nodes ->
                if (nodes.isNotEmpty()) {
                    nodes.forEach { node ->
                        messageClient.sendMessage(node.id, ALERT_MESSAGE_PATH, type.name.toByteArray())
                    }
                } else {
                    fallback.play(tone)
                }
            }
            .addOnFailureListener {
                fallback.play(tone)
            }
    }
}
