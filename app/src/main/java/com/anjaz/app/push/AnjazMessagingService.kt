package com.anjaz.app.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AnjazMessagingService : FirebaseMessagingService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        scope.launch { runCatching { DeviceTokens.store(token) } }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val kind = message.data["kind"].orEmpty()
        val n = message.notification
        Notifications.show(
            applicationContext, (message.messageId ?: kind).hashCode(), n?.title.orEmpty().ifEmpty { "إنجاز" }, n?.body.orEmpty(),
            high = kind == "sos", route = routeFor(kind, message.data["id"]),
        )
    }

    private fun routeFor(kind: String, id: String?): String? = when (kind) {
        "sos" -> "sos"
        "chat" -> id?.let { "room/$it" }
        "task" -> "tasks"
        "report" -> "reports"
        "call" -> id?.let { "call/$it" }
        else -> null
    }
}
