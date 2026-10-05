package com.gestores.app

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/** Android despierta este servicio cuando llega un gesto de un reloj Wear OS, aunque la app esté cerrada. */
class PhoneListenerService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != Proto.PATH_GESTURE) return
        val gesture = String(event.data)
        // Pasa por el mismo filtro: horario y confirmación (repitiendo el gesto en el reloj)
        val result = runBlocking { withTimeoutOrNull(9000) { Gate.handle(this@PhoneListenerService, gesture) } }
            ?: "⚠ La acción tardó demasiado"
        Wearable.getMessageClient(this).sendMessage(event.sourceNodeId, Proto.PATH_ACK, result.toByteArray())
    }
}
