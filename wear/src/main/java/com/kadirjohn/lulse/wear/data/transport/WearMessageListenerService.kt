package com.kadirjohn.lulse.wear.data.transport

import android.content.Context
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.kadirjohn.lulse.wear.data.health.SamsungHealthSensorSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Wear OS Data Layer listener — telefondan gelen mesajları alır (docs 01, 05).
 *
 * Manifest'te declare edilir (BIND_LISTENER). Activity kapalıyken de çalışır
 * — bu yüzden [WearTransportRepository] singleton olarak paylaşılır
 * ([TransportHolder] ile, process scope).
 */
class WearMessageListenerService : WearableListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(message: MessageEvent) {
        val transport = TransportHolder.get(this)
        transport.handleMessage(message.path, message.data, message.sourceNodeId)
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}

/**
 * Process-scoped [WearTransportRepository] singleton — hem Activity hem
 * [WearMessageListenerService] aynı instance'ı paylaşır (docs planı Adım 4).
 */
object TransportHolder {
    @Volatile private var instance: WearTransportRepository? = null

    fun get(context: Context): WearTransportRepository {
        return instance ?: synchronized(this) {
            instance ?: create(context).also { instance = it }
        }
    }

    private fun create(context: Context): WearTransportRepository {
        val app = context.applicationContext
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val healthSource = SamsungHealthSensorSource(app, scope)
        val transport = WearTransportRepository(app, healthSource, scope)
        transport.advertiseCapability()
        return transport
    }
}
