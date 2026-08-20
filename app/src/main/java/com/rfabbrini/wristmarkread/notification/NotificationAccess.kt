package com.rfabbrini.wristmarkread.notification

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import com.rfabbrini.wristmarkread.LOG_TAG

/**
 * Notification access ("notification listener") is not a runtime permission - it is granted from
 * Settings or with adb, so the app can only observe it and offer a shortcut.
 */
object NotificationAccess {

    fun listenerComponent(context: Context): ComponentName =
        ComponentName(context, MarkReadNotificationListenerService::class.java)

    fun isGranted(context: Context): Boolean {
        val component = listenerComponent(context)

        val viaManager = runCatching {
            context.getSystemService(NotificationManager::class.java)
                ?.isNotificationListenerAccessGranted(component)
        }.getOrNull()
        if (viaManager == true) return true

        // Fallback: some builds only reflect the grant in Secure settings.
        val enabled = runCatching {
            Settings.Secure.getString(context.contentResolver, ENABLED_NOTIFICATION_LISTENERS)
        }.getOrNull().orEmpty()

        return enabled.split(':').any { entry ->
            entry.isNotBlank() && ComponentName.unflattenFromString(entry) == component
        }
    }

    /**
     * An intent that opens the notification-access screen, or the general Settings app when that
     * screen is not present (some Wear OS builds only expose the toggle on the paired phone).
     */
    fun settingsIntent(context: Context): Intent {
        val listenerSettings = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (listenerSettings.resolveActivity(context.packageManager) != null) return listenerSettings

        Log.w(LOG_TAG, "Notification listener settings not available on this device; opening Settings instead")
        return Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private const val ENABLED_NOTIFICATION_LISTENERS = "enabled_notification_listeners"
}
