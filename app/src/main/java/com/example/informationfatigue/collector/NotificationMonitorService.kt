package com.example.informationfatigue.collector

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.concurrent.atomic.AtomicInteger

/**
 * Monitors notifications that the user interacts with (clicks or dismisses).
 * Requires BIND_NOTIFICATION_LISTENER_SERVICE permission and user enabling
 * notification access in device Settings.
 *
 * API 24+ provides the reason parameter in onNotificationRemoved().
 */
class NotificationMonitorService : NotificationListenerService() {

    companion object {
        private val interactionCount = AtomicInteger(0)

        /**
         * Get the current count and reset to 0 atomically.
         */
        fun getAndResetCount(): Int {
            return interactionCount.getAndSet(0)
        }

        /**
         * Get the current count without resetting.
         */
        fun getCount(): Int {
            return interactionCount.get()
        }
    }

    override fun onNotificationRemoved(
        sbn: StatusBarNotification,
        rankingMap: RankingMap,
        reason: Int
    ) {
        super.onNotificationRemoved(sbn, rankingMap, reason)
        // REASON_CLICK = 1, REASON_CANCEL = 2 (user swipe dismiss)
        // On API 24+ these constants are available
        when (reason) {
            REASON_CLICK,    // User tapped the notification
            REASON_CANCEL -> // User swiped away / dismissed
            {
                interactionCount.incrementAndGet()
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        // Service connected — nothing special needed
    }
}
