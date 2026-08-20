package com.rfabbrini.wristmarkread

import android.content.ActivityNotFoundException
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.rfabbrini.wristmarkread.gesture.detectGestureSupport
import com.rfabbrini.wristmarkread.notification.MarkReadRepository
import com.rfabbrini.wristmarkread.notification.NotificationAccess
import com.rfabbrini.wristmarkread.ui.WristMarkReadApp

/**
 * The only screen of the app.
 *
 * The wrist-turn gesture is delivered by the Wear OS one-handed gesture framework to whichever
 * composable currently owns it, which means it only fires while this UI is in the foreground. That
 * is a platform boundary, not a shortcut: no third-party app can take over the gesture inside the
 * system notification stream.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val gestureSupport = detectGestureSupport(this)
        Log.i(LOG_TAG, "Gesture support on this device: $gestureSupport (API ${Build.VERSION.SDK_INT})")

        setContent {
            val state by MarkReadRepository.uiState.collectAsState()
            WristMarkReadApp(
                state = state,
                gestureSupport = gestureSupport,
                onOpenNotificationSettings = ::openNotificationAccessSettings,
                // Both the button and the gesture end up in this one call.
                onMarkAsRead = { source -> MarkReadRepository.markCurrentNotificationAsRead(source) },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        // Notification access can be granted or revoked while the app sits in the background.
        val granted = NotificationAccess.isGranted(this)
        Log.i(LOG_TAG, "Notification access granted=$granted")
        MarkReadRepository.setNotificationAccessGranted(granted)
    }

    private fun openNotificationAccessSettings() {
        try {
            startActivity(NotificationAccess.settingsIntent(this))
        } catch (e: ActivityNotFoundException) {
            Log.w(
                LOG_TAG,
                "No settings activity could be opened. Grant access from the paired phone, or run: " +
                    "adb shell cmd notification allow_listener " +
                    NotificationAccess.listenerComponent(this).flattenToString(),
                e,
            )
        }
    }
}
