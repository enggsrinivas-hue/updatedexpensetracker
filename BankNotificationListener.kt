package com.expensetracker.sms

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.expensetracker.sms.data.TransactionRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface NotificationEntryPoint {
    fun repository(): TransactionRepository
}

/**
 * Reads notifications from the phone's SMS app only. Anything that is not a bank
 * transaction is dropped by the parser and never stored. Everything stays on the phone.
 */
class BankNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val repository: TransactionRepository by lazy {
        EntryPointAccessors
            .fromApplication(applicationContext, NotificationEntryPoint::class.java)
            .repository()
    }

    override fun onListenerConnected() {
        // Pick up bank alerts that are still sitting in the notification shade.
        try {
            activeNotifications?.forEach { handle(it) }
        } catch (_: Exception) {
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = handle(sbn)

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun handle(sbn: StatusBarNotification) {
        if (sbn.packageName !in SMS_APPS) return
        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()

        val items = mutableListOf<Triple<String, String, Long>>() // sender, body, time
        @Suppress("DEPRECATION")
        val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        if (messages != null) {
            Notification.MessagingStyle.Message.getMessagesFromBundleArray(messages).forEach { m ->
                items += Triple(title, m.text?.toString().orEmpty(), m.timestamp)
            }
        } else {
            val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
            items += Triple(title, text, sbn.postTime)
        }

        scope.launch {
            items.forEach { (sender, body, time) ->
                if (body.isNotBlank()) repository.ingest(sender, body, time)
            }
        }
    }

    private companion object {
        val SMS_APPS = setOf(
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging",
            "com.android.mms",
            "com.android.messaging",
            "com.oneplus.mms",
            "com.miui.mms"
        )
    }
}
