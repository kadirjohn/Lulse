package com.kadirjohn.lulse.data.wear

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

/**
 * Telefon tarafı Wear OS Data Layer listener (docs 01, 05).
 *
 * Manifest'te declare edilir (BIND_LISTENER). Activity kapalıyken de çalışır —
 * bu yüzden [WearConnectionRepository]'yi process-scoped singleton olarak paylaşır
 * ([WearConnectionHolder] ile). Watch mesajları activity'sizken de işlenir.
 *
 * Watch opsiyoneldir: service hiç mesaj almazsa telefon normal çalışır.
 */
class WearMessageListenerService : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        val repo = WearConnectionHolder.get(this)
        repo.handleMessage(event.path, event.data, event.sourceNodeId)
    }
}

/**
 * Process-scoped [WearConnectionRepository] singleton — hem service hem
 * [MainViewModel] aynı instance'ı paylaşır (docs planı Adım 3).
 * Repository Application context ile kurulur.
 */
object WearConnectionHolder {
    @Volatile private var instance: WearConnectionRepository? = null

    fun get(context: android.content.Context): WearConnectionRepository {
        return instance ?: synchronized(this) {
            instance ?: create(context).also { instance = it }
        }
    }

    private fun create(context: android.content.Context): WearConnectionRepository {
        val app = context.applicationContext
        return WearConnectionRepository(app)
    }
}