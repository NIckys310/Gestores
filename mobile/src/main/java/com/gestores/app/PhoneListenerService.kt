package com.gestores.app

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/** Android despierta este servicio cuando llega un gesto del reloj, aunque la app esté cerrada. */
class PhoneListenerService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != Proto.PATH_GESTURE) return
        val gesture = String(event.data)
        val result = runBlocking { withTimeoutOrNull(9000) { ActionRunner.runGesture(this@PhoneListenerService, gesture) } }
            ?: "⚠ La acción tardó demasiado"
        EventLog.add(this, gesture, result)
        // Responde al reloj para que muestre el resultado
        Wearable.getMessageClient(this).sendMessage(event.sourceNodeId, Proto.PATH_ACK, result.toByteArray())
    }
}
