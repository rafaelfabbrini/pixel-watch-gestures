package com.rfabbrini.wristmarkread.notification

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.rfabbrini.wristmarkread.LOG_TAG

/**
 * Observes every notification the watch can see and keeps [MarkReadRepository] pointed at the
 * newest one that exposes a mark-as-read action.
 *
 * The service holds no state of its own: after a process restart the system reconnects it and
 * [onListenerConnected] rebuilds everything from the currently active notifications.
 */
class MarkReadNotificationListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        MarkReadRepository.setListenerConnected(true)
        rescanActiveNotifications("listener connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        MarkReadRepository.setListenerConnected(false)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        Log.d(LOG_TAG, "Notification posted: pkg=${sbn.packageName} key=${sbn.key}")

        val target = MarkAsReadDetector.detect(sbn)
        if (target == null) {
            Log.d(LOG_TAG, "No mark-as-read action on ${sbn.packageName}; keeping the previous target")
            // An unrelated notification must not clear a perfectly good target.
            return
        }

        Log.i(
            LOG_TAG,
            "Mark-as-read action detected: pkg=${target.packageName} match=${target.matchSource} " +
                "semanticAction=${target.semanticAction} label='${target.actionLabel}'",
        )
        MarkReadRepository.setTarget(target)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        if (sbn.key == MarkReadRepository.currentKey()) {
            Log.i(LOG_TAG, "Tracked notification removed (key=${sbn.key}); looking for a replacement")
            rescanActiveNotifications("tracked notification removed")
        }
    }

    /**
     * Rebuilds the current target from the live notification set, newest first. Used on connect and
     * whenever the tracked notification disappears.
     */
    private fun rescanActiveNotifications(reason: String) {
        val active = try {
            activeNotifications
        } catch (e: SecurityException) {
            // Thrown when the listener is not (yet) bound - nothing to rebuild from.
            Log.w(LOG_TAG, "Cannot read active notifications ($reason)", e)
            null
        }

        if (active == null) {
            MarkReadRepository.setTarget(null)
            return
        }

        val target = active
            .sortedByDescending { it.postTime }
            .firstNotNullOfOrNull { MarkAsReadDetector.detect(it) }

        Log.i(LOG_TAG, "Rescanned ${active.size} active notification(s) after: $reason")
        MarkReadRepository.setTarget(target)
    }
}
