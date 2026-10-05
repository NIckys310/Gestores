package com.gestores.app

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService

/** Recibe del celular las reglas de cada gesto y la dirección del PC. */
class WearDataService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        events.forEach { e ->
            if (e.type == DataEvent.TYPE_CHANGED && e.dataItem.uri.path == Proto.PATH_CONFIG) {
                DataMapItem.fromDataItem(e.dataItem).dataMap.getString("json")?.let { ConfigStore.saveJson(this, it) }
            }
        }
    }
}
